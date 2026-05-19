package com.hify.modules.provider.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class ProviderUpdateRequest {

    @NotBlank(message = "提供商名称不能为空")
    private String name;

    @NotBlank(message = "提供商类型不能为空")
    private String providerType;

    private String baseUrl;

    @NotNull(message = "认证配置不能为空")
    private Map<String, Object> authConfig;

    @NotNull(message = "启用状态不能为空")
    private Boolean isEnabled;
}
