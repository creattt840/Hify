package com.hify.modules.workflow.engine.executor;

/**
 * 知识库检索节点配置 —— 向量相似度搜索 Top-K 分块。
 */
public record KnowledgeConfig(
    Long knowledgeBaseId,
    String query,
    Integer topK,
    String outputVariable
) implements NodeConfigDef {
}
