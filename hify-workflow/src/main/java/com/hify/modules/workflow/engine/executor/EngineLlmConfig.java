package com.hify.modules.workflow.engine.executor;

/**
 * LLM 节点配置 —— 同步调用大模型生成文本。
 */
public record EngineLlmConfig(
    Long modelConfigId,
    String prompt,
    String outputVariable
) implements NodeConfigDef {
}
