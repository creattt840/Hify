package com.hify.modules.workflow.api;

import java.util.Map;

/**
 * 工作流执行记录服务 —— 供引擎层写入执行和节点级别的运行记录。
 */
public interface WorkflowRunService {

    /**
     * 开始一次工作流执行，写入 RUNNING 状态。
     *
     * @param workflowId 工作流 ID
     * @param input      用户输入
     * @return 执行记录 ID
     */
    Long startRun(Long workflowId, String input);

    /**
     * 执行成功完成。
     *
     * @param runId     执行记录 ID
     * @param output    最终输出
     * @param elapsedMs 总耗时（毫秒）
     */
    void completeRun(Long runId, String output, int elapsedMs);

    /**
     * 执行失败。
     *
     * @param runId     执行记录 ID
     * @param error     错误信息
     * @param elapsedMs 总耗时（毫秒）
     */
    void failRun(Long runId, String error, int elapsedMs);

    /**
     * 开始执行单个节点，写入 RUNNING 状态。
     *
     * @param runId    执行记录 ID
     * @param nodeKey  节点标识
     * @param nodeType 节点类型
     * @return 节点执行记录 ID
     */
    Long startNodeRun(Long runId, String nodeKey, String nodeType);

    /**
     * 节点执行成功。
     *
     * @param nodeRunId 节点执行记录 ID
     * @param outputs   ctx.snapshot() 快照
     * @param elapsedMs 节点耗时（毫秒）
     */
    void completeNodeRun(Long nodeRunId, Map<String, Object> outputs, int elapsedMs);

    /**
     * 节点执行失败。
     *
     * @param nodeRunId 节点执行记录 ID
     * @param error     错误信息
     * @param elapsedMs 节点耗时（毫秒）
     */
    void failNodeRun(Long nodeRunId, String error, int elapsedMs);

    /**
     * 查询指定工作流的最新一条执行记录（含节点详情）。
     *
     * @param workflowId 工作流 ID
     * @return 最新执行记录，无记录时返回 null
     */
    WorkflowRunResponse getLatestRun(Long workflowId);
}
