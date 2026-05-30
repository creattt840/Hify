package com.hify.modules.agent.infra.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_agent")
public class AgentPo extends BaseEntity {

    private String name;

    private String description;

    private String systemPrompt;

    private Long modelConfigId;

    private Double temperature;

    private Boolean isEnabled;

    /** 绑定的工作流 ID，null 表示自由对话模式。updateStrategy=ALWAYS 允许更新为 null（解绑） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long workflowId;
}
