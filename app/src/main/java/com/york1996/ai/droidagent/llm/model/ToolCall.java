package com.york1996.ai.droidagent.llm.model;

/**
 * LLM 返回的单个工具调用
 */
public class ToolCall {
    public final String id;
    public final String name;
    public final String arguments; // JSON 字符串

    public ToolCall(String id, String name, String arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }
}
