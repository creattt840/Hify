package com.hify.modules.agent.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AgentUpdateRequest {

    @NotBlank(message = "Agent 名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "系统提示词不能为空")
    private String systemPrompt;

    @NotNull(message = "模型配置 ID 不能为空")
    private Long modelConfigId;

    @DecimalMin(value = "0.0", message = "温度不能小于 0.0")
    @DecimalMax(value = "2.0", message = "温度不能大于 2.0")
    private Double temperature;

    @NotNull(message = "启用状态不能为空")
    private Boolean isEnabled;
}
