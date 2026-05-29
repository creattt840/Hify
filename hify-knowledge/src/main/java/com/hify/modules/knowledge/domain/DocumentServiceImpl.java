package com.hify.modules.knowledge.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResult;
import com.hify.modules.knowledge.api.DocumentChunkResponse;
import com.hify.modules.knowledge.api.DocumentResponse;
import com.hify.modules.knowledge.api.DocumentService;
import com.hify.modules.knowledge.infra.entity.KnowledgeBasePo;
import com.hify.modules.knowledge.infra.entity.KnowledgeDocumentPo;
import com.hify.modules.knowledge.infra.mapper.KnowledgeBaseMapper;
import com.hify.modules.knowledge.infra.mapper.KnowledgeDocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Set<String> ALLOWED_TYPES = Set.of("txt", "md", "pdf");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final JdbcTemplate pgJdbcTemplate;
    private final DocumentProcessor documentProcessor;

    @Value("${hify.knowledge.upload-dir:./upload}")
    private String uploadDir;

    public DocumentServiceImpl(KnowledgeDocumentMapper documentMapper,
                                KnowledgeBaseMapper knowledgeBaseMapper,
                                @Qualifier("pgJdbcTemplate") JdbcTemplate pgJdbcTemplate,
                                DocumentProcessor documentProcessor) {
        this.documentMapper = documentMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.pgJdbcTemplate = pgJdbcTemplate;
        this.documentProcessor = documentProcessor;
    }

    @Override
    public DocumentResponse upload(Long kbId, MultipartFile file) {
        requireKnowledgeBase(kbId);

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件名为空");
        }

        String ext = getExtension(originalName).toLowerCase();
        if (!ALLOWED_TYPES.contains(ext)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的文件类型: " + ext + "，仅接受 txt/md/pdf");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件超过 10MB 限制");
        }

        String storedName = System.currentTimeMillis() + "_" + originalName;
        Path kbDir = Path.of(uploadDir, kbId.toString());
        try {
            Files.createDirectories(kbDir);
            Files.copy(file.getInputStream(), kbDir.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException(ErrorCode.KNOWLEDGE_UPLOAD_FAILED, "文件保存失败: " + e.getMessage());
        }

        KnowledgeDocumentPo po = new KnowledgeDocumentPo();
        po.setKbId(kbId);
        po.setName(originalName);
        po.setFileType(ext);
        po.setFilePath(kbDir.resolve(storedName).toString());
        po.setFileSize(file.getSize());
        po.setStatus("PENDING");
        po.setChunkCount(0);
        po.setErrorMsg("");
        documentMapper.insert(po);

        documentProcessor.process(po.getId(), kbId);

        return buildResponse(po);
    }

    @Override
    public PageResult<List<DocumentResponse>> listDocuments(Long kbId, int page, int size) {
        Page<KnowledgeDocumentPo> pageParam = new Page<>(page, size);
        Page<KnowledgeDocumentPo> result = documentMapper.selectPage(pageParam,
                new LambdaQueryWrapper<KnowledgeDocumentPo>()
                        .eq(KnowledgeDocumentPo::getKbId, kbId)
                        .orderByDesc(KnowledgeDocumentPo::getCreatedAt));

        List<DocumentResponse> list = result.getRecords().isEmpty()
                ? Collections.emptyList()
                : result.getRecords().stream().map(this::buildResponse).collect(Collectors.toList());

        return PageResult.of(result.getTotal(), page, size, list);
    }

    @Override
    public DocumentResponse getDocument(Long id) {
        return buildResponse(requireDocument(id));
    }

    @Override
    public List<DocumentChunkResponse> getChunks(Long id) {
        requireDocument(id);

        String sql = "SELECT id, document_id, kb_id, chunk_index, content, token_count, created_at " +
                     "FROM t_knowledge_chunk WHERE document_id = ? AND deleted = 0 ORDER BY chunk_index";
        return pgJdbcTemplate.query(sql, (rs, rowNum) -> {
            DocumentChunkResponse r = new DocumentChunkResponse();
            r.setId(rs.getLong("id"));
            r.setDocumentId(rs.getLong("document_id"));
            r.setKbId(rs.getLong("kb_id"));
            r.setChunkIndex(rs.getInt("chunk_index"));
            r.setContent(rs.getString("content"));
            r.setTokenCount(rs.getInt("token_count"));
            r.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            return r;
        }, id);
    }

    @Override
    public void deleteDocument(Long id) {
        KnowledgeDocumentPo po = requireDocument(id);

        softDeletePgChunksByDocumentId(id);
        documentMapper.deleteById(po.getId());
        log.info("文档已删除: id={}, name={}", id, po.getName());
    }

    private void softDeletePgChunksByDocumentId(Long documentId) {
        try {
            pgJdbcTemplate.update("UPDATE t_knowledge_chunk SET deleted = 1 WHERE document_id = ?", documentId);
        } catch (Exception e) {
            log.warn("PostgreSQL 分块软删除失败，继续删除文档: documentId={}, reason={}", documentId, e.getMessage());
        }
    }

    // ────────────────────────── 内部方法 ──────────────────────────

    private KnowledgeDocumentPo requireDocument(Long id) {
        KnowledgeDocumentPo po = documentMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return po;
    }

    private void requireKnowledgeBase(Long kbId) {
        KnowledgeBasePo kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }
    }

    private DocumentResponse buildResponse(KnowledgeDocumentPo po) {
        DocumentResponse r = new DocumentResponse();
        r.setId(po.getId());
        r.setKbId(po.getKbId());
        r.setName(po.getName());
        r.setFileType(po.getFileType());
        r.setFileSize(po.getFileSize());
        r.setStatus(po.getStatus());
        r.setChunkCount(po.getChunkCount());
        r.setErrorMsg(po.getErrorMsg());
        r.setCreatedAt(po.getCreatedAt());
        r.setUpdatedAt(po.getUpdatedAt());
        return r;
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1);
    }
}
