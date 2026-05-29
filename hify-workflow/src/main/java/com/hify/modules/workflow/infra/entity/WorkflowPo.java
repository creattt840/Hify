package com.hify.modules.workflow.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流定义 PO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_workflow")
public class WorkflowPo extends BaseEntity {

    private String name;
    private String description;
    private Integer version;
    private Boolean isEnabled;
    private Boolean isPublished;
}
