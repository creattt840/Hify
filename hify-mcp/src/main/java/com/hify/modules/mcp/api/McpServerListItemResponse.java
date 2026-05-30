package com.hify.modules.mcp.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class McpServerListItemResponse {

    private Long id;
    private String name;
    private String endpoint;
    private Boolean isEnabled;
    private Integer toolCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
