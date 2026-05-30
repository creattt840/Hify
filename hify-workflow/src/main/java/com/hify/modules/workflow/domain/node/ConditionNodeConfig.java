package com.hify.modules.workflow.domain.node;

import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * CONDITION 节点配置 —— 表达式求值为 true/false 后分支。
 */
@JsonTypeName("CONDITION")
public record ConditionNodeConfig(
    String expression,
    String outputVariable
) implements NodeConfig {
}
