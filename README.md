# DroidAgent

一个运行在 Android 端的 AI Agent 框架，支持 OpenAI 兼容接口，实现了 ReAct 循环、工具调用、短期/长期记忆与 RAG 检索增强生成。

---

## 功能特性

- **ReAct 循环**：Reason → Act → Observe，Agent 自主决策多步执行
- **工具调用**：基于 OpenAI Function Calling 规范，可扩展任意工具
- **短期记忆**：滑动窗口管理当前会话消息历史
- **长期记忆**：Room 数据库持久化，向量余弦相似度检索
- **RAG**：每轮对话前自动检索相关历史记忆，注入 System Prompt
- **云端模型**：接入任意 OpenAI 兼容接口（OpenAI、Azure、DeepSeek、本地 Ollama 等）

---

## 架构

```
┌─────────────────────────────────────────────────┐
│                    UI Layer                     │
│  MainActivity  SettingsActivity  ChatAdapter    │
│               AgentViewModel                    │
└────────────────────┬────────────────────────────┘
                     │ LiveData
┌────────────────────▼────────────────────────────┐
│                  AgentCore                      │
│              (ReAct Loop Engine)                │
│                                                 │
│  ┌──────────┐  ┌───────────┐  ┌─────────────┐  │
│  │LLMClient │  │ToolRegistry│  │ MemoryMgr   │  │
│  │(OkHttp)  │  │           │  │             │  │
│  └──────────┘  └───────────┘  └─────┬───────┘  │
└────────────────────────────────────-│───────────┘
                                      │
              ┌───────────────────────┤
              │                       │
   ┌──────────▼──────┐    ┌──────────▼──────┐
   │  ShortTermMemory │    │  LongTermMemory  │
   │  (滑动窗口列表)   │    │  (Room + 向量)   │
   └──────────────────┘    └────────┬────────┘
                                    │
                           ┌────────▼────────┐
                           │   RagEngine      │
                           │ (余弦相似度检索)  │
                           └─────────────────┘
```

### 包结构

| 包 | 职责 |
|---|---|
| `agent` | `AgentConfig`（配置）、`AgentCallback`（事件回调）、`AgentCore`（循环引擎） |
| `llm` | `LLMClient`（HTTP 客户端）、消息/响应模型 |
| `tool` | `Tool` 接口、`ToolRegistry`、内置工具实现 |
| `memory` | 短期记忆、长期记忆、Room 数据库、向量工具 |
| `rag` | `RagEngine`（检索 + Prompt 注入） |
| `ui` | Activity、ViewModel、RecyclerView Adapter |

---

## 内置工具

| 工具名 | 说明 | 依赖 |
|---|---|---|
| `calculator` | 数学表达式求值，支持 `+`、`-`、`*`、`/`、`^`、`()`、`sqrt`、`sin`、`cos`、`log` 等 | 无 |
| `web_search` | 网络搜索，返回摘要与相关链接 | DuckDuckGo Instant Answer API（免费无 Key） |
| `weather` | 查询城市当前天气及 3 天预报 | wttr.in（免费无 Key） |
| `file_manager` | 读写、列举、删除 App 私有存储中的文本文件 | 无 |
| `battery_info` | 查询电池电量、充电状态、健康度、温度、电压 | 无 |
| `current_location` | 获取当前设备位置（经纬度 + 反向地理编码得到城市/区域） | 需要定位权限（运行时自动请求） |

---

## 记忆系统

### 短期记忆
- 基于内存的消息列表，维护当前会话上下文
- 滑动窗口裁剪（默认保留最近 20 条消息）
- 会话结束后不持久化

### 长期记忆
- 使用 Room SQLite 数据库持久化
- 每轮对话结束后自动保存摘要
- 支持两种检索模式：
  - **向量检索**（推荐）：调用 Embedding API 生成 float 向量，存入数据库，检索时计算余弦相似度
  - **关键词向量降级**：Embedding API 不可用时，自动退化为 TF-IDF Hash 向量（512 维）

### RAG 流程
```
用户输入 → Embedding → 与长期记忆库余弦相似度排序
        → 取 Top-K（阈值过滤）→ 拼接至 System Prompt → 调用 LLM
```

---

## Agent 执行流程

```
用户输入
    │
    ▼
RAG 检索长期记忆 → 注入 System Prompt
    │
    ▼
调用 LLM（携带工具定义）
    │
    ├── finish_reason = "tool_calls"
    │       │
    │       ▼
    │   执行工具（可并发多个）
    │       │
    │       ▼
    │   工具结果写入对话历史
    │       │
    │       └──────────────────► 回到调用 LLM（最多 10 轮）
    │
    └── finish_reason = "stop"
            │
            ▼
        最终回答 → 短期记忆 → 异步保存长期记忆 → 回调 UI
```

---

## 快速开始

### 环境要求

- Android Studio Ladybug 或更高版本
- Android 8.0（API 26）及以上设备或模拟器
- 可访问 OpenAI 兼容接口的网络环境

### 1. 克隆并打开项目

```bash
git clone <repo-url>
```

用 Android Studio 打开 `DroidAgent` 目录。

### 2. 配置 API

启动 App 后点击右上角菜单 → **设置**，填写以下信息：

| 字段 | 说明 | 示例 |
|---|---|---|
| API Key | 接口鉴权 Key | `sk-xxxxxxxx` |
| Base URL | OpenAI 兼容服务地址 | `https://api.openai.com` |
| Chat 模型 | 对话模型名称 | `gpt-4o-mini` |
| Embedding 模型 | 向量化模型（可留空） | `text-embedding-ada-002` |
| 流式输出 | 开启后回答逐字输出，关闭则等待完整回复 | 开关 |

> **兼容服务示例**：OpenAI、Azure OpenAI、DeepSeek、Moonshot、本地 Ollama（`http://localhost:11434`）等任意 OpenAI 规范接口均可接入。

### 3. 开始对话

直接在输入框发送消息，Agent 会自动决策是否调用工具，并在聊天界面实时展示每一步的工具调用与结果。

**示例问题**：
- `今天天气怎么样？` → 自动调用 `current_location` 获取位置 → 再调用 `weather` 查询当地天气
- `北京今天天气怎么样？` → 调用 `weather` 工具
- `计算 sqrt(144) + 2^10` → 调用 `calculator` 工具
- `搜索一下最新的 Android 开发趋势` → 调用 `web_search` 工具
- `把刚才的天气信息保存到 weather.txt` → 调用 `file_manager` 工具
- `手机还有多少电？` → 调用 `battery_info` 工具

---

## 扩展工具

### 涉及文件

```
tool/
├── Tool.java          ← 接口定义（不需要修改）
├── ToolResult.java    ← 返回值封装（不需要修改）
├── ToolRegistry.java  ← 注册表（不需要修改）
└── YourTool.java      ← 新建你的工具
ui/
└── AgentViewModel.java  ← rebuildAgent() 中注册工具
```

### Step 1：实现 `Tool` 接口

在 `tool/` 包下新建一个 Java 类，实现 4 个方法：

```java
public class TranslateTool implements Tool {

    // 1. 工具唯一名称，LLM 通过此名称调用，建议用小写+下划线
    @Override
    public String getName() {
        return "translate";
    }

    // 2. 工具功能描述，直接影响 LLM 判断"是否应该使用此工具"
    //    写清楚：能做什么、适合什么场景、不能做什么
    @Override
    public String getDescription() {
        return "Translate text from one language to another. "
                + "Supports Chinese, English, Japanese, French, etc. "
                + "Use this when the user asks to translate something.";
    }

    // 3. 参数的 JSON Schema，LLM 按此填写参数
    //    type 必须是 "object"，properties 定义每个参数，required 列出必填项
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        // 参数一：待翻译文本
        JsonObject text = new JsonObject();
        text.addProperty("type", "string");
        text.addProperty("description", "The text to translate");
        properties.add("text", text);

        // 参数二：目标语言
        JsonObject targetLang = new JsonObject();
        targetLang.addProperty("type", "string");
        targetLang.addProperty("description", "Target language, e.g. 'Chinese', 'English', 'Japanese'");
        properties.add("target_language", targetLang);

        schema.add("properties", properties);

        // 声明哪些参数是必填的
        JsonArray required = new JsonArray();
        required.add("text");
        required.add("target_language");
        schema.add("required", required);

        return schema;
    }

    // 4. 工具执行逻辑
    //    - 运行在后台线程，可以直接做网络请求、数据库操作等阻塞调用
    //    - 从 params 中按 schema 定义的参数名取值
    //    - 成功返回 ToolResult.ok(内容)，失败返回 ToolResult.error(原因)
    @Override
    public ToolResult execute(JsonObject params, Context context) {
        if (!params.has("text") || !params.has("target_language")) {
            return ToolResult.error("Missing required parameters");
        }

        String text = params.get("text").getAsString();
        String targetLang = params.get("target_language").getAsString();

        try {
            // 调用你的翻译 API ...
            String result = callTranslateApi(text, targetLang);
            return ToolResult.ok("Translation (" + targetLang + "): " + result);
        } catch (Exception e) {
            return ToolResult.error("Translation failed: " + e.getMessage());
        }
    }

    private String callTranslateApi(String text, String lang) throws Exception {
        // 实现你的翻译逻辑
        return "...";
    }
}
```

### Step 2：注册工具

打开 `ui/AgentViewModel.java`，在 `rebuildAgent()` 方法中添加一行：

```java
private void rebuildAgent() {
    // ...
    ToolRegistry toolRegistry = new ToolRegistry();
    toolRegistry.register(new CalculatorTool());
    toolRegistry.register(new WebSearchTool());
    toolRegistry.register(new WeatherTool());
    toolRegistry.register(new FileReadWriteTool());
    toolRegistry.register(new BatteryTool());
    toolRegistry.register(new LocationTool());
    toolRegistry.register(new TranslateTool());  // ← 添加这一行
    // ...
}
```

注册后无需其他改动，框架会自动将工具定义序列化为 OpenAI `tools` 数组传给 LLM，LLM 决定何时调用。

### 参数类型速查

JSON Schema 的 `type` 字段支持以下类型，按实际参数选择：

| Schema type | Java 取值方式 | 示例场景 |
|---|---|---|
| `"string"` | `params.get("key").getAsString()` | 文本、城市名、文件名 |
| `"number"` | `params.get("key").getAsDouble()` | 数值、坐标、金额 |
| `"integer"` | `params.get("key").getAsInt()` | 数量、页码 |
| `"boolean"` | `params.get("key").getAsBoolean()` | 开关、是否 |
| `"array"` | `params.get("key").getAsJsonArray()` | 多个关键词、列表 |

可选参数不放入 `required` 数组，取值前先用 `params.has("key")` 判断是否存在。

### 注意事项

- `execute()` 在后台线程执行，**可以**直接调用网络/IO，**不可以**直接操作 UI
- 返回内容会原文传回 LLM，建议使用简洁的纯文本，避免过长（超过 4000 字符可截断）
- `getName()` 返回的名称在所有已注册工具中必须唯一
- `getDescription()` 建议用英文，LLM 对英文描述的理解更稳定

---
## 项目结构

```
app/src/main/java/com/york1996/ai/droidagent/
├── agent/
│   ├── AgentCallback.java
│   ├── AgentConfig.java
│   ├── AgentCore.java
│   └── SystemPromptBuilder.java
├── llm/
│   ├── LLMClient.java
│   └── model/
│       ├── LLMChatMessage.java
│       ├── LLMResponse.java
│       └── ToolCall.java
├── memory/
│   ├── LongTermMemory.java
│   ├── MemoryDao.java
│   ├── MemoryDatabase.java
│   ├── MemoryEntity.java
│   ├── ShortTermMemory.java
│   └── VectorUtils.java
├── rag/
│   └── RagEngine.java
├── tool/
│   ├── BatteryTool.java
│   ├── CalculatorTool.java
│   ├── FileReadWriteTool.java
│   ├── LocationTool.java
│   ├── Tool.java
│   ├── ToolRegistry.java
│   ├── ToolResult.java
│   ├── WeatherTool.java
│   └── WebSearchTool.java
└── ui/
    ├── AgentViewModel.java
    ├── ChatAdapter.java
    ├── MainActivity.java
    ├── SettingsActivity.java
    └── UiMessage.java
```

---

## License

MIT
