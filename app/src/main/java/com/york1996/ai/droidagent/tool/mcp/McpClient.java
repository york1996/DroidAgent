package com.york1996.ai.droidagent.tool.mcp;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * MCP (Model Context Protocol) JSON-RPC client — Streamable HTTP transport.
 *
 * 协议流程：
 *   1. POST initialize  → 握手，可能拿到 Mcp-Session-Id
 *   2. POST notifications/initialized  → 通知服务端握手完成
 *   3. POST tools/list  → 获取工具列表
 *   4. POST tools/call  → 调用工具
 *
 * 所有请求都 POST 到同一个 endpoint。
 */
public class McpClient {

    private static final String TAG = "McpClient";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String serverUrl;
    private final OkHttpClient httpClient;
    private final AtomicInteger idSeq = new AtomicInteger(1);
    private String sessionId; // 服务端可能返回的 session

    public McpClient(String serverUrl) {
        this.serverUrl = serverUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 握手。必须在任何其他调用之前执行。
     * @return 握手成功返回 true
     */
    public boolean initialize() throws IOException {
        JsonObject clientInfo = new JsonObject();
        clientInfo.addProperty("name", "DroidAgent");
        clientInfo.addProperty("version", "1.0");

        JsonObject params = new JsonObject();
        params.addProperty("protocolVersion", "2024-11-05");
        params.add("capabilities", new JsonObject());
        params.add("clientInfo", clientInfo);

        JsonObject response = rpc("initialize", params);
        if (response == null || !response.has("result")) return false;

        // 握手完成通知（无需等响应）
        sendNotification("notifications/initialized", new JsonObject());
        return true;
    }

    /**
     * 获取服务端暴露的所有工具定义。
     */
    public List<McpToolDef> listTools() throws IOException {
        List<McpToolDef> tools = new ArrayList<>();
        JsonObject response = rpc("tools/list", new JsonObject());
        if (response == null || !response.has("result")) return tools;

        JsonObject result = response.getAsJsonObject("result");
        if (!result.has("tools")) return tools;

        for (JsonElement el : result.getAsJsonArray("tools")) {
            JsonObject t = el.getAsJsonObject();
            String name = t.get("name").getAsString();
            String desc = t.has("description") ? t.get("description").getAsString() : "";
            JsonObject schema = t.has("inputSchema")
                    ? t.getAsJsonObject("inputSchema") : new JsonObject();
            tools.add(new McpToolDef(name, desc, schema));
        }
        return tools;
    }

    /**
     * 调用指定工具，返回文本结果。
     *
     * @param toolName  工具名
     * @param arguments 工具参数（JSON 对象）
     */
    public String callTool(String toolName, JsonObject arguments) throws IOException {
        JsonObject params = new JsonObject();
        params.addProperty("name", toolName);
        params.add("arguments", arguments != null ? arguments : new JsonObject());

        JsonObject response = rpc("tools/call", params);
        if (response == null) return "Error: no response from MCP server";

        if (response.has("error")) {
            String msg = response.getAsJsonObject("error").get("message").getAsString();
            return "Error: " + msg;
        }

        JsonObject result = response.getAsJsonObject("result");
        if (!result.has("content")) return result.toString();

        // content 是 [{type, text}, ...] 数组，拼接所有 text 类型
        StringBuilder sb = new StringBuilder();
        for (JsonElement el : result.getAsJsonArray("content")) {
            JsonObject item = el.getAsJsonObject();
            if ("text".equals(item.get("type").getAsString())) {
                sb.append(item.get("text").getAsString());
            }
        }
        return sb.toString();
    }

    // ── 私有工具方法 ──────────────────────────────────────────────────────────

    private JsonObject rpc(String method, JsonObject params) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("jsonrpc", "2.0");
        body.addProperty("id", idSeq.getAndIncrement());
        body.addProperty("method", method);
        body.add("params", params);
        return post(body.toString());
    }

    /** 单向通知，无 id，不等待结果。 */
    private void sendNotification(String method, JsonObject params) {
        JsonObject body = new JsonObject();
        body.addProperty("jsonrpc", "2.0");
        body.addProperty("method", method);
        body.add("params", params);
        try { post(body.toString()); } catch (Exception ignored) {}
    }

    private JsonObject post(String json) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(serverUrl)
                .post(RequestBody.create(json, JSON))
                .header("Accept", "application/json, text/event-stream");

        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            // 保存服务端颁发的 session id
            String sid = response.header("Mcp-Session-Id");
            if (sid != null) sessionId = sid;

            if (response.body() == null) return null;
            String bodyStr = response.body().string();
            if (bodyStr.isBlank()) return null;

            // 高德等服务端有时以 SSE 格式返回，需提取 data: 行中的 JSON
            String contentType = response.header("Content-Type", "");
            if (contentType != null && contentType.contains("text/event-stream")) {
                bodyStr = extractJsonFromSse(bodyStr);
                if (bodyStr == null) return null;
            }

            return JsonParser.parseString(bodyStr).getAsJsonObject();
        }
    }

    /**
     * 从 SSE 响应体中提取最后一条有效 data: 行的 JSON。
     * SSE 格式：每行形如 "data: {...}" 或 "event: message" 等，空行为分隔符。
     */
    private static String extractJsonFromSse(String sseBody) {
        String lastData = null;
        for (String line : sseBody.split("\n")) {
            line = line.trim();
            if (line.startsWith("data:")) {
                String data = line.substring(5).trim();
                if (!data.isEmpty() && !data.equals("[DONE]")) {
                    lastData = data;
                }
            }
        }
        return lastData;
    }
}
