package com.york1996.ai.droidagent.ui;

/**
 * UI 层消息模型，对应 RecyclerView 中的不同消息气泡类型
 */
public class UiMessage {

    public static final int TYPE_USER        = 0; // 用户消息（右侧蓝色气泡）
    public static final int TYPE_ASSISTANT   = 1; // AI 最终回复（左侧灰色气泡）
    public static final int TYPE_TOOL_CALL   = 2; // 工具调用卡片
    public static final int TYPE_TOOL_RESULT = 3; // 工具结果卡片
    public static final int TYPE_THINKING    = 4; // 思考中占位符

    public final int type;
    public String content;
    public String toolName;      // TYPE_TOOL_CALL / TYPE_TOOL_RESULT 时有效
    public boolean toolSuccess;  // TYPE_TOOL_RESULT 时有效
    public final long timestamp;

    public UiMessage(int type, String content) {
        this.type = type;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public static UiMessage user(String content) {
        return new UiMessage(TYPE_USER, content);
    }

    public static UiMessage assistant(String content) {
        return new UiMessage(TYPE_ASSISTANT, content);
    }

    public static UiMessage toolCall(String toolName, String arguments) {
        UiMessage msg = new UiMessage(TYPE_TOOL_CALL, arguments);
        msg.toolName = toolName;
        return msg;
    }

    public static UiMessage toolResult(String toolName, String result, boolean success) {
        UiMessage msg = new UiMessage(TYPE_TOOL_RESULT, result);
        msg.toolName = toolName;
        msg.toolSuccess = success;
        return msg;
    }

    public static UiMessage thinking() {
        return new UiMessage(TYPE_THINKING, "");
    }
}
