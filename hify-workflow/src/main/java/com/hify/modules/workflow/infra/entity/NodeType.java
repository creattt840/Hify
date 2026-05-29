package com.hify.modules.workflow.infra.entity;

/**
 * 工作流节点类型枚举。存入 t_workflow_node.node_type 列 (VARCHAR)。
 */
public enum NodeType {

    START,
    LLM,
    HTTP,
    SWITCH,
    CLASSIFY,
    COUPON,
    REPLY,
    END;

    public static NodeType fromCode(String code) {
        for (NodeType t : values()) {
            if (t.name().equalsIgnoreCase(code)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown node type: " + code);
    }
}
