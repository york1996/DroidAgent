package com.york1996.ai.droidagent.rag;

import com.york1996.ai.droidagent.memory.LongTermMemory;
import com.york1996.ai.droidagent.memory.MemoryEntity;

import java.util.List;

/**
 * RAG（检索增强生成）引擎
 * 在每轮 LLM 调用前，从长期记忆中检索相关内容，注入到 System Prompt 中
 */
public class RagEngine {

    private final LongTermMemory longTermMemory;

    public RagEngine(LongTermMemory longTermMemory) {
        this.longTermMemory = longTermMemory;
    }

    /**
     * 根据用户输入检索相关记忆，并拼接为可注入的上下文字符串
     *
     * @param query     当前用户输入
     * @param topK      最多返回条数
     * @param threshold 相似度阈值
     * @return 格式化的记忆上下文字符串，若无相关记忆则返回 null
     */
    public String retrieve(String query, int topK, double threshold) {
        List<MemoryEntity> memories = longTermMemory.retrieve(query, topK, threshold);
        if (memories.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("## Relevant memories from past conversations:\n");
        for (int i = 0; i < memories.size(); i++) {
            sb.append(i + 1).append(". ").append(memories.get(i).content).append("\n");
        }
        return sb.toString().trim();
    }
}
