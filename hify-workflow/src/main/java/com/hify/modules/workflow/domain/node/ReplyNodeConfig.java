package com.hify.modules.workflow.domain.node;

import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * REPLY 节点配置 —— 生成最终回复文本。
 */
@JsonTypeName("REPLY")
public record ReplyNodeConfig(
    String replyContent,
    String replyTemplate
) implements NodeConfig {
}
