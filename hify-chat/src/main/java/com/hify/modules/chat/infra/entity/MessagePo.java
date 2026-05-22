package com.hify.modules.chat.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_message")
public class MessagePo extends BaseEntity {

    private Long conversationId;

    private String role;

    private String content;

    private Integer tokenCount;

    private String metadata;
}
