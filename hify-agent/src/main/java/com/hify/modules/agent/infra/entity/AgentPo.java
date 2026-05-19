package com.hify.modules.agent.infra.entity;

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
}
