package com.hify.modules.agent.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Agent 工具绑定请求 —— 传 toolId 数组，全量替换。
 */
@Data
public class AgentToolBindRequest {

    @NotNull(message = "工具 ID 列表不能为空")
    @Size(max = 10, message = "最多绑定 10 个工具")
    private List<Long> toolIds;
}
