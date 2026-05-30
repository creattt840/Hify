package com.hify.modules.mcp.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 工具调试响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpDebugResponse {

    /** 工具返回的文本结果 */
    private String result;

    /** 调用耗时（毫秒） */
    private int elapsedMs;
}
