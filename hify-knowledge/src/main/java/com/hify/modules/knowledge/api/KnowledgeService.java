package com.hify.modules.knowledge.api;

import com.hify.common.web.PageResult;

import java.util.List;

/**
 * 知识库模块对外接口。
 */
public interface KnowledgeService {

    KnowledgeBaseResponse create(KnowledgeBaseCreateRequest request);

    PageResult<List<KnowledgeBaseResponse>> list(int page, int size, String name);

    KnowledgeBaseResponse getById(Long id);

    KnowledgeBaseResponse update(Long id, KnowledgeBaseUpdateRequest request);

    void delete(Long id);
}
