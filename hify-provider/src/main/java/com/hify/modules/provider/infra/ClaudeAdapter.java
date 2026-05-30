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
                messages.add(toAnthropicMessage(msg));
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

        // Anthropic tools 参数
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ChatRequest.Tool tool : request.getTools()) {
                Map<String, Object> t = new HashMap<>();
                t.put("name", tool.getFunction().getName());
                t.put("description", tool.getFunction().getDescription());
                t.put("input_schema", tool.getFunction().getParameters());
                tools.add(t);
            }
            root.put("tools", tools);
        }

        if (request.getExtraParams() != null) root.putAll(request.getExtraParams());
        return objectMapper.writeValueAsString(root);
    }

    /** 将 OpenAI 格式消息转为 Anthropic 格式 */
    private Map<String, Object> toAnthropicMessage(ChatRequest.Message msg) throws JsonProcessingException {
        Map<String, Object> m = new HashMap<>();

        // role=tool → user + tool_result content block
        if ("tool".equals(msg.getRole())) {
            m.put("role", "user");
            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> toolResult = new HashMap<>();
            toolResult.put("type", "tool_result");
            toolResult.put("tool_use_id", msg.getToolCallId());
            toolResult.put("content", msg.getContent() != null ? msg.getContent() : "");
            content.add(toolResult);
            m.put("content", content);
            return m;
        }

        // role=assistant + tool_calls → assistant + text/tool_use blocks
        if ("assistant".equals(msg.getRole())
                && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
            m.put("role", "assistant");
            List<Map<String, Object>> content = new ArrayList<>();
            if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                Map<String, Object> text = new HashMap<>();
                text.put("type", "text");
                text.put("text", msg.getContent());
                content.add(text);
            }
            for (ChatRequest.ToolCall tc : msg.getToolCalls()) {
                Map<String, Object> toolUse = new HashMap<>();
                toolUse.put("type", "tool_use");
                toolUse.put("id", tc.getId());
                toolUse.put("name", tc.getFunction().getName());
                @SuppressWarnings("unchecked")
                Map<String, Object> input = objectMapper.readValue(
                        tc.getFunction().getArguments(), Map.class);
                toolUse.put("input", input);
                content.add(toolUse);
            }
            m.put("content", content);
            return m;
        }

        // user / assistant 纯文本
        m.put("role", msg.getRole());
        m.put("content", msg.getContent() != null ? msg.getContent() : "");
        return m;
    }

    private ChatResponse parseResponse(Response resp) throws IOException {
        if (!resp.isSuccessful()) {
            String err = resp.body() != null ? resp.body().string() : "";
            throw new RuntimeException("HTTP " + resp.code() + ": " + err);
        }
        JsonNode node = objectMapper.readTree(resp.body().string());
        JsonNode content = node.path("content");
        StringBuilder text = new StringBuilder();
        List<ChatResponse.ToolCall> toolCalls = new ArrayList<>();
        if (content.isArray()) {
            for (JsonNode block : content) {
                String type = block.path("type").asText();
                if ("text".equals(type)) {
                    text.append(block.path("text").asText());
                } else if ("tool_use".equals(type)) {
                    toolCalls.add(ChatResponse.ToolCall.builder()
                            .id(block.path("id").asText())
                            .type("function")
                            .function(ChatResponse.FunctionCall.builder()
                                    .name(block.path("name").asText())
                                    .arguments(objectMapper.writeValueAsString(block.path("input")))
                                    .build())
                            .build());
                }
            }
        }
        JsonNode usage = node.path("usage");
        String stopReason = node.path("stop_reason").asText("end_turn");
        String finishReason = (!toolCalls.isEmpty() || "tool_use".equals(stopReason))
                ? "tool_calls" : stopReason;

        return ChatResponse.builder()
                .id(node.path("id").asText())
                .model(node.path("model").asText())
                .content(text.toString())
                .finishReason(finishReason)
                .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
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
