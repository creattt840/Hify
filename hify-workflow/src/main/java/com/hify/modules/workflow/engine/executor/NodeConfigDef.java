package com.hify.modules.workflow.engine.executor;

/**
 * 引擎层节点配置基类 —— sealed interface，每种可执行节点类型对应一个 record 实现。
 * 和 domain/node/NodeConfig 不同：引擎配置只包含执行器需要的字段，不含 UI 相关字段。
 */
public sealed interface NodeConfigDef
    permits EngineLlmConfig, ConditionConfig, ApiCallConfig, KnowledgeConfig {
}
