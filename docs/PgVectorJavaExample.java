package docs;

// ═══════════════════════════════════════════════════════════════
// pgvector + MyBatis-Plus 完整 Java 示例
// 此文件仅作参考，不参与编译
// ═══════════════════════════════════════════════════════════════

/*
 * ============================================================
 * 1. 实体类 (KnowledgeChunkPo)
 * ============================================================
 */
@TableName(value = "t_knowledge_chunk", autoResultMap = true)
public class KnowledgeChunkPo {
    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;

    @TableField(typeHandler = PgVectorTypeHandler.class)  // ★ 关键：声明 TypeHandler
    private float[] embedding;                             // vector(1536) ↔ float[]

    private LocalDateTime createdAt;
    // getter / setter 省略
}

/*
 * ============================================================
 * 2. Mapper (KnowledgeChunkMapper) —— 继承标准 BaseMapper
 * ============================================================
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunkPo> {

    // 相似度搜索需要手写 SQL（<=> 运算符 MyBatis-Plus 不会自动生成）
    @Select("""
        SELECT id, content, embedding,
               1 - (embedding <=> #{queryVec}::vector) AS similarity
        FROM t_knowledge_chunk
        WHERE kb_id = #{kbId}
        ORDER BY embedding <=> #{queryVec}::vector
        LIMIT #{topK}
        """)
    List<KnowledgeChunkPo> searchSimilar(
            @Param("kbId") Long kbId,
            @Param("queryVec") String queryVec,  // '[1.2,3.4,...]' 格式
            @Param("topK") int topK
    );
}

/*
 * ============================================================
 * 3. Service —— 插入向量 + 相似度检索
 * ============================================================
 */
@Service
@RequiredArgsConstructor
public class KnowledgeSearchService {

    private final KnowledgeChunkMapper chunkMapper;

    // ── 插入向量 ──
    public void saveChunk(Long documentId, int index, String content, float[] embedding) {
        KnowledgeChunkPo po = new KnowledgeChunkPo();
        po.setDocumentId(documentId);
        po.setChunkIndex(index);
        po.setContent(content);
        po.setEmbedding(embedding);  // ★ PgVectorTypeHandler 自动转 '[...]'::vector
        po.setCreatedAt(LocalDateTime.now());
        chunkMapper.insert(po);
    }

    // ── 相似度检索 ──
    public List<KnowledgeChunkPo> search(Long kbId, float[] queryEmbedding, int topK) {
        // float[] → '[1.2,3.4,5.6]' 字符串
        String vecStr = Arrays.stream(queryEmbedding)
                .mapToObj(f -> String.format("%.6f", f))
                .collect(Collectors.joining(",", "[", "]"));
        return chunkMapper.searchSimilar(kbId, vecStr, topK);
    }
}

/*
 * ============================================================
 * 4. application.yml —— PostgreSQL 数据源配置
 * ============================================================
spring:
  datasource:
    pg:
      jdbc-url: jdbc:postgresql://localhost:5432/hify_knowledge
      username: postgres
      password: 123456
      driver-class-name: org.postgresql.Driver
      hikari:
        maximum-pool-size: 5     # 向量库读多写少，5个连接足够
        connection-timeout: 3000
 * ============================================================
 */

/*
 * ============================================================
 * 5. 完整调用示例
 * ============================================================
 */
public class Demo {
    public void demo(KnowledgeSearchService service) {
        // --- 写入：文档切块后向量化并保存 ---
        float[] embedding = callOpenAiEmbedding("退货政策：用户可在7天内申请退货...");
        // embedding = [0.0123, -0.0456, 0.0789, ...] (1536 维)
        service.saveChunk(1L, 0, "退货政策：用户可在7天内申请退货...", embedding);

        // --- 查询：用户问题向量化后检索 Top-5 ---
        float[] queryVec = callOpenAiEmbedding("怎么退货？");
        List<KnowledgeChunkPo> results = service.search(1L, queryVec, 5);

        for (KnowledgeChunkPo chunk : results) {
            System.out.println(chunk.getContent());
        }
    }

    private float[] callOpenAiEmbedding(String text) {
        // POST https://api.openai.com/v1/embeddings
        // { "model": "text-embedding-3-small", "input": "..." }
        // 返回值: { data: [{ embedding: [0.012, -0.045, ...] }] }
        // 你的项目已有 OpenAI adapter，直接复用
        throw new UnsupportedOperationException("示意代码");
    }
}
