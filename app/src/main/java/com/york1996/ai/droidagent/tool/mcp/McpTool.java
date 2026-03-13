package com.york1996.ai.droidagent.tool.mcp;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonObject;
import com.york1996.ai.droidagent.tool.Tool;
import com.york1996.ai.droidagent.tool.ToolResult;

/**
 * 将 MCP server 上的单个工具包装为 Tool，无缝接入 ToolRegistry。
 */
public class McpTool implements Tool {

    private static final String TAG = "McpTool";

    private final McpClient client;
    private final McpToolDef def;

    public McpTool(McpClient client, McpToolDef def) {
        this.client = client;
        this.def = def;
    }

    @Override public String getName() { return def.name; }
    @Override public String getDescription() { return def.description; }
    @Override public JsonObject getParametersSchema() { return def.inputSchema; }

    @Override
    public ToolResult execute(JsonObject params, Context context) {
        try {
            String result = client.callTool(def.name, params);
            return ToolResult.ok(result);
        } catch (Exception e) {
            Log.e(TAG, "MCP tool call failed: " + def.name, e);
            return ToolResult.error("MCP error: " + e.getMessage());
        }
    }
}
