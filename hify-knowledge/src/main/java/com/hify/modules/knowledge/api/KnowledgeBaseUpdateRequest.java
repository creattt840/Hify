package com.hify.modules.knowledge.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeBaseUpdateRequest {

    @NotBlank(message = "知识库名称不能为空")
    private String name;

    private String description;

    private Boolean isEnabled;
}
