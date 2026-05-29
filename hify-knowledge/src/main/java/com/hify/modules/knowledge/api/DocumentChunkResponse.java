package com.hify.modules.knowledge.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentChunkResponse {

    private Long id;
    private Long documentId;
    private Long kbId;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    private LocalDateTime createdAt;
}
