package com.hify.modules.workflow.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowListItemResponse {

    private Long id;
    private String name;
    private String description;
    private Integer version;
    private Boolean isEnabled;
    private Boolean isPublished;
    private Integer nodeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
