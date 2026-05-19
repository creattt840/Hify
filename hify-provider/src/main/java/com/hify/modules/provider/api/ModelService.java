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
}
