package com.hify.modules.provider.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证配置读写：前端与文档统一使用 apiKey，兼容历史 api_key。
 */
public final class AuthConfigUtil {

    private AuthConfigUtil() {
    }

    public static String extractApiKey(Map<String, Object> authConfig) {
        if (authConfig == null || authConfig.isEmpty()) {
            return "";
        }
        Object key = authConfig.get("apiKey");
        if (key == null) {
            key = authConfig.get("api_key");
        }
        return key != null ? key.toString().trim() : "";
    }

    public static Map<String, Object> normalize(Map<String, Object> authConfig) {
        String apiKey = extractApiKey(authConfig);
        if (apiKey.isEmpty()) {
            return Map.of();
        }
        return Map.of("apiKey", apiKey);
    }

    /**
     * 更新时合并：新 key 非空则覆盖，否则保留库中已有配置。
     */
    public static Map<String, Object> mergeOnUpdate(Map<String, Object> existing,
                                                    Map<String, Object> incoming) {
        String newKey = extractApiKey(incoming);
        if (!newKey.isEmpty()) {
            return Map.of("apiKey", newKey);
        }
        if (existing != null && !existing.isEmpty()) {
            return new HashMap<>(existing);
        }
        return Map.of();
    }
}
