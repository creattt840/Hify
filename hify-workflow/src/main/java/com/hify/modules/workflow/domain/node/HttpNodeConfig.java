package com.hify.modules.workflow.domain.node;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.Map;

/**
 * HTTP 节点配置 —— 调用外部 HTTP API。
 */
@JsonTypeName("HTTP")
public record HttpNodeConfig(
    String url,
    String method,
    Map<String, String> headers,
    String bodyTemplate,
    String outputKey
) implements NodeConfig {
}
