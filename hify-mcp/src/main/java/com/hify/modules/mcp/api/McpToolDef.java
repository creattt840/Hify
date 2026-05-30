package com.hify.modules.mcp.api;

import lombok.Data;

import java.util.Map;

/**
 * MCP 工具定义 —— 供 ChatService 构造 LLM tools 参数和工具调用。
 */
@Data
public class McpToolDef {

    /** t_mcp_tool.id */
    private Long id;

    /** 所属 MCP Server ID（工具调用时需要） */
    private Long serverId;

    /** 工具名称 */
    private String name;

    /** 工具描述（供 LLM 选工具用） */
    private String description;

    /** 输入参数 JSON Schema */
    private Map<String, Object> inputSchema;
}
