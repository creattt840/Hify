package com.hify.modules.agent.api;

import com.hify.common.web.PageResult;

import java.util.List;

public interface AgentService {

    AgentResponse create(AgentCreateRequest request);

    PageResult<List<AgentResponse>> list(int page, int pageSize);

    AgentDetailResponse getById(Long id);

    AgentResponse update(Long id, AgentUpdateRequest request);

    void delete(Long id);
}
