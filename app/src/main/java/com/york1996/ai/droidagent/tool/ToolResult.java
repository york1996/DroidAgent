package com.york1996.ai.droidagent.tool;

/**
 * 工具执行结果
 */
public class ToolResult {

    public final boolean success;
    public final String content; // 返回给 LLM 的文本内容

    private ToolResult(boolean success, String content) {
        this.success = success;
        this.content = content;
    }

    public static ToolResult ok(String content) {
        return new ToolResult(true, content);
    }

    public static ToolResult error(String errorMessage) {
        return new ToolResult(false, "Error: " + errorMessage);
    }
}
