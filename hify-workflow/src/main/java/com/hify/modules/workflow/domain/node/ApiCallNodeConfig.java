package com.hify.modules.workflow.domain.node;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.Map;

/**
 * API_CALL 节点配置 —— 调用外部 HTTP API。
 */
@JsonTypeName("API_CALL")
public record ApiCallNodeConfig(
    String url,
    String method,
    Map<String, String> headers,
    String outputVariable
) implements NodeConfig {
}
