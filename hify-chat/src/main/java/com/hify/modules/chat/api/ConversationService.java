package com.hify.modules.chat.api;

import com.hify.modules.chat.infra.entity.ConversationPo;
import com.hify.modules.chat.infra.entity.MessagePo;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ConversationService {

    /** 发送消息，返回 SSE 流 */
    SseEmitter sendMessage(Long agentId, Long conversationId, String content);

    /** 列出所有会话 */
    List<ConversationPo> listConversations();

    /** 获取会话消息列表（时间升序） */
    List<MessagePo> getMessages(Long conversationId);

    /** 删除会话及其消息（逻辑删除） */
    void deleteConversation(Long id);
}
