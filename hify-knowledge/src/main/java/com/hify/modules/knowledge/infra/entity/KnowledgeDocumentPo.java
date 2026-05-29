package com.hify.modules.knowledge.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_knowledge_document")
public class KnowledgeDocumentPo extends BaseEntity {

    private Long kbId;
    private String name;
    private String fileType;
    private String filePath;
    private Long fileSize;
    private String status;
    private Integer chunkCount;
    private String errorMsg;
}
