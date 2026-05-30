package com.hify.modules.workflow.engine.executor;

import java.util.Map;

/**
 * API 调用节点配置 —— 发起 HTTP 请求。
 */
public record ApiCallConfig(
    String url,
    String method,
    Map<String, String> headers,
    String outputVariable
) implements NodeConfigDef {
}
