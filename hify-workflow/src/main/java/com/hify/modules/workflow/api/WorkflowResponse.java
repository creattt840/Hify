package com.hify.modules.workflow.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WorkflowResponse {

    private Long id;
    private String name;
    private String description;
    private Integer version;
    private Boolean isEnabled;
    private Boolean isPublished;
    private Integer nodeCount;
    private List<NodeResponse> nodes;
    private List<EdgeResponse> edges;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
