package com.hify.modules.mcp.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP Server PO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_mcp_server")
public class McpServerPo extends BaseEntity {

    /** MCP Server 名称 */
    private String name;

    /** MCP Server 端点 URL */
    private String endpoint;

    /** 是否启用 */
    private Boolean isEnabled;
}
