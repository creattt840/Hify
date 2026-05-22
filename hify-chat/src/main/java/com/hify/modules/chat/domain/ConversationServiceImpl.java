package com.hify.modules.chat.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.agent.api.AgentDetailResponse;
import com.hify.modules.agent.api.AgentService;
import com.hify.modules.chat.api.ConversationService;
import com.hify.modules.chat.infra.entity.ConversationPo;
import com.hify.modules.chat.infra.entity.MessagePo;
import com.hify.modules.chat.infra.mapper.ConversationMapper;
import com.hify.modules.chat.infra.mapper.MessageMapper;
import com.hify.modules.provider.api.ChatRequest;
import com.hify.modules.provider.api.ChatResponse;
import com.hify.modules.provider.api.ModelConfigResponse;
import com.hify.modules.provider.api.ModelService;
import com.hify.modules.provider.api.ProviderAdapter;
import com.hify.modules.provider.api.ProviderResponse;
import com.hify.modules.provider.domain.ProviderAdapterFactory;
import com.hify.modules.provider.infra.entity.ProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final AgentService agentService;
    private final ModelService modelService;
    private final ProviderAdapterFactory adapterFactory;
    private final ObjectMapper objectMapper;

    @Lazy
    @Autowired
    private ConversationServiceImpl self;

    @Qualifier("llmStreamExecutor")
    private final ThreadPoolExecutor llmStreamExecutor;

    // ─── sendMessage（无 @Transactional） ───

    @Override
    public SseEmitter sendMessage(Long agentId, Long conversationId, String content) {
        // 1. 解析 Agent 和模型配置
        AgentDetailResponse agent = agentService.getById(agentId);
        if (agent == null) throw new BizException(ErrorCode.AGENT_NOT_FOUND);

        ModelConfigResponse modelConfig = modelService.getModelConfigById(agent.getModelConfigId());
        if (modelConfig == null) throw new BizException(ErrorCode.NOT_FOUND, "模型配置不存在");

        ProviderResponse provider = modelService.getProviderById(modelConfig.getProviderId());
        if (provider == null) throw new BizException(ErrorCode.PROVIDER_NOT_FOUND);

        // 2. 创建或获取会话
        final Long convId;
        if (conversationId != null) {
            ConversationPo existing = conversationMapper.selectById(conversationId);
            if (existing == null) throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND);
            convId = existing.getId();
        } else {
            ConversationPo conv = new ConversationPo();
            conv.setAgentId(agentId);
            conv.setTitle(content.length() > 50 ? content.substring(0, 50) : content);
            conv.setMessageCount(0);
            conv.setStatus("active");
            conversationMapper.insert(conv);
            convId = conv.getId();
        }

        // 3. 保存用户消息（独立事务）
        self.saveUserMessage(convId, content);

        // 4. 加载历史消息（最近 20 条，用于构造上下文）
        List<MessagePo> history = self.loadHistory(convId);

        // 5. 构建 ChatRequest
        List<ChatRequest.Message> messages = new ArrayList<>();
        messages.add(ChatRequest.Message.builder().role("system").content(agent.getSystemPrompt()).build());
        for (MessagePo msg : history) {
            messages.add(ChatRequest.Message.builder().role(msg.getRole()).content(msg.getContent()).build());
        }

        ChatRequest chatRequest = ChatRequest.builder()
                .model(modelConfig.getModelId())
                .messages(messages)
                .temperature(agent.getTemperature())
                .maxTokens(4096)
                .build();

        // 6. 创建 ProviderAdapter
        ProviderType type = ProviderType.fromCode(provider.getProviderType());
        ProviderAdapter adapter = adapterFactory.create(type, provider.getBaseUrl(), provider.getAuthConfig());

        // 7. 创建 SseEmitter
        SseEmitter emitter = new SseEmitter(180_000L);
        emitter.onTimeout(() -> log.warn("SSE timeout, conversationId={}", convId));
        emitter.onError(ex -> log.error("SSE error, conversationId={}", convId, ex));

        // 8. 异步调用 LLM
        llmStreamExecutor.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            ChatResponse.TokenUsage[] usageHolder = new ChatResponse.TokenUsage[1];
            AtomicBoolean clientDisconnected = new AtomicBoolean(false);

            try {
                adapter.streamChat(chatRequest, chunk -> {
                    if (clientDisconnected.get()) return;

                    try {
                        if (chunk.getContent() != null && !chunk.getContent().isEmpty()) {
                            fullResponse.append(chunk.getContent());
                            emitter.send(SseEmitter.event()
                                    .name("delta")
                                    .data(chunk.getContent()));
                        }
                        if (chunk.getUsage() != null) {
                            usageHolder[0] = chunk.getUsage();
                        }
                    } catch (IOException e) {
                        clientDisconnected.set(true);
                        log.debug("client disconnected, conversationId={}", convId);
                    }
                });

                if (!clientDisconnected.get()) {
                    // 保存 AI 回复（独立事务）
                    ChatResponse.TokenUsage usage = usageHolder[0];
                    self.saveAssistantMessage(convId, fullResponse.toString(),
                            usage != null ? usage.getPromptTokens() : 0,
                            usage != null ? usage.getCompletionTokens() : 0);

                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data(Map.of("convId", convId)));
                    emitter.complete();
                }
            } catch (Exception e) {
                if (clientDisconnected.get()) return;
                log.error("LLM stream error, conversationId={}", convId, e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // ─── 独立事务方法 ───

    @Transactional
    public void saveUserMessage(Long conversationId, String content) {
        MessagePo msg = new MessagePo();
        msg.setConversationId(conversationId);
        msg.setRole("user");
        msg.setContent(content);
        msg.setTokenCount(0);
        msg.setMetadata("{}");
        messageMapper.insert(msg);

        // 更新会话消息计数和标题
        ConversationPo conv = conversationMapper.selectById(conversationId);
        if (conv != null) {
            conv.setMessageCount(conv.getMessageCount() + 1);
            if (conv.getMessageCount() == 1 && (conv.getTitle() == null || conv.getTitle().isEmpty())) {
                conv.setTitle(content.length() > 50 ? content.substring(0, 50) : content);
            }
            conversationMapper.updateById(conv);
        }
    }

    @Transactional
    public void saveAssistantMessage(Long conversationId, String content, int promptTokens, int completionTokens) {
        MessagePo msg = new MessagePo();
        msg.setConversationId(conversationId);
        msg.setRole("assistant");
        msg.setContent(content);
        msg.setTokenCount(promptTokens + completionTokens);
        try {
            msg.setMetadata(objectMapper.writeValueAsString(
                    Map.of("promptTokens", promptTokens, "completionTokens", completionTokens)));
        } catch (JsonProcessingException e) {
            msg.setMetadata("{}");
        }
        messageMapper.insert(msg);

        ConversationPo conv = conversationMapper.selectById(conversationId);
        if (conv != null) {
            conv.setMessageCount(conv.getMessageCount() + 1);
            conversationMapper.updateById(conv);
        }
    }

    @Transactional(readOnly = true)
    public List<MessagePo> loadHistory(Long conversationId) {
        // 游标分页取最近 20 条，按时间升序返回（用于构建 LLM 上下文）
        Page<MessagePo> page = new Page<>(0, 20);
        LambdaQueryWrapper<MessagePo> wrapper = new LambdaQueryWrapper<MessagePo>()
                .eq(MessagePo::getConversationId, conversationId)
                .orderByDesc(MessagePo::getCreatedAt);
        List<MessagePo> records = messageMapper.selectPage(page, wrapper).getRecords();

        // 反转为时间升序
        List<MessagePo> result = new ArrayList<>(records);
        java.util.Collections.reverse(result);
        return result;
    }

    @Override
    public List<ConversationPo> listConversations() {
        return conversationMapper.selectList(
                new LambdaQueryWrapper<ConversationPo>()
                        .orderByDesc(ConversationPo::getUpdatedAt));
    }

    @Override
    public List<MessagePo> getMessages(Long conversationId) {
        LambdaQueryWrapper<MessagePo> wrapper = new LambdaQueryWrapper<MessagePo>()
                .eq(MessagePo::getConversationId, conversationId)
                .orderByAsc(MessagePo::getCreatedAt);
        return messageMapper.selectList(wrapper);
    }
}
