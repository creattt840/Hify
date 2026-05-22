package com.hify.modules.provider.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.provider.api.ProviderAdapter;
import com.hify.modules.provider.infra.ClaudeAdapter;
import com.hify.modules.provider.infra.GeminiAdapter;
import com.hify.modules.provider.infra.LlmHttpClient;
import com.hify.modules.provider.infra.OllamaAdapter;
import com.hify.modules.provider.infra.OpenAiAdapter;
import com.hify.modules.provider.infra.entity.ProviderType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ProviderAdapterFactory {

    private final LlmHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ProviderAdapterFactory(LlmHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public ProviderAdapter create(ProviderType type, String baseUrl, Map<String, Object> authConfig) {
        String apiKey = extractApiKey(authConfig);
        switch (type) {
            case OPENAI:
            case OPENAI_COMPATIBLE:
                return new OpenAiAdapter(httpClient, objectMapper, baseUrl, apiKey);
            case ANTHROPIC:
                return new ClaudeAdapter(httpClient, objectMapper, baseUrl, apiKey);
            case GEMINI:
                return new GeminiAdapter(httpClient, objectMapper, baseUrl, apiKey);
            case OLLAMA:
                return new OllamaAdapter(httpClient, objectMapper, baseUrl);
            default:
                throw new BizException(ErrorCode.INVALID_PROVIDER_TYPE, "不支持的提供商: " + type);
        }
    }

    private String extractApiKey(Map<String, Object> authConfig) {
        if (authConfig == null) return "";
        Object key = authConfig.get("api_key");
        return key != null ? key.toString() : "";
    }
}
