package com.york1996.ai.droidagent.memory;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.york1996.ai.droidagent.agent.AgentConfig;
import com.york1996.ai.droidagent.llm.LLMClient;
import com.york1996.ai.droidagent.llm.model.LLMChatMessage;
import com.york1996.ai.droidagent.llm.model.LLMResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * 周期性 LLM 压缩长期记忆，避免记忆膨胀
 * 当记忆数量超过阈值时，用 LLM 合并、去重后替换原始条目
 */
public class MemoryConsolidator {

    private static final String TAG = "MemoryConsolidator";
    public static final int DEFAULT_THRESHOLD = 50;

    private final LongTermMemory longTermMemory;
    private final LLMClient llmClient;
    private final int threshold;

    public MemoryConsolidator(LongTermMemory longTermMemory, LLMClient llmClient,
                              AgentConfig config) {
        this.longTermMemory = longTermMemory;
        this.llmClient = llmClient;
        this.threshold = DEFAULT_THRESHOLD;
    }

    /**
     * 若记忆数量超过阈值则触发 LLM 压缩，否则立即返回。
     * 必须在后台线程调用。
     */
    public void consolidateIfNeeded() {
        if (longTermMemory.count() <= threshold) return;

        Log.d(TAG, "Memory count exceeds threshold, starting consolidation...");
        List<MemoryEntity> all = longTermMemory.getAll();
        if (all.isEmpty()) return;

        String prompt = buildPrompt(all);

        List<LLMChatMessage> messages = new ArrayList<>();
        messages.add(new LLMChatMessage("user", prompt));

        LLMResponse response;
        try {
            response = llmClient.chat(messages, null);
        } catch (Exception e) {
            Log.e(TAG, "Consolidation LLM call failed", e);
            return;  // 失败保护：不删原始数据
        }

        if (response == null || response.getContent() == null) {
            Log.w(TAG, "Consolidation returned null response, skipping");
            return;
        }

        List<String> summaries = parseJsonArray(response.getContent());
        if (summaries.isEmpty()) {
            Log.w(TAG, "Consolidation returned empty summaries, skipping to protect data");
            return;  // 失败保护：不删原始数据
        }

        longTermMemory.clearAll();
        for (String s : summaries) {
            longTermMemory.save(s, "summary", "");
        }
        Log.d(TAG, "Consolidation complete: " + all.size() + " → " + summaries.size() + " memories");
    }

    private String buildPrompt(List<MemoryEntity> memories) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a memory consolidation assistant. Merge, deduplicate, and compress ");
        sb.append("the following memories into a smaller set of dense, self-contained statements.\n");
        sb.append("Rules:\n");
        sb.append("- Merge memories about the same fact/preference into one statement.\n");
        sb.append("- Discard trivial or redundant entries.\n");
        sb.append("- Keep all concrete personal facts (name, job, location, preferences).\n");
        sb.append("- Each output must be standalone and readable without context.\n");
        sb.append("- Output ONLY a valid JSON array of strings. No explanation, no markdown.\n\n");
        sb.append("Memories:\n");
        for (int i = 0; i < memories.size(); i++) {
            MemoryEntity e = memories.get(i);
            String tag = (e.tags != null && !e.tags.isEmpty()) ? e.tags : "general";
            sb.append(i + 1).append(". [").append(tag).append("] ").append(e.content).append("\n");
        }
        return sb.toString();
    }

    private List<String> parseJsonArray(String content) {
        List<String> results = new ArrayList<>();
        try {
            // 去掉可能的 markdown fence
            String cleaned = content.trim();
            if (cleaned.startsWith("```")) {
                int start = cleaned.indexOf('\n');
                int end = cleaned.lastIndexOf("```");
                if (start >= 0 && end > start) {
                    cleaned = cleaned.substring(start + 1, end).trim();
                }
            }
            JsonArray arr = JsonParser.parseString(cleaned).getAsJsonArray();
            for (JsonElement el : arr) {
                String s = el.getAsString().trim();
                if (!s.isEmpty()) results.add(s);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse consolidation JSON: " + e.getMessage());
        }
        return results;
    }
}
