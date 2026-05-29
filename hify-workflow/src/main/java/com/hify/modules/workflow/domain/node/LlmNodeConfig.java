package com.hify.modules.workflow.domain.node;

import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * LLM 节点配置 —— 调用大模型生成文本。
 */
@JsonTypeName("LLM")
public record LlmNodeConfig(
    String prompt,
    Long modelConfigId,
    Double temperature,
    Integer maxTokens,
    String outputKey
) implements NodeConfig {
}
