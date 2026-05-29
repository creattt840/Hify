package com.hify.modules.provider.api;

import java.util.function.Consumer;

/**
 * LLM 提供商适配器接口 —— 每种提供商（OpenAI/Claude/Gemini/Ollama）各自实现。
 */
public interface ProviderAdapter {

    /** 同步非流式调用 */
    ChatResponse chat(ChatRequest request);

    /** 流式调用，每次收到片段回调 consumer */
    void streamChat(ChatRequest request, Consumer<ChatResponse> onChunk);

    /** 向量嵌入调用，默认抛出 UnsupportedOperationException */
    default EmbeddingResponse embed(EmbeddingRequest request) {
        throw new UnsupportedOperationException("Embedding not supported by this provider");
    }
}
