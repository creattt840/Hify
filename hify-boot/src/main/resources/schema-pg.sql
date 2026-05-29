-- ============================================================
-- Hify PostgreSQL 向量表
-- 依赖: pgvector 扩展
-- 安装: CREATE EXTENSION vector;
-- ============================================================

-- 知识库分块表（向量 + 原文同库存储）
CREATE TABLE IF NOT EXISTS t_knowledge_chunk (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id     BIGINT       NOT NULL,    -- 软引用 MySQL t_knowledge_document.id
    kb_id           BIGINT       NOT NULL,    -- 冗余：软引用 MySQL t_knowledge_base.id（加速按知识库过滤）
    chunk_index     INT          NOT NULL,    -- 块序号，从 0 开始
    content         TEXT         NOT NULL,    -- 分块原文
    embedding       vector(1536),            -- Embedding 向量，一期暂可空，后续注入
    token_count     INT          NOT NULL DEFAULT 0,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ivfflat 索引（余弦相似度检索）
-- lists = sqrt(预计总行数)，< 10 万条时设 100
CREATE INDEX IF NOT EXISTS idx_chunk_embedding
    ON t_knowledge_chunk
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100)
    WHERE embedding IS NOT NULL;

-- 按知识库过滤的 B-tree 索引（WHERE kb_id = ? AND deleted = 0）
CREATE INDEX IF NOT EXISTS idx_chunk_kb
    ON t_knowledge_chunk (kb_id, deleted);

-- 按文档查询分块的 B-tree 索引（WHERE document_id = ? AND deleted = 0）
CREATE INDEX IF NOT EXISTS idx_chunk_doc
    ON t_knowledge_chunk (document_id, deleted);

-- ============================================================
-- 常用查询
-- ============================================================

-- 相似度检索（Top-K）
-- SET ivfflat.probes = 10;
-- SELECT content, 1 - (embedding <=> '[...]'::vector) AS similarity
-- FROM t_knowledge_chunk
-- WHERE kb_id = ? AND deleted = 0 AND embedding IS NOT NULL
-- ORDER BY embedding <=> '[...]'::vector
-- LIMIT 5;

-- 按文档查询分块列表
-- SELECT id, chunk_index, content, token_count, created_at
-- FROM t_knowledge_chunk
-- WHERE document_id = ? AND deleted = 0
-- ORDER BY chunk_index;
