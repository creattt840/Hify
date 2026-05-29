package com.hify.modules.workflow.domain.node;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;

import java.util.Map;

/**
 * 将原始 config Map 解析为类型化的 NodeConfig 实现。
 * <p>
 * 利用 Jackson @JsonTypeInfo 多态反序列化：先向 Map 注入 _type 字段标记节点类型，
 * 再序列化为 JSON 后反序列化为对应的 record 子类。
 */
public final class NodeConfigParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NodeConfigParser() {
    }

    /**
     * 根据 nodeType 将 config Map 解析为类型化配置对象。
     *
     * @param nodeType 节点类型（START / LLM / HTTP / SWITCH / CLASSIFY / COUPON / REPLY / END）
     * @param raw      config 列的原始 Map（可能为 null 或空）
     * @return 类型化的 NodeConfig 实现
     */
    public static NodeConfig parse(String nodeType, Map<String, Object> raw) {
        String type = nodeType.toUpperCase();
        if (raw == null || raw.isEmpty()) {
            return createEmpty(type);
        }
        // 注入 _type 使 Jackson 多态反序列化能路由到正确的子类
        raw.put("_type", type);
        try {
            String json = MAPPER.writeValueAsString(raw);
            return MAPPER.readValue(json, NodeConfig.class);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.WORKFLOW_NODE_INVALID,
                    "节点配置解析失败: " + e.getMessage());
        }
    }

    private static NodeConfig createEmpty(String nodeType) {
        return switch (nodeType) {
            case "START" -> new StartNodeConfig();
            case "END"   -> new EndNodeConfig();
            default -> throw new BizException(ErrorCode.WORKFLOW_NODE_INVALID,
                    "节点类型 " + nodeType + " 不允许空配置");
        };
    }
}
