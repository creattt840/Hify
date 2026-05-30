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
    workflow_id     BIGINT          DEFAULT NULL        COMMENT '绑定的工作流 ID (t_workflow.id)',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_agent_name (name, deleted),
    INDEX idx_agent_enabled (is_enabled, deleted),
    INDEX idx_agent_model (model_config_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent';

-- ----------------------------------------------------------
-- Agent ↔ 知识库关联（N:N，一期实现）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_agent_knowledge_base (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    agent_id          BIGINT        NOT NULL            COMMENT '关联 t_agent.id',
    knowledge_base_id BIGINT        NOT NULL            COMMENT '关联 t_knowledge_base.id',
    created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted           TINYINT(1)    NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_akb_unique (agent_id, knowledge_base_id, deleted),
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

-- ----------------------------------------------------------
-- 知识库（元数据在 MySQL，向量数据在 PostgreSQL + pgvector）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_knowledge_base (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(128)    NOT NULL            COMMENT '知识库名称',
    description     VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '描述',
    embed_model     VARCHAR(128)    NOT NULL DEFAULT '' COMMENT 'Embedding 模型标识（如 text-embedding-3-small）',
    chunk_size      INT             NOT NULL DEFAULT 512 COMMENT '分块大小（字符数）',
    chunk_overlap   INT             NOT NULL DEFAULT 64  COMMENT '分块重叠（字符数）',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 1  COMMENT '是否启用',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库';

CREATE TABLE IF NOT EXISTS t_knowledge_document (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    kb_id           BIGINT          NOT NULL            COMMENT '关联 t_knowledge_base.id',
    name            VARCHAR(256)    NOT NULL            COMMENT '文件名',
    file_type       VARCHAR(16)     NOT NULL            COMMENT '文件类型: pdf / docx / txt / md',
    file_path       VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '文件存储路径（本地磁盘或 OSS）',
    file_size       BIGINT          NOT NULL DEFAULT 0  COMMENT '文件大小（字节）',
    status          VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT 'pending → processing → completed → failed',
    chunk_count     INT             NOT NULL DEFAULT 0  COMMENT '分块数量',
    error_msg       VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '处理失败时的错误信息',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_kb_doc_kb (kb_id, deleted, status),
    INDEX idx_kb_doc_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档';

-- ----------------------------------------------------------
-- 会话
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_conversation (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    agent_id        BIGINT          NOT NULL            COMMENT '关联 t_agent.id',
    title           VARCHAR(256)    NOT NULL DEFAULT '' COMMENT '会话标题 (首轮用户消息截取)',
    status          VARCHAR(16)     NOT NULL DEFAULT 'active' COMMENT '状态: active / archived',
    message_count   INT             NOT NULL DEFAULT 0  COMMENT '消息数量 (冗余，方便列表展示)',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_conv_agent (agent_id, deleted, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话';

-- ----------------------------------------------------------
-- 消息 (游标分页)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_message (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT          NOT NULL            COMMENT '关联 t_conversation.id',
    role            VARCHAR(16)     NOT NULL            COMMENT '角色: user / assistant / system / tool',
    content         MEDIUMTEXT      NOT NULL            COMMENT '消息内容',
    token_count     INT             NOT NULL DEFAULT 0  COMMENT 'token 消耗',
    metadata        JSON            NOT NULL            COMMENT '扩展信息 (工具调用/TTFT/引用)',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_msg_conv_created (conversation_id, deleted, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息';

-- ----------------------------------------------------------
-- 工作流定义
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_workflow (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(128)    NOT NULL            COMMENT '工作流名称',
    description     VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '描述',
    version         INT             NOT NULL DEFAULT 1  COMMENT '版本号',
    is_enabled      TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '是否启用',
    is_published    TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '是否已发布',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_wf_enabled (is_enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流定义';

-- ----------------------------------------------------------
-- 工作流节点（所有类型共用一张表，config JSON 列承载差异化字段）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_workflow_node (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    workflow_id     BIGINT          NOT NULL            COMMENT '关联 t_workflow.id',
    node_key        VARCHAR(64)     NOT NULL            COMMENT '节点标识（工作流内唯一）',
    node_type       VARCHAR(32)     NOT NULL            COMMENT '节点类型: START/LLM/HTTP/SWITCH/CLASSIFY/COUPON/REPLY/END',
    title           VARCHAR(128)    NOT NULL DEFAULT '' COMMENT '节点标题（展示用）',
    config          JSON            NOT NULL            COMMENT '节点配置（类型决定结构）',
    sort_order      INT             NOT NULL DEFAULT 0  COMMENT '展示排序',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_wfn_key (workflow_id, node_key, deleted),
    INDEX idx_wfn_workflow (workflow_id, deleted, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流节点';

-- ----------------------------------------------------------
-- 工作流边（节点之间的连接关系）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_workflow_edge (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    workflow_id     BIGINT          NOT NULL            COMMENT '关联 t_workflow.id',
    source          VARCHAR(64)     NOT NULL            COMMENT '源节点 node_key',
    target          VARCHAR(64)     NOT NULL            COMMENT '目标节点 node_key',
    `condition`     VARCHAR(256)    NOT NULL DEFAULT '' COMMENT '条件表达式（空表示无条件连接）',
    sort_order      INT             NOT NULL DEFAULT 0  COMMENT '同源节点的分支排序',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_wfe_workflow (workflow_id, deleted, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流边';

-- ----------------------------------------------------------
-- 工作流执行记录（每次用户触发执行，生成一条）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_workflow_run (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    workflow_id     BIGINT          NOT NULL            COMMENT '关联 t_workflow.id',
    status          VARCHAR(20)     NOT NULL            COMMENT 'RUNNING / SUCCESS / FAILED',
    input           TEXT                                COMMENT '用户输入',
    output          TEXT                                COMMENT '最终输出',
    error           VARCHAR(500)                        COMMENT '错误信息',
    elapsed_ms      INT                                 COMMENT '总耗时（毫秒）',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_wfr_workflow (workflow_id, deleted, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流执行记录';

-- ----------------------------------------------------------
-- 工作流节点执行记录（每次执行中每个节点生成一条）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_workflow_node_run (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    workflow_run_id BIGINT          NOT NULL            COMMENT '关联 t_workflow_run.id',
    node_key        VARCHAR(64)     NOT NULL            COMMENT '节点标识',
    node_type       VARCHAR(30)     NOT NULL            COMMENT '节点类型',
    status          VARCHAR(20)     NOT NULL            COMMENT 'RUNNING / SUCCESS / FAILED',
    outputs         JSON                                COMMENT 'ctx.snapshot() 快照',
    error           VARCHAR(500)                        COMMENT '错误信息',
    elapsed_ms      INT                                 COMMENT '节点耗时（毫秒）',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_wfnr_run (workflow_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流节点执行记录';
