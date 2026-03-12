package com.york1996.ai.droidagent.agent;

/**
 * Agent 运行过程中的事件回调，所有回调均在主线程触发
 */
public interface AgentCallback {

    /** 开始思考 / 等待 LLM 响应 */
    void onThinking();

    /**
     * 流式输出：收到一个新 token（仅 streaming 模式下触发）
     * 第一个 token 到达时，UI 应先移除 thinking 气泡并创建新的 assistant 气泡
     */
    void onToken(String token);

    /** 即将调用某个工具 */
    void onToolCall(String toolName, String arguments);

    /** 工具执行完毕，返回结果 */
    void onToolResult(String toolName, String result, boolean success);

    /** 最终回答生成完毕 */
    void onComplete(String answer);

    /** 发生错误 */
    void onError(String error);
}
