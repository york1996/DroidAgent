package com.york1996.ai.droidagent.memory;

import com.york1996.ai.droidagent.llm.model.LLMChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 短期记忆（会话内）：维护当前对话的消息历史
 * 超过最大长度时滑动丢弃最早的 user/assistant 消息（保留 system 消息）
 */
public class ShortTermMemory {

    private final int maxMessages;
    private final List<LLMChatMessage> history = new ArrayList<>();

    public ShortTermMemory(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public synchronized void addMessage(LLMChatMessage message) {
        history.add(message);
        trim();
    }

    public synchronized void addUserMessage(String content) {
        addMessage(new LLMChatMessage("user", content));
    }

    public synchronized void addAssistantMessage(String content) {
        addMessage(new LLMChatMessage("assistant", content));
    }

    /** 获取对话历史（不含 system 消息，由外部拼接） */
    public synchronized List<LLMChatMessage> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public synchronized void clear() {
        history.clear();
    }

    public synchronized int size() {
        return history.size();
    }

    /**
     * 滑动窗口裁剪：保留最新的 maxMessages 条（非 system）消息
     * 为保持上下文一致性，总是成对删除 user+assistant
     */
    private void trim() {
        while (history.size() > maxMessages) {
            // 移除最旧的非 system 消息
            for (int i = 0; i < history.size(); i++) {
                if (!history.get(i).getRole().equals("system")) {
                    history.remove(i);
                    break;
                }
            }
        }
    }
}
