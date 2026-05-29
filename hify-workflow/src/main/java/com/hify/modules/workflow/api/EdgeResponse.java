package com.hify.modules.workflow.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EdgeResponse {

    private Long id;
    private String source;
    private String target;
    private String condition;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
