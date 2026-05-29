package com.hify.modules.knowledge.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeBaseResponse {

    private Long id;
    private String name;
    private String description;
    private String embedModel;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Boolean isEnabled;
    private Integer documentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
