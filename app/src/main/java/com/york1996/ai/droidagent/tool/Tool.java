package com.york1996.ai.droidagent.tool;

import android.content.Context;

import com.google.gson.JsonObject;

/**
 * Agent 工具接口
 * 所有工具都在后台线程执行，可以进行网络请求、文件 IO 等阻塞操作
 */
public interface Tool {

    /** 工具唯一名称（英文，供 LLM 调用） */
    String getName();

    /** 工具功能描述（供 LLM 理解） */
    String getDescription();

    /**
     * 参数的 JSON Schema（OpenAI function calling 格式）
     * 示例：{"type":"object","properties":{"expression":{"type":"string","description":"..."}},
     *        "required":["expression"]}
     */
    JsonObject getParametersSchema();

    /**
     * 执行工具
     *
     * @param params  LLM 传入的参数（已解析的 JsonObject）
     * @param context Android Context
     * @return 执行结果
     */
    ToolResult execute(JsonObject params, Context context);
}
