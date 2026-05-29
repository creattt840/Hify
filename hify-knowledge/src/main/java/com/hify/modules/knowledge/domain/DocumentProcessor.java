package com.hify.modules.knowledge.domain;

import com.hify.modules.knowledge.api.ChunkDTO;
import com.hify.modules.knowledge.infra.entity.KnowledgeBasePo;
import com.hify.modules.knowledge.infra.entity.KnowledgeDocumentPo;
import com.hify.modules.knowledge.infra.mapper.KnowledgeBaseMapper;
import com.hify.modules.knowledge.infra.mapper.KnowledgeDocumentMapper;
import com.hify.modules.provider.api.EmbeddingRequest;
import com.hify.modules.provider.api.EmbeddingResponse;
import com.hify.modules.provider.api.ModelService;
import com.hify.modules.provider.api.ProviderAdapter;
import com.hify.modules.provider.api.ProviderResponse;
import com.hify.modules.provider.domain.ProviderAdapterFactory;
import com.hify.modules.provider.infra.entity.ProviderType;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DocumentProcessor {

    private static final int EMBED_BATCH_SIZE = 100;
    private static final String[] SPLIT_SEPARATORS = {"\n\n", "\n", "。", "！", "？", ".", "!", "?", " "};

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final ModelService modelService;
    private final ProviderAdapterFactory adapterFactory;

    private final JdbcTemplate pgJdbcTemplate;

    public DocumentProcessor(KnowledgeDocumentMapper documentMapper,
                              KnowledgeBaseMapper knowledgeBaseMapper,
                              ModelService modelService,
                              ProviderAdapterFactory adapterFactory,
                              @Qualifier("pgJdbcTemplate") JdbcTemplate pgJdbcTemplate) {
        this.documentMapper = documentMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.modelService = modelService;
        this.adapterFactory = adapterFactory;
        this.pgJdbcTemplate = pgJdbcTemplate;
    }

    // ═══════════════════════════════════════════════════════════════
    // 管线入口
    // ═══════════════════════════════════════════════════════════════

    @Async("asyncExecutor")
    public void process(Long documentId, Long kbId) {
        KnowledgeDocumentPo doc = documentMapper.selectById(documentId);
        if (doc == null) {
            log.error("文档不存在: documentId={}", documentId);
            return;
        }

        try {
            // 1. 状态更新
            stepUpdateStatus(doc);

            // 2. 解析
            String text = stepExtractText(doc.getFilePath(), doc.getFileType());

            // 3. 分块
            List<ChunkDTO> chunks = stepSplitChunks(text);

            // 4. 向量化
            KnowledgeBasePo kb = knowledgeBaseMapper.selectById(kbId);
            chunks = stepEmbedChunks(chunks, kb);

            // 5. 存储
            stepSaveChunks(doc, kbId, chunks);

            doc.setStatus("DONE");
            doc.setChunkCount(chunks.size());
            documentMapper.updateById(doc);
            log.info("文档处理完成: documentId={}, chunks={}", documentId, chunks.size());

        } catch (Exception e) {
            log.error("文档处理失败: documentId={}", documentId, e);
            doc.setStatus("FAILED");
            doc.setErrorMsg(truncate(e.getMessage(), 500));
            documentMapper.updateById(doc);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 环节 1：状态更新
    // ═══════════════════════════════════════════════════════════════

    private void stepUpdateStatus(KnowledgeDocumentPo doc) {
        doc.setStatus("PROCESSING");
        documentMapper.updateById(doc);
    }

    // ═══════════════════════════════════════════════════════════════
    // 环节 2：文档解析 → 纯文本
    // ═══════════════════════════════════════════════════════════════

    private String stepExtractText(String filePath, String fileType) throws IOException {
        return switch (fileType) {
            case "txt", "md" -> readTextFile(filePath);
            case "pdf" -> extractPdfText(filePath);
            default -> throw new IllegalArgumentException("不支持的文件类型: " + fileType);
        };
    }

    private String readTextFile(String filePath) throws IOException {
        return Files.readString(Path.of(filePath));
    }

    private String extractPdfText(String filePath) throws IOException {
        try (org.apache.pdfbox.pdmodel.PDDocument pdf = Loader.loadPDF(new File(filePath))) {
            if (pdf.isEncrypted()) {
                throw new IOException("PDF 已加密，无法提取文字");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(pdf);
            if (text == null || text.isBlank()) {
                throw new IOException("PDF 无可提取的文字层，扫描版 PDF 暂不支持");
            }
            return text;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 环节 3：递归分块
    // ═══════════════════════════════════════════════════════════════

    private List<ChunkDTO> stepSplitChunks(String text) {
        List<String> segments = splitRecursive(text, 512, 0);
        List<ChunkDTO> chunks = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            String seg = segments.get(i);
            if (seg.isEmpty()) continue;
            ChunkDTO dto = new ChunkDTO();
            dto.setChunkIndex(i);
            dto.setContent(seg);
            dto.setTokenCount(estimateTokens(seg));
            chunks.add(dto);
        }
        // 重新编号，确保 index 连续
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setChunkIndex(i);
        }
        return chunks;
    }

    /**
     * 递归分割：按分隔符优先级逐层尝试。
     * 段落 → 换行 → 句子 → 空格 → 字符
     */
    private List<String> splitRecursive(String text, int maxTokens, int sepLevel) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        if (estimateTokens(text) <= maxTokens || sepLevel >= SPLIT_SEPARATORS.length) {
            return List.of(text.trim());
        }

        String sep = SPLIT_SEPARATORS[sepLevel];
        String[] parts = text.split(Pattern.quote(sep), -1);

        if (parts.length <= 1) {
            return splitRecursive(text, maxTokens, sepLevel + 1);
        }

        // 贪心合并短片段，超过阈值的片段递归降级
        List<String> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (String part : parts) {
            String candidate = buffer.isEmpty() ? part : buffer + sep + part;
            if (estimateTokens(candidate) <= maxTokens) {
                if (!buffer.isEmpty()) buffer.append(sep);
                buffer.append(part);
            } else {
                if (!buffer.isEmpty()) {
                    result.addAll(splitRecursive(buffer.toString(), maxTokens, sepLevel + 1));
                    buffer = new StringBuilder();
                }
                if (estimateTokens(part) > maxTokens) {
                    result.addAll(splitRecursive(part, maxTokens, sepLevel + 1));
                } else {
                    buffer.append(part);
                }
            }
        }
        if (!buffer.isEmpty()) {
            result.addAll(splitRecursive(buffer.toString(), maxTokens, sepLevel + 1));
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // 环节 4：向量化
    // ═══════════════════════════════════════════════════════════════

    private List<ChunkDTO> stepEmbedChunks(List<ChunkDTO> chunks, KnowledgeBasePo kb) {
        if (chunks.isEmpty()) {
            return chunks;
        }

        ProviderResponse provider = resolveEmbeddingProvider(kb.getEmbedModel());
        if (provider == null) {
            throw new RuntimeException("未找到可用的 Embedding 提供商，请先在模型管理中配置 OpenAI 类提供商");
        }

        ProviderAdapter adapter = adapterFactory.create(
                ProviderType.fromCode(provider.getProviderType()),
                provider.getBaseUrl(),
                provider.getAuthConfig());

        String model = !kb.getEmbedModel().isEmpty() ? kb.getEmbedModel() : "text-embedding-3-small";

        for (int batchStart = 0; batchStart < chunks.size(); batchStart += EMBED_BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + EMBED_BATCH_SIZE, chunks.size());
            List<ChunkDTO> batch = chunks.subList(batchStart, batchEnd);

            List<String> inputs = batch.stream().map(ChunkDTO::getContent).collect(Collectors.toList());

            EmbeddingRequest req = EmbeddingRequest.builder()
                    .model(model)
                    .input(inputs)
                    .build();
            EmbeddingResponse resp = adapter.embed(req);

            // API 返回的 data[] 不保证顺序，按 index 排序后对应
            List<EmbeddingResponse.EmbeddingData> sorted = resp.getData().stream()
                    .sorted(Comparator.comparingInt(EmbeddingResponse.EmbeddingData::getIndex))
                    .collect(Collectors.toList());

            for (int i = 0; i < batch.size(); i++) {
                if (i < sorted.size()) {
                    batch.get(i).setEmbedding(sorted.get(i).getEmbedding());
                }
            }
        }

        return chunks;
    }

    private ProviderResponse resolveEmbeddingProvider(String embedModel) {
        return modelService.resolveEmbeddingProvider(embedModel);
    }

    // ═══════════════════════════════════════════════════════════════
    // 环节 5：批量存储 + 终态更新
    // ═══════════════════════════════════════════════════════════════

    private void stepSaveChunks(KnowledgeDocumentPo doc, Long kbId, List<ChunkDTO> chunks) {
        String sql = "INSERT INTO t_knowledge_chunk (document_id, kb_id, chunk_index, content, embedding, token_count) " +
                     "VALUES (?, ?, ?, ?, ?::vector, ?)";
        pgJdbcTemplate.batchUpdate(sql, chunks, chunks.size(), (ps, chunk) -> {
            ps.setLong(1, doc.getId());
            ps.setLong(2, kbId);
            ps.setInt(3, chunk.getChunkIndex());
            ps.setString(4, chunk.getContent());
            ps.setObject(5, formatEmbeddingVector(chunk.getEmbedding()), java.sql.Types.OTHER);
            ps.setInt(6, chunk.getTokenCount());
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════════════════════

    /** float[] → '[1.2,3.4,5.6]' 格式，供 PostgreSQL vector 列使用 */
    private String formatEmbeddingVector(float[] embedding) {
        if (embedding == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(String.format("%.6f", embedding[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 粗略 token 估算：英文单词 ~1.3 token，CJK 字符 ~1 token，标点 ~1 token。
     * 仅用于分块边界判断，不需要和分词器精确一致。
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int tokens = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                tokens += 1;
            } else if (c < 128 && Character.isLetter(c)) {
                int start = i;
                while (i < text.length() && Character.isLetter(text.charAt(i)) && text.charAt(i) < 128) i++;
                tokens += Math.max(1, (i - start + 3) / 4);
                i--;
            } else {
                tokens += 1;
            }
        }
        return tokens;
    }

    private String truncate(String msg, int maxLen) {
        if (msg == null) return "";
        return msg.length() <= maxLen ? msg : msg.substring(0, maxLen);
    }
}
