package com.hify.modules.workflow.infra.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 工作流节点 PO。config 列通过 JacksonTypeHandler 解析为 Map。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_workflow_node", autoResultMap = true)
public class WorkflowNodePo extends BaseEntity {

    private Long workflowId;
    private String nodeKey;
    private String nodeType;
    private String title;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> config;

    private Integer sortOrder;
}
