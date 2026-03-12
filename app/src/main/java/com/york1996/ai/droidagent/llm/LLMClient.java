package com.york1996.ai.droidagent.llm;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.york1996.ai.droidagent.agent.AgentConfig;
import com.york1996.ai.droidagent.llm.model.LLMChatMessage;
import com.york1996.ai.droidagent.llm.model.LLMResponse;
import com.york1996.ai.droidagent.llm.model.ToolCall;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

/**
 * OpenAI 兼容的 LLM 客户端
 * 支持普通模式和 SSE 流式输出（stream=true）
 */
public class LLMClient {

    private static final String TAG = "LLMClient";
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");
    private static final String SSE_PREFIX = "data: ";
    private static final String SSE_DONE   = "[DONE]";

    /** 流式 token 回调（在调用线程上触发，调用方自行切换至主线程） */
    public interface TokenCallback {
        void onToken(String token);
    }

    private final AgentConfig config;
    private final OkHttpClient httpClient;

    public LLMClient(AgentConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    // ───────────────────────── Public API ─────────────────────────

    /** 普通（非流式）调用 */
    public LLMResponse chat(List<LLMChatMessage> messages, JsonArray toolsJsonArr) throws IOException {
        JsonObject body = buildRequestBody(messages, toolsJsonArr, false);
        String responseJson = post(config.getChatUrl(), body.toString());
        return parseFullResponse(responseJson);
    }

    /**
     * 流式调用（SSE）
     * 内容 token 会实时通过 {@code tokenCallback} 回调；
     * 工具调用响应不产生 token 回调，完整 ToolCall 列表通过返回值获取。
     *
     * @param tokenCallback 每个内容 token 的回调，在调用线程触发
     */
    public LLMResponse chatStream(List<LLMChatMessage> messages, JsonArray toolsJsonArr,
                                  TokenCallback tokenCallback) throws IOException {
        JsonObject body = buildRequestBody(messages, toolsJsonArr, true);

        Request request = new Request.Builder()
                .url(config.getChatUrl())
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON_MEDIA))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                String err = response.body() != null ? response.body().string() : "(no body)";
                throw new IOException("HTTP " + response.code() + ": " + err);
            }
            return parseSseResponse(response.body().source(), tokenCallback);
        }
    }

    /** 生成 Embedding 向量；失败时返回 null（降级为关键词向量） */
    public float[] embed(String text) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", config.getEmbeddingModel());
            body.addProperty("input", text);

            String responseJson = post(config.getEmbeddingUrl(), body.toString());
            JsonObject resp = JsonParser.parseString(responseJson).getAsJsonObject();
            JsonArray data = resp.getAsJsonArray("data");
            if (data != null && data.size() > 0) {
                JsonArray embedding = data.get(0).getAsJsonObject().getAsJsonArray("embedding");
                float[] vec = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vec[i] = embedding.get(i).getAsFloat();
                }
                return vec;
            }
        } catch (Exception e) {
            Log.w(TAG, "Embedding failed, fallback to keyword vector: " + e.getMessage());
        }
        return null;
    }

    // ───────────────────────── Request Builder ─────────────────────────

    private JsonObject buildRequestBody(List<LLMChatMessage> messages,
                                        JsonArray toolsJsonArr, boolean stream) {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.getChatModel());
        body.addProperty("max_tokens", config.getMaxTokens());
        body.addProperty("temperature", config.getTemperature());
        body.addProperty("stream", stream);

        JsonArray msgsArr = new JsonArray();
        for (LLMChatMessage msg : messages) {
            msgsArr.add(msg.toJson());
        }
        body.add("messages", msgsArr);

        if (toolsJsonArr != null && toolsJsonArr.size() > 0) {
            body.add("tools", toolsJsonArr);
            body.addProperty("tool_choice", "auto");
        }
        return body;
    }

    // ───────────────────────── Full Response Parser ─────────────────────────

    private LLMResponse parseFullResponse(String json) {
        JsonObject resp = JsonParser.parseString(json).getAsJsonObject();

        if (resp.has("error")) {
            String errMsg = resp.getAsJsonObject("error").get("message").getAsString();
            throw new RuntimeException("LLM API Error: " + errMsg);
        }

        JsonArray choices = resp.getAsJsonArray("choices");
        JsonObject choice = choices.get(0).getAsJsonObject();
        String finishReason = choice.get("finish_reason").getAsString();
        JsonObject message = choice.getAsJsonObject("message");

        String content = null;
        if (message.has("content") && !message.get("content").isJsonNull()) {
            content = message.get("content").getAsString();
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        JsonArray rawToolCalls = null;
        if (message.has("tool_calls") && !message.get("tool_calls").isJsonNull()) {
            rawToolCalls = message.getAsJsonArray("tool_calls");
            for (JsonElement el : rawToolCalls) {
                JsonObject tc = el.getAsJsonObject();
                String id = tc.get("id").getAsString();
                JsonObject fn = tc.getAsJsonObject("function");
                toolCalls.add(new ToolCall(id, fn.get("name").getAsString(),
                        fn.get("arguments").getAsString()));
            }
        }

        return new LLMResponse(content, toolCalls, finishReason, rawToolCalls);
    }

    // ───────────────────────── SSE Stream Parser ─────────────────────────

    /**
     * 逐行读取 SSE 流，实时回调内容 token，并组装完整 {@link LLMResponse}。
     * <p>
     * OpenAI SSE 格式：每行以 "data: " 开头，最后一行为 "data: [DONE]"。
     * 工具调用的 arguments 分多个 chunk 累积。
     */
    private LLMResponse parseSseResponse(BufferedSource source,
                                         TokenCallback tokenCallback) throws IOException {
        StringBuilder contentBuilder = new StringBuilder();
        // key = tool_call index（支持同一次响应多个工具调用）
        Map<Integer, ToolCallBuilder> tcBuilders = new LinkedHashMap<>();
        String finishReason = null;

        while (!source.exhausted()) {
            String line = source.readUtf8Line();
            if (line == null) break;
            if (!line.startsWith(SSE_PREFIX)) continue;

            String data = line.substring(SSE_PREFIX.length()).trim();
            if (SSE_DONE.equals(data)) break;
            if (data.isEmpty()) continue;

            try {
                JsonObject chunk = JsonParser.parseString(data).getAsJsonObject();
                JsonArray choices = chunk.getAsJsonArray("choices");
                if (choices == null || choices.size() == 0) continue;

                JsonObject choice = choices.get(0).getAsJsonObject();

                // finish_reason
                if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
                    finishReason = choice.get("finish_reason").getAsString();
                }

                JsonObject delta = choice.has("delta") ? choice.getAsJsonObject("delta") : null;
                if (delta == null) continue;

                // ── 内容 token ──
                if (delta.has("content") && !delta.get("content").isJsonNull()) {
                    String token = delta.get("content").getAsString();
                    if (!token.isEmpty()) {
                        contentBuilder.append(token);
                        if (tokenCallback != null) tokenCallback.onToken(token);
                    }
                }

                // ── 工具调用片段（逐字段累积） ──
                if (delta.has("tool_calls") && !delta.get("tool_calls").isJsonNull()) {
                    for (JsonElement el : delta.getAsJsonArray("tool_calls")) {
                        JsonObject tc = el.getAsJsonObject();
                        int idx = tc.has("index") ? tc.get("index").getAsInt() : 0;
                        ToolCallBuilder b = tcBuilders.computeIfAbsent(idx, i -> new ToolCallBuilder());

                        if (tc.has("id") && !tc.get("id").isJsonNull()) {
                            b.id = tc.get("id").getAsString();
                        }
                        if (tc.has("function") && !tc.get("function").isJsonNull()) {
                            JsonObject fn = tc.getAsJsonObject("function");
                            if (fn.has("name") && !fn.get("name").isJsonNull()) {
                                b.name = fn.get("name").getAsString();
                            }
                            if (fn.has("arguments") && !fn.get("arguments").isJsonNull()) {
                                b.argsBuilder.append(fn.get("arguments").getAsString());
                            }
                        }
                    }
                }

            } catch (Exception e) {
                Log.w(TAG, "Failed to parse SSE chunk: " + data, e);
            }
        }

        // 组装工具调用列表 + 重建 rawToolCallsJson（供消息历史使用）
        List<ToolCall> toolCalls = new ArrayList<>();
        JsonArray rawToolCalls = null;
        if (!tcBuilders.isEmpty()) {
            rawToolCalls = new JsonArray();
            for (ToolCallBuilder b : tcBuilders.values()) {
                toolCalls.add(new ToolCall(b.id, b.name, b.argsBuilder.toString()));
                JsonObject rawTc = new JsonObject();
                rawTc.addProperty("id", b.id);
                rawTc.addProperty("type", "function");
                JsonObject fn = new JsonObject();
                fn.addProperty("name", b.name);
                fn.addProperty("arguments", b.argsBuilder.toString());
                rawTc.add("function", fn);
                rawToolCalls.add(rawTc);
            }
        }

        String content = contentBuilder.length() > 0 ? contentBuilder.toString() : null;
        return new LLMResponse(content, toolCalls, finishReason, rawToolCalls);
    }

    // ───────────────────────── HTTP Helper ─────────────────────────

    private String post(String url, String jsonBody) throws IOException {
        RequestBody requestBody = RequestBody.create(jsonBody, JSON_MEDIA);
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.body() == null) {
                throw new IOException("Empty response body from: " + url);
            }
            String body = response.body().string();
            if (!response.isSuccessful()) {
                Log.e(TAG, "HTTP " + response.code() + " from " + url + ": " + body);
                throw new IOException("HTTP " + response.code() + ": " + body);
            }
            return body;
        }
    }

    // ───────────────────────── Inner Types ─────────────────────────

    /** 流式工具调用片段累积器 */
    private static class ToolCallBuilder {
        String id = "";
        String name = "";
        final StringBuilder argsBuilder = new StringBuilder();
    }
}
