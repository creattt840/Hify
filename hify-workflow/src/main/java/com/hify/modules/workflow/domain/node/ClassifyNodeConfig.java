package com.hify.modules.workflow.domain.node;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;

/**
 * CLASSIFY 节点配置 —— 调用 LLM 对输入做意图分类。
 */
@JsonTypeName("CLASSIFY")
public record ClassifyNodeConfig(
    String prompt,
    Long modelConfigId,
    List<String> categories,
    String outputKey
) implements NodeConfig {
}
