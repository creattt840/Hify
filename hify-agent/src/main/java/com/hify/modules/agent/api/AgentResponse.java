package com.hify.modules.agent.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AgentResponse {

    private Long id;
    private String name;
    private String description;
    private String systemPrompt;
    private Long modelConfigId;
    private Double temperature;
    private String modelName;
    private String modelId;
    private String providerName;
    private String providerType;
    private Boolean isEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
