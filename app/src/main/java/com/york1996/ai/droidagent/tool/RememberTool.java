package com.york1996.ai.droidagent.tool;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.york1996.ai.droidagent.memory.LongTermMemory;

/**
 * 记忆存储工具 —— 让 Agent 主动决定何时写入长期记忆
 */
public class RememberTool implements Tool {

    public static final String NAME = "remember_memory";

    private final LongTermMemory longTermMemory;
    private volatile String sessionId = "default";

    public RememberTool(LongTermMemory longTermMemory) {
        this.longTermMemory = longTermMemory;
    }

    public void setSessionId(String sid) {
        this.sessionId = sid;
    }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() {
        return "Save important information to long-term memory for future reference. "
                + "Use this tool when: (1) the user explicitly asks you to remember something, "
                + "OR (2) you detect a clear user preference, habit, or personal fact that "
                + "would meaningfully improve future conversations. "
                + "Do NOT use this for ordinary Q&A exchanges.";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject content = new JsonObject();
        content.addProperty("type", "string");
        content.addProperty("description",
                "A self-contained statement to remember. e.g. 'User prefers concise answers.'");
        props.add("content", content);

        JsonObject category = new JsonObject();
        category.addProperty("type", "string");
        JsonArray enumVals = new JsonArray();
        enumVals.add("explicit");
        enumVals.add("trait");
        enumVals.add("fact");
        category.add("enum", enumVals);
        category.addProperty("description",
                "explicit=用户明确要求记住; trait=推断的偏好/习惯; fact=用户的客观信息");
        props.add("category", category);

        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("content");
        required.add("category");
        schema.add("required", required);

        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, Context context) {
        if (!params.has("content") || !params.has("category")) {
            return ToolResult.error("Missing required parameters: content, category");
        }
        String content = params.get("content").getAsString().trim();
        String category = params.get("category").getAsString().trim();
        if (content.isEmpty()) {
            return ToolResult.error("content must not be empty");
        }
        longTermMemory.save(content, category, sessionId);
        return ToolResult.ok("Memory saved: " + content);
    }
}
