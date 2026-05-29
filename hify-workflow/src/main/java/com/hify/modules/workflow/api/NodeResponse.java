package com.hify.modules.workflow.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class NodeResponse {

    private Long id;
    private String nodeKey;
    private String nodeType;
    private String title;
    private Map<String, Object> config;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
