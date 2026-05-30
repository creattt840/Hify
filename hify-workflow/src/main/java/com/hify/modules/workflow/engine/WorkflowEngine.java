package com.hify.modules.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.workflow.domain.node.NodeConfigParser;
import com.hify.modules.workflow.engine.executor.*;
import com.hify.modules.workflow.infra.entity.WorkflowEdgePo;
import com.hify.modules.workflow.infra.entity.WorkflowNodePo;
import com.hify.modules.workflow.infra.entity.WorkflowNodeRunPo;
import com.hify.modules.workflow.infra.entity.WorkflowRunPo;
import com.hify.modules.workflow.infra.mapper.WorkflowEdgeMapper;
import com.hify.modules.workflow.infra.mapper.WorkflowNodeMapper;
import com.hify.modules.workflow.infra.mapper.WorkflowNodeRunMapper;
import com.hify.modules.workflow.infra.mapper.WorkflowRunMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作流执行引擎 —— 加载节点/边，从 START 遍历执行，写执行记录。
 * <p>
 * 同步执行，不引入新的异步机制。依赖 MyBatis-Plus Mapper 直连数据库，
 * 通过 {@link NodeExecutorRegistry} 分发到各类型节点执行器。
 */
@Slf4j
@Component
public class WorkflowEngine {

    private static final int MAX_STEPS = 50;

    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowEdgeMapper edgeMapper;
    private final NodeExecutorRegistry executorRegistry;
    private final WorkflowRunMapper runMapper;
    private final WorkflowNodeRunMapper nodeRunMapper;
    private final ObjectMapper objectMapper;

    public WorkflowEngine(WorkflowNodeMapper nodeMapper,
                          WorkflowEdgeMapper edgeMapper,
                          NodeExecutorRegistry executorRegistry,
                          WorkflowRunMapper runMapper,
                          WorkflowNodeRunMapper nodeRunMapper,
                          ObjectMapper objectMapper) {
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
        this.executorRegistry = executorRegistry;
        this.runMapper = runMapper;
        this.nodeRunMapper = nodeRunMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行工作流。
     *
     * @param workflowId  工作流 ID
     * @param userMessage 用户输入
     * @return 最终输出文本
     */
    public String execute(Long workflowId, String userMessage) {
        // ── 1. 加载节点和边 ──
        List<WorkflowNodePo> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<WorkflowNodePo>()
                        .eq(WorkflowNodePo::getWorkflowId, workflowId)
                        .orderByAsc(WorkflowNodePo::getSortOrder));
        List<WorkflowEdgePo> edges = edgeMapper.selectList(
                new LambdaQueryWrapper<WorkflowEdgePo>()
                        .eq(WorkflowEdgePo::getWorkflowId, workflowId)
                        .orderByAsc(WorkflowEdgePo::getSortOrder));

        // ── 2. 构建索引 ──
        Map<String, WorkflowNodePo> nodeMap = new HashMap<>();
        for (WorkflowNodePo n : nodes) {
            nodeMap.put(n.getNodeKey(), n);
        }

        // 邻接表: source → [出边]，按 sortOrder 排序
        Map<String, List<WorkflowEdgePo>> edgeMap = new HashMap<>();
        for (WorkflowEdgePo e : edges) {
            edgeMap.computeIfAbsent(e.getSource(), k -> new ArrayList<>()).add(e);
        }
        for (List<WorkflowEdgePo> list : edgeMap.values()) {
            list.sort(Comparator.comparingInt(WorkflowEdgePo::getSortOrder));
        }

        // ── 3. 校验 START 节点 ──
        WorkflowNodePo startNode = nodeMap.get("start");
        if (startNode == null) {
            throw new BizException(ErrorCode.WORKFLOW_START_NODE_REQUIRED);
        }

        // ── 4. 创建执行记录 ──
        Long runId = createRunRecord(workflowId, userMessage);
        ExecutionContext ctx = new ExecutionContext(runId.toString(), userMessage);
        long startTime = System.currentTimeMillis();

        // ── 5. 遍历执行 ──
        String currentKey = firstTarget(edgeMap, "start");
        StringBuilder accumulatedOutput = new StringBuilder();
        Set<String> visitedNodes = new HashSet<>();
        int steps = 0;

        try {
            while (currentKey != null && steps < MAX_STEPS) {
                steps++;

                WorkflowNodePo nodePo = nodeMap.get(currentKey);
                if (nodePo == null) {
                    throw new BizException(ErrorCode.WORKFLOW_NODE_INVALID,
                            "目标节点不存在: " + currentKey);
                }

                // 环检测：同一节点被重复访问，说明存在死循环
                if (!visitedNodes.add(currentKey)) {
                    String error = "检测到循环引用：节点 [" + currentKey + "] 被重复访问";
                    updateRunFailed(runId, error, (int) (System.currentTimeMillis() - startTime));
                    throw new BizException(ErrorCode.WORKFLOW_CYCLE_DETECTED, error);
                }

                String nodeType = nodePo.getNodeType().toUpperCase();

                // END: 取 outputVariable 解析最终输出，终止循环
                if ("END".equals(nodeType)) {
                    String outputVar = getOutputVariable(nodePo);
                    if (outputVar != null) {
                        String resolved = ctx.resolve("{{" + outputVar + "}}");
                        // 如果解析后不等于原占位符，说明变量存在
                        if (!resolved.equals("{{" + outputVar + "}}")) {
                            accumulatedOutput = new StringBuilder(resolved);
                        }
                    }
                    break;
                }

                // START: 不应该在循环中再次出现，跳过
                if ("START".equals(nodeType)) {
                    currentKey = firstTarget(edgeMap, currentKey);
                    continue;
                }

                // 可执行节点
                if (executorRegistry.contains(nodeType)) {
                    Long nodeRunId = createNodeRunRecord(runId, nodePo);
                    long nodeStart = System.currentTimeMillis();

                    try {
                        // 解析配置（先验证 domain 层，再转 engine 层）
                        NodeConfigParser.parse(nodeType, nodePo.getConfig());
                        NodeConfigDef configDef = parseConfigDef(nodeType, nodePo.getConfig());

                        WorkflowNode engineNode = new WorkflowNode(
                                nodePo.getNodeKey(), nodePo.getNodeType(),
                                nodePo.getTitle(), nodePo.getConfig());

                        NodeExecutor executor = executorRegistry.get(nodeType);
                        executor.execute(engineNode, configDef, ctx);

                        // 收集文本输出
                        if ("REPLY".equals(nodeType) || "LLM".equals(nodeType)) {
                            String outputVar = getOutputVariable(nodePo);
                            if (outputVar != null) {
                                Object val = ctx.get(nodePo.getNodeKey(), outputVar);
                                if (val != null) {
                                    accumulatedOutput.append(val.toString());
                                }
                            }
                        }

                        int elapsed = (int) (System.currentTimeMillis() - nodeStart);
                        updateNodeRunSuccess(nodeRunId, ctx.snapshot(), elapsed);

                    } catch (Exception e) {
                        int elapsed = (int) (System.currentTimeMillis() - nodeStart);
                        String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                        updateNodeRunFailed(nodeRunId, error, elapsed);
                        updateRunFailed(runId, error, (int) (System.currentTimeMillis() - startTime));
                        throw new BizException(ErrorCode.WORKFLOW_EXECUTION_FAILED,
                                "节点执行失败 [" + currentKey + "]: " + error);
                    }

                } else {
                    // 非可执行节点类型（如 REPLY, HTTP 等尚未迁移到新执行器的类型）
                    log.debug("跳过非执行器节点: nodeKey={}, nodeType={}", currentKey, nodeType);
                }

                // ── 找下一个节点 ──
                currentKey = findNext(edgeMap, currentKey, nodePo, ctx);
            }

            // ── 步数保护 ──
            if (steps >= MAX_STEPS) {
                String error = "执行步数超过上限 " + MAX_STEPS + "，可能存在死循环";
                updateRunFailed(runId, error, (int) (System.currentTimeMillis() - startTime));
                throw new BizException(ErrorCode.WORKFLOW_EXECUTION_FAILED, error);
            }

            // ── 6. 执行成功 ──
            String finalOutput = accumulatedOutput.toString();
            updateRunSuccess(runId, finalOutput, (int) (System.currentTimeMillis() - startTime));
            log.info("工作流执行完成: workflowId={}, runId={}, steps={}, outputLen={}",
                    workflowId, runId, steps, finalOutput.length());
            return finalOutput;

        } catch (BizException e) {
            // 节点执行异常已在内部 updateRunFailed；其他异常（如空指针/环检测）确保 FAILED
            ensureRunFailed(runId, e.getMessage(), (int) (System.currentTimeMillis() - startTime));
            throw e;
        } catch (Exception e) {
            String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            updateRunFailed(runId, error, (int) (System.currentTimeMillis() - startTime));
            throw new BizException(ErrorCode.WORKFLOW_EXECUTION_FAILED, error);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // findNext —— 步骤二：条件分支
    // ═══════════════════════════════════════════════════════════════

    /**
     * 找下一个节点：
     * <ul>
     *   <li>CONDITION 节点：从 ctx 取布尔结果，匹配 condition = "true" / "false" 的边</li>
     *   <li>其他节点：先找 condition 为空的边（无条件边），没有则取第一条</li>
     *   <li>无边：返回 null（执行结束）</li>
     * </ul>
     */
    private String findNext(Map<String, List<WorkflowEdgePo>> edgeMap,
                            String currentKey,
                            WorkflowNodePo nodePo,
                            ExecutionContext ctx) {
        String nodeType = nodePo.getNodeType().toUpperCase();
        List<WorkflowEdgePo> outEdges = edgeMap.get(currentKey);

        if (outEdges == null || outEdges.isEmpty()) {
            // END 节点不需要出边，已在循环体中 break，不会走到这里
            if ("CONDITION".equals(nodeType)) {
                throw new BizException(ErrorCode.WORKFLOW_NODE_INVALID,
                        "CONDITION 节点 [" + currentKey + "] 没有出边，工作流无法继续");
            }
            // 其他节点无边则自然结束
            return null;
        }

        // CONDITION 节点：匹配布尔结果
        if ("CONDITION".equals(nodeType)) {
            String outputVar = getOutputVariable(nodePo);
            Object result = ctx.get(currentKey, outputVar);
            String boolStr = (result instanceof Boolean b && b) ? "true" : "false";

            for (WorkflowEdgePo e : outEdges) {
                String cond = e.getCondition();
                if (cond != null && cond.trim().equalsIgnoreCase(boolStr)) {
                    return e.getTarget();
                }
            }
            // 无匹配分支：取第一条（兜底）
            return outEdges.get(0).getTarget();
        }

        // 其他节点：优先无条件边，否则取第一条
        for (WorkflowEdgePo e : outEdges) {
            if (e.getCondition() == null || e.getCondition().isEmpty()) {
                return e.getTarget();
            }
        }
        return outEdges.get(0).getTarget();
    }

    // ═══════════════════════════════════════════════════════════════
    // 配置解析
    // ═══════════════════════════════════════════════════════════════

    /**
     * 将原始 config Map 转换为引擎层 NodeConfigDef。
     * START / END 返回 null（不需要执行器配置）。
     */
    private NodeConfigDef parseConfigDef(String nodeType, Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return switch (nodeType.toUpperCase()) {
            case "LLM"       -> objectMapper.convertValue(raw, EngineLlmConfig.class);
            case "CONDITION" -> objectMapper.convertValue(raw, ConditionConfig.class);
            case "API_CALL"  -> objectMapper.convertValue(raw, ApiCallConfig.class);
            case "KNOWLEDGE" -> objectMapper.convertValue(raw, KnowledgeConfig.class);
            default          -> null;
        };
    }

    /** 从节点 config 中提取 outputVariable / outputKey，默认 "output" */
    private String getOutputVariable(WorkflowNodePo nodePo) {
        Map<String, Object> config = nodePo.getConfig();
        if (config == null) return "output";
        if (config.containsKey("outputVariable")) {
            Object v = config.get("outputVariable");
            return v != null ? v.toString() : "output";
        }
        if (config.containsKey("outputKey")) {
            Object v = config.get("outputKey");
            return v != null ? v.toString() : "output";
        }
        return "output";
    }

    // ═══════════════════════════════════════════════════════════════
    // 拓扑辅助
    // ═══════════════════════════════════════════════════════════════

    /** 取 source 的第一条出边的 target，无边返回 null */
    private String firstTarget(Map<String, List<WorkflowEdgePo>> edgeMap, String source) {
        List<WorkflowEdgePo> out = edgeMap.get(source);
        return (out != null && !out.isEmpty()) ? out.get(0).getTarget() : null;
    }

    // ═══════════════════════════════════════════════════════════════
    // 执行记录写入（失败只打 log，不影响主流程）—— 步骤三
    // ═══════════════════════════════════════════════════════════════

    private Long createRunRecord(Long workflowId, String input) {
        try {
            WorkflowRunPo po = new WorkflowRunPo();
            po.setWorkflowId(workflowId);
            po.setStatus("RUNNING");
            po.setInput(input);
            runMapper.insert(po);
            return po.getId();
        } catch (Exception e) {
            log.warn("执行记录写入失败（不影响执行）: workflowId={}", workflowId, e);
            return 0L;
        }
    }

    private void updateRunSuccess(Long runId, String output, int elapsedMs) {
        if (runId == 0) return;
        try {
            WorkflowRunPo po = new WorkflowRunPo();
            po.setId(runId);
            po.setStatus("SUCCESS");
            po.setOutput(output);
            po.setElapsedMs(elapsedMs);
            runMapper.updateById(po);
        } catch (Exception e) {
            log.warn("执行记录更新失败（不影响执行）: runId={}", runId, e);
        }
    }

    private void updateRunFailed(Long runId, String error, int elapsedMs) {
        if (runId == 0) return;
        try {
            WorkflowRunPo po = new WorkflowRunPo();
            po.setId(runId);
            po.setStatus("FAILED");
            po.setError(truncate(error, 500));
            po.setElapsedMs(elapsedMs);
            runMapper.updateById(po);
        } catch (Exception e) {
            log.warn("执行记录更新失败（不影响执行）: runId={}", runId, e);
        }
    }

    /**
     * 兜底标记运行失败 —— 仅在 run 仍为 RUNNING 时更新，避免覆盖节点执行器已写入的错误详情。
     */
    private void ensureRunFailed(Long runId, String error, int elapsedMs) {
        if (runId == 0) return;
        try {
            WorkflowRunPo existing = runMapper.selectById(runId);
            if (existing != null && "RUNNING".equals(existing.getStatus())) {
                WorkflowRunPo po = new WorkflowRunPo();
                po.setId(runId);
                po.setStatus("FAILED");
                po.setError(truncate(error, 500));
                po.setElapsedMs(elapsedMs);
                runMapper.updateById(po);
            }
        } catch (Exception e) {
            log.warn("兜底更新运行状态失败（不影响执行）: runId={}", runId, e);
        }
    }

    private Long createNodeRunRecord(Long runId, WorkflowNodePo nodePo) {
        if (runId == 0) return 0L;
        try {
            WorkflowNodeRunPo po = new WorkflowNodeRunPo();
            po.setWorkflowRunId(runId);
            po.setNodeKey(nodePo.getNodeKey());
            po.setNodeType(nodePo.getNodeType());
            po.setStatus("RUNNING");
            nodeRunMapper.insert(po);
            return po.getId();
        } catch (Exception e) {
            log.warn("节点执行记录写入失败（不影响执行）: nodeKey={}", nodePo.getNodeKey(), e);
            return 0L;
        }
    }

    private void updateNodeRunSuccess(Long nodeRunId, Map<String, Object> outputs, int elapsedMs) {
        if (nodeRunId == 0) return;
        try {
            WorkflowNodeRunPo po = new WorkflowNodeRunPo();
            po.setId(nodeRunId);
            po.setStatus("SUCCESS");
            po.setOutputs(outputs);
            po.setElapsedMs(elapsedMs);
            nodeRunMapper.updateById(po);
        } catch (Exception e) {
            log.warn("节点执行记录更新失败（不影响执行）: nodeRunId={}", nodeRunId, e);
        }
    }

    private void updateNodeRunFailed(Long nodeRunId, String error, int elapsedMs) {
        if (nodeRunId == 0) return;
        try {
            WorkflowNodeRunPo po = new WorkflowNodeRunPo();
            po.setId(nodeRunId);
            po.setStatus("FAILED");
            po.setError(truncate(error, 500));
            po.setElapsedMs(elapsedMs);
            nodeRunMapper.updateById(po);
        } catch (Exception e) {
            log.warn("节点执行记录更新失败（不影响执行）: nodeRunId={}", nodeRunId, e);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
