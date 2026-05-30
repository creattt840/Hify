package com.hify.modules.chat.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.agent.api.AgentDetailResponse;
import com.hify.modules.agent.api.AgentService;
import com.hify.modules.chat.api.ConversationResponse;
import com.hify.modules.chat.api.ConversationService;
import com.hify.modules.chat.infra.entity.ConversationPo;
import com.hify.modules.chat.infra.entity.MessagePo;
import com.hify.modules.chat.infra.mapper.ConversationMapper;
import com.hify.modules.chat.infra.mapper.MessageMapper;
import com.hify.modules.mcp.api.McpService;
import com.hify.modules.mcp.api.McpServerService;
import com.hify.modules.mcp.api.McpToolDef;
import com.hify.modules.provider.api.ChatRequest;
import com.hify.modules.provider.api.ChatResponse;
import com.hify.modules.provider.api.ModelConfigResponse;
import com.hify.modules.provider.api.ModelService;
import com.hify.modules.provider.api.ProviderAdapter;
import com.hify.modules.provider.api.ProviderResponse;
import com.hify.modules.provider.domain.ProviderAdapterFactory;
import com.hify.modules.provider.infra.entity.ProviderType;
import com.hify.modules.workflow.engine.WorkflowEngine;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final AgentService agentService;
    private final ModelService modelService;
    private final ProviderAdapterFactory adapterFactory;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;
    private final McpServerService mcpServerService;
    private final McpService mcpService;

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

        // 如果 Agent 绑定了工作流，走 WorkflowEngine 同步执行
        if (agent.getWorkflowId() != null) {
            log.info("Agent 绑定工作流，走 WorkflowEngine: agentId={}, workflowId={}", agentId, agent.getWorkflowId());
            return runWorkflow(agent.getWorkflowId(), content, convId);
        }

        // 4. 加载 Agent 绑定的有效工具（已过滤失效/禁用的绑定）
        List<Long> boundToolIds = agentService.getBoundToolIds(agentId);
        List<McpToolDef> tools = boundToolIds.isEmpty()
                ? Collections.emptyList()
                : mcpServerService.getToolDefsByIds(boundToolIds);
        if (tools.isEmpty()) {
            log.debug("Agent [{}] 无有效 MCP 工具绑定，对话不走工具调用", agentId);
        } else {
            log.info("Agent [{}] 已加载 {} 个 MCP 工具: {}", agentId, tools.size(),
                    tools.stream().map(McpToolDef::getName).toList());
        }

        // 5. 加载历史消息（最近 20 条，用于构造上下文）
        List<MessagePo> history = self.loadHistory(convId);

        // 6. 构建 ChatRequest（过滤无效历史，避免 MCP 启用前后上下文冲突）
        List<ChatRequest.Message> messages = buildLlmMessages(agent.getSystemPrompt(), history);

        ChatRequest chatRequest = ChatRequest.builder()
                .model(modelConfig.getModelId())
                .messages(messages)
                .temperature(agent.getTemperature())
                .maxTokens(4096)
                .tools(tools.isEmpty() ? null : buildToolSchemas(tools))
                .build();

        // 7. 创建 ProviderAdapter
        ProviderType type = ProviderType.fromCode(provider.getProviderType());
        ProviderAdapter adapter = adapterFactory.create(type, provider.getBaseUrl(), provider.getAuthConfig());

        // 8. 创建 SseEmitter
        SseEmitter emitter = new SseEmitter(180_000L);
        emitter.onTimeout(() -> log.warn("SSE timeout, conversationId={}", convId));
        emitter.onError(ex -> log.error("SSE error, conversationId={}", convId, ex));

        // 9. 异步调用 LLM
        llmStreamExecutor.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            ChatResponse.TokenUsage[] usageHolder = new ChatResponse.TokenUsage[1];
            AtomicBoolean clientDisconnected = new AtomicBoolean(false);

            try {
                if (!tools.isEmpty()) {
                    runToolCallLoop(emitter, convId, adapter, chatRequest, messages, tools,
                            fullResponse, usageHolder, clientDisconnected);
                } else {
                    adapter.streamChat(chatRequest, streamCallback(
                            emitter, fullResponse, usageHolder, clientDisconnected));

                    if (!clientDisconnected.get()) {
                        finishStream(emitter, convId, fullResponse.toString(), usageHolder[0]);
                    }
                }

            } catch (Exception e) {
                if (clientDisconnected.get()) return;
                log.error("LLM stream error, conversationId={}", convId, e);
                String errMsg = e.getMessage() != null ? e.getMessage() : "对话请求失败";
                safeSend(emitter, "error", errMsg);
                emitter.complete();
            }
        });

        return emitter;
    }

    // ─── 工具相关辅助方法 ───

    private static final int MAX_TOOL_ROUNDS = 8;

    /**
     * MCP 工具调用循环：同步调用 LLM，支持多轮 tool_use，每段文本即时推送 delta。
     */
    private void runToolCallLoop(SseEmitter emitter, Long convId, ProviderAdapter adapter,
                                 ChatRequest baseRequest, List<ChatRequest.Message> messages,
                                 List<McpToolDef> tools, StringBuilder fullResponse,
                                 ChatResponse.TokenUsage[] usageHolder,
                                 AtomicBoolean clientDisconnected) {
        List<ChatRequest.Tool> toolSchemas = baseRequest.getTools();

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            ChatRequest roundRequest = ChatRequest.builder()
                    .model(baseRequest.getModel())
                    .messages(messages)
                    .temperature(baseRequest.getTemperature())
                    .maxTokens(baseRequest.getMaxTokens())
                    .tools(toolSchemas)
                    .build();

            ChatResponse resp = adapter.chat(roundRequest);
            usageHolder[0] = resp.getUsage();

            if ("tool_calls".equals(resp.getFinishReason())
                    && resp.getToolCalls() != null
                    && !resp.getToolCalls().isEmpty()) {

                appendAndEmitDelta(emitter, fullResponse, resp.getContent(), clientDisconnected);

                List<ChatRequest.ToolCall> reqToolCalls = toRequestToolCalls(resp.getToolCalls());
                messages.add(ChatRequest.Message.builder()
                        .role("assistant")
                        .content(resp.getContent() != null ? resp.getContent() : "")
                        .toolCalls(reqToolCalls)
                        .build());

                List<ChatRequest.Message> toolResults = executeTools(resp.getToolCalls(), tools);
                messages.addAll(toolResults);
                for (ChatRequest.Message tr : toolResults) {
                    self.saveToolMessage(convId, tr.getToolCallId(), tr.getContent());
                }
                continue;
            }

            appendAndEmitDelta(emitter, fullResponse, resp.getContent(), clientDisconnected);
            if (!clientDisconnected.get()) {
                finishStream(emitter, convId, fullResponse.toString(), usageHolder[0]);
            }
            return;
        }

        if (!clientDisconnected.get()) {
            safeSend(emitter, "error", "工具调用次数过多，请简化问题后重试");
            emitter.complete();
        }
    }

    private void appendAndEmitDelta(SseEmitter emitter, StringBuilder fullResponse,
                                    String content, AtomicBoolean clientDisconnected) {
        if (clientDisconnected.get() || content == null || content.isBlank()) {
            return;
        }
        fullResponse.append(content);
        safeSend(emitter, "delta", content);
    }

    /**
     * 从 DB 历史构建 LLM 消息列表。
     * <p>
     * 跳过 tool 消息：库中未保存 assistant tool_calls，直接传给 API 会导致请求失败；
     * 工具结果已体现在紧随其后的 assistant 回复中。
     * 跳过空 assistant 占位消息。
     */
    private List<ChatRequest.Message> buildLlmMessages(String systemPrompt, List<MessagePo> history) {
        List<ChatRequest.Message> messages = new ArrayList<>();
        messages.add(ChatRequest.Message.builder().role("system").content(systemPrompt).build());
        for (MessagePo msg : history) {
            if ("tool".equals(msg.getRole())) {
                continue;
            }
            if ("assistant".equals(msg.getRole())
                    && (msg.getContent() == null || msg.getContent().isBlank())) {
                continue;
            }
            messages.add(ChatRequest.Message.builder()
                    .role(msg.getRole())
                    .content(msg.getContent())
                    .build());
        }
        return messages;
    }

    /** 从 McpToolDef 构造 OpenAI tools 参数 */
    private List<ChatRequest.Tool> buildToolSchemas(List<McpToolDef> tools) {
        return tools.stream().map(t -> ChatRequest.Tool.builder()
                .type("function")
                .function(ChatRequest.Function.builder()
                        .name(t.getName())
                        .description(t.getDescription())
                        .parameters(t.getInputSchema())
                        .build())
                .build()).toList();
    }

    /**
     * 执行工具调用列表，返回 role=tool 的消息。
     * 工具调用失败时不抛异常，而是把错误信息作为 tool 消息返回给 LLM。
     */
    private List<ChatRequest.Message> executeTools(
            List<ChatResponse.ToolCall> toolCalls, List<McpToolDef> toolsDefs) {

        // 构建 toolId → McpToolDef 的映射（按工具名查找 serverId）
        Map<String, McpToolDef> nameToDef = new HashMap<>();
        for (McpToolDef def : toolsDefs) {
            nameToDef.put(def.getName(), def);
        }

        List<ChatRequest.Message> results = new ArrayList<>();
        for (ChatResponse.ToolCall tc : toolCalls) {
            String toolName = tc.getFunction().getName();
            String toolCallId = tc.getId();
            String argsJson = tc.getFunction().getArguments();

            String toolResult;
            try {
                McpToolDef def = nameToDef.get(toolName);
                if (def == null) {
                    toolResult = "错误：工具 [" + toolName + "] 未在 Agent 绑定的工具列表中找到";
                    log.warn("工具调用失败: toolName={}, 未找到对应的 McpToolDef", toolName);
                } else {
                    // 解析 arguments JSON → Map
                    @SuppressWarnings("unchecked")
                    Map<String, Object> arguments = objectMapper.readValue(argsJson, Map.class);
                    toolResult = mcpService.callTool(def.getServerId(), toolName, arguments);
                    log.info("工具调用成功: toolName={}, serverId={}", toolName, def.getServerId());
                }
            } catch (Exception e) {
                log.error("工具调用失败: toolName={}", toolName, e);
                toolResult = "工具调用失败: " + e.getMessage();
            }

            results.add(ChatRequest.Message.builder()
                    .role("tool")
                    .toolCallId(toolCallId)
                    .content(toolResult)
                    .build());
        }
        return results;
    }

    /** 将 ChatResponse.ToolCall 转为 ChatRequest.ToolCall（用于保存到 messages 历史） */
    private List<ChatRequest.ToolCall> toRequestToolCalls(List<ChatResponse.ToolCall> respCalls) {
        return respCalls.stream().map(tc -> ChatRequest.ToolCall.builder()
                .id(tc.getId())
                .type(tc.getType())
                .function(ChatRequest.FunctionCall.builder()
                        .name(tc.getFunction().getName())
                        .arguments(tc.getFunction().getArguments())
                        .build())
                .build()).toList();
    }

    // ─── 流式回调 ───

    /** 创建流式回调，处理 delta 推送和 usage 记录 */
    private Consumer<ChatResponse> streamCallback(
            SseEmitter emitter, StringBuilder fullResponse,
            ChatResponse.TokenUsage[] usageHolder, AtomicBoolean clientDisconnected) {
        return chunk -> {
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
                log.debug("client disconnected, SSE send failed");
            }
        };
    }

    /** 保存 assistant 消息并发送 done 事件 */
    private void finishStream(SseEmitter emitter, Long convId,
                              String fullContent, ChatResponse.TokenUsage usage) {
        if (fullContent == null || fullContent.isBlank()) {
            log.warn("LLM 返回空内容, conversationId={}", convId);
            safeSend(emitter, "error", "模型未返回有效内容，请重试");
            emitter.complete();
            return;
        }
        self.saveAssistantMessage(convId, fullContent,
                usage != null ? usage.getPromptTokens() : 0,
                usage != null ? usage.getCompletionTokens() : 0);

        safeSend(emitter, "done", Map.of("convId", convId));
        emitter.complete();
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

    @Transactional
    public void saveToolMessage(Long conversationId, String toolCallId, String content) {
        MessagePo msg = new MessagePo();
        msg.setConversationId(conversationId);
        msg.setRole("tool");
        msg.setContent(content);
        msg.setTokenCount(0);
        try {
            msg.setMetadata(objectMapper.writeValueAsString(Map.of("toolCallId", toolCallId)));
        } catch (JsonProcessingException e) {
            msg.setMetadata("{}");
        }
        messageMapper.insert(msg);
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

    // ─── 工作流执行 ───

    /**
     * 通过 WorkflowEngine 同步执行工作流，结果以 SSE 事件推送给前端。
     * 和原有 LLM 流一样：创建 SseEmitter → 提交 llmStreamExecutor → 立即返回 emitter。
     */
    private SseEmitter runWorkflow(Long workflowId, String userInput, Long convId) {
        SseEmitter emitter = new SseEmitter(180_000L);
        emitter.onTimeout(() -> log.warn("Workflow SSE timeout, workflowId={}", workflowId));
        emitter.onError(e -> log.error("Workflow SSE error, workflowId={}", workflowId, e));

        llmStreamExecutor.execute(() -> {
            try {
                String result = workflowEngine.execute(workflowId, userInput);

                // 保存 AI 回复
                self.saveAssistantMessage(convId, result, 0, 0);

                safeSend(emitter, "done", Map.of("convId", convId));
                emitter.complete();
                log.info("工作流执行完成: workflowId={}, convId={}", workflowId, convId);

            } catch (BizException e) {
                log.error("工作流执行失败: workflowId={}", workflowId, e);
                safeSend(emitter, "error", e.getMessage());
                emitter.complete();  // 不能让 emitter 处于未完成状态

            } catch (Exception e) {
                log.error("工作流执行异常: workflowId={}", workflowId, e);
                safeSend(emitter, "error", "工作流执行失败: " + e.getMessage());
                emitter.complete();
            }
        });

        return emitter;
    }

    /** 安全推送 SSE 事件，忽略客户端断开的 IOException */
    private void safeSend(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException e) {
            log.debug("SSE send failed, client may have disconnected");
        }
    }

    @Override
    public List<ConversationResponse> listConversations() {
        List<ConversationPo> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<ConversationPo>()
                        .orderByDesc(ConversationPo::getUpdatedAt));
        if (conversations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> agentIds = conversations.stream()
                .map(ConversationPo::getAgentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> agentNameMap = agentService.getNamesByIds(agentIds);

        return conversations.stream()
                .map(po -> toConversationResponse(po, agentNameMap))
                .toList();
    }

    private ConversationResponse toConversationResponse(ConversationPo po, Map<Long, String> agentNameMap) {
        ConversationResponse response = new ConversationResponse();
        response.setId(po.getId());
        response.setAgentId(po.getAgentId());
        response.setTitle(po.getTitle());
        response.setStatus(po.getStatus());
        response.setMessageCount(po.getMessageCount());
        response.setCreatedAt(po.getCreatedAt());
        response.setUpdatedAt(po.getUpdatedAt());

        if (po.getAgentId() != null) {
            String name = agentNameMap.get(po.getAgentId());
            response.setAgentName(name != null ? name : "Agent #" + po.getAgentId());
        } else {
            response.setAgentName("");
        }
        return response;
    }

    @Override
    public List<MessagePo> getMessages(Long conversationId) {
        LambdaQueryWrapper<MessagePo> wrapper = new LambdaQueryWrapper<MessagePo>()
                .eq(MessagePo::getConversationId, conversationId)
                .orderByAsc(MessagePo::getCreatedAt);
        return messageMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(Long id) {
        ConversationPo po = conversationMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        messageMapper.delete(
                new LambdaQueryWrapper<MessagePo>().eq(MessagePo::getConversationId, id));
        conversationMapper.deleteById(id);
        log.info("对话已删除: id={}, title={}", id, po.getTitle());
    }
}
