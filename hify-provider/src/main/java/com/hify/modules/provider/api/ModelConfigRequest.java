package com.hify.modules.provider.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class ModelConfigRequest {

    @NotBlank(message = "配置名称不能为空")
    private String name;

    @NotBlank(message = "模型 ID 不能为空")
    private String modelId;

    private Map<String, Object> extraParams;
}
