package com.hify.modules.agent.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent ↔ MCP 工具绑定 PO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_agent_tool")
public class AgentToolPo extends BaseEntity {

    /** 关联 t_agent.id */
    private Long agentId;

    /** 关联 t_mcp_tool.id */
    private Long toolId;
}
