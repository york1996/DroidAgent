package com.york1996.ai.droidagent.tool;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 网络搜索工具
 * 默认使用 DuckDuckGo Instant Answer API（免费，无需 Key）
 * 可通过参数切换为其他搜索引擎
 */
public class WebSearchTool implements Tool {

    private static final String TAG = "WebSearchTool";
    private static final String DDG_URL = "https://api.duckduckgo.com/";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    @Override
    public String getName() { return "web_search"; }

    @Override
    public String getDescription() {
        return "Search the web for current information. Returns a summary and relevant links. "
                + "Use this when you need real-time or up-to-date information.";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();
        JsonObject query = new JsonObject();
        query.addProperty("type", "string");
        query.addProperty("description", "The search query");
        props.add("query", query);
        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("query");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, Context context) {
        if (!params.has("query")) {
            return ToolResult.error("Missing parameter: query");
        }
        String query = params.get("query").getAsString();
        try {
            return searchDDG(query);
        } catch (Exception e) {
            Log.e(TAG, "Search failed", e);
            return ToolResult.error("Search failed: " + e.getMessage());
        }
    }

    private ToolResult searchDDG(String query) throws IOException {
        String url = DDG_URL + "?q=" + Uri.encode(query)
                + "&format=json&no_html=1&skip_disambig=1&no_redirect=1";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "DroidAgent/1.0")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.body() == null) {
                return ToolResult.error("Empty response");
            }
            String body = response.body().string();
            return parseDDGResponse(query, body);
        }
    }

    private ToolResult parseDDGResponse(String query, String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            StringBuilder sb = new StringBuilder();
            sb.append("Search results for: \"").append(query).append("\"\n\n");

            // Abstract
            String abstractText = getStr(obj, "AbstractText");
            String abstractSource = getStr(obj, "AbstractSource");
            String abstractUrl = getStr(obj, "AbstractURL");
            if (!abstractText.isEmpty()) {
                sb.append("Summary (").append(abstractSource).append("):\n")
                        .append(abstractText).append("\n");
                if (!abstractUrl.isEmpty()) {
                    sb.append("Source: ").append(abstractUrl).append("\n");
                }
                sb.append("\n");
            }

            // Answer（短直接答案）
            String answer = getStr(obj, "Answer");
            if (!answer.isEmpty()) {
                sb.append("Direct Answer: ").append(answer).append("\n\n");
            }

            // Related topics
            JsonArray related = obj.getAsJsonArray("RelatedTopics");
            if (related != null && related.size() > 0) {
                sb.append("Related:\n");
                int count = 0;
                for (JsonElement el : related) {
                    if (count >= 5) break;
                    if (!el.isJsonObject()) continue;
                    JsonObject topic = el.getAsJsonObject();
                    String text = getStr(topic, "Text");
                    String link = getStr(topic, "FirstURL");
                    if (!text.isEmpty()) {
                        sb.append("• ").append(text);
                        if (!link.isEmpty()) sb.append("\n  ").append(link);
                        sb.append("\n");
                        count++;
                    }
                }
            }

            if (sb.toString().equals("Search results for: \"" + query + "\"\n\n")) {
                return ToolResult.ok("No results found for: \"" + query
                        + "\". Try a different search query.");
            }

            return ToolResult.ok(sb.toString().trim());
        } catch (Exception e) {
            return ToolResult.error("Failed to parse search results: " + e.getMessage());
        }
    }

    private String getStr(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "";
    }
}
