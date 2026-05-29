package com.hify.modules.workflow.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.baomidou.mybatisplus.annotation.TableField;

/**
 * 工作流边 PO —— 记录节点之间的连接关系。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_workflow_edge")
public class WorkflowEdgePo extends BaseEntity {

    private Long workflowId;
    private String source;
    private String target;

    @TableField("`condition`")
    private String condition;

    private Integer sortOrder;
}
