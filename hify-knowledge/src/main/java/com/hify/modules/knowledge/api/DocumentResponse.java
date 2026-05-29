package com.hify.modules.knowledge.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentResponse {

    private Long id;
    private Long kbId;
    private String name;
    private String fileType;
    private Long fileSize;
    private String status;
    private Integer chunkCount;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
