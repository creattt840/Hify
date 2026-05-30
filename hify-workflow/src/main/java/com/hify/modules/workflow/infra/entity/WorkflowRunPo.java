package com.hify.modules.workflow.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流执行记录 PO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_workflow_run")
public class WorkflowRunPo extends BaseEntity {

    /** 关联 t_workflow.id */
    private Long workflowId;

    /** RUNNING / SUCCESS / FAILED */
    private String status;

    /** 用户输入 */
    private String input;

    /** 最终输出 */
    private String output;

    /** 错误信息 */
    private String error;

    /** 总耗时（毫秒） */
    private Integer elapsedMs;
}
