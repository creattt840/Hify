package com.hify.modules.agent.api;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgentDetailResponse extends AgentResponse {

    /** 关联的知识库列表（一期返回空数组） */
    private List<Map<String, Object>> knowledgeBases;

    /** 关联的 MCP 工具列表（一期返回空数组） */
    private List<Map<String, Object>> mcpTools;
}
