package com.hify.modules.provider.api;

import lombok.Data;

import java.util.List;

@Data
public class EmbeddingResponse {

    private String model;
    private List<EmbeddingData> data;
    private TokenUsage usage;

    @Data
    public static class EmbeddingData {
        private int index;
        private float[] embedding;
    }

    @Data
    public static class TokenUsage {
        private int promptTokens;
        private int totalTokens;
    }
}
