package com.hify.modules.knowledge.api;

import lombok.Data;

/**
 * 知识库分块检索结果。
 */
@Data
public class KnowledgeSearchResult {

    /** 分块 ID */
    private Long chunkId;
    /** 分块原文 */
    private String content;
    /** 所属文档 ID */
    private Long documentId;
    /** 余弦相似度 (0~1) */
    private Double similarity;
}
