package com.york1996.ai.droidagent.llm.model;

import java.util.List;

/**
 * LLM Chat Completions 响应的解析结果
 */
public class LLMResponse {

    private final String content;
    private final List<ToolCall> toolCalls;
    private final String finishReason;
    private final com.google.gson.JsonArray rawToolCallsJson; // 原始 JSON，用于构建历史消息

    public LLMResponse(String content, List<ToolCall> toolCalls,
                       String finishReason,
                       com.google.gson.JsonArray rawToolCallsJson) {
        this.content = content;
        this.toolCalls = toolCalls;
        this.finishReason = finishReason;
        this.rawToolCallsJson = rawToolCallsJson;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public String getContent() { return content; }
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public String getFinishReason() { return finishReason; }
    public com.google.gson.JsonArray getRawToolCallsJson() { return rawToolCallsJson; }
}
