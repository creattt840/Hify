package com.hify.modules.mcp.api;

import java.util.List;
import java.util.Map;

/**
 * MCP Client 接口 —— 供 Agent / Workflow 模块调用，实际与 MCP Server 通信。
 */
public interface McpClientService {

    /**
     * 调用指定 MCP Server 上的工具。
     *
     * @param mcpServerId MCP Server ID
     * @param toolName    工具名称
     * @param arguments   参数
     * @return 工具执行结果文本（多条 TextContent 用换行拼接）
     */
    String callTool(Long mcpServerId, String toolName, Map<String, Object> arguments);

    /**
     * 列出指定 MCP Server 上所有工具的名称。
     *
     * @param mcpServerId MCP Server ID
     * @return 工具名称列表
     */
    List<String> listTools(Long mcpServerId);
}
