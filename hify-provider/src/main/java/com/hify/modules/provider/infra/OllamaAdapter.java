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
public class OllamaAdapter implements ProviderAdapter {

    private final LlmHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public OllamaAdapter(LlmHttpClient httpClient, ObjectMapper objectMapper, String baseUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = rtrim(baseUrl);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            String body = buildRequestBody(request, false);
            try (Response resp = httpClient.post(baseUrl + "/api/chat", null, body)) {
                return parseResponse(resp);
            }
        } catch (IOException e) {
            throw new RuntimeException("Ollama chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<ChatResponse> onChunk) {
        try {
            String body = buildRequestBody(request, true);
            try (Response resp = httpClient.postStream(baseUrl + "/api/chat", null, body)) {
                if (!resp.isSuccessful()) {
                    String err = resp.body() != null ? resp.body().string() : "";
                    throw new RuntimeException("Ollama stream failed: HTTP " + resp.code() + " " + err);
                }
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resp.body().byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) continue;
                        JsonNode node = objectMapper.readTree(line);
                        JsonNode msg = node.path("message");
                        onChunk.accept(ChatResponse.builder()
                                .content(msg.path("content").asText(""))
                                .finishReason(node.path("done").asBoolean() ? "stop" : null)
                                .build());
                        if (node.path("done").asBoolean()) break;
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Ollama stream failed: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(ChatRequest request, boolean stream) throws JsonProcessingException {
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatRequest.Message msg : request.getMessages()) {
            Map<String, String> m = new HashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            messages.add(m);
        }
        Map<String, Object> root = new HashMap<>();
        root.put("model", request.getModel());
        root.put("messages", messages);
        root.put("stream", stream);
        Map<String, Object> options = new HashMap<>();
        if (request.getTemperature() != null) options.put("temperature", request.getTemperature());
        if (!options.isEmpty()) root.put("options", options);
        if (request.getExtraParams() != null) root.putAll(request.getExtraParams());
        return objectMapper.writeValueAsString(root);
    }

    private ChatResponse parseResponse(Response resp) throws IOException {
        if (!resp.isSuccessful()) {
            String err = resp.body() != null ? resp.body().string() : "";
            throw new RuntimeException("HTTP " + resp.code() + ": " + err);
        }
        JsonNode node = objectMapper.readTree(resp.body().string());
        JsonNode msg = node.path("message");
        return ChatResponse.builder()
                .content(msg.path("content").asText(""))
                .finishReason(node.path("done_reason").asText("stop"))
                .usage(ChatResponse.TokenUsage.builder()
                        .promptTokens(node.path("prompt_eval_count").asInt())
                        .completionTokens(node.path("eval_count").asInt())
                        .totalTokens(node.path("prompt_eval_count").asInt() + node.path("eval_count").asInt())
                        .build())
                .build();
    }

    private static String rtrim(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
