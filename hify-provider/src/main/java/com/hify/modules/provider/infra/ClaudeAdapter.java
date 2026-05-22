package com.hify.modules.provider.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.modules.provider.api.ChatRequest;
import com.hify.modules.provider.api.ChatResponse;
import com.hify.modules.provider.api.ProviderAdapter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
public class ClaudeAdapter implements ProviderAdapter {

    private final LlmHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;

    public ClaudeAdapter(LlmHttpClient httpClient, ObjectMapper objectMapper,
                         String baseUrl, String apiKey) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = rtrim(baseUrl);
        this.apiKey = apiKey;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            String body = buildRequestBody(request, false);
            Map<String, String> headers = buildHeaders();
            try (Response resp = httpClient.post(baseUrl + "/v1/messages", headers, body)) {
                return parseResponse(resp);
            }
        } catch (IOException e) {
            throw new RuntimeException("Claude chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<ChatResponse> onChunk) {
        try {
            String body = buildRequestBody(request, true);
            Map<String, String> headers = buildHeaders();
            try (Response resp = httpClient.postStream(baseUrl + "/v1/messages", headers, body)) {
                if (!resp.isSuccessful()) {
                    String err = resp.body() != null ? resp.body().string() : "";
                    throw new RuntimeException("Claude stream failed: HTTP " + resp.code() + " " + err);
                }
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resp.body().byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("event: ")) {
                            continue;
                        }
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            JsonNode node = objectMapper.readTree(data);
                            String type = node.path("type").asText();

                            if ("content_block_delta".equals(type)) {
                                JsonNode delta = node.path("delta");
                                onChunk.accept(ChatResponse.builder()
                                        .content(delta.path("text").asText(""))
                                        .build());
                            } else if ("message_delta".equals(type)) {
                                JsonNode d = node.path("delta");
                                JsonNode usage = node.path("usage");
                                ChatResponse chunk = ChatResponse.builder()
                                        .finishReason(d.path("stop_reason").asText("end_turn"))
                                        .build();
                                if (!usage.isMissingNode()) {
                                    chunk.setUsage(ChatResponse.TokenUsage.builder()
                                            .promptTokens(usage.path("input_tokens").asInt())
                                            .completionTokens(usage.path("output_tokens").asInt())
                                            .totalTokens(usage.path("input_tokens").asInt() + usage.path("output_tokens").asInt())
                                            .build());
                                }
                                onChunk.accept(chunk);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Claude stream failed: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(ChatRequest request, boolean stream) throws JsonProcessingException {
        // 提取 system 消息
        List<String> systemContents = new ArrayList<>();
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatRequest.Message msg : request.getMessages()) {
            if ("system".equals(msg.getRole())) {
                systemContents.add(msg.getContent());
            } else {
                Map<String, Object> m = new HashMap<>();
                m.put("role", msg.getRole());
                m.put("content", msg.getContent());
                messages.add(m);
            }
        }

        Map<String, Object> root = new HashMap<>();
        root.put("model", request.getModel());
        root.put("messages", messages);
        root.put("stream", stream);
        root.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 4096);
        if (!systemContents.isEmpty()) {
            if (systemContents.size() == 1) {
                root.put("system", systemContents.get(0));
            } else {
                root.put("system", systemContents);
            }
        }
        if (request.getTemperature() != null) root.put("temperature", request.getTemperature());
        if (request.getExtraParams() != null) root.putAll(request.getExtraParams());
        return objectMapper.writeValueAsString(root);
    }

    private ChatResponse parseResponse(Response resp) throws IOException {
        if (!resp.isSuccessful()) {
            String err = resp.body() != null ? resp.body().string() : "";
            throw new RuntimeException("HTTP " + resp.code() + ": " + err);
        }
        JsonNode node = objectMapper.readTree(resp.body().string());
        JsonNode content = node.path("content");
        StringBuilder text = new StringBuilder();
        if (content.isArray()) {
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
        }
        JsonNode usage = node.path("usage");
        return ChatResponse.builder()
                .id(node.path("id").asText())
                .model(node.path("model").asText())
                .content(text.toString())
                .finishReason(node.path("stop_reason").asText("end_turn"))
                .usage(ChatResponse.TokenUsage.builder()
                        .promptTokens(usage.path("input_tokens").asInt())
                        .completionTokens(usage.path("output_tokens").asInt())
                        .totalTokens(usage.path("input_tokens").asInt() + usage.path("output_tokens").asInt())
                        .build())
                .build();
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", "2023-06-01");
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private static String rtrim(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
