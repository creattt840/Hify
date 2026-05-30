package com.hify.modules.mcp.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class McpServerResponse {

    private Long id;
    private String name;
    private String endpoint;
    private Boolean isEnabled;
    /** 连通性测试时同步的工具列表 */
    private List<McpToolResponse> tools;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
