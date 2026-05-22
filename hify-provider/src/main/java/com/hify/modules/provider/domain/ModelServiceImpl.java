package com.hify.modules.provider.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResult;
import com.hify.modules.provider.api.*;
import com.hify.modules.provider.infra.LlmHttpClient;
import com.hify.modules.provider.infra.entity.ModelConfigPo;
import com.hify.modules.provider.infra.entity.ProviderPo;
import com.hify.modules.provider.infra.entity.ProviderHealthPo;
import com.hify.modules.provider.infra.entity.ProviderType;
import com.hify.modules.provider.infra.mapper.ModelConfigMapper;
import com.hify.modules.provider.infra.mapper.ProviderHealthMapper;
import com.hify.modules.provider.infra.mapper.ProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelServiceImpl implements ModelService {

    private final ProviderMapper providerMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final ProviderHealthMapper providerHealthMapper;
    private final LlmHttpClient llmHttpClient;
    private final ObjectMapper objectMapper;

    // ────────────────────────────── CRUD ──────────────────────────────

    @Override
    public ProviderResponse create(ProviderCreateRequest request) {
        ProviderPo po = new ProviderPo();
        po.setName(request.getName());
        po.setProviderType(request.getProviderType());
        po.setBaseUrl(request.getBaseUrl() != null ? request.getBaseUrl() : "");
        po.setAuthConfig(request.getAuthConfig());
        po.setIsEnabled(request.getIsEnabled());
        providerMapper.insert(po);
        return toResponse(po);
    }

    @Override
    public PageResult<List<ProviderResponse>> list(int page, int pageSize) {
        Page<ProviderPo> pageParam = new Page<>(page, pageSize);
        Page<ProviderPo> result = providerMapper.selectPage(pageParam,
                new LambdaQueryWrapper<ProviderPo>().orderByDesc(ProviderPo::getCreatedAt));
        List<ProviderResponse> list = result.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        if (!list.isEmpty()) {
            List<Long> ids = list.stream().map(ProviderResponse::getId).collect(Collectors.toList());

            // 批量查 health
            List<ProviderHealthPo> healths = providerHealthMapper.selectList(
                    new LambdaQueryWrapper<ProviderHealthPo>().in(ProviderHealthPo::getProviderId, ids));
            Map<Long, ProviderHealthPo> healthMap = healths.stream()
                    .collect(Collectors.toMap(ProviderHealthPo::getProviderId, Function.identity(), (a, b) -> a));

            // 批量查模型数
            List<ModelConfigPo> configs = modelConfigMapper.selectList(
                    new LambdaQueryWrapper<ModelConfigPo>().in(ModelConfigPo::getProviderId, ids));
            Map<Long, Long> modelCountMap = configs.stream()
                    .collect(Collectors.groupingBy(ModelConfigPo::getProviderId, Collectors.counting()));

            for (ProviderResponse item : list) {
                ProviderHealthPo h = healthMap.get(item.getId());
                if (h != null) {
                    ProviderHealthResponse hr = new ProviderHealthResponse();
                    hr.setFailCount(h.getFailCount());
                    hr.setLatencyMs(h.getLatencyMs());
                    hr.setLastSuccessAt(h.getLastSuccessAt());
                    item.setHealth(hr);
                }
                item.setModelCount(modelCountMap.getOrDefault(item.getId(), 0L).intValue());
            }
        }

        return PageResult.of(result.getTotal(), page, pageSize, list);
    }

    @Override
    public ProviderDetailResponse getById(Long id) {
        ProviderPo provider = requireProvider(id);
        ProviderDetailResponse detail = new ProviderDetailResponse();
        BeanUtils.copyProperties(toResponse(provider), detail);

        // model configs
        List<ModelConfigPo> configs = modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfigPo>().eq(ModelConfigPo::getProviderId, id));
        detail.setModelConfigs(configs.stream().map(c -> {
            ModelConfigResponse r = new ModelConfigResponse();
            r.setId(c.getId());
            r.setProviderId(c.getProviderId());
            r.setName(c.getName());
            r.setModelId(c.getModelId());
            r.setExtraParams(c.getExtraParams());
            return r;
        }).collect(Collectors.toList()));

        // health
        ProviderHealthPo health = findHealth(id);
        if (health != null) {
            ProviderHealthResponse hr = new ProviderHealthResponse();
            hr.setFailCount(health.getFailCount());
            hr.setLatencyMs(health.getLatencyMs());
            hr.setLastSuccessAt(health.getLastSuccessAt());
            detail.setHealth(hr);
        }

        return detail;
    }

    @Override
    public ProviderResponse update(Long id, ProviderUpdateRequest request) {
        ProviderPo po = requireProvider(id);
        po.setName(request.getName());
        po.setProviderType(request.getProviderType());
        po.setBaseUrl(request.getBaseUrl() != null ? request.getBaseUrl() : "");
        po.setAuthConfig(request.getAuthConfig());
        po.setIsEnabled(request.getIsEnabled());
        providerMapper.updateById(po);
        return toResponse(po);
    }

    @Override
    public void delete(Long id) {
        ProviderPo po = requireProvider(id);
        providerMapper.deleteById(po.getId());
    }

    // ────────────────────────── 连通性测试 ──────────────────────────

    @Override
    public ConnectionTestResult testConnection(Long providerId) {
        ProviderPo provider = requireProvider(providerId);

        ProviderType type = ProviderType.fromCode(provider.getProviderType());
        String baseUrl = rtrimSlash(provider.getBaseUrl());
        Map<String, String> headers = buildHeaders(type, provider.getAuthConfig());

        long start = System.currentTimeMillis();
        try (Response response = llmHttpClient.get(buildTestUrl(type, baseUrl, provider.getAuthConfig()), headers)) {
            long latencyMs = System.currentTimeMillis() - start;

            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                String msg = String.format("HTTP %d: %s", response.code(), body);
                updateHealthFailed(providerId, latencyMs);
                return ConnectionTestResult.fail(msg);
            }

            String body = response.body() != null ? response.body().string() : "{}";
            int modelCount = parseModelCount(type, body);
            updateHealthSuccess(providerId, latencyMs);
            return ConnectionTestResult.ok(latencyMs, modelCount);
        } catch (IOException e) {
            long latencyMs = System.currentTimeMillis() - start;
            updateHealthFailed(providerId, latencyMs);
            log.warn("connectivity test failed for provider {}: {}", providerId, e.getMessage());
            return ConnectionTestResult.fail("网络异常: " + e.getMessage());
        }
    }

    // ────────────────────────── 内部方法 ──────────────────────────

    private ProviderPo requireProvider(Long id) {
        ProviderPo provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BizException(ErrorCode.PROVIDER_NOT_FOUND);
        }
        return provider;
    }

    private ProviderResponse toResponse(ProviderPo po) {
        ProviderResponse r = new ProviderResponse();
        r.setId(po.getId());
        r.setName(po.getName());
        r.setProviderType(po.getProviderType());
        r.setBaseUrl(po.getBaseUrl());
        r.setAuthConfig(po.getAuthConfig());
        r.setIsEnabled(po.getIsEnabled());
        r.setCreatedAt(po.getCreatedAt());
        r.setUpdatedAt(po.getUpdatedAt());
        return r;
    }

    private Map<String, String> buildHeaders(ProviderType type, Map<String, Object> authConfig) {
        Map<String, String> headers = new HashMap<>();
        String apiKey = extractApiKey(authConfig);

        switch (type) {
            case OPENAI:
            case OPENAI_COMPATIBLE:
                headers.put("Authorization", "Bearer " + apiKey);
                break;
            case ANTHROPIC:
                headers.put("x-api-key", apiKey);
                headers.put("anthropic-version", "2023-06-01");
                break;
            case GEMINI:
            case OLLAMA:
                break;
        }
        return headers;
    }

    private String buildTestUrl(ProviderType type, String baseUrl, Map<String, Object> authConfig) {
        switch (type) {
            case OLLAMA:
                return baseUrl + "/api/tags";
            case GEMINI: {
                String key = extractApiKey(authConfig);
                String sep = baseUrl.contains("?") ? "&" : "?";
                return baseUrl + "/models" + sep + "key=" + key;
            }
            case ANTHROPIC:
                return baseUrl + "/v1/models";
            default:
                if (baseUrl.endsWith("/v1")) {
                    return baseUrl + "/models";
                }
                return baseUrl + "/v1/models";
        }
    }

    private int parseModelCount(ProviderType type, String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            switch (type) {
                case OLLAMA: {
                    JsonNode models = root.get("models");
                    return models != null && models.isArray() ? models.size() : 0;
                }
                default: {
                    JsonNode data = root.get("data");
                    return data != null && data.isArray() ? data.size() : 0;
                }
            }
        } catch (Exception e) {
            log.warn("failed to parse response body: {}", e.getMessage());
            return 0;
        }
    }

    private String extractApiKey(Map<String, Object> authConfig) {
        if (authConfig == null) {
            return "";
        }
        Object key = authConfig.get("api_key");
        return key != null ? key.toString() : "";
    }

    // ────────────────────────── 健康状态 ──────────────────────────

    private ProviderHealthPo findHealth(Long providerId) {
        LambdaQueryWrapper<ProviderHealthPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProviderHealthPo::getProviderId, providerId);
        return providerHealthMapper.selectOne(wrapper);
    }

    private void updateHealthSuccess(Long providerId, long latencyMs) {
        ProviderHealthPo health = getOrCreateHealth(providerId);
        health.setFailCount(0);
        health.setLatencyMs((int) latencyMs);
        health.setLastSuccessAt(LocalDateTime.now());
        saveHealth(health);
    }

    private void updateHealthFailed(Long providerId, long latencyMs) {
        ProviderHealthPo health = getOrCreateHealth(providerId);
        health.setFailCount(health.getFailCount() + 1);
        health.setLatencyMs((int) latencyMs);
        saveHealth(health);
    }

    private ProviderHealthPo getOrCreateHealth(Long providerId) {
        ProviderHealthPo existing = findHealth(providerId);
        if (existing != null) {
            return existing;
        }
        ProviderHealthPo health = new ProviderHealthPo();
        health.setProviderId(providerId);
        health.setFailCount(0);
        health.setLatencyMs(0);
        return health;
    }

    private void saveHealth(ProviderHealthPo health) {
        if (health.getId() != null) {
            providerHealthMapper.updateById(health);
        } else {
            providerHealthMapper.insert(health);
        }
    }

    // ──────────────────────── 跨模块查询 ────────────────────────

    @Override
    public ModelConfigResponse getModelConfigById(Long id) {
        ModelConfigPo po = modelConfigMapper.selectById(id);
        if (po == null) return null;
        ModelConfigResponse r = new ModelConfigResponse();
        r.setId(po.getId());
        r.setProviderId(po.getProviderId());
        r.setName(po.getName());
        r.setModelId(po.getModelId());
        r.setExtraParams(po.getExtraParams());
        return r;
    }

    @Override
    public List<ModelConfigResponse> getModelConfigsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return modelConfigMapper.selectList(
                        new LambdaQueryWrapper<ModelConfigPo>().in(ModelConfigPo::getId, ids))
                .stream().map(po -> {
                    ModelConfigResponse r = new ModelConfigResponse();
                    r.setId(po.getId());
                    r.setProviderId(po.getProviderId());
                    r.setName(po.getName());
                    r.setModelId(po.getModelId());
                    r.setExtraParams(po.getExtraParams());
                    return r;
                }).collect(Collectors.toList());
    }

    @Override
    public ProviderResponse getProviderById(Long id) {
        ProviderPo po = providerMapper.selectById(id);
        return po != null ? toResponse(po) : null;
    }

    @Override
    public List<ProviderResponse> getProvidersByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return providerMapper.selectList(
                        new LambdaQueryWrapper<ProviderPo>().in(ProviderPo::getId, ids))
                .stream().map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ModelConfigResponse> listModelConfigs() {
        // 只返回已启用 Provider 的模型配置
        List<Long> enabledProviderIds = providerMapper.selectList(
                        new LambdaQueryWrapper<ProviderPo>()
                                .eq(ProviderPo::getIsEnabled, true)
                                .eq(ProviderPo::getDeleted, 0))
                .stream().map(ProviderPo::getId)
                .collect(Collectors.toList());

        if (enabledProviderIds.isEmpty()) return List.of();

        return modelConfigMapper.selectList(
                        new LambdaQueryWrapper<ModelConfigPo>()
                                .in(ModelConfigPo::getProviderId, enabledProviderIds)
                                .orderByAsc(ModelConfigPo::getName))
                .stream().map(this::toModelConfigResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ModelConfigResponse> listModelConfigsByProviderId(Long providerId) {
        return modelConfigMapper.selectList(
                        new LambdaQueryWrapper<ModelConfigPo>()
                                .eq(ModelConfigPo::getProviderId, providerId)
                                .orderByAsc(ModelConfigPo::getName))
                .stream().map(this::toModelConfigResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ModelConfigResponse createModelConfig(Long providerId, ModelConfigRequest request) {
        // 验证 Provider 存在
        requireProvider(providerId);
        ModelConfigPo po = new ModelConfigPo();
        po.setProviderId(providerId);
        po.setName(request.getName());
        po.setModelId(request.getModelId());
        po.setExtraParams(request.getExtraParams() != null ? request.getExtraParams() : Map.of());
        modelConfigMapper.insert(po);
        return toModelConfigResponse(po);
    }

    @Override
    public ModelConfigResponse updateModelConfig(Long configId, ModelConfigRequest request) {
        ModelConfigPo po = modelConfigMapper.selectById(configId);
        if (po == null) throw new BizException(ErrorCode.NOT_FOUND, "模型配置不存在");
        po.setName(request.getName());
        po.setModelId(request.getModelId());
        po.setExtraParams(request.getExtraParams() != null ? request.getExtraParams() : Map.of());
        modelConfigMapper.updateById(po);
        return toModelConfigResponse(po);
    }

    @Override
    public void deleteModelConfig(Long configId) {
        ModelConfigPo po = modelConfigMapper.selectById(configId);
        if (po == null) throw new BizException(ErrorCode.NOT_FOUND, "模型配置不存在");
        modelConfigMapper.deleteById(configId);
    }

    private static String rtrimSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private ModelConfigResponse toModelConfigResponse(ModelConfigPo po) {
        ModelConfigResponse r = new ModelConfigResponse();
        r.setId(po.getId());
        r.setProviderId(po.getProviderId());
        r.setName(po.getName());
        r.setModelId(po.getModelId());
        r.setExtraParams(po.getExtraParams());
        return r;
    }
}
