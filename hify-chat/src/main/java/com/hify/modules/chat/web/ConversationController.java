package com.hify.modules.chat.web;

import com.hify.common.web.Result;
import com.hify.modules.chat.api.ChatSendRequest;
import com.hify.modules.chat.api.ConversationService;
import com.hify.modules.chat.infra.entity.ConversationPo;
import com.hify.modules.chat.infra.entity.MessagePo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public Result<List<ConversationPo>> list() {
        return Result.ok(conversationService.listConversations());
    }

    @GetMapping("/{id}/messages")
    public Result<List<MessagePo>> messages(@PathVariable Long id) {
        return Result.ok(conversationService.getMessages(id));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatSendRequest request) {
        return conversationService.sendMessage(
                request.getAgentId(),
                request.getConversationId(),
                request.getContent());
    }
}
