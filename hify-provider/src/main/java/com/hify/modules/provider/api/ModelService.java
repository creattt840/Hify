package com.hify.modules.provider.api;

import com.hify.common.web.PageResult;

import java.util.List;

/**
 * 模型管理模块对外暴露接口 —— 供其他模块（agent、conversation 等）调用。
 */
public interface ModelService {

    ProviderResponse create(ProviderCreateRequest request);

    PageResult<List<ProviderResponse>> list(int page, int pageSize);

    ProviderDetailResponse getById(Long id);

    ProviderResponse update(Long id, ProviderUpdateRequest request);

    void delete(Long id);

    ConnectionTestResult testConnection(Long providerId);

    // ─── 跨模块数据查询 ───

    /** 获取单个模型配置 */
    ModelConfigResponse getModelConfigById(Long id);

    /** 批量获取模型配置 */
    List<ModelConfigResponse> getModelConfigsByIds(List<Long> ids);

    /** 获取单个提供商 */
    ProviderResponse getProviderById(Long id);

    /** 批量获取提供商 */
    List<ProviderResponse> getProvidersByIds(List<Long> ids);

    /** 获取全部模型配置列表（供下拉选择） */
    List<ModelConfigResponse> listModelConfigs();

    /** 获取某 Provider 下的模型配置列表 */
    List<ModelConfigResponse> listModelConfigsByProviderId(Long providerId);

    /** 为 Provider 创建模型配置 */
    ModelConfigResponse createModelConfig(Long providerId, ModelConfigRequest request);

    /** 更新模型配置 */
    ModelConfigResponse updateModelConfig(Long configId, ModelConfigRequest request);

    /** 删除模型配置 */
    void deleteModelConfig(Long configId);

    /** 根据模型名查找可用的 Embedding 提供商，优先匹配 modelId，无匹配则回退到首个启用的 OpenAI 类提供商 */
    ProviderResponse resolveEmbeddingProvider(String modelId);
}
