package com.hify.modules.workflow.domain.node;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 节点配置 —— sealed interface，每个节点类型对应一个 record 实现。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "_type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StartNodeConfig.class,    name = "START"),
    @JsonSubTypes.Type(value = EndNodeConfig.class,      name = "END"),
    @JsonSubTypes.Type(value = LlmNodeConfig.class,      name = "LLM"),
    @JsonSubTypes.Type(value = HttpNodeConfig.class,     name = "HTTP"),
    @JsonSubTypes.Type(value = SwitchNodeConfig.class,   name = "SWITCH"),
    @JsonSubTypes.Type(value = ClassifyNodeConfig.class, name = "CLASSIFY"),
    @JsonSubTypes.Type(value = CouponNodeConfig.class,   name = "COUPON"),
    @JsonSubTypes.Type(value = ReplyNodeConfig.class,    name = "REPLY"),
})
public sealed interface NodeConfig
    permits StartNodeConfig, EndNodeConfig, LlmNodeConfig,
            HttpNodeConfig, SwitchNodeConfig, ClassifyNodeConfig,
            CouponNodeConfig, ReplyNodeConfig {
}
