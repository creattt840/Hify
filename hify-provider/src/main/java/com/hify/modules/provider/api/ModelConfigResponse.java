package com.hify.modules.provider.api;

import lombok.Data;

import java.util.Map;

@Data
public class ModelConfigResponse {

    private Long id;
    private Long providerId;
    /** 所属提供商名称（列表接口填充，便于 Agent 选择） */
    private String providerName;
    private String name;
    private String modelId;
    private Map<String, Object> extraParams;
}
