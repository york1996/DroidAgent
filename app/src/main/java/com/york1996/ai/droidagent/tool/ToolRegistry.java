package com.york1996.ai.droidagent.tool;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.york1996.ai.droidagent.llm.model.ToolCall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表：注册、查找工具，生成 OpenAI tools 定义数组
 */
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public void register(Tool tool) {
        tools.put(tool.getName(), tool);
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public List<String> getToolNames() {
        return new ArrayList<>(tools.keySet());
    }

    /**
     * 生成 OpenAI Chat Completions 的 tools 数组
     * [{"type":"function","function":{"name":"...","description":"...","parameters":{...}}},...]
     */
    public JsonArray buildToolDefinitions() {
        JsonArray arr = new JsonArray();
        for (Tool tool : tools.values()) {
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("type", "function");

            JsonObject fn = new JsonObject();
            fn.addProperty("name", tool.getName());
            fn.addProperty("description", tool.getDescription());
            fn.add("parameters", tool.getParametersSchema());

            wrapper.add("function", fn);
            arr.add(wrapper);
        }
        return arr;
    }

    /**
     * 根据 LLM 返回的 ToolCall 执行对应工具
     */
    public ToolResult execute(ToolCall toolCall, Context context) {
        Tool tool = tools.get(toolCall.name);
        if (tool == null) {
            return ToolResult.error("Unknown tool: " + toolCall.name);
        }
        try {
            JsonObject params = JsonParser.parseString(toolCall.arguments).getAsJsonObject();
            return tool.execute(params, context);
        } catch (Exception e) {
            return ToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }
}
