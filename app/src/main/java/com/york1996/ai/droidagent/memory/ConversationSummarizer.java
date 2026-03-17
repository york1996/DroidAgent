package com.york1996.ai.droidagent.memory;

import android.util.Log;

import com.york1996.ai.droidagent.llm.LLMClient;
import com.york1996.ai.droidagent.llm.model.LLMChatMessage;
import com.york1996.ai.droidagent.llm.model.LLMResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话历史压缩器：当消息数超过阈值时，用 LLM 把早期对话压缩成一条摘要插回历史。
 * 保留近期消息完整性，同时控制 token 消耗。
 */
public class ConversationSummarizer {

    private static final String TAG = "ConversationSummarizer";
    public static final int DEFAULT_THRESHOLD  = 20; // 消息数达到此值触发压缩
    public static final int DEFAULT_KEEP_RECENT = 6; // 保留最近 N 条消息不压缩

    private final LLMClient llmClient;
    private final int threshold;
    private final int keepRecent;

    public ConversationSummarizer(LLMClient llmClient) {
        this.llmClient  = llmClient;
        this.threshold  = DEFAULT_THRESHOLD;
        this.keepRecent = DEFAULT_KEEP_RECENT;
    }

    /**
     * 在后台线程调用。size <= threshold 时快速返回，否则压缩并原地替换历史。
     */
    public void summarizeIfNeeded(ShortTermMemory memory) {
        if (memory.size() <= threshold) return;

        List<LLMChatMessage> history  = memory.getHistory();
        int splitIdx = history.size() - keepRecent; // 前段压缩，后段保留

        List<LLMChatMessage> toSummarize = new ArrayList<>(history.subList(0, splitIdx));
        List<LLMChatMessage> recent      = new ArrayList<>(history.subList(splitIdx, history.size()));

        String summary = callLLMForSummary(toSummarize);
        if (summary == null || summary.isEmpty()) {
            Log.w(TAG, "Summary returned empty, skipping compression");
            return; // 失败保护：不丢原始数据
        }

        // 重建历史：摘要对话对 + 近期消息
        List<LLMChatMessage> newHistory = new ArrayList<>();
        newHistory.add(new LLMChatMessage("user",
                "[Earlier conversation summary]\n" + summary));
        newHistory.add(new LLMChatMessage("assistant",
                "Understood. I'll keep that context in mind."));
        newHistory.addAll(recent);

        memory.replaceHistory(newHistory);
        Log.d(TAG, "Compressed " + toSummarize.size() + " messages → 1 summary");
    }

    private String callLLMForSummary(List<LLMChatMessage> messages) {
        String prompt = buildPrompt(messages);
        List<LLMChatMessage> req = new ArrayList<>();
        req.add(new LLMChatMessage("user", prompt));
        try {
            LLMResponse resp = llmClient.chat(req, null); // 无工具
            return resp != null ? resp.getContent() : null;
        } catch (Exception e) {
            Log.e(TAG, "LLM summary call failed", e);
            return null;
        }
    }

    private String buildPrompt(List<LLMChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("Summarize the following conversation into a concise paragraph.\n");
        sb.append("Focus on: user's goals, key facts, decisions made, and any preferences expressed.\n");
        sb.append("Write in third person. Do NOT include tool execution details.\n\n");
        sb.append("Conversation:\n");
        for (LLMChatMessage msg : messages) {
            String role = msg.getRole();
            if ("tool".equals(role)) continue; // 跳过 tool result 消息
            String content = msg.getContent();
            if (content == null || content.isEmpty()) continue; // 跳过纯 tool_calls 消息
            sb.append(role.toUpperCase()).append(": ").append(content).append("\n");
        }
        return sb.toString();
    }
}
