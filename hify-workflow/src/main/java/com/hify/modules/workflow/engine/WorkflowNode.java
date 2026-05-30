package com.hify.modules.workflow.engine;

import java.util.Map;

/**
 * 引擎层节点表示，和 MyBatis-Plus PO 解耦。
 * 包含节点标识和原始配置 Map，执行前由外部解析为类型化的 {@link com.hify.modules.workflow.engine.executor.NodeConfigDef}。
 */
public class WorkflowNode {

    private final String nodeKey;
    private final String nodeType;
    private final String title;
    private final Map<String, Object> config;

    public WorkflowNode(String nodeKey, String nodeType, String title, Map<String, Object> config) {
        this.nodeKey = nodeKey;
        this.nodeType = nodeType;
        this.title = title;
        this.config = config;
    }

    public String getNodeKey() { return nodeKey; }
    public String getNodeType() { return nodeType; }
    public String getTitle() { return title; }
    public Map<String, Object> getConfig() { return config; }
}
