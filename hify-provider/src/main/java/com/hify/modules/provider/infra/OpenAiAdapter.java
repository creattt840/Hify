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
public class OpenAiAdapter implements ProviderAdapter {

    private final LlmHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;

    public OpenAiAdapter(LlmHttpClient httpClient, ObjectMapper objectMapper,
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
            try (Response resp = httpClient.post(baseUrl + "/v1/chat/completions", headers, body)) {
                return parseResponse(resp);
            }
        } catch (IOException e) {
            throw new RuntimeException("OpenAI chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<ChatResponse> onChunk) {
        try {
            String body = buildRequestBody(request, true);
            Map<String, String> headers = buildHeaders();
            try (Response resp = httpClient.postStream(baseUrl + "/v1/chat/completions", headers, body)) {
                if (!resp.isSuccessful()) {
                    String err = resp.body() != null ? resp.body().string() : "";
                    throw new RuntimeException("OpenAI stream failed: HTTP " + resp.code() + " " + err);
                }
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resp.body().byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty() || !line.startsWith("data: ")) continue;
                        String data = line.substring(6);
                        if ("[DONE]".equals(data)) break;
                        JsonNode node = objectMapper.readTree(data);
                        JsonNode choices = node.get("choices");
                        if (choices == null || choices.isEmpty()) continue;
                        JsonNode delta = choices.get(0).get("delta");
                        JsonNode finish = choices.get(0).get("finish_reason");
                        ChatResponse chunk = ChatResponse.builder()
                                .id(node.path("id").asText())
                                .model(node.path("model").asText())
                                .content(delta != null ? delta.path("content").asText("") : "")
                                .finishReason(finish != null && !finish.isNull() ? finish.asText() : null)
                                .build();
                        onChunk.accept(chunk);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("OpenAI stream failed: " + e.getMessage(), e);
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
        if (request.getTemperature() != null) root.put("temperature", request.getTemperature());
        if (request.getMaxTokens() != null) root.put("max_tokens", request.getMaxTokens());
        if (request.getExtraParams() != null) root.putAll(request.getExtraParams());
        return objectMapper.writeValueAsString(root);
    }

    private ChatResponse parseResponse(Response resp) throws IOException {
        if (!resp.isSuccessful()) {
            String err = resp.body() != null ? resp.body().string() : "";
            throw new RuntimeException("HTTP " + resp.code() + ": " + err);
        }
        JsonNode node = objectMapper.readTree(resp.body().string());
        JsonNode choice = node.path("choices").path(0);
        JsonNode msg = choice.path("message");
        JsonNode usage = node.path("usage");

        return ChatResponse.builder()
                .id(node.path("id").asText())
                .model(node.path("model").asText())
                .content(msg.path("content").asText(""))
                .finishReason(choice.path("finish_reason").asText("stop"))
                .usage(ChatResponse.TokenUsage.builder()
                        .promptTokens(usage.path("prompt_tokens").asInt())
                        .completionTokens(usage.path("completion_tokens").asInt())
                        .totalTokens(usage.path("total_tokens").asInt())
                        .build())
                .build();
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private static String rtrim(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
