package com.hify.modules.chat.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSendRequest {

    private Long agentId;

    private Long conversationId;

    private String content;
}
