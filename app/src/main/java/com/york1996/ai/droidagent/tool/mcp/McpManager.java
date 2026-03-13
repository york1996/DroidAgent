package com.york1996.ai.droidagent.tool.mcp;

import android.util.Log;

import com.york1996.ai.droidagent.tool.ToolRegistry;

import java.util.List;

/**
 * MCP server 注册入口。
 *
 * 用法（在后台线程调用）：
 * <pre>
 *   // 注册远程 MCP server
 *   McpManager.registerServer("https://my-mcp-server.example.com/mcp", toolRegistry);
 * </pre>
 *
 * registerServer 会自动：
 *   1. 与 server 握手（initialize）
 *   2. 拉取工具列表（tools/list）
 *   3. 将每个工具包装为 McpTool 注册到 ToolRegistry
 */
public class McpManager {

    private static final String TAG = "McpManager";

    /**
     * 连接 MCP server 并将其所有工具注册到 registry。
     * <p>
     * 必须在后台线程调用（网络操作）。
     *
     * @param serverUrl MCP server HTTP endpoint，如 "http://127.0.0.1:3000/mcp"
     * @param registry  目标 ToolRegistry
     * @return 成功注册的工具数量，握手失败返回 -1
     */
    public static int registerServer(String serverUrl, ToolRegistry registry) {
        try {
            McpClient client = new McpClient(serverUrl);

            if (!client.initialize()) {
                Log.e(TAG, "MCP handshake failed: " + serverUrl);
                return -1;
            }

            List<McpToolDef> tools = client.listTools();
            for (McpToolDef def : tools) {
                registry.register(new McpTool(client, def));
                Log.i(TAG, "Registered MCP tool: " + def.name + " from " + serverUrl);
            }

            Log.i(TAG, "MCP server registered: " + serverUrl + " (" + tools.size() + " tools)");
            return tools.size();

        } catch (Exception e) {
            Log.e(TAG, "Failed to register MCP server: " + serverUrl, e);
            return -1;
        }
    }
}
