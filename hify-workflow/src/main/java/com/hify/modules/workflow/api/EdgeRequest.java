package com.hify.modules.workflow.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EdgeRequest {

    @NotBlank(message = "source 不能为空")
    private String source;

    @NotBlank(message = "target 不能为空")
    private String target;

    private String condition;

    private Integer sortOrder;
}
