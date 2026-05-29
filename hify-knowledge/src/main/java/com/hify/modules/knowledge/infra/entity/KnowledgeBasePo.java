package com.hify.modules.knowledge.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_knowledge_base")
public class KnowledgeBasePo extends BaseEntity {

    private String name;
    private String description;
    private String embedModel;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Boolean isEnabled;
}
