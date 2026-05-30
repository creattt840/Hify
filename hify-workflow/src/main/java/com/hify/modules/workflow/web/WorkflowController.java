package com.hify.modules.workflow.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.workflow.api.WorkflowCreateRequest;
import com.hify.modules.workflow.api.WorkflowListItemResponse;
import com.hify.modules.workflow.api.WorkflowResponse;
import com.hify.modules.workflow.api.WorkflowRunResponse;
import com.hify.modules.workflow.api.WorkflowRunService;
import com.hify.modules.workflow.api.WorkflowService;
import com.hify.modules.workflow.api.WorkflowUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowRunService workflowRunService;

    public WorkflowController(WorkflowService workflowService,
                              WorkflowRunService workflowRunService) {
        this.workflowService = workflowService;
        this.workflowRunService = workflowRunService;
    }

    @PostMapping
    public Result<WorkflowResponse> create(@Valid @RequestBody WorkflowCreateRequest request) {
        return Result.ok(workflowService.create(request));
    }

    @GetMapping
    public PageResult<List<WorkflowListItemResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return workflowService.list(page, pageSize);
    }

    @GetMapping("/{id}")
    public Result<WorkflowResponse> getById(@PathVariable Long id) {
        return Result.ok(workflowService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<WorkflowResponse> update(@PathVariable Long id,
                                           @Valid @RequestBody WorkflowUpdateRequest request) {
        return Result.ok(workflowService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workflowService.delete(id);
        return Result.ok(null);
    }

    /** 查询指定工作流最新一条执行记录（含节点详情） */
    @GetMapping("/{id}/runs/latest")
    public Result<WorkflowRunResponse> getLatestRun(@PathVariable Long id) {
        WorkflowRunResponse run = workflowRunService.getLatestRun(id);
        return Result.ok(run);
    }
}
