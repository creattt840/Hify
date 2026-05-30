package com.hify.modules.workflow.engine.executor;

/**
 * 条件节点配置 —— 解析表达式并求值，支持 ==、!=、true/false。
 */
public record ConditionConfig(
    String expression,
    String outputVariable
) implements NodeConfigDef {
}
