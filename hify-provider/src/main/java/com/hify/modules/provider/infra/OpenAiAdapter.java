package com.hify.modules.provider.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.modules.provider.api.ChatRequest;
import com.hify.modules.provider.api.ChatResponse;
import com.hify.modules.provider.api.EmbeddingRequest;
import com.hify.modules.provider.api.EmbeddingResponse;
import com.hify.modules.provider.api.ProviderAdapter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
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
                    // 流式 tool_calls 累积状态
                    Map<Integer, ChatResponse.ToolCall> tcMap = new HashMap<>();
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

                        String content = "";
                        List<ChatResponse.ToolCall> toolCalls = null;

                        if (delta != null) {
                            // 文本内容
                            JsonNode contentNode = delta.get("content");
                            if (contentNode != null && !contentNode.isNull()) {
                                content = contentNode.asText("");
                            }
                            // 工具调用 delta（流式累积）
                            JsonNode tcDelta = delta.get("tool_calls");
                            if (tcDelta != null && tcDelta.isArray()) {
                                for (JsonNode tc : tcDelta) {
                                    int idx = tc.path("index").asInt();
                                    ChatResponse.ToolCall cur = tcMap.computeIfAbsent(idx, k ->
                                            ChatResponse.ToolCall.builder()
                                                    .id("")
                                                    .type("function")
                                                    .function(ChatResponse.FunctionCall.builder()
                                                            .name("")
                                                            .arguments("")
                                                            .build())
                                                    .build());
                                    if (tc.has("id") && !tc.get("id").isNull()) {
                                        cur.setId(tc.get("id").asText());
                                    }
                                    JsonNode fn = tc.get("function");
                                    if (fn != null) {
                                        if (fn.has("name") && !fn.get("name").isNull()) {
                                            cur.getFunction().setName(
                                                    cur.getFunction().getName() + fn.get("name").asText());
                                        }
                                        if (fn.has("arguments") && !fn.get("arguments").isNull()) {
                                            cur.getFunction().setArguments(
                                                    cur.getFunction().getArguments() + fn.get("arguments").asText());
                                        }
                                    }
                                }
                            }
                        }

                        String finishReason = (finish != null && !finish.isNull()) ? finish.asText() : null;

                        // 最后一个 chunk（finish_reason 不为 null）时附上累积的 tool_calls
                        if (finishReason != null && !tcMap.isEmpty()) {
                            toolCalls = new ArrayList<>(tcMap.values());
                        }

                        ChatResponse chunk = ChatResponse.builder()
                                .id(node.path("id").asText())
                                .model(node.path("model").asText())
                                .content(content)
                                .finishReason(finishReason)
                                .toolCalls(toolCalls)
                                .build();
                        onChunk.accept(chunk);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("OpenAI stream failed: " + e.getMessage(), e);
        }
    }

    // ── 请求体构造 ──

    private String buildRequestBody(ChatRequest request, boolean stream) throws JsonProcessingException {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatRequest.Message msg : request.getMessages()) {
            Map<String, Object> m = new HashMap<>();
            m.put("role", msg.getRole());

            // tool 消息：content + tool_call_id
            if ("tool".equals(msg.getRole())) {
                m.put("content", msg.getContent());
                m.put("tool_call_id", msg.getToolCallId());
            } else if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                // assistant 消息含 tool_calls
                if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                    m.put("content", msg.getContent());
                } else {
                    m.put("content", "");
                }
                List<Map<String, Object>> tcs = new ArrayList<>();
                for (ChatRequest.ToolCall tc : msg.getToolCalls()) {
                    Map<String, Object> tcm = new HashMap<>();
                    tcm.put("id", tc.getId());
                    tcm.put("type", tc.getType());
                    Map<String, String> fn = new HashMap<>();
                    fn.put("name", tc.getFunction().getName());
                    fn.put("arguments", tc.getFunction().getArguments());
                    tcm.put("function", fn);
                    tcs.add(tcm);
                }
                m.put("tool_calls", tcs);
            } else {
                m.put("content", msg.getContent());
            }
            messages.add(m);
        }

        Map<String, Object> root = new HashMap<>();
        root.put("model", request.getModel());
        root.put("messages", messages);
        root.put("stream", stream);
        if (request.getTemperature() != null) root.put("temperature", request.getTemperature());
        if (request.getMaxTokens() != null) root.put("max_tokens", request.getMaxTokens());

        // tools 参数
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            List<Map<String, Object>> toolList = new ArrayList<>();
            for (ChatRequest.Tool tool : request.getTools()) {
                Map<String, Object> tm = new HashMap<>();
                tm.put("type", tool.getType());
                Map<String, Object> fn = new HashMap<>();
                fn.put("name", tool.getFunction().getName());
                fn.put("description", tool.getFunction().getDescription());
                fn.put("parameters", tool.getFunction().getParameters());
                tm.put("function", fn);
                toolList.add(tm);
            }
            root.put("tools", toolList);
        }

        if (request.getExtraParams() != null) root.putAll(request.getExtraParams());
        return objectMapper.writeValueAsString(root);
    }

    // ── 响应解析 ──

    private ChatResponse parseResponse(Response resp) throws IOException {
        if (!resp.isSuccessful()) {
            String err = resp.body() != null ? resp.body().string() : "";
            throw new RuntimeException("HTTP " + resp.code() + ": " + err);
        }
        JsonNode node = objectMapper.readTree(resp.body().string());
        JsonNode choice = node.path("choices").path(0);
        JsonNode msg = choice.path("message");
        JsonNode usage = node.path("usage");

        // 文本内容
        String content = msg.path("content").asText("");

        // tool_calls（非流式响应）
        List<ChatResponse.ToolCall> toolCalls = null;
        JsonNode tcNode = msg.get("tool_calls");
        if (tcNode != null && tcNode.isArray()) {
            toolCalls = new ArrayList<>();
            for (JsonNode tc : tcNode) {
                JsonNode fn = tc.path("function");
                toolCalls.add(ChatResponse.ToolCall.builder()
                        .id(tc.path("id").asText())
                        .type(tc.path("type").asText("function"))
                        .function(ChatResponse.FunctionCall.builder()
                                .name(fn.path("name").asText())
                                .arguments(fn.path("arguments").asText())
                                .build())
                        .build());
            }
        }

        return ChatResponse.builder()
                .id(node.path("id").asText())
                .model(node.path("model").asText())
                .content(content)
                .finishReason(choice.path("finish_reason").asText("stop"))
                .toolCalls(toolCalls)
                .usage(ChatResponse.TokenUsage.builder()
                        .promptTokens(usage.path("prompt_tokens").asInt())
                        .completionTokens(usage.path("completion_tokens").asInt())
                        .totalTokens(usage.path("total_tokens").asInt())
                        .build())
                .build();
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", request.getModel());
            body.put("input", request.getInput());
            String json = objectMapper.writeValueAsString(body);

            Map<String, String> headers = buildHeaders();
            try (Response resp = httpClient.post(baseUrl + "/v1/embeddings", headers, json)) {
                if (!resp.isSuccessful()) {
                    String err = resp.body() != null ? resp.body().string() : "";
                    throw new RuntimeException("Embedding failed: HTTP " + resp.code() + " " + err);
                }
                return parseEmbeddingResponse(resp);
            }
        } catch (IOException e) {
            throw new RuntimeException("OpenAI embedding failed: " + e.getMessage(), e);
        }
    }

    private EmbeddingResponse parseEmbeddingResponse(Response resp) throws IOException {
        JsonNode root = objectMapper.readTree(resp.body().string());

        EmbeddingResponse response = new EmbeddingResponse();
        response.setModel(root.path("model").asText());

        List<EmbeddingResponse.EmbeddingData> dataList = new ArrayList<>();
        JsonNode dataArray = root.path("data");
        for (JsonNode item : dataArray) {
            EmbeddingResponse.EmbeddingData d = new EmbeddingResponse.EmbeddingData();
            d.setIndex(item.path("index").asInt());
            JsonNode embeddingNode = item.path("embedding");
            float[] vec = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vec[i] = (float) embeddingNode.get(i).asDouble();
            }
            d.setEmbedding(vec);
            dataList.add(d);
        }
        response.setData(dataList);

        JsonNode usageNode = root.path("usage");
        EmbeddingResponse.TokenUsage usage = new EmbeddingResponse.TokenUsage();
        usage.setPromptTokens(usageNode.path("prompt_tokens").asInt());
        usage.setTotalTokens(usageNode.path("total_tokens").asInt());
        response.setUsage(usage);

        return response;
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
