package com.hify.modules.workflow.api;

import com.hify.common.web.PageResult;

import java.util.List;

/**
 * 工作流模块对外暴露接口 —— 供其他模块调用。
 */
public interface WorkflowService {

    WorkflowResponse create(WorkflowCreateRequest request);

    WorkflowResponse update(Long id, WorkflowUpdateRequest request);

    WorkflowResponse getById(Long id);

    PageResult<List<WorkflowListItemResponse>> list(int page, int pageSize);

    void delete(Long id);
}
