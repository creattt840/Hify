package com.hify.modules.workflow.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工作流执行记录响应 —— 含节点级别详情。
 */
@Data
public class WorkflowRunResponse {

    private Long id;
    private Long workflowId;
    private String status;
    private String input;
    private String output;
    private String error;
    private Integer elapsedMs;
    private LocalDateTime createdAt;
    private List<NodeRunItem> nodeRuns;

    @Data
    public static class NodeRunItem {
        private Long id;
        private String nodeKey;
        private String nodeType;
        private String status;
        private Map<String, Object> outputs;
        private String error;
        private Integer elapsedMs;
    }
}
