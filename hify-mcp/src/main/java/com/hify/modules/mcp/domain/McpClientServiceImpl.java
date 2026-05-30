package com.hify.modules.mcp.domain;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.mcp.api.McpService;
import com.hify.modules.mcp.infra.McpClientFactory;
import com.hify.modules.mcp.infra.entity.McpServerPo;
import com.hify.modules.mcp.infra.mapper.McpServerMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 客户端服务实现 —— 每次调用创建独立 McpSyncClient，用完即关。
 */
@Slf4j
@Service
public class McpClientServiceImpl implements McpService {

    private final McpServerMapper serverMapper;

    public McpClientServiceImpl(McpServerMapper serverMapper) {
        this.serverMapper = serverMapper;
    }

    @Override
    public String callTool(Long mcpServerId, String toolName, Map<String, Object> arguments) {
        McpServerPo server = requireServer(mcpServerId);

        try {
            McpSyncClient client = buildClient(server.getEndpoint());
            try {
                client.initialize();
                McpSchema.CallToolResult result = client.callTool(
                        new McpSchema.CallToolRequest(toolName, arguments));

                if (result == null || result.content() == null || result.content().isEmpty()) {
                    return "";
                }

                // 取所有 TextContent，多条用换行拼接
                return result.content().stream()
                        .filter(c -> c instanceof McpSchema.TextContent)
                        .map(c -> ((McpSchema.TextContent) c).text())
                        .collect(Collectors.joining("\n"));

            } finally {
                client.closeGracefully();
            }

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("MCP 工具调用失败: serverId={}, toolName={}", mcpServerId, toolName, e);
            throw new BizException(ErrorCode.MCP_TOOL_CALL_FAILED,
                    "工具调用失败 [" + toolName + "]: " + e.getMessage());
        }
    }

    @Override
    public List<String> listTools(Long mcpServerId) {
        McpServerPo server = requireServer(mcpServerId);

        try {
            McpSyncClient client = buildClient(server.getEndpoint());
            try {
                client.initialize();
                McpSchema.ListToolsResult result = client.listTools();
                if (result == null || result.tools() == null) {
                    return Collections.emptyList();
                }
                return result.tools().stream()
                        .map(McpSchema.Tool::name)
                        .collect(Collectors.toList());

            } finally {
                client.closeGracefully();
            }

        } catch (Exception e) {
            log.error("MCP Server 工具列表获取失败: serverId={}", mcpServerId, e);
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND,
                    "MCP Server 连接失败: " + e.getMessage());
        }
    }

    // ────────────────────────── 内部方法 ──────────────────────────

    private McpServerPo requireServer(Long mcpServerId) {
        McpServerPo po = serverMapper.selectById(mcpServerId);
        if (po == null) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }
        return po;
    }

    private McpSyncClient buildClient(String endpoint) {
        return McpClientFactory.buildSyncClient(endpoint, Duration.ofSeconds(30));
    }
}
