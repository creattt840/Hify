package com.hify.modules.mcp.api;

import lombok.Data;

/** 供 Agent 绑定时选择的 MCP 工具选项 */
@Data
public class McpToolOptionResponse {

    private Long id;
    private String name;
    private String description;
    private Long serverId;
    private String serverName;
}
