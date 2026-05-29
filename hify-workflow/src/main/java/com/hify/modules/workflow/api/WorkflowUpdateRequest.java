package com.hify.modules.workflow.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class WorkflowUpdateRequest {

    @NotBlank(message = "工作流名称不能为空")
    private String name;

    private String description;

    private Boolean isEnabled;

    @NotEmpty(message = "节点列表不能为空")
    @Valid
    private List<NodeRequest> nodes;

    @Valid
    private List<EdgeRequest> edges;
}
