package com.york1996.ai.droidagent.rag;

import com.york1996.ai.droidagent.memory.LongTermMemory;

import java.util.List;

/**
 * RAG（检索增强生成）引擎
 * 只负责"检索"，不负责格式化——格式化由 SystemPromptBuilder 完成
 */
public class RagEngine {

    private final LongTermMemory longTermMemory;

    public RagEngine(LongTermMemory longTermMemory) {
        this.longTermMemory = longTermMemory;
    }

    /**
     * 根据用户输入检索相关记忆，返回带相似度分数的列表
     *
     * @param query     当前用户输入
     * @param topK      最多返回条数
     * @param threshold 相似度阈值
     * @return 按相似度降序排列的记忆列表，无结果时返回空列表
     */
    public List<LongTermMemory.ScoredMemory> retrieve(String query, int topK, double threshold) {
        return longTermMemory.retrieveWithScores(query, topK, threshold);
    }
}
