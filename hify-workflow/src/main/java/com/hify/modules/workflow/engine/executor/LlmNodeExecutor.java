package com.hify.modules.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.provider.api.ChatRequest;
import com.hify.modules.provider.api.ChatResponse;
import com.hify.modules.provider.api.ModelConfigResponse;
import com.hify.modules.provider.api.ModelService;
import com.hify.modules.provider.api.ProviderAdapter;
import com.hify.modules.provider.api.ProviderResponse;
import com.hify.modules.provider.domain.ProviderAdapterFactory;
import com.hify.modules.provider.infra.entity.ProviderType;
import com.hify.modules.workflow.engine.ExecutionContext;
import com.hify.modules.workflow.engine.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LLM 节点执行器 —— 同步调用大模型，结果写入上下文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmNodeExecutor implements NodeExecutor {

    private final ModelService modelService;
    private final ProviderAdapterFactory adapterFactory;

    @Override
    public String nodeType() {
        return "LLM";
    }

    @Override
    public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        EngineLlmConfig llmConfig = (EngineLlmConfig) config;

        // 1. 模板变量替换
        String prompt = ctx.resolve(llmConfig.prompt());

        // 2. 加载模型配置
        ModelConfigResponse mc = modelService.getModelConfigById(llmConfig.modelConfigId());
        if (mc == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型配置不存在: " + llmConfig.modelConfigId());
        }
        ProviderResponse provider = modelService.getProviderById(mc.getProviderId());
        if (provider == null) {
            throw new BizException(ErrorCode.PROVIDER_NOT_FOUND);
        }

        // 3. 创建适配器
        ProviderAdapter adapter = adapterFactory.create(
                ProviderType.fromCode(provider.getProviderType()),
                provider.getBaseUrl(),
                provider.getAuthConfig());

        // 4. 同步调用 LLM
        ChatRequest req = ChatRequest.builder()
                .model(mc.getModelId())
                .messages(List.of(ChatRequest.Message.builder()
                        .role("user").content(prompt).build()))
                .build();

        ChatResponse resp = adapter.chat(req);
        String content = resp.getContent() != null ? resp.getContent() : "";

        // 5. 结果写入上下文
        String varName = llmConfig.outputVariable() != null ? llmConfig.outputVariable() : "output";
        ctx.set(node.getNodeKey(), varName, content);
        log.info("LLM 节点执行完成: nodeKey={}, model={}, outputLen={}",
                node.getNodeKey(), mc.getModelId(), content.length());
    }
}
