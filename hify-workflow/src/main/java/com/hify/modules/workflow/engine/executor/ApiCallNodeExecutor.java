package com.hify.modules.workflow.engine.executor;

import com.hify.modules.provider.infra.LlmHttpClient;
import com.hify.modules.workflow.engine.ExecutionContext;
import com.hify.modules.workflow.engine.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * API 调用节点执行器 —— 发起 HTTP 请求，响应体写入上下文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiCallNodeExecutor implements NodeExecutor {

    private final LlmHttpClient httpClient;

    @Override
    public String nodeType() {
        return "API_CALL";
    }

    @Override
    public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        ApiCallConfig apiConfig = (ApiCallConfig) config;

        // 1. 模板变量替换 url
        String url = ctx.resolve(apiConfig.url());

        // 2. 模板变量替换 headers
        Map<String, String> resolvedHeaders = new HashMap<>();
        if (apiConfig.headers() != null) {
            for (Map.Entry<String, String> e : apiConfig.headers().entrySet()) {
                resolvedHeaders.put(e.getKey(), ctx.resolve(e.getValue()));
            }
        }

        String method = apiConfig.method() != null ? apiConfig.method().toUpperCase() : "GET";

        // 3. 发起 HTTP 请求
        String responseBody;
        try {
            Response response = switch (method) {
                case "GET" -> httpClient.get(url, resolvedHeaders);
                case "POST" -> httpClient.post(url, resolvedHeaders, "{}");
                default -> throw new IllegalArgumentException("不支持的 HTTP 方法: " + method);
            };

            responseBody = response.body() != null ? response.body().string() : "";
            response.close();

            if (!response.isSuccessful()) {
                responseBody = "[HTTP " + response.code() + "] " + responseBody;
            }
        } catch (IOException e) {
            log.error("API 调用失败: nodeKey={}, url={}", node.getNodeKey(), url, e);
            responseBody = "[ERROR] " + e.getMessage();
        }

        // 4. 结果写入上下文
        String varName = apiConfig.outputVariable() != null ? apiConfig.outputVariable() : "response";
        ctx.set(node.getNodeKey(), varName, responseBody);
        log.info("API 调用节点执行完成: nodeKey={}, method={}, url={}, responseLen={}",
                node.getNodeKey(), method, url, responseBody.length());
    }
}
