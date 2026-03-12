package com.york1996.ai.droidagent.agent;

import com.york1996.ai.droidagent.memory.LongTermMemory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * System Prompt 构建器
 * 将多个信息源组装为结构化的 system 消息，替代简单字符串拼接：
 *
 * <pre>
 * ┌─ 基础角色提示词（AgentConfig.systemPrompt）
 * ├─ 当前时间上下文
 * ├─ 长期记忆（RAG 检索结果，含相关度 + 时间距离）
 * └─ 行为准则
 * </pre>
 */
public class SystemPromptBuilder {

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm (EEE)";

    private SystemPromptBuilder() {}

    /**
     * 构建完整的 system 消息
     *
     * @param config   Agent 配置（含基础提示词）
     * @param memories RAG 检索结果，可为 null 或空列表
     */
    public static String build(AgentConfig config,
                               List<LongTermMemory.ScoredMemory> memories) {
        StringBuilder sb = new StringBuilder();

        // ── 1. 基础角色提示词 ──────────────────────────────────────────
        sb.append(config.getSystemPrompt().trim());

        // ── 2. 当前时间（让 LLM 感知时序，避免用训练截止日期回答） ─────
        sb.append("\n\n## Current Time\n");
        sb.append(new SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
                .format(new Date()));

        // ── 3. 长期记忆（RAG 结果） ────────────────────────────────────
        if (memories != null && !memories.isEmpty()) {
            sb.append("\n\n## Relevant Past Knowledge\n");
            sb.append("The following memories from previous conversations may be helpful. ");
            sb.append("Use them as background reference if relevant, ");
            sb.append("but always prioritize the current conversation context.\n");

            for (int i = 0; i < memories.size(); i++) {
                LongTermMemory.ScoredMemory sm = memories.get(i);
                sb.append("\n▸ [")
                  .append(String.format(Locale.US, "Relevance: %.0f%%", sm.score * 100))
                  .append(" | ")
                  .append(formatTimeAgo(sm.entity.createdAt))
                  .append("]\n")
                  .append("  ")
                  .append(sm.entity.content.replace("\n", "\n  "))  // 保持缩进对齐
                  .append("\n");
            }
        }

        // ── 4. 行为准则（固定，不受用户配置影响） ───────────────────────
        sb.append("\n\n## Behavioral Guidelines\n");
        sb.append("- Call tools directly whenever needed; never ask the user for permission first.\n");
        sb.append("- If a tool returns an error, try an alternative approach or explain why it failed.\n");
        sb.append("- Respond in Chinese unless the user explicitly writes in another language.\n");
        sb.append("- Reference past memories naturally if relevant; do not recite them verbatim.\n");
        sb.append("- Be concise: avoid repeating information already stated in the conversation.");

        return sb.toString();
    }

    // ───────────────────────── Helpers ─────────────────────────

    /**
     * 将毫秒时间戳转换为人类可读的"距今多久"字符串
     * 例如：just now / 5 minutes ago / 2 hours ago / 3 days ago / 2 weeks ago
     */
    static String formatTimeAgo(long timestampMs) {
        long diffMs = System.currentTimeMillis() - timestampMs;
        long diffSec = diffMs / 1000;

        if (diffSec < 60)           return "just now";
        if (diffSec < 3600)         return (diffSec / 60) + " min ago";
        if (diffSec < 86400)        return (diffSec / 3600) + " hours ago";
        if (diffSec < 86400 * 7)    return (diffSec / 86400) + " days ago";
        if (diffSec < 86400 * 30)   return (diffSec / (86400 * 7)) + " weeks ago";
        if (diffSec < 86400 * 365)  return (diffSec / (86400 * 30)) + " months ago";
        return (diffSec / (86400 * 365)) + " years ago";
    }
}
