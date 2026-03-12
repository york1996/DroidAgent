package com.york1996.ai.droidagent.agent;

/**
 * Agent 全局配置：LLM 接入参数、Agent 运行参数
 */
public class AgentConfig {

    // ---------- LLM ----------
    private String baseUrl = "https://api.openai.com";
    private String apiKey = "";
    private String chatModel = "gpt-4o-mini";
    private String embeddingModel = "text-embedding-ada-002";
    private int maxTokens = 4096;
    private double temperature = 0.7;

    // ---------- Agent 循环 ----------
    private int maxIterations = 10;     // 最大工具调用轮次
    private int maxHistory = 20;        // 短期记忆保留的最大消息数
    private int ragTopK = 3;            // RAG 检索返回的最大条目数
    private double ragThreshold = 0.5;  // RAG 相似度阈值
    private boolean streamingEnabled = false; // 是否启用流式输出

    // ---------- 系统提示词 ----------
    private String systemPrompt =
            "You are DroidAgent, a helpful AI assistant running on Android. "
            + "You have access to tools to help you accomplish tasks. "
            + "When you need to use a tool, use it. "
            + "When you have enough information, provide a clear and concise answer in Chinese. "
            + "Always think step by step before acting.";

    // ---------- Getters / Setters ----------

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getChatModel() { return chatModel; }
    public void setChatModel(String chatModel) { this.chatModel = chatModel; }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getMaxIterations() { return maxIterations; }
    public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }

    public int getMaxHistory() { return maxHistory; }
    public void setMaxHistory(int maxHistory) { this.maxHistory = maxHistory; }

    public int getRagTopK() { return ragTopK; }
    public void setRagTopK(int ragTopK) { this.ragTopK = ragTopK; }

    public double getRagThreshold() { return ragThreshold; }
    public void setRagThreshold(double ragThreshold) { this.ragThreshold = ragThreshold; }

    public boolean isStreamingEnabled() { return streamingEnabled; }
    public void setStreamingEnabled(boolean streamingEnabled) { this.streamingEnabled = streamingEnabled; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    /** 返回完整的 chat completions URL */
    public String getChatUrl() {
        return baseUrl.replaceAll("/$", "") + "/v1/chat/completions";
    }

    /** 返回完整的 embeddings URL */
    public String getEmbeddingUrl() {
        return baseUrl.replaceAll("/$", "") + "/v1/embeddings";
    }
}
