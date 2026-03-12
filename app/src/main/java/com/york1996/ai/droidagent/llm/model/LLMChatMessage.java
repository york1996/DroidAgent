package com.york1996.ai.droidagent.llm.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * OpenAI Chat Completions API 消息格式
 * role: system | user | assistant | tool
 */
public class LLMChatMessage {

    private String role;
    private String content;        // 文本内容（assistant 有 tool_calls 时可为 null）
    private JsonArray toolCalls;   // assistant 返回的工具调用列表
    private String toolCallId;     // role=tool 时，对应的 tool_call id

    public LLMChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    /** 构造 tool 角色消息 */
    public static LLMChatMessage toolResult(String toolCallId, String content) {
        LLMChatMessage msg = new LLMChatMessage("tool", content);
        msg.toolCallId = toolCallId;
        return msg;
    }

    /** 构造含 tool_calls 的 assistant 消息 */
    public static LLMChatMessage assistantWithToolCalls(JsonArray toolCalls) {
        LLMChatMessage msg = new LLMChatMessage("assistant", null);
        msg.toolCalls = toolCalls;
        return msg;
    }

    /** 序列化为 Gson JsonObject 用于构建请求 */
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", role);
        if (content != null) {
            obj.addProperty("content", content);
        } else {
            obj.add("content", com.google.gson.JsonNull.INSTANCE);
        }
        if (toolCalls != null) {
            obj.add("tool_calls", toolCalls);
        }
        if (toolCallId != null) {
            obj.addProperty("tool_call_id", toolCallId);
        }
        return obj;
    }

    public String getRole() { return role; }
    public String getContent() { return content; }
    public JsonArray getToolCalls() { return toolCalls; }
    public String getToolCallId() { return toolCallId; }
}
