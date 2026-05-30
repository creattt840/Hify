package com.hify.modules.mcp.infra;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * MCP 客户端工厂 —— 根据端点 URL 自动选择传输协议。
 * <ul>
 *   <li>路径以 {@code /sse} 结尾：SSE 传输（Cursor / 百度地图等远程 MCP）</li>
 *   <li>其他：Streamable HTTP 传输（本地 Mock MCP 等）</li>
 * </ul>
 */
@Slf4j
public final class McpClientFactory {

    private McpClientFactory() {
    }

    public static McpSyncClient buildSyncClient(String endpoint, Duration requestTimeout) {
        String url = endpoint.trim();
        McpClientTransport transport = isSseEndpoint(url)
                ? buildSseTransport(url)
                : buildStreamableTransport(url);
        return McpClient.sync(transport)
                .requestTimeout(requestTimeout)
                .build();
    }

    private static boolean isSseEndpoint(String url) {
        URI uri = URI.create(url);
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return normalized.endsWith("/sse");
    }

    private static HttpClient.Builder httpClientBuilder() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL);
    }

    private static McpClientTransport buildSseTransport(String url) {
        URI uri = URI.create(url);
        String baseUri = uri.getScheme() + "://" + uri.getAuthority();
        String ssePath = uri.getRawPath();
        if (ssePath == null || ssePath.isBlank()) {
            ssePath = "/sse";
        }
        if (uri.getRawQuery() != null) {
            ssePath += "?" + uri.getRawQuery();
        }
        log.debug("MCP SSE transport: baseUri={}, sseEndpoint={}", baseUri, ssePath);
        return HttpClientSseClientTransport.builder(baseUri)
                .sseEndpoint(ssePath)
                .clientBuilder(httpClientBuilder())
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private static McpClientTransport buildStreamableTransport(String url) {
        return HttpClientStreamableHttpTransport.builder(url)
                .clientBuilder(httpClientBuilder())
                .build();
    }
}
