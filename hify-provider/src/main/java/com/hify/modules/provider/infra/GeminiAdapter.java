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
public class GeminiAdapter implements ProviderAdapter {

    private final LlmHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;

    public GeminiAdapter(LlmHttpClient httpClient, ObjectMapper objectMapper,
                         String baseUrl, String apiKey) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = rtrim(baseUrl);
        this.apiKey = apiKey;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            String body = buildRequestBody(request);
            Map<String, String> headers = buildHeaders();
            String url = baseUrl + "/v1beta/models/" + request.getModel() + ":generateContent";
            try (Response resp = httpClient.post(url, headers, body)) {
                return parseResponse(resp);
            }
        } catch (IOException e) {
            throw new RuntimeException("Gemini chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<ChatResponse> onChunk) {
        try {
            String body = buildRequestBody(request);
            Map<String, String> headers = buildHeaders();
            String url = baseUrl + "/v1beta/models/" + request.getModel() + ":streamGenerateContent?alt=sse";
            try (Response resp = httpClient.postStream(url, headers, body)) {
                if (!resp.isSuccessful()) {
                    String err = resp.body() != null ? resp.body().string() : "";
                    throw new RuntimeException("Gemini stream failed: HTTP " + resp.code() + " " + err);
                }
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resp.body().byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty() || !line.startsWith("data: ")) continue;
                        String data = line.substring(6);
                        JsonNode node = objectMapper.readTree(data);
                        JsonNode candidates = node.path("candidates");
                        if (candidates.isEmpty()) continue;
                        JsonNode content = candidates.get(0).path("content");
                        JsonNode parts = content.path("parts");
                        StringBuilder text = new StringBuilder();
                        if (parts.isArray()) {
                            for (JsonNode part : parts) {
                                text.append(part.path("text").asText(""));
                            }
                        }
                        String finishReason = candidates.get(0).path("finishReason").asText();
                        onChunk.accept(ChatResponse.builder()
                                .content(text.toString())
                                .finishReason(finishReason.isEmpty() ? null : finishReason)
                                .build());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Gemini stream failed: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(ChatRequest request) throws JsonProcessingException {
        // Gemini 格式: contents[]  + systemInstruction + generationConfig
        List<Map<String, Object>> contents = new ArrayList<>();
        String systemText = null;

        for (ChatRequest.Message msg : request.getMessages()) {
            if ("system".equals(msg.getRole())) {
                systemText = msg.getContent();
            } else {
                String role = "assistant".equals(msg.getRole()) ? "model" : "user";
                Map<String, Object> part = new HashMap<>();
                part.put("text", msg.getContent());
                Map<String, Object> c = new HashMap<>();
                c.put("role", role);
                c.put("parts", List.of(part));
                contents.add(c);
            }
        }

        Map<String, Object> root = new HashMap<>();
        root.put("contents", contents);

        if (systemText != null) {
            Map<String, Object> si = new HashMap<>();
            si.put("parts", List.of(Map.of("text", systemText)));
            root.put("systemInstruction", si);
        }

        Map<String, Object> genConfig = new HashMap<>();
        if (request.getTemperature() != null) genConfig.put("temperature", request.getTemperature());
        if (request.getMaxTokens() != null) genConfig.put("maxOutputTokens", request.getMaxTokens());
        if (!genConfig.isEmpty()) root.put("generationConfig", genConfig);
        if (request.getExtraParams() != null) root.putAll(request.getExtraParams());

        return objectMapper.writeValueAsString(root);
    }

    private ChatResponse parseResponse(Response resp) throws IOException {
        if (!resp.isSuccessful()) {
            String err = resp.body() != null ? resp.body().string() : "";
            throw new RuntimeException("HTTP " + resp.code() + ": " + err);
        }
        JsonNode node = objectMapper.readTree(resp.body().string());
        JsonNode candidate = node.path("candidates").path(0);
        JsonNode parts = candidate.path("content").path("parts");
        StringBuilder text = new StringBuilder();
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                text.append(part.path("text").asText(""));
            }
        }
        JsonNode usage = node.path("usageMetadata");
        return ChatResponse.builder()
                .content(text.toString())
                .finishReason(candidate.path("finishReason").asText("STOP"))
                .usage(ChatResponse.TokenUsage.builder()
                        .promptTokens(usage.path("promptTokenCount").asInt())
                        .completionTokens(usage.path("candidatesTokenCount").asInt())
                        .totalTokens(usage.path("totalTokenCount").asInt())
                        .build())
                .build();
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-goog-api-key", apiKey);
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private static String rtrim(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
