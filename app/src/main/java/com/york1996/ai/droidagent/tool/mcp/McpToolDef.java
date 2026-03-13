package com.york1996.ai.droidagent.tool.mcp;

import com.google.gson.JsonObject;

/** 从 MCP server tools/list 拿到的工具元数据 */
public class McpToolDef {
    public final String name;
    public final String description;
    public final JsonObject inputSchema;

    public McpToolDef(String name, String description, JsonObject inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }
}
