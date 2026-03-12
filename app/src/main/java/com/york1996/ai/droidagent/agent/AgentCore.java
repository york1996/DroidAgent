package com.york1996.ai.droidagent.agent;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.JsonArray;
import com.york1996.ai.droidagent.llm.LLMClient;
import com.york1996.ai.droidagent.llm.model.LLMChatMessage;
import com.york1996.ai.droidagent.llm.model.LLMResponse;
import com.york1996.ai.droidagent.llm.model.ToolCall;
import com.york1996.ai.droidagent.memory.LongTermMemory;
import com.york1996.ai.droidagent.memory.ShortTermMemory;
import com.york1996.ai.droidagent.rag.RagEngine;
import com.york1996.ai.droidagent.tool.ToolRegistry;
import com.york1996.ai.droidagent.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agent 核心引擎（ReAct 循环）
 * <p>
 * 流程：
 * 1. 用户输入 → 短期记忆
 * 2. RAG 检索长期记忆 → 注入 system 消息
 * 3. 构建消息列表，调用 LLM
 * 4. LLM 返回 tool_calls → 执行工具 → 追加结果 → 回到步骤 3
 * 5. LLM 返回最终答案 → 保存至长期记忆 → 回调 onComplete
 */
public class AgentCore {

    private static final String TAG = "AgentCore";

    private final AgentConfig config;
    private final LLMClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ShortTermMemory shortTermMemory;
    private final LongTermMemory longTermMemory;
    private final RagEngine ragEngine;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String sessionId = "default";

    public AgentCore(AgentConfig config,
                     LLMClient llmClient,
                     ToolRegistry toolRegistry,
                     ShortTermMemory shortTermMemory,
                     LongTermMemory longTermMemory,
                     RagEngine ragEngine) {
        this.config = config;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.shortTermMemory = shortTermMemory;
        this.longTermMemory = longTermMemory;
        this.ragEngine = ragEngine;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 提交用户消息，异步执行 Agent 循环，通过 callback 在主线程回调结果
     */
    public void submit(String userInput, Context context, AgentCallback callback) {
        executor.execute(() -> {
            try {
                runAgentLoop(userInput, context, callback);
            } catch (Exception e) {
                Log.e(TAG, "Agent loop error", e);
                postOnMain(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Unknown error"));
            }
        });
    }

    /**
     * 清空短期记忆（开启新对话）
     */
    public void clearHistory() {
        shortTermMemory.clear();
    }

    // ───────────────────────── Agent Loop ─────────────────────────

    private void runAgentLoop(String userInput, Context context, AgentCallback callback) throws Exception {
        // 1. 用户输入加入短期记忆
        shortTermMemory.addUserMessage(userInput);

        // 2. RAG 检索（返回带相似度分数的结果列表）
        List<LongTermMemory.ScoredMemory> ragMemories = ragEngine.retrieve(
                userInput, config.getRagTopK(), config.getRagThreshold());

        // 3. 用 SystemPromptBuilder 构建结构化 System 消息
        String systemContent = SystemPromptBuilder.build(config, ragMemories);

        // 4. 工具定义
        JsonArray toolDefs = toolRegistry.buildToolDefinitions();

        // 通知 UI 开始思考
        postOnMain(callback::onThinking);

        // 5. ReAct 循环
        for (int iteration = 0; iteration < config.getMaxIterations(); iteration++) {
            // 构建完整消息列表：[system] + [对话历史]
            List<LLMChatMessage> messages = buildMessages(systemContent);

            // 调用 LLM（普通 or 流式）
            LLMResponse response;
            if (config.isStreamingEnabled()) {
                // 流式：token 在后台线程产生，postOnMain 后交由 callback.onToken 处理
                response = llmClient.chatStream(messages, toolDefs,
                        token -> postOnMain(() -> callback.onToken(token)));
            } else {
                response = llmClient.chat(messages, toolDefs);
            }

            if (response.hasToolCalls()) {
                // 6a. 有工具调用：执行工具后继续循环
                // 先将 assistant 的 tool_calls 消息加入历史
                shortTermMemory.addMessage(
                        LLMChatMessage.assistantWithToolCalls(response.getRawToolCallsJson()));

                for (ToolCall toolCall : response.getToolCalls()) {
                    // 通知 UI 正在调用工具
                    postOnMain(() -> callback.onToolCall(toolCall.name, toolCall.arguments));

                    // 执行工具（在后台线程）
                    ToolResult result = toolRegistry.execute(toolCall, context);

                    // 通知 UI 工具结果
                    postOnMain(() -> callback.onToolResult(toolCall.name, result.content, result.success));

                    // 工具结果加入历史
                    shortTermMemory.addMessage(
                            LLMChatMessage.toolResult(toolCall.id, result.content));
                }

                // 多工具调用后继续思考
                postOnMain(callback::onThinking);

            } else {
                // 6b. 最终答案
                String answer = response.getContent();
                if (answer == null || answer.isEmpty()) {
                    answer = "（Agent 未返回有效内容）";
                }
                final String finalAnswer = answer;

                // 加入短期记忆
                shortTermMemory.addAssistantMessage(finalAnswer);

                // 异步保存至长期记忆（不阻塞回调）
                final String memContent = "User: " + userInput + "\nAssistant: " + finalAnswer;
                final String sid = sessionId;
                executor.execute(() -> longTermMemory.save(memContent, "", sid));

                // 回调主线程
                postOnMain(() -> callback.onComplete(finalAnswer));
                return;
            }
        }

        // 超过最大迭代次数
        String errMsg = "Reached maximum iterations (" + config.getMaxIterations() + ") without a final answer.";
        postOnMain(() -> callback.onError(errMsg));
    }

    /**
     * 构建传入 LLM 的消息列表：[system message] + [对话历史]
     */
    private List<LLMChatMessage> buildMessages(String systemContent) {
        List<LLMChatMessage> messages = new ArrayList<>();
        messages.add(new LLMChatMessage("system", systemContent));
        messages.addAll(shortTermMemory.getHistory());
        return messages;
    }

    private void postOnMain(Runnable action) {
        mainHandler.post(action);
    }
}
