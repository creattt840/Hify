package com.hify.modules.mcp.api;

import com.hify.common.web.PageResult;

import java.util.List;

/**
 * MCP Server 管理接口 —— 供 Controller 层调用。
 */
public interface McpServerService {

    McpServerResponse create(McpServerCreateRequest request);

    McpServerResponse update(Long id, McpServerUpdateRequest request);

    McpServerResponse getById(Long id);

    PageResult<List<McpServerListItemResponse>> list(int page, int pageSize);

    void delete(Long id);

    ConnectionTestResult testConnection(Long id);

    /**
     * 校验工具 ID 列表是否有效（工具存在 且 所属 MCP Server 已启用）。
     * @param toolIds 待校验的工具 ID 列表
     * @throws com.hify.common.exception.BizException 如果有工具不存在或所属 Server 已禁用
     */
    void validateTools(java.util.List<Long> toolIds);

    /**
     * 批量获取工具定义（name、description、inputSchema、serverId），供 ChatService 构造 LLM tools 参数。
     * 仅返回存在且所属 MCP Server 已启用的工具。
     */
    java.util.List<McpToolDef> getToolDefsByIds(java.util.List<Long> toolIds);

    /**
     * 从工具 ID 列表中过滤出有效项：工具存在（未删除）且所属 MCP Server 已启用。
     */
    java.util.List<Long> filterAvailableToolIds(java.util.List<Long> toolIds);

    /** 列出所有已启用 MCP Server 下的工具，供 Agent 绑定选择 */
    java.util.List<McpToolOptionResponse> listAvailableTools();
}
