package com.hify.modules.provider.api;

import lombok.Data;

import java.util.Map;

@Data
public class ModelConfigResponse {

    private Long id;
    private Long providerId;
    private String name;
    private String modelId;
    private Map<String, Object> extraParams;
}
