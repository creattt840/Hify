package com.hify.modules.workflow.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class NodeRequest {

    @NotBlank(message = "nodeKey 不能为空")
    private String nodeKey;

    @NotBlank(message = "nodeType 不能为空")
    private String nodeType;

    private String title;

    private Map<String, Object> config;

    private Integer sortOrder;
}
