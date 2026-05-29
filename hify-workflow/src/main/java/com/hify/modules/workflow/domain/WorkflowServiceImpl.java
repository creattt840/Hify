package com.hify.modules.workflow.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResult;
import com.hify.modules.workflow.api.*;
import com.hify.modules.workflow.infra.entity.WorkflowEdgePo;
import com.hify.modules.workflow.infra.entity.WorkflowNodePo;
import com.hify.modules.workflow.infra.entity.WorkflowPo;
import com.hify.modules.workflow.infra.mapper.WorkflowEdgeMapper;
import com.hify.modules.workflow.infra.mapper.WorkflowNodeMapper;
import com.hify.modules.workflow.infra.mapper.WorkflowMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowMapper workflowMapper;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowEdgeMapper workflowEdgeMapper;

    public WorkflowServiceImpl(WorkflowMapper workflowMapper,
                               WorkflowNodeMapper workflowNodeMapper,
                               WorkflowEdgeMapper workflowEdgeMapper) {
        this.workflowMapper = workflowMapper;
        this.workflowNodeMapper = workflowNodeMapper;
        this.workflowEdgeMapper = workflowEdgeMapper;
    }

    // ────────────────────────── 创建 ──────────────────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowResponse create(WorkflowCreateRequest request) {
        validateNodesNotEmpty(request.getNodes());
        validateHasStartNode(request.getNodes());

        // 1. 写 t_workflow
        WorkflowPo wf = new WorkflowPo();
        wf.setName(request.getName());
        wf.setDescription(request.getDescription() != null ? request.getDescription() : "");
        wf.setVersion(1);
        wf.setIsEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : false);
        wf.setIsPublished(false);
        workflowMapper.insert(wf);
        Long workflowId = wf.getId();

        // 2. 批量写 t_workflow_node
        int sortOrder = 0;
        for (NodeRequest nr : request.getNodes()) {
            WorkflowNodePo node = new WorkflowNodePo();
            node.setWorkflowId(workflowId);
            node.setNodeKey(nr.getNodeKey());
            node.setNodeType(nr.getNodeType().toUpperCase());
            node.setTitle(nr.getTitle() != null ? nr.getTitle() : "");
            node.setConfig(nr.getConfig() != null ? nr.getConfig() : Collections.emptyMap());
            node.setSortOrder(nr.getSortOrder() != null ? nr.getSortOrder() : sortOrder++);
            workflowNodeMapper.insert(node);
        }

        // 3. 批量写 t_workflow_edge
        if (request.getEdges() != null) {
            int edgeOrder = 0;
            for (EdgeRequest er : request.getEdges()) {
                WorkflowEdgePo edge = new WorkflowEdgePo();
                edge.setWorkflowId(workflowId);
                edge.setSource(er.getSource());
                edge.setTarget(er.getTarget());
                edge.setCondition(er.getCondition() != null ? er.getCondition() : "");
                edge.setSortOrder(er.getSortOrder() != null ? er.getSortOrder() : edgeOrder++);
                workflowEdgeMapper.insert(edge);
            }
        }

        log.info("工作流创建成功: id={}, name={}, nodeCount={}, edgeCount={}",
                workflowId, wf.getName(), request.getNodes().size(),
                request.getEdges() != null ? request.getEdges().size() : 0);

        return buildResponse(wf, request.getNodes().size());
    }

    // ────────────────────────── 更新（替换模式） ──────────────────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowResponse update(Long id, WorkflowUpdateRequest request) {
        WorkflowPo wf = requireWorkflow(id);

        if (Boolean.TRUE.equals(wf.getIsPublished())) {
            throw new BizException(ErrorCode.WORKFLOW_ALREADY_PUBLISHED,
                    "工作流已发布，请先取消发布再修改");
        }

        validateNodesNotEmpty(request.getNodes());
        validateHasStartNode(request.getNodes());

        // 更新元数据
        wf.setName(request.getName());
        wf.setDescription(request.getDescription() != null ? request.getDescription() : "");
        wf.setIsEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : wf.getIsEnabled());
        workflowMapper.updateById(wf);

        // 逻辑删除旧节点和旧边
        workflowNodeMapper.delete(
                new LambdaQueryWrapper<WorkflowNodePo>().eq(WorkflowNodePo::getWorkflowId, id));
        workflowEdgeMapper.delete(
                new LambdaQueryWrapper<WorkflowEdgePo>().eq(WorkflowEdgePo::getWorkflowId, id));

        // 批量插入新节点
        int sortOrder = 0;
        for (NodeRequest nr : request.getNodes()) {
            WorkflowNodePo node = new WorkflowNodePo();
            node.setWorkflowId(id);
            node.setNodeKey(nr.getNodeKey());
            node.setNodeType(nr.getNodeType().toUpperCase());
            node.setTitle(nr.getTitle() != null ? nr.getTitle() : "");
            node.setConfig(nr.getConfig() != null ? nr.getConfig() : Collections.emptyMap());
            node.setSortOrder(nr.getSortOrder() != null ? nr.getSortOrder() : sortOrder++);
            workflowNodeMapper.insert(node);
        }

        // 批量插入新边
        if (request.getEdges() != null) {
            int edgeOrder = 0;
            for (EdgeRequest er : request.getEdges()) {
                WorkflowEdgePo edge = new WorkflowEdgePo();
                edge.setWorkflowId(id);
                edge.setSource(er.getSource());
                edge.setTarget(er.getTarget());
                edge.setCondition(er.getCondition() != null ? er.getCondition() : "");
                edge.setSortOrder(er.getSortOrder() != null ? er.getSortOrder() : edgeOrder++);
                workflowEdgeMapper.insert(edge);
            }
        }

        log.info("工作流更新成功: id={}, name={}, nodeCount={}, edgeCount={}",
                id, wf.getName(), request.getNodes().size(),
                request.getEdges() != null ? request.getEdges().size() : 0);

        return buildResponse(wf, request.getNodes().size());
    }

    // ────────────────────────── 查询详情 ──────────────────────────

    @Override
    public WorkflowResponse getById(Long id) {
        WorkflowPo wf = requireWorkflow(id);

        List<WorkflowNodePo> nodes = workflowNodeMapper.selectList(
                new LambdaQueryWrapper<WorkflowNodePo>()
                        .eq(WorkflowNodePo::getWorkflowId, id)
                        .orderByAsc(WorkflowNodePo::getSortOrder));

        List<WorkflowEdgePo> edges = workflowEdgeMapper.selectList(
                new LambdaQueryWrapper<WorkflowEdgePo>()
                        .eq(WorkflowEdgePo::getWorkflowId, id)
                        .orderByAsc(WorkflowEdgePo::getSortOrder));

        WorkflowResponse resp = buildResponse(wf, nodes.size());

        resp.setNodes(nodes.stream().map(n -> {
            NodeResponse nr = new NodeResponse();
            nr.setId(n.getId());
            nr.setNodeKey(n.getNodeKey());
            nr.setNodeType(n.getNodeType());
            nr.setTitle(n.getTitle());
            nr.setConfig(n.getConfig());
            nr.setSortOrder(n.getSortOrder());
            nr.setCreatedAt(n.getCreatedAt());
            return nr;
        }).collect(Collectors.toList()));

        resp.setEdges(edges.stream().map(e -> {
            EdgeResponse er = new EdgeResponse();
            er.setId(e.getId());
            er.setSource(e.getSource());
            er.setTarget(e.getTarget());
            er.setCondition(e.getCondition());
            er.setSortOrder(e.getSortOrder());
            er.setCreatedAt(e.getCreatedAt());
            return er;
        }).collect(Collectors.toList()));

        return resp;
    }

    // ────────────────────────── 分页列表 ──────────────────────────

    @Override
    public PageResult<List<WorkflowListItemResponse>> list(int page, int pageSize) {
        Page<WorkflowPo> pageParam = new Page<>(page, pageSize);
        Page<WorkflowPo> result = workflowMapper.selectPage(pageParam,
                new LambdaQueryWrapper<WorkflowPo>()
                        .orderByDesc(WorkflowPo::getCreatedAt));

        List<WorkflowListItemResponse> list = result.getRecords().isEmpty()
                ? Collections.emptyList()
                : result.getRecords().stream().map(wf -> {
                    Long nodeCount = workflowNodeMapper.selectCount(
                            new LambdaQueryWrapper<WorkflowNodePo>()
                                    .eq(WorkflowNodePo::getWorkflowId, wf.getId()));
                    return buildListItem(wf, nodeCount.intValue());
                }).collect(Collectors.toList());

        return PageResult.of(result.getTotal(), page, pageSize, list);
    }

    // ────────────────────────── 删除 ──────────────────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        WorkflowPo wf = requireWorkflow(id);

        workflowNodeMapper.delete(
                new LambdaQueryWrapper<WorkflowNodePo>().eq(WorkflowNodePo::getWorkflowId, id));
        workflowEdgeMapper.delete(
                new LambdaQueryWrapper<WorkflowEdgePo>().eq(WorkflowEdgePo::getWorkflowId, id));
        workflowMapper.deleteById(id);

        log.info("工作流已删除: id={}, name={}", id, wf.getName());
    }

    // ────────────────────────── 内部方法 ──────────────────────────

    private WorkflowPo requireWorkflow(Long id) {
        WorkflowPo wf = workflowMapper.selectById(id);
        if (wf == null) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_FOUND);
        }
        return wf;
    }

    private void validateNodesNotEmpty(List<NodeRequest> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new BizException(ErrorCode.WORKFLOW_NODE_INVALID, "工作流至少需要一个节点");
        }
    }

    private void validateHasStartNode(List<NodeRequest> nodes) {
        boolean hasStart = nodes.stream()
                .anyMatch(n -> "START".equalsIgnoreCase(n.getNodeType()));
        if (!hasStart) {
            throw new BizException(ErrorCode.WORKFLOW_START_NODE_REQUIRED);
        }
    }

    private WorkflowResponse buildResponse(WorkflowPo po, int nodeCount) {
        WorkflowResponse r = new WorkflowResponse();
        r.setId(po.getId());
        r.setName(po.getName());
        r.setDescription(po.getDescription());
        r.setVersion(po.getVersion());
        r.setIsEnabled(po.getIsEnabled());
        r.setIsPublished(po.getIsPublished());
        r.setNodeCount(nodeCount);
        r.setCreatedAt(po.getCreatedAt());
        r.setUpdatedAt(po.getUpdatedAt());
        return r;
    }

    private WorkflowListItemResponse buildListItem(WorkflowPo po, int nodeCount) {
        WorkflowListItemResponse r = new WorkflowListItemResponse();
        r.setId(po.getId());
        r.setName(po.getName());
        r.setDescription(po.getDescription());
        r.setVersion(po.getVersion());
        r.setIsEnabled(po.getIsEnabled());
        r.setIsPublished(po.getIsPublished());
        r.setNodeCount(nodeCount);
        r.setCreatedAt(po.getCreatedAt());
        r.setUpdatedAt(po.getUpdatedAt());
        return r;
    }
}
