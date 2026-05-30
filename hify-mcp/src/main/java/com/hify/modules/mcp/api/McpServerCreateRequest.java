package com.hify.modules.mcp.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class McpServerCreateRequest {

    @NotBlank(message = "MCP Server 名称不能为空")
    private String name;

    @NotBlank(message = "端点 URL 不能为空")
    private String endpoint;

    @NotNull(message = "启用状态不能为空")
    private Boolean isEnabled;
}
