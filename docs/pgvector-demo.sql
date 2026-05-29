-- ============================================================
-- pgvector 从零上手完整示例
-- 当前用 PostgreSQL 数组模拟向量（安装 pgvector 后改类型即可）
-- ============================================================

-- ═══════════════════════════════════════════════════════
-- Step 1: 创建知识库相关的两张表
-- ═══════════════════════════════════════════════════════

-- 文档表：存上传的原始文档元数据
CREATE TABLE IF NOT EXISTS t_knowledge_document (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(256) NOT NULL,
    file_type   VARCHAR(16)  NOT NULL,           -- pdf / txt / md
    status      VARCHAR(16)  NOT NULL DEFAULT 'pending',  -- pending → processing → completed
    chunk_count INT          NOT NULL DEFAULT 0,
    content     TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 分块表：存切块后的文本 + 向量
-- ★ 当前用 real[] 模拟，安装 pgvector 后改为 vector(3)
CREATE TABLE IF NOT EXISTS t_knowledge_chunk (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id BIGINT       NOT NULL REFERENCES t_knowledge_document(id),
    chunk_index INT          NOT NULL,
    content     TEXT         NOT NULL,          -- 切块原文
    embedding   real[]       NOT NULL           -- ★ pgvector 下改为 vector(3)
);

-- ═══════════════════════════════════════════════════════
-- Step 2: 插入模拟数据（3 维向量，方便手动验证）
--
-- 用真实语义类比：
--   文档 A: "退货政策：7天内可退"      → 向量 [1, 2, 3]
--   文档 B: "退款流程：3-5天到账"      → 向量 [2, 3, 4]
--   文档 C: "员工考勤制度"             → 向量 [8, 9, 10]
--   文档 D: "年假申请流程"             → 向量 [9, 10, 11]
-- ═══════════════════════════════════════════════════════

INSERT INTO t_knowledge_document (name, file_type, status, chunk_count, content) VALUES
('产品退货政策.pdf',  'pdf', 'completed', 2, '退货政策全文...'),
('员工手册.pdf',      'pdf', 'completed', 2, '员工手册全文...');

INSERT INTO t_knowledge_chunk (document_id, chunk_index, content, embedding) VALUES
-- 文档 1（退货政策）的两个分块 —— 向量相近，都在 [1,2,3] ~ [2,3,4] 区域
(1, 0, '退货政策：用户可在 7 天内申请退货，商品需保持完好。',     ARRAY[1.0, 2.0, 3.0]::real[]),
(1, 1, '退款流程：财务审核通过后，3-5 个工作日退款到原账户。',    ARRAY[2.0, 3.0, 4.0]::real[]),
-- 文档 2（员工手册）的两个分块 —— 向量和退货政策相距很远
(2, 0, '员工考勤制度：工作日上班时间为 9:00-18:00。',           ARRAY[8.0, 9.0, 10.0]::real[]),
(2, 1, '年假申请流程：提前 3 个工作日向直属上级提交申请。',       ARRAY[9.0, 10.0, 11.0]::real[]);

-- ═══════════════════════════════════════════════════════
-- Step 3: 相似度查询 —— 用户问「怎么退货」
--
-- 查询要点：
--   用户问题向量化为 [1.1, 2.1, 3.1]（离退货政策很近）
--   用余弦相似度找出最相关的 Top-3 分块
-- ═══════════════════════════════════════════════════════

-- 3a. 定义用户问题的向量
WITH query_vec AS (
    SELECT ARRAY[1.1, 2.1, 3.1]::real[] AS vec
),
-- 3b. 计算每个分块与问题向量的余弦相似度
scored AS (
    SELECT
        c.id,
        c.content,
        c.embedding,
        -- 余弦相似度 = (A·B) / (||A|| × ||B||)
        -- 值越接近 1 表示越相似
        (
            (c.embedding[1] * q.vec[1] + c.embedding[2] * q.vec[2] + c.embedding[3] * q.vec[3])
            /
            (
                sqrt(c.embedding[1]^2 + c.embedding[2]^2 + c.embedding[3]^2)
                *
                sqrt(q.vec[1]^2 + q.vec[2]^2 + q.vec[3]^2)
            )
        ) AS similarity
    FROM t_knowledge_chunk c
    CROSS JOIN query_vec q
)
SELECT
    id,
    content,
    round(similarity::numeric, 4) AS similarity
FROM scored
ORDER BY similarity DESC
LIMIT 3;

-- ═══════════════════════════════════════════════════════
-- Step 4: 验证 —— 检查各分块之间的向量距离
--
--   退货政策分块0 vs 退货政策分块1 → 应该很近（同为退货主题）
--   退货政策分块0 vs 考勤制度    → 应该很远（完全不同主题）
-- ═══════════════════════════════════════════════════════

SELECT
    'chunk-1-0 vs chunk-1-1 (同主题)' AS comparison,
    round((1 - ((1.0*2.0 + 2.0*3.0 + 3.0*4.0)
        / (sqrt(1^2+2^2+3^2) * sqrt(2^2+3^2+4^2))))::numeric, 4) AS cosine_distance
UNION ALL
SELECT
    'chunk-1-0 vs chunk-2-0 (不同主题)',
    round((1 - ((1.0*8.0 + 2.0*9.0 + 3.0*10.0)
        / (sqrt(1^2+2^2+3^2) * sqrt(8^2+9^2+10^2))))::numeric, 4);

-- ═══════════════════════════════════════════════════════
-- pgvector 安装后迁移步骤:
--
--   CREATE EXTENSION vector;
--   ALTER TABLE t_knowledge_chunk ALTER COLUMN embedding TYPE vector(3);
--   -- 索引
--   CREATE INDEX idx_chunk_embedding ON t_knowledge_chunk
--       USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);
--   -- 查询变成
--   SELECT content, 1 - (embedding <=> '[1.1,2.1,3.1]') AS similarity
--   FROM t_knowledge_chunk ORDER BY embedding <=> '[1.1,2.1,3.1]' LIMIT 3;
-- ═══════════════════════════════════════════════════════
