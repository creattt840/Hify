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

    /**
     * 向量相似度检索 Top-K 分块。
     * 对查询文本做 embedding 后在 pgvector 中执行余弦相似度搜索。
     *
     * @param kbId  知识库 ID
     * @param query 查询文本
     * @param topK  返回数量
     * @return 相似分块列表，按相似度降序
     */
    List<KnowledgeSearchResult> searchChunks(Long kbId, String query, int topK);
}
