package com.hify.modules.mcp.infra.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * MCP 工具 PO。input_schema 通过 JacksonTypeHandler 存储为 JSON。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_mcp_tool", autoResultMap = true)
public class McpToolPo extends BaseEntity {

    /** 关联 t_mcp_server.id */
    private Long serverId;

    /** 工具名称 */
    private String name;

    /** 工具描述（供 LLM 选工具用） */
    private String description;

    /** 输入参数 JSON Schema */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputSchema;
}
