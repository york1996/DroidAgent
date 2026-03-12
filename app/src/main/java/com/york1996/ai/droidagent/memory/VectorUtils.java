package com.york1996.ai.droidagent.memory;

/**
 * 向量工具类：余弦相似度、序列化/反序列化
 */
public class VectorUtils {

    /**
     * 计算两个向量的余弦相似度，范围 [-1, 1]
     * 维度不同时返回 0
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot   += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * float[] → 逗号分隔字符串（存入 Room）
     */
    public static String serialize(float[] vec) {
        if (vec == null || vec.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        return sb.toString();
    }

    /**
     * 逗号分隔字符串 → float[]（从 Room 读取）
     */
    public static float[] deserialize(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] parts = s.split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vec[i] = Float.parseFloat(parts[i].trim());
        }
        return vec;
    }

    /**
     * 基于 TF-IDF 思路的简单关键词向量（无需 API，作为 embedding 降级方案）
     * 将文本 hash 映射到固定维度的稀疏向量，归一化后可近似比较
     */
    public static float[] keywordVector(String text, int dim) {
        float[] vec = new float[dim];
        String[] words = text.toLowerCase()
                .replaceAll("[^a-z0-9\\u4e00-\\u9fff ]", " ")
                .split("\\s+");
        for (String word : words) {
            if (word.isEmpty()) continue;
            int idx = Math.abs(word.hashCode()) % dim;
            vec[idx] += 1.0f;
        }
        // L2 归一化
        float norm = 0;
        for (float v : vec) norm += v * v;
        if (norm > 0) {
            norm = (float) Math.sqrt(norm);
            for (int i = 0; i < vec.length; i++) vec[i] /= norm;
        }
        return vec;
    }
}
