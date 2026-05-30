package com.hify.modules.agent.api;

import com.hify.common.web.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface AgentService {

    AgentResponse create(AgentCreateRequest request);

    PageResult<List<AgentResponse>> list(int page, int pageSize);

    AgentDetailResponse getById(Long id);

    AgentResponse update(Long id, AgentUpdateRequest request);

    void delete(Long id);

    /**
     * 绑定工具列表（全量替换）。传空数组表示清空所有绑定。
     * @param agentId Agent ID
     * @param request 工具 ID 列表，最多 10 个
     */
    void bindTools(Long agentId, AgentToolBindRequest request);

    /**
     * 获取 Agent 当前绑定的有效工具 ID 列表。
     * 自动过滤已删除/Server 已禁用的工具，并清理失效绑定记录。
     */
    List<Long> getBoundToolIds(Long agentId);

    /** 批量获取 Agent 名称，已删除或不存在的 ID 不包含在结果中 */
    Map<Long, String> getNamesByIds(Collection<Long> ids);
}
