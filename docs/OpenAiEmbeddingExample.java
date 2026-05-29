package docs;

// ═══════════════════════════════════════════════════════════════
// 给 OpenAiAdapter 加 Embedding 方法 —— 最小改动
// ═══════════════════════════════════════════════════════════════

/*
 * ============================================================
 * 对比：你现有的 chat() 方法
 * ============================================================
public ChatResponse chat(ChatRequest request) {
    // body: { model, messages: [{role, content}], temperature, ... }
    String body = buildRequestBody(request, false);
    Response resp = httpClient.post(baseUrl + "/v1/chat/completions", headers, body);
    // 解析: choices[0].message.content
    return parseResponse(resp);
}
 * ============================================================
 */

/*
 * ============================================================
 * 新增的 embed() 方法 —— 照着 chat() 抄，只改 3 处
 * ============================================================
 */

// ① DTO（新建 EmbeddingRequest.java）
@Data
@Builder
public class EmbeddingRequest {
    private String model;      // "text-embedding-3-small"
    private String input;      // 单条文本
    // 默认用 float 返回，不需要额外参数
}

// ② DTO（新建 EmbeddingResponse.java）
@Data
@Builder
public class EmbeddingResponse {
    private String model;
    private float[] embedding;    // 1536 维向量
    private int promptTokens;
}

// ③ 在 OpenAiAdapter 里加这个方法
public EmbeddingResponse embed(EmbeddingRequest request) {
    try {
        // 构建请求体
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel());
        body.put("input", request.getInput());   // 直接字符串，不需要 messages 数组
        // 不需要 temperature、max_tokens、stream

        String json = objectMapper.writeValueAsString(body);
        Map<String, String> headers = buildHeaders();

        // ★ 唯一不同：URL 是 /v1/embeddings
        try (Response resp = httpClient.post(baseUrl + "/v1/embeddings", headers, json)) {
            if (!resp.isSuccessful()) {
                String err = resp.body() != null ? resp.body().string() : "";
                throw new RuntimeException("Embedding failed: HTTP " + resp.code() + " " + err);
            }

            // ★ 解析逻辑不同：取 data[0].embedding，没有 choices
            JsonNode node = objectMapper.readTree(resp.body().string());
            JsonNode data = node.path("data").get(0);
            JsonNode embeddingNode = data.path("embedding");
            float[] embedding = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }

            return EmbeddingResponse.builder()
                    .model(node.path("model").asText())
                    .embedding(embedding)
                    .promptTokens(node.path("usage").path("prompt_tokens").asInt())
                    .build();
        }
    } catch (IOException e) {
        throw new RuntimeException("Embedding failed: " + e.getMessage(), e);
    }
}
