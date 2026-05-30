package com.hify.modules.mcp.api;

import lombok.Data;

import java.util.Map;

@Data
public class McpToolResponse {

    private Long id;
    private Long serverId;
    private String name;
    private String description;
    private Map<String, Object> inputSchema;
}
