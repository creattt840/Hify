package com.hify.modules.mcp.api;

import java.util.List;
import java.util.Map;

/**
 * MCP 模块对外暴露的统一接口 —— 供 Agent、Workflow 模块调用。
 * 委托给 {@link McpClientService} 实现。
 */
public interface McpService extends McpClientService {

    /** @see McpClientService#callTool */
    @Override
    String callTool(Long mcpServerId, String toolName, Map<String, Object> arguments);

    /** @see McpClientService#listTools */
    @Override
    List<String> listTools(Long mcpServerId);
}
