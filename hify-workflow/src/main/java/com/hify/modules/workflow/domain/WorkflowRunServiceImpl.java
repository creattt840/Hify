package com.hify.modules.workflow.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.modules.workflow.api.WorkflowRunResponse;
import com.hify.modules.workflow.api.WorkflowRunService;
import com.hify.modules.workflow.infra.entity.WorkflowNodeRunPo;
import com.hify.modules.workflow.infra.entity.WorkflowRunPo;
import com.hify.modules.workflow.infra.mapper.WorkflowNodeRunMapper;
import com.hify.modules.workflow.infra.mapper.WorkflowRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowRunServiceImpl implements WorkflowRunService {

    private final WorkflowRunMapper runMapper;
    private final WorkflowNodeRunMapper nodeRunMapper;

    // ─── 工作流级别 ───

    @Override
    @Transactional
    public Long startRun(Long workflowId, String input) {
        WorkflowRunPo po = new WorkflowRunPo();
        po.setWorkflowId(workflowId);
        po.setStatus("RUNNING");
        po.setInput(input);
        runMapper.insert(po);
        log.info("工作流开始执行: runId={}, workflowId={}", po.getId(), workflowId);
        return po.getId();
    }

    @Override
    @Transactional
    public void completeRun(Long runId, String output, int elapsedMs) {
        WorkflowRunPo po = new WorkflowRunPo();
        po.setId(runId);
        po.setStatus("SUCCESS");
        po.setOutput(output);
        po.setElapsedMs(elapsedMs);
        runMapper.updateById(po);
        log.info("工作流执行成功: runId={}, elapsedMs={}", runId, elapsedMs);
    }

    @Override
    @Transactional
    public void failRun(Long runId, String error, int elapsedMs) {
        WorkflowRunPo po = new WorkflowRunPo();
        po.setId(runId);
        po.setStatus("FAILED");
        po.setError(error != null && error.length() > 500 ? error.substring(0, 500) : error);
        po.setElapsedMs(elapsedMs);
        runMapper.updateById(po);
        log.info("工作流执行失败: runId={}, error={}", runId, error);
    }

    // ─── 节点级别 ───

    @Override
    @Transactional
    public Long startNodeRun(Long runId, String nodeKey, String nodeType) {
        WorkflowNodeRunPo po = new WorkflowNodeRunPo();
        po.setWorkflowRunId(runId);
        po.setNodeKey(nodeKey);
        po.setNodeType(nodeType);
        po.setStatus("RUNNING");
        nodeRunMapper.insert(po);
        return po.getId();
    }

    @Override
    @Transactional
    public void completeNodeRun(Long nodeRunId, Map<String, Object> outputs, int elapsedMs) {
        WorkflowNodeRunPo po = new WorkflowNodeRunPo();
        po.setId(nodeRunId);
        po.setStatus("SUCCESS");
        po.setOutputs(outputs);
        po.setElapsedMs(elapsedMs);
        nodeRunMapper.updateById(po);
    }

    @Override
    @Transactional
    public void failNodeRun(Long nodeRunId, String error, int elapsedMs) {
        WorkflowNodeRunPo po = new WorkflowNodeRunPo();
        po.setId(nodeRunId);
        po.setStatus("FAILED");
        po.setError(error != null && error.length() > 500 ? error.substring(0, 500) : error);
        po.setElapsedMs(elapsedMs);
        nodeRunMapper.updateById(po);
    }

    // ─── 查询 ───

    @Override
    public WorkflowRunResponse getLatestRun(Long workflowId) {
        // 取最新一条执行记录（按 created_at 倒序）
        LambdaQueryWrapper<WorkflowRunPo> wrapper = new LambdaQueryWrapper<WorkflowRunPo>()
                .eq(WorkflowRunPo::getWorkflowId, workflowId)
                .orderByDesc(WorkflowRunPo::getCreatedAt)
                .last("LIMIT 1");
        WorkflowRunPo run = runMapper.selectOne(wrapper);
        if (run == null) {
            return null;
        }

        WorkflowRunResponse resp = new WorkflowRunResponse();
        resp.setId(run.getId());
        resp.setWorkflowId(run.getWorkflowId());
        resp.setStatus(run.getStatus());
        resp.setInput(run.getInput());
        resp.setOutput(run.getOutput());
        resp.setError(run.getError());
        resp.setElapsedMs(run.getElapsedMs());
        resp.setCreatedAt(run.getCreatedAt());

        // 查节点执行记录
        List<WorkflowNodeRunPo> nodeRuns = nodeRunMapper.selectList(
                new LambdaQueryWrapper<WorkflowNodeRunPo>()
                        .eq(WorkflowNodeRunPo::getWorkflowRunId, run.getId())
                        .orderByAsc(WorkflowNodeRunPo::getCreatedAt));

        resp.setNodeRuns(nodeRuns.stream().map(nr -> {
            WorkflowRunResponse.NodeRunItem item = new WorkflowRunResponse.NodeRunItem();
            item.setId(nr.getId());
            item.setNodeKey(nr.getNodeKey());
            item.setNodeType(nr.getNodeType());
            item.setStatus(nr.getStatus());
            item.setOutputs(nr.getOutputs());
            item.setError(nr.getError());
            item.setElapsedMs(nr.getElapsedMs());
            return item;
        }).collect(Collectors.toList()));

        return resp;
    }
}
