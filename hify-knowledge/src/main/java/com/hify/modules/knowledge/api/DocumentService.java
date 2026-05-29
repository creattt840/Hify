package com.hify.modules.knowledge.api;

import com.hify.common.web.PageResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档管理模块对外接口。
 */
public interface DocumentService {

    DocumentResponse upload(Long kbId, MultipartFile file);

    PageResult<List<DocumentResponse>> listDocuments(Long kbId, int page, int size);

    DocumentResponse getDocument(Long id);

    List<DocumentChunkResponse> getChunks(Long id);

    void deleteDocument(Long id);
}
