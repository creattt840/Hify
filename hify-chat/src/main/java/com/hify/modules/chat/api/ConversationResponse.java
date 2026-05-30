package com.hify.modules.chat.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationResponse {

    private Long id;
    private Long agentId;
    private String agentName;
    private String title;
    private String status;
    private Integer messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
