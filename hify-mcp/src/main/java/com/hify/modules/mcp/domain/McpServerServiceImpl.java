package com.hify.modules.mcp.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResult;
import com.hify.modules.mcp.api.*;
import com.hify.modules.mcp.infra.McpClientFactory;
import com.hify.modules.mcp.infra.entity.McpServerPo;
import com.hify.modules.mcp.infra.entity.McpToolPo;
import com.hify.modules.mcp.infra.mapper.McpServerMapper;
import com.hify.modules.mcp.infra.mapper.McpToolMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class McpServerServiceImpl implements McpServerService {

    private final McpServerMapper serverMapper;
    private final McpToolMapper toolMapper;

    public McpServerServiceImpl(McpServerMapper serverMapper,
                                McpToolMapper toolMapper) {
        this.serverMapper = serverMapper;
        this.toolMapper = toolMapper;
    }

    // ────────────────────────── 创建 ──────────────────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpServerResponse create(McpServerCreateRequest request) {
        McpServerPo po = new McpServerPo();
        po.setName(request.getName());
        po.setEndpoint(request.getEndpoint());
        po.setIsEnabled(request.getIsEnabled());
        serverMapper.insert(po);
        log.info("MCP Server 创建成功: id={}, name={}", po.getId(), po.getName());
        return buildResponse(po, Collections.emptyList());
    }

    // ────────────────────────── 更新 ──────────────────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpServerResponse update(Long id, McpServerUpdateRequest request) {
        McpServerPo po = requireServer(id);
        po.setName(request.getName());
        po.setEndpoint(request.getEndpoint());
        po.setIsEnabled(request.getIsEnabled());
        serverMapper.updateById(po);

        List<McpToolPo> tools = toolMapper.selectList(
                new LambdaQueryWrapper<McpToolPo>()
                        .eq(McpToolPo::getServerId, id)
                        .orderByAsc(McpToolPo::getName));
        return buildResponse(po, tools);
    }

    // ────────────────────────── 详情 ──────────────────────────

    @Override
    public McpServerResponse getById(Long id) {
        McpServerPo po = requireServer(id);
        List<McpToolPo> tools = toolMapper.selectList(
                new LambdaQueryWrapper<McpToolPo>()
                        .eq(McpToolPo::getServerId, id)
                        .orderByAsc(McpToolPo::getName));
        return buildResponse(po, tools);
    }

    // ────────────────────────── 分页列表 ──────────────────────────

    @Override
    public PageResult<List<McpServerListItemResponse>> list(int page, int pageSize) {
        Page<McpServerPo> pageParam = new Page<>(page, pageSize);
        Page<McpServerPo> result = serverMapper.selectPage(pageParam,
                new LambdaQueryWrapper<McpServerPo>()
                        .orderByDesc(McpServerPo::getCreatedAt));

        List<McpServerListItemResponse> list = result.getRecords().isEmpty()
                ? Collections.emptyList()
                : result.getRecords().stream()
                .map(po -> {
                    long toolCount = toolMapper.selectCount(
                            new LambdaQueryWrapper<McpToolPo>()
                                    .eq(McpToolPo::getServerId, po.getId()));
                    return buildListItem(po, (int) toolCount);
                })
                .collect(Collectors.toList());

        return PageResult.of(result.getTotal(), page, pageSize, list);
    }

    // ────────────────────────── 删除 ──────────────────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        McpServerPo po = requireServer(id);

        // 检查是否有 Agent 绑定了该 Server 的工具
        int bindingCount = toolMapper.countAgentBindings(id);
        if (bindingCount > 0) {
            throw new BizException(ErrorCode.MCP_SERVER_IN_USE,
                    "有 " + bindingCount + " 个 Agent 正在使用该 MCP Server 的工具，请先解绑");
        }

        // 逻辑删除工具
        toolMapper.delete(new LambdaQueryWrapper<McpToolPo>()
                .eq(McpToolPo::getServerId, id));

        // 逻辑删除 Server
        serverMapper.deleteById(id);
        log.info("MCP Server 已删除: id={}, name={}", id, po.getName());
    }

    // ────────────────────────── 连通性测试 ──────────────────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConnectionTestResult testConnection(Long id) {
        McpServerPo po = requireServer(id);
        ConnectionTestResult result = new ConnectionTestResult();

        try {
            McpSyncClient client = buildClient(po.getEndpoint());
            try {
                client.initialize();
                McpSchema.ListToolsResult toolsResult = client.listTools();
                List<McpSchema.Tool> tools = toolsResult != null ? toolsResult.tools() : Collections.emptyList();

                // 同步工具列表：先删旧，再批量插新
                toolMapper.delete(new LambdaQueryWrapper<McpToolPo>()
                        .eq(McpToolPo::getServerId, id));

                for (McpSchema.Tool tool : tools) {
                    McpToolPo toolPo = new McpToolPo();
                    toolPo.setServerId(id);
                    toolPo.setName(tool.name());
                    toolPo.setDescription(tool.description() != null ? tool.description() : "");
                    toolPo.setInputSchema(convertSchema(tool.inputSchema()));
                    toolMapper.insert(toolPo);
                }

                result.setSuccess(true);
                result.setMessage("连接成功，发现 " + tools.size() + " 个工具");
                result.setToolCount(tools.size());
                log.info("MCP Server 连通性测试成功: id={}, toolCount={}", id, tools.size());

            } finally {
                client.closeGracefully();
            }

        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("连接失败: " + e.getMessage());
            result.setToolCount(0);
            log.warn("MCP Server 连通性测试失败: id={}, error={}", id, e.getMessage());
        }

        return result;
    }

    // ────────────────────────── 工具校验 ──────────────────────────

    @Override
    public void validateTools(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return;
        }

        // 去重后校验
        Set<Long> uniqueIds = Set.copyOf(toolIds);
        List<McpToolPo> tools = toolMapper.selectBatchIds(uniqueIds);

        // 1. 工具必须全部存在
        if (tools.size() != uniqueIds.size()) {
            Set<Long> found = tools.stream().map(McpToolPo::getId).collect(Collectors.toSet());
            List<Long> missing = uniqueIds.stream().filter(id -> !found.contains(id)).toList();
            throw new BizException(ErrorCode.MCP_TOOL_NOT_FOUND,
                    "工具不存在: " + missing);
        }

        // 2. 工具所属的 MCP Server 必须启用
        List<Long> serverIds = tools.stream()
                .map(McpToolPo::getServerId).distinct().toList();
        List<McpServerPo> servers = serverMapper.selectBatchIds(serverIds);
        Map<Long, Boolean> enabledMap = servers.stream()
                .collect(Collectors.toMap(McpServerPo::getId, McpServerPo::getIsEnabled));

        for (McpToolPo tool : tools) {
            if (!enabledMap.getOrDefault(tool.getServerId(), false)) {
                McpServerPo server = serverMapper.selectById(tool.getServerId());
                String serverName = server != null ? server.getName() : "未知";
                throw new BizException(ErrorCode.MCP_SERVER_DISABLED,
                        "工具 [" + tool.getName() + "] 所属 MCP Server [" + serverName + "] 已禁用");
            }
        }
    }

    @Override
    public List<Long> filterAvailableToolIds(List<Long> toolIds) {
        return loadAvailableTools(toolIds).stream()
                .map(McpToolPo::getId)
                .collect(Collectors.toList());
    }

    @Override
    public List<McpToolDef> getToolDefsByIds(List<Long> toolIds) {
        return loadAvailableTools(toolIds).stream().map(t -> {
            McpToolDef def = new McpToolDef();
            def.setId(t.getId());
            def.setServerId(t.getServerId());
            def.setName(t.getName());
            def.setDescription(t.getDescription());
            def.setInputSchema(t.getInputSchema());
            return def;
        }).collect(Collectors.toList());
    }

    /** 加载存在且所属 Server 已启用的工具（忽略无效/已删除/Server 禁用的 ID） */
    private List<McpToolPo> loadAvailableTools(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> uniqueIds = new HashSet<>(toolIds);
        List<McpToolPo> tools = toolMapper.selectList(
                new LambdaQueryWrapper<McpToolPo>()
                        .in(McpToolPo::getId, uniqueIds));
        if (tools.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> serverIds = tools.stream()
                .map(McpToolPo::getServerId).distinct().toList();
        Set<Long> enabledServerIds = serverMapper.selectList(
                        new LambdaQueryWrapper<McpServerPo>()
                                .in(McpServerPo::getId, serverIds)
                                .eq(McpServerPo::getIsEnabled, true))
                .stream()
                .map(McpServerPo::getId)
                .collect(Collectors.toSet());

        return tools.stream()
                .filter(t -> enabledServerIds.contains(t.getServerId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<McpToolOptionResponse> listAvailableTools() {
        List<McpServerPo> servers = serverMapper.selectList(
                new LambdaQueryWrapper<McpServerPo>()
                        .eq(McpServerPo::getIsEnabled, true)
                        .orderByAsc(McpServerPo::getName));
        if (servers.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, String> serverNames = servers.stream()
                .collect(Collectors.toMap(McpServerPo::getId, McpServerPo::getName, (a, b) -> a));
        List<Long> serverIds = servers.stream().map(McpServerPo::getId).toList();

        List<McpToolPo> tools = toolMapper.selectList(
                new LambdaQueryWrapper<McpToolPo>()
                        .in(McpToolPo::getServerId, serverIds)
                        .orderByAsc(McpToolPo::getServerId)
                        .orderByAsc(McpToolPo::getName));

        return tools.stream().map(t -> {
            McpToolOptionResponse option = new McpToolOptionResponse();
            option.setId(t.getId());
            option.setName(t.getName());
            option.setDescription(t.getDescription());
            option.setServerId(t.getServerId());
            option.setServerName(serverNames.getOrDefault(t.getServerId(), ""));
            return option;
        }).collect(Collectors.toList());
    }

    // ────────────────────────── 内部方法 ──────────────────────────

    private McpServerPo requireServer(Long id) {
        McpServerPo po = serverMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }
        return po;
    }

    /** 创建 McpSyncClient —— 自动识别 SSE / Streamable HTTP，10s 超时 */
    private McpSyncClient buildClient(String endpoint) {
        return McpClientFactory.buildSyncClient(endpoint, Duration.ofSeconds(30));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertSchema(McpSchema.JsonSchema schema) {
        if (schema == null) return Collections.emptyMap();
        // JsonSchema 通过 ObjectMapper 往返转为 Map 存入数据库
        // v1.1.1 的 JsonSchema 是 record，直接序列化即可
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(schema);
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("工具输入 Schema 转换失败，存为空: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private McpServerResponse buildResponse(McpServerPo po, List<McpToolPo> tools) {
        McpServerResponse r = new McpServerResponse();
        r.setId(po.getId());
        r.setName(po.getName());
        r.setEndpoint(po.getEndpoint());
        r.setIsEnabled(po.getIsEnabled());
        r.setTools(tools.stream().map(this::toToolResponse).collect(Collectors.toList()));
        r.setCreatedAt(po.getCreatedAt());
        r.setUpdatedAt(po.getUpdatedAt());
        return r;
    }

    private McpServerListItemResponse buildListItem(McpServerPo po, int toolCount) {
        McpServerListItemResponse r = new McpServerListItemResponse();
        r.setId(po.getId());
        r.setName(po.getName());
        r.setEndpoint(po.getEndpoint());
        r.setIsEnabled(po.getIsEnabled());
        r.setToolCount(toolCount);
        r.setCreatedAt(po.getCreatedAt());
        r.setUpdatedAt(po.getUpdatedAt());
        return r;
    }

    private McpToolResponse toToolResponse(McpToolPo po) {
        McpToolResponse r = new McpToolResponse();
        r.setId(po.getId());
        r.setServerId(po.getServerId());
        r.setName(po.getName());
        r.setDescription(po.getDescription());
        r.setInputSchema(po.getInputSchema());
        return r;
    }
}
