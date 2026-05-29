package com.hify.modules.knowledge.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResult;
import com.hify.modules.knowledge.api.KnowledgeBaseCreateRequest;
import com.hify.modules.knowledge.api.KnowledgeBaseResponse;
import com.hify.modules.knowledge.api.KnowledgeBaseUpdateRequest;
import com.hify.modules.knowledge.api.KnowledgeService;
import com.hify.modules.knowledge.infra.entity.KnowledgeBasePo;
import com.hify.modules.knowledge.infra.entity.KnowledgeDocumentPo;
import com.hify.modules.knowledge.infra.mapper.KnowledgeBaseMapper;
import com.hify.modules.knowledge.infra.mapper.KnowledgeDocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final JdbcTemplate pgJdbcTemplate;

    public KnowledgeServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper,
                                KnowledgeDocumentMapper knowledgeDocumentMapper,
                                @Qualifier("pgJdbcTemplate") JdbcTemplate pgJdbcTemplate) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.pgJdbcTemplate = pgJdbcTemplate;
    }

    @Override
    public KnowledgeBaseResponse create(KnowledgeBaseCreateRequest request) {
        KnowledgeBasePo po = new KnowledgeBasePo();
        po.setName(request.getName());
        po.setDescription(request.getDescription() != null ? request.getDescription() : "");
        po.setEmbedModel("");
        po.setChunkSize(512);
        po.setChunkOverlap(64);
        po.setIsEnabled(true);
        knowledgeBaseMapper.insert(po);
        return buildResponse(po);
    }

    @Override
    public PageResult<List<KnowledgeBaseResponse>> list(int page, int size, String name) {
        Page<KnowledgeBasePo> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<KnowledgeBasePo> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isBlank()) {
            wrapper.like(KnowledgeBasePo::getName, name);
        }
        wrapper.orderByDesc(KnowledgeBasePo::getCreatedAt);
        Page<KnowledgeBasePo> result = knowledgeBaseMapper.selectPage(pageParam, wrapper);

        List<KnowledgeBaseResponse> list = result.getRecords().isEmpty()
                ? Collections.emptyList()
                : result.getRecords().stream().map(this::buildResponse).collect(Collectors.toList());

        // 批量查询文档数量
        if (!list.isEmpty()) {
            List<Long> kbIds = list.stream().map(KnowledgeBaseResponse::getId).collect(Collectors.toList());
            List<KnowledgeDocumentPo> docs = knowledgeDocumentMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeDocumentPo>().in(KnowledgeDocumentPo::getKbId, kbIds));
            java.util.Map<Long, Long> countMap = docs.stream()
                    .collect(Collectors.groupingBy(KnowledgeDocumentPo::getKbId, Collectors.counting()));
            list.forEach(r -> r.setDocumentCount(countMap.getOrDefault(r.getId(), 0L).intValue()));
        }

        return PageResult.of(result.getTotal(), page, size, list);
    }

    @Override
    public KnowledgeBaseResponse getById(Long id) {
        return buildResponse(requireKnowledgeBase(id));
    }

    @Override
    public KnowledgeBaseResponse update(Long id, KnowledgeBaseUpdateRequest request) {
        KnowledgeBasePo po = requireKnowledgeBase(id);
        po.setName(request.getName());
        po.setDescription(request.getDescription() != null ? request.getDescription() : "");
        if (request.getIsEnabled() != null) {
            po.setIsEnabled(request.getIsEnabled());
        }
        knowledgeBaseMapper.updateById(po);
        return buildResponse(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        KnowledgeBasePo po = requireKnowledgeBase(id);

        // 1. 软删除关联文档 (MySQL)
        knowledgeDocumentMapper.delete(
                new LambdaQueryWrapper<KnowledgeDocumentPo>()
                        .eq(KnowledgeDocumentPo::getKbId, id));

        // 2. 逻辑删除关联分块 (PostgreSQL，失败不阻断 MySQL 删除)
        softDeletePgChunksByKbId(id);

        // 3. 软删除知识库 (MySQL)
        knowledgeBaseMapper.deleteById(po.getId());

        log.info("知识库已删除: id={}, name={}", id, po.getName());
    }

    private void softDeletePgChunksByKbId(Long kbId) {
        try {
            pgJdbcTemplate.update("UPDATE t_knowledge_chunk SET deleted = 1 WHERE kb_id = ?", kbId);
        } catch (Exception e) {
            log.warn("PostgreSQL 分块软删除失败，继续删除知识库: kbId={}, reason={}", kbId, e.getMessage());
        }
    }

    // ────────────────────────── 内部方法 ──────────────────────────

    private KnowledgeBasePo requireKnowledgeBase(Long id) {
        KnowledgeBasePo po = knowledgeBaseMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }
        return po;
    }

    private KnowledgeBaseResponse buildResponse(KnowledgeBasePo po) {
        KnowledgeBaseResponse r = new KnowledgeBaseResponse();
        r.setId(po.getId());
        r.setName(po.getName());
        r.setDescription(po.getDescription());
        r.setEmbedModel(po.getEmbedModel());
        r.setChunkSize(po.getChunkSize());
        r.setChunkOverlap(po.getChunkOverlap());
        r.setIsEnabled(po.getIsEnabled());
        r.setDocumentCount(0);
        r.setCreatedAt(po.getCreatedAt());
        r.setUpdatedAt(po.getUpdatedAt());
        return r;
    }
}
