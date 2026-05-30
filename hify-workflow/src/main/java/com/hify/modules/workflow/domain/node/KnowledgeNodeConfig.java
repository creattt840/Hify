package com.hify.modules.workflow.domain.node;

import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * KNOWLEDGE 节点配置 —— 从知识库检索相关内容。
 */
@JsonTypeName("KNOWLEDGE")
public record KnowledgeNodeConfig(
    Long knowledgeBaseId,
    String query,
    Integer topK,
    String outputVariable
) implements NodeConfig {
}
