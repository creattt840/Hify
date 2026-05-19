package com.hify.modules.agent.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResult;
import com.hify.modules.agent.api.*;
import com.hify.modules.agent.infra.entity.AgentPo;
import com.hify.modules.agent.infra.mapper.AgentMapper;
import com.hify.modules.provider.api.ModelConfigResponse;
import com.hify.modules.provider.api.ModelService;
import com.hify.modules.provider.api.ProviderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;
    private final ModelService modelService;

    // ────────────────────────────── CRUD ──────────────────────────────

    @Override
    public AgentResponse create(AgentCreateRequest request) {
        AgentPo po = new AgentPo();
        po.setName(request.getName());
        po.setDescription(request.getDescription() != null ? request.getDescription() : "");
        po.setSystemPrompt(request.getSystemPrompt());
        po.setModelConfigId(request.getModelConfigId());
        po.setTemperature(request.getTemperature() != null ? request.getTemperature() : 0.7);
        po.setIsEnabled(request.getIsEnabled());
        agentMapper.insert(po);
        return enrichResponse(po);
    }

    @Override
    public PageResult<List<AgentResponse>> list(int page, int pageSize) {
        Page<AgentPo> pageParam = new Page<>(page, pageSize);
        Page<AgentPo> result = agentMapper.selectPage(pageParam,
                new LambdaQueryWrapper<AgentPo>().orderByDesc(AgentPo::getCreatedAt));

        List<AgentPo> records = result.getRecords();
        List<AgentResponse> list;
        if (!records.isEmpty()) {
            // 批量取模型配置
            List<Long> modelConfigIds = records.stream()
                    .map(AgentPo::getModelConfigId).distinct().collect(Collectors.toList());
            Map<Long, ModelConfigResponse> modelMap = modelService.getModelConfigsByIds(modelConfigIds)
                    .stream().collect(Collectors.toMap(ModelConfigResponse::getId, Function.identity(), (a, b) -> a));

            // 批量取提供商
            List<Long> providerIds = modelMap.values().stream()
                    .map(ModelConfigResponse::getProviderId).distinct().collect(Collectors.toList());
            Map<Long, ProviderResponse> providerMap = modelService.getProvidersByIds(providerIds)
                    .stream().collect(Collectors.toMap(ProviderResponse::getId, Function.identity(), (a, b) -> a));

            list = records.stream()
                    .map(po -> buildResponse(po, modelMap, providerMap))
                    .collect(Collectors.toList());
        } else {
            list = Collections.emptyList();
        }

        return PageResult.of(result.getTotal(), page, pageSize, list);
    }

    @Override
    public AgentDetailResponse getById(Long id) {
        AgentPo agent = requireAgent(id);
        AgentDetailResponse detail = new AgentDetailResponse();
        copyFields(agent, detail);

        ModelConfigResponse mc = modelService.getModelConfigById(agent.getModelConfigId());
        if (mc != null) {
            detail.setModelName(mc.getName());
            detail.setModelId(mc.getModelId());
            ProviderResponse provider = modelService.getProviderById(mc.getProviderId());
            if (provider != null) {
                detail.setProviderName(provider.getName());
                detail.setProviderType(provider.getProviderType());
            }
        }

        detail.setKnowledgeBases(Collections.emptyList());
        detail.setMcpTools(Collections.emptyList());
        return detail;
    }

    @Override
    public AgentResponse update(Long id, AgentUpdateRequest request) {
        AgentPo po = requireAgent(id);
        po.setName(request.getName());
        po.setDescription(request.getDescription() != null ? request.getDescription() : "");
        po.setSystemPrompt(request.getSystemPrompt());
        po.setModelConfigId(request.getModelConfigId());
        po.setTemperature(request.getTemperature() != null ? request.getTemperature() : 0.7);
        po.setIsEnabled(request.getIsEnabled());
        agentMapper.updateById(po);
        return enrichResponse(po);
    }

    @Override
    public void delete(Long id) {
        AgentPo po = requireAgent(id);
        agentMapper.deleteById(po.getId());
    }

    // ────────────────────────── 内部方法 ──────────────────────────

    private AgentPo requireAgent(Long id) {
        AgentPo agent = agentMapper.selectById(id);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }
        return agent;
    }

    /** 单条 Agent 响应（创建/更新后返回），实时查关联信息 */
    private AgentResponse enrichResponse(AgentPo po) {
        AgentResponse r = new AgentResponse();
        copyFields(po, r);
        ModelConfigResponse mc = modelService.getModelConfigById(po.getModelConfigId());
        if (mc != null) {
            r.setModelName(mc.getName());
            r.setModelId(mc.getModelId());
            ProviderResponse provider = modelService.getProviderById(mc.getProviderId());
            if (provider != null) {
                r.setProviderName(provider.getName());
                r.setProviderType(provider.getProviderType());
            }
        }
        return r;
    }

    /** 列表批量组装，从已查好的 Map 中取，避免 N+1 */
    private AgentResponse buildResponse(AgentPo po,
                                        Map<Long, ModelConfigResponse> modelMap,
                                        Map<Long, ProviderResponse> providerMap) {
        AgentResponse r = new AgentResponse();
        copyFields(po, r);
        ModelConfigResponse mc = modelMap.get(po.getModelConfigId());
        if (mc != null) {
            r.setModelName(mc.getName());
            r.setModelId(mc.getModelId());
            ProviderResponse provider = providerMap.get(mc.getProviderId());
            if (provider != null) {
                r.setProviderName(provider.getName());
                r.setProviderType(provider.getProviderType());
            }
        }
        return r;
    }

    private void copyFields(AgentPo po, AgentResponse r) {
        r.setId(po.getId());
        r.setName(po.getName());
        r.setDescription(po.getDescription());
        r.setSystemPrompt(po.getSystemPrompt());
        r.setModelConfigId(po.getModelConfigId());
        r.setTemperature(po.getTemperature());
        r.setIsEnabled(po.getIsEnabled());
        r.setCreatedAt(po.getCreatedAt());
        r.setUpdatedAt(po.getUpdatedAt());
    }
}
