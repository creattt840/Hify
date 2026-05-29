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
        po.setAuthConfig(AuthConfigUtil.normalize(request.getAuthConfig()));
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
        po.setAuthConfig(AuthConfigUtil.mergeOnUpdate(po.getAuthConfig(), request.getAuthConfig()));
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
        ConnectivityProbe probe = resolveConnectivityProbe(type, baseUrl, provider.getAuthConfig());

        long start = System.currentTimeMillis();
        try (Response response = llmHttpClient.get(probe.url(), probe.headers())) {
            long latencyMs = System.currentTimeMillis() - start;

            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                String msg = formatProbeError(response.code(), body, type, baseUrl);
                updateHealthFailed(providerId, latencyMs);
                return ConnectionTestResult.fail(msg);
            }

            String body = response.body() != null ? response.body().string() : "{}";
            int modelCount = parseModelCount(probe.openAiStyleModels(), body);
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

    /**
     * 连通性探测：URL + 请求头。
     * DeepSeek 的 Anthropic 兼容入口不提供 /v1/models，需改用 OpenAI 兼容地址验证密钥。
     */
    private record ConnectivityProbe(String url, Map<String, String> headers, boolean openAiStyleModels) {
        ConnectivityProbe(String url, Map<String, String> headers) {
            this(url, headers, true);
        }
    }

    private ConnectivityProbe resolveConnectivityProbe(ProviderType type, String baseUrl,
                                                       Map<String, Object> authConfig) {
        String base = rtrimSlash(baseUrl);
        String lower = base.toLowerCase();
        String apiKey = AuthConfigUtil.extractApiKey(authConfig);

        if (lower.contains("deepseek.com")) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + apiKey);
            return new ConnectivityProbe("https://api.deepseek.com/v1/models", headers, true);
        }

        if (type == ProviderType.OLLAMA) {
            return new ConnectivityProbe(base + "/api/tags", Map.of(), false);
        }

        if (type == ProviderType.GEMINI) {
            String sep = base.contains("?") ? "&" : "?";
            return new ConnectivityProbe(base + "/models" + sep + "key=" + apiKey, Map.of(), true);
        }

        if (type == ProviderType.ANTHROPIC) {
            Map<String, String> headers = new HashMap<>();
            headers.put("x-api-key", apiKey);
            headers.put("anthropic-version", "2023-06-01");
            String url = base.endsWith("/v1") ? base + "/models" : base + "/v1/models";
            return new ConnectivityProbe(url, headers, true);
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        String url = base.endsWith("/v1") ? base + "/models" : base + "/v1/models";
        return new ConnectivityProbe(url, headers, true);
    }

    private String formatProbeError(int code, String body, ProviderType type, String baseUrl) {
        String trimmed = body != null && body.length() > 200 ? body.substring(0, 200) + "..." : body;
        String msg = String.format("HTTP %d: %s", code, trimmed != null ? trimmed : "");
        if (code == 404 && baseUrl.toLowerCase().contains("deepseek.com")
                && type == ProviderType.ANTHROPIC) {
            msg += "（DeepSeek 请使用类型 OpenAI Compatible，API 地址填 https://api.deepseek.com）";
        }
        return msg;
    }

    private int parseModelCount(boolean openAiStyleModels, String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!openAiStyleModels) {
                JsonNode models = root.get("models");
                return models != null && models.isArray() ? models.size() : 0;
            }
            JsonNode data = root.get("data");
            return data != null && data.isArray() ? data.size() : 0;
        } catch (Exception e) {
            log.warn("failed to parse response body: {}", e.getMessage());
            return 0;
        }
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
        List<ProviderPo> enabledProviders = providerMapper.selectList(
                new LambdaQueryWrapper<ProviderPo>().eq(ProviderPo::getIsEnabled, true));
        if (enabledProviders.isEmpty()) {
            return List.of();
        }

        Map<Long, String> providerNames = enabledProviders.stream()
                .collect(Collectors.toMap(ProviderPo::getId, ProviderPo::getName, (a, b) -> a));
        List<Long> enabledProviderIds = enabledProviders.stream()
                .map(ProviderPo::getId)
                .collect(Collectors.toList());

        return modelConfigMapper.selectList(
                        new LambdaQueryWrapper<ModelConfigPo>()
                                .in(ModelConfigPo::getProviderId, enabledProviderIds)
                                .orderByAsc(ModelConfigPo::getName))
                .stream()
                .map(po -> {
                    ModelConfigResponse r = toModelConfigResponse(po);
                    r.setProviderName(providerNames.get(po.getProviderId()));
                    return r;
                })
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

    @Override
    public ProviderResponse resolveEmbeddingProvider(String modelId) {
        // 优先：查找 modelId 匹配的模型配置
        List<ProviderPo> enabledProviders = providerMapper.selectList(
                new LambdaQueryWrapper<ProviderPo>().eq(ProviderPo::getIsEnabled, true));
        if (enabledProviders.isEmpty()) {
            return null;
        }
        Map<Long, ProviderPo> providerMap = enabledProviders.stream()
                .collect(Collectors.toMap(ProviderPo::getId, Function.identity(), (a, b) -> a));

        if (modelId != null && !modelId.isBlank()) {
            List<ModelConfigPo> configs = modelConfigMapper.selectList(
                    new LambdaQueryWrapper<ModelConfigPo>()
                            .eq(ModelConfigPo::getModelId, modelId));
            for (ModelConfigPo c : configs) {
                ProviderPo p = providerMap.get(c.getProviderId());
                if (p != null) {
                    return toResponse(p);
                }
            }
        }

        // 回退：首个启用的 OpenAI / OpenAI Compatible 提供商
        for (ProviderPo p : enabledProviders) {
            String type = p.getProviderType();
            if ("openai".equals(type) || "openai_compatible".equals(type)) {
                return toResponse(p);
            }
        }

        return null;
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
