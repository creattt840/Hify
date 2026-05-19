package com.hify.modules.provider.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ProviderResponse {

    private Long id;
    private String name;
    private String providerType;
    private String baseUrl;
    private Map<String, Object> authConfig;
    private Boolean isEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 健康状态，列表查询时填充 */
    private ProviderHealthResponse health;
    /** 已启用模型数量，列表查询时填充 */
    private Integer modelCount;
}
