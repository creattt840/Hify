-- ============================================================
-- Hify 一期数据模型
-- 规范：InnoDB / utf8mb4 / utf8mb4_unicode_ci / 全字段 NOT NULL
-- ============================================================

-- ----------------------------------------------------------
-- 模型提供商
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_provider (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(128)    NOT NULL            COMMENT '提供商名称',
    provider_type   VARCHAR(32)     NOT NULL            COMMENT '提供商类型: openai/claude/gemini/ollama',
    base_url        VARCHAR(512)    NOT NULL DEFAULT '' COMMENT 'API 地址',
    auth_config     JSON            NOT NULL            COMMENT '认证配置 (api_key/organization_id 等)',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1  COMMENT '是否启用',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_provider_type (provider_type, deleted),
    INDEX idx_provider_enabled (is_enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型提供商';

-- ----------------------------------------------------------
-- 模型配置 (name 与 model_id 分开存储)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_model_config (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    provider_id     BIGINT          NOT NULL            COMMENT '关联 t_provider.id',
    name            VARCHAR(128)    NOT NULL            COMMENT '配置名称 (用户可见标签)',
    model_id        VARCHAR(128)    NOT NULL            COMMENT '模型标识 (如 gpt-4o, claude-opus-4-7)',
    extra_params    JSON            NOT NULL            COMMENT '模型额外参数 (temperature/max_tokens 等)',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_mdl_provider (provider_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型配置';

-- ----------------------------------------------------------
-- 提供商健康状态 (独立存储，避免 provider 行锁竞争)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_provider_health (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    provider_id     BIGINT          NOT NULL            COMMENT '关联 t_provider.id',
    fail_count      INT             NOT NULL DEFAULT 0  COMMENT '累计失败次数',
    latency_ms      INT             NOT NULL DEFAULT 0  COMMENT '最近一次延迟 (毫秒)',
    last_success_at DATETIME(3)     NULL                COMMENT '最近一次连通成功时间',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_health_provider (provider_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提供商健康状态';

-- ----------------------------------------------------------
-- Agent 管理
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_agent (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(128)    NOT NULL            COMMENT 'Agent 名称',
    description     VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '简要描述',
    system_prompt   MEDIUMTEXT      NOT NULL            COMMENT '系统提示词',
    model_config_id BIGINT          NOT NULL            COMMENT '绑定的模型配置 ID (t_model_config.id)',
    temperature     DECIMAL(3,2)    NOT NULL DEFAULT 0.7 COMMENT '温度参数 (0.0-2.0)',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '是否启用',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_agent_name (name, deleted),
    INDEX idx_agent_enabled (is_enabled, deleted),
    INDEX idx_agent_model (model_config_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent';

-- ----------------------------------------------------------
-- Agent ↔ 知识库关联（一期建表预留，不实现功能）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_agent_knowledge_base (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    agent_id          BIGINT        NOT NULL            COMMENT '关联 t_agent.id',
    knowledge_base_id BIGINT        NOT NULL            COMMENT '关联 t_knowledge_base.id',
    created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted           TINYINT(1)    NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_akb_agent (agent_id, deleted),
    INDEX idx_akb_kb (knowledge_base_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 知识库关联';

-- ----------------------------------------------------------
-- Agent ↔ MCP 工具关联（一期建表预留，不实现功能）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_agent_mcp_tool (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    agent_id        BIGINT          NOT NULL            COMMENT '关联 t_agent.id',
    mcp_tool_id     BIGINT          NOT NULL            COMMENT '关联 t_mcp_tool.id',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_amt_agent (agent_id, deleted),
    INDEX idx_amt_tool (mcp_tool_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent MCP 工具关联';
