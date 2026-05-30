package com.hify.modules.workflow.engine.executor;

import com.hify.modules.workflow.engine.ExecutionContext;
import com.hify.modules.workflow.engine.WorkflowNode;

/**
 * 节点执行器接口 —— 每种可执行节点类型对应一个实现。
 * <p>
 * 执行失败时由实现内部 catch 异常并记录日志，
 * 外层 WorkflowEngine 统一处理执行状态。
 */
public interface NodeExecutor {

    /** 支持的节点类型标识，如 "LLM"、"CONDITION" */
    String nodeType();

    /** 执行节点逻辑，结果写入 ctx */
    void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx);
}
