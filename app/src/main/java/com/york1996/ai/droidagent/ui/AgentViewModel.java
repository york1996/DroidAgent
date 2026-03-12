package com.york1996.ai.droidagent.ui;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.york1996.ai.droidagent.agent.AgentCallback;
import com.york1996.ai.droidagent.agent.AgentConfig;
import com.york1996.ai.droidagent.agent.AgentCore;
import com.york1996.ai.droidagent.llm.LLMClient;
import com.york1996.ai.droidagent.memory.LongTermMemory;
import com.york1996.ai.droidagent.memory.ShortTermMemory;
import com.york1996.ai.droidagent.rag.RagEngine;
import com.york1996.ai.droidagent.tool.CalculatorTool;
import com.york1996.ai.droidagent.tool.FileReadWriteTool;
import com.york1996.ai.droidagent.tool.ToolRegistry;
import com.york1996.ai.droidagent.tool.WeatherTool;
import com.york1996.ai.droidagent.tool.WebSearchTool;

/**
 * ViewModel：持有 AgentCore，通过 LiveData 与 UI 通信
 *
 * 流式输出时的 LiveData 信号流：
 *   startStreaming (true)  → UI 创建空 assistant 气泡
 *   streamingToken (token) → UI 向气泡追加文字
 *   onComplete 时只关闭 loading，不重复加消息
 */
public class AgentViewModel extends AndroidViewModel {

    public static final String PREFS_NAME       = "agent_prefs";
    public static final String KEY_API_KEY      = "api_key";
    public static final String KEY_BASE_URL     = "base_url";
    public static final String KEY_CHAT_MODEL   = "chat_model";
    public static final String KEY_EMB_MODEL    = "embedding_model";
    public static final String KEY_STREAMING    = "streaming_enabled";

    // ── 普通消息事件（用户气泡 / thinking / 工具卡片 / 非流式 assistant 消息）
    private final MutableLiveData<UiMessage> newMessage     = new MutableLiveData<>();
    // ── 流式专用：true = 开始新气泡，false/null = 忽略
    private final MutableLiveData<Boolean>   startStreaming = new MutableLiveData<>();
    // ── 流式专用：追加文字到当前流式气泡
    private final MutableLiveData<String>    streamingToken = new MutableLiveData<>();
    // ── 加载状态 & 错误
    private final MutableLiveData<Boolean>   isLoading      = new MutableLiveData<>(false);
    private final MutableLiveData<String>    errorEvent     = new MutableLiveData<>();

    private AgentConfig config;
    private AgentCore   agentCore;
    private boolean     initialized      = false;
    /** 标记当前 LLM 回复是否已产生过至少一个流式 token（用于区分 onComplete 的行为） */
    private boolean     streamingStarted = false;

    public AgentViewModel(@NonNull Application application) {
        super(application);
    }

    public void init() {
        if (initialized) return;
        config = loadConfig();
        rebuildAgent();
        initialized = true;
    }

    public void reload() {
        config = loadConfig();
        rebuildAgent();
    }

    private void rebuildAgent() {
        LLMClient llmClient = new LLMClient(config);

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new CalculatorTool());
        toolRegistry.register(new WebSearchTool());
        toolRegistry.register(new WeatherTool());
        toolRegistry.register(new FileReadWriteTool());

        ShortTermMemory shortTermMemory = new ShortTermMemory(config.getMaxHistory());
        LongTermMemory  longTermMemory  = new LongTermMemory(getApplication(), llmClient, config);
        RagEngine       ragEngine       = new RagEngine(longTermMemory);

        agentCore = new AgentCore(config, llmClient, toolRegistry,
                shortTermMemory, longTermMemory, ragEngine);
    }

    // ───────────────────────── Send Message ─────────────────────────

    public void sendMessage(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) return;
        isLoading.setValue(true);
        newMessage.setValue(UiMessage.user(userInput));

        agentCore.submit(userInput.trim(), getApplication(), new AgentCallback() {

            @Override
            public void onThinking() {
                // 每次等待 LLM 前重置流式标记，确保新一轮从头开始
                streamingStarted = false;
                newMessage.setValue(UiMessage.thinking());
            }

            /**
             * 流式 token 回调（仅 streaming 模式下触发，已在主线程）
             * 第一个 token 到达时：移除 thinking 气泡，创建空 assistant 气泡
             * 后续 token：追加到气泡末尾
             */
            @Override
            public void onToken(String token) {
                if (!streamingStarted) {
                    streamingStarted = true;
                    startStreaming.setValue(true);   // 信号：创建新气泡
                }
                streamingToken.setValue(token);      // 信号：追加文字
            }

            @Override
            public void onToolCall(String toolName, String arguments) {
                newMessage.setValue(UiMessage.toolCall(toolName, arguments));
            }

            @Override
            public void onToolResult(String toolName, String result, boolean success) {
                newMessage.setValue(UiMessage.toolResult(toolName, result, success));
            }

            @Override
            public void onComplete(String answer) {
                isLoading.setValue(false);
                if (streamingStarted) {
                    // 流式模式：内容已通过 onToken 逐字写入气泡，无需再添加
                    streamingStarted = false;
                } else {
                    // 非流式模式：一次性展示完整答案
                    newMessage.setValue(UiMessage.assistant(answer));
                }
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                streamingStarted = false;
                errorEvent.setValue(error);
            }
        });
    }

    public void clearHistory() {
        if (agentCore != null) agentCore.clearHistory();
    }

    // ───────────────────────── Config ─────────────────────────

    private AgentConfig loadConfig() {
        SharedPreferences prefs = getApplication()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        AgentConfig cfg = new AgentConfig();
        cfg.setApiKey(prefs.getString(KEY_API_KEY, ""));
        cfg.setBaseUrl(prefs.getString(KEY_BASE_URL, "https://api.openai.com"));
        cfg.setChatModel(prefs.getString(KEY_CHAT_MODEL, "gpt-4o-mini"));
        cfg.setEmbeddingModel(prefs.getString(KEY_EMB_MODEL, "text-embedding-ada-002"));
        cfg.setStreamingEnabled(prefs.getBoolean(KEY_STREAMING, false));
        return cfg;
    }

    // ───────────────────────── LiveData ─────────────────────────

    public LiveData<UiMessage> getNewMessage()     { return newMessage; }
    public LiveData<Boolean>   getStartStreaming() { return startStreaming; }
    public LiveData<String>    getStreamingToken() { return streamingToken; }
    public LiveData<Boolean>   getIsLoading()      { return isLoading; }
    public LiveData<String>    getErrorEvent()     { return errorEvent; }
    public AgentConfig         getConfig()         { return config; }
}
