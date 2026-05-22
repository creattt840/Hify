package com.hify.modules.chat.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_conversation")
public class ConversationPo extends BaseEntity {

    private Long agentId;

    private String title;

    private String status;

    private Integer messageCount;
}
