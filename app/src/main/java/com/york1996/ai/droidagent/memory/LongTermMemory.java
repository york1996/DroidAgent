package com.york1996.ai.droidagent.memory;

import android.content.Context;
import android.util.Log;

import com.york1996.ai.droidagent.agent.AgentConfig;
import com.york1996.ai.droidagent.llm.LLMClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 长期记忆：Room 持久化 + 向量相似度检索
 * 检索策略：先用 embedding 余弦相似度排序，embedding 不可用时降级为关键词匹配
 */
public class LongTermMemory {

    private static final String TAG = "LongTermMemory";
    private static final int KEYWORD_VEC_DIM = 512;

    private final MemoryDao dao;
    private final LLMClient llmClient;
    private final AgentConfig config;

    public LongTermMemory(Context context, LLMClient llmClient, AgentConfig config) {
        this.dao = MemoryDatabase.getInstance(context).memoryDao();
        this.llmClient = llmClient;
        this.config = config;
    }

    /**
     * 保存一条记忆（在后台线程调用）
     *
     * @param content   记忆内容
     * @param tags      标签（可为空）
     * @param sessionId 会话 ID（可为空）
     */
    public void save(String content, String tags, String sessionId) {
        if (content == null || content.trim().isEmpty()) return;

        MemoryEntity entity = new MemoryEntity();
        entity.content = content.trim();
        entity.tags = tags != null ? tags : "";
        entity.sessionId = sessionId != null ? sessionId : "";
        entity.createdAt = System.currentTimeMillis();
        entity.accessedAt = entity.createdAt;

        // 尝试生成 embedding
        float[] vec = llmClient.embed(content);
        if (vec != null) {
            entity.embedding = VectorUtils.serialize(vec);
        } else {
            // 降级：使用关键词向量
            float[] kwVec = VectorUtils.keywordVector(content, KEYWORD_VEC_DIM);
            entity.embedding = VectorUtils.serialize(kwVec);
        }

        dao.insert(entity);
        Log.d(TAG, "Memory saved: " + content.substring(0, Math.min(50, content.length())));
    }

    /**
     * 检索与查询最相关的 topK 条记忆（在后台线程调用）
     *
     * @param query     查询文本
     * @param topK      返回条数
     * @param threshold 最低相似度阈值
     */
    public List<MemoryEntity> retrieve(String query, int topK, double threshold) {
        List<MemoryEntity> all = dao.getAll();
        if (all.isEmpty()) return Collections.emptyList();

        // 生成查询向量
        float[] queryVec = llmClient.embed(query);
        if (queryVec == null) {
            queryVec = VectorUtils.keywordVector(query, KEYWORD_VEC_DIM);
        }

        // 计算相似度
        final float[] finalQueryVec = queryVec;
        List<ScoredMemory> scored = new ArrayList<>();
        for (MemoryEntity entity : all) {
            if (entity.embedding == null || entity.embedding.isEmpty()) continue;
            float[] entityVec = VectorUtils.deserialize(entity.embedding);
            if (entityVec == null) continue;
            double similarity = VectorUtils.cosineSimilarity(finalQueryVec, entityVec);
            if (similarity >= threshold) {
                scored.add(new ScoredMemory(entity, similarity));
            }
        }

        // 按相似度降序排序
        Collections.sort(scored, (a, b) -> Double.compare(b.score, a.score));

        // 返回 topK 结果，并更新访问记录
        List<MemoryEntity> results = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            MemoryEntity entity = scored.get(i).entity;
            dao.recordAccess(entity.id, System.currentTimeMillis());
            results.add(entity);
        }

        return results;
    }

    public List<MemoryEntity> getAll() {
        return dao.getAll();
    }

    public int count() {
        return dao.count();
    }

    public void delete(MemoryEntity entity) {
        dao.delete(entity);
    }

    public void clearAll() {
        dao.deleteAll();
    }

    private static class ScoredMemory {
        final MemoryEntity entity;
        final double score;
        ScoredMemory(MemoryEntity entity, double score) {
            this.entity = entity;
            this.score = score;
        }
    }
}
