package com.hify.modules.workflow.domain.node;

import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * SWITCH 节点配置 —— 条件分支。分支连接关系由 edges 表的 condition 字段承载。
 */
@JsonTypeName("SWITCH")
public record SwitchNodeConfig(
    String switchVariable
) implements NodeConfig {
}
