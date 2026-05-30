package com.hify.modules.workflow.infra.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 工作流节点执行记录 PO。outputs 列通过 JacksonTypeHandler 存储 ctx.snapshot()。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_workflow_node_run", autoResultMap = true)
public class WorkflowNodeRunPo extends BaseEntity {

    /** 关联 t_workflow_run.id */
    private Long workflowRunId;

    /** 节点标识 */
    private String nodeKey;

    /** 节点类型 */
    private String nodeType;

    /** RUNNING / SUCCESS / FAILED */
    private String status;

    /** ctx.snapshot() 快照 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> outputs;

    /** 错误信息 */
    private String error;

    /** 节点耗时（毫秒） */
    private Integer elapsedMs;
}
