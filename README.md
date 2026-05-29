# Hify

简版 AI Agent 开发平台，本地部署，面向 20–50 人团队内部使用。

仓库地址：[https://github.com/creattt840/Hify](https://github.com/creattt840/Hify)

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.3、MyBatis-Plus、MySQL 8.x、Redis 7.x |
| 向量库 | PostgreSQL 18 + pgvector（知识库分块与 Embedding 存储） |
| 前端 | Vue 3、TypeScript、Vite、Element Plus |

## 功能概览

- **模型管理**：配置 OpenAI / Anthropic / Gemini / Ollama 及 OpenAI 兼容服务，支持连通性测试与模型 ID 配置
- **Agent 管理**：系统提示词、温度、绑定已启用提供商下的模型
- **对话**：SSE 流式输出、多轮会话、Markdown 渲染
- **知识库 RAG**：知识库 CRUD、文档上传（txt/md/pdf）、异步分块、向量化写入 pgvector、分块查询与删除

## 开发进度

| 模块 | 状态 | 说明 |
|------|:----:|------|
| hify-common | ✅ | BaseEntity、Result/PageResult、BizException、MyBatis-Plus 配置 |
| hify-provider | ✅ | CRUD、连通性测试、健康状态、模型配置、Embedding API |
| hify-agent | ✅ | CRUD、temperature、跨模块模型绑定 |
| hify-chat | ✅ | SSE 流式、四种 LLM 适配器、会话/消息存储 |
| hify-knowledge | ✅ | 知识库/文档 CRUD、分块管线、MySQL + PostgreSQL 双数据源 |
| hify-workflow | ⏳ | 简版工作流（JSON 配置） |
| hify-mcp | ⏳ | MCP 工具接入 |

## 快速启动

**环境要求**：JDK 17+、Maven 3.8+、Node.js 18+、MySQL 8.x、Redis 7.x、PostgreSQL 18+（含 pgvector 扩展）

### 1. 准备 MySQL

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS hify DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -uroot -p hify < hify-boot/src/main/resources/schema.sql
```

### 2. 准备 PostgreSQL（知识库向量）

```bash
# 创建数据库
psql -U postgres -c "CREATE DATABASE hify_knowledge;"

# 启用 pgvector 并建表
psql -U postgres -d hify_knowledge -c "CREATE EXTENSION IF NOT EXISTS vector;"
psql -U postgres -d hify_knowledge -f hify-boot/src/main/resources/schema-pg.sql
```

> Windows 若未安装 pgvector，可从 [pgvector_pgsql_windows](https://github.com/andreiramani/pgvector_pgsql_windows/releases) 下载与 PostgreSQL 版本匹配的扩展包，解压到 PostgreSQL 的 `share/extension`、`lib` 目录后执行 `CREATE EXTENSION vector;`。

连接配置见 `hify-boot/src/main/resources/application.yml` 中的 `hify.pg.*`（可按本机修改）。

### 3. 启动后端

```bash
mvn clean install -DskipTests
cd hify-boot
mvn spring-boot:run
```

服务默认监听 **http://localhost:8080**。启动日志中应出现 `HifyMySQLPool` 与 `HifyPgPool` 两个连接池。

### 4. 启动前端

```bash
cd hify-web
npm install
npm run dev
```

开发地址：**http://localhost:5173**（`/api` 代理至后端 8080）。

### 5. 验证

```bash
# 提供商列表
curl "http://localhost:8080/api/v1/providers?page=1&pageSize=5"

# 知识库列表
curl "http://localhost:8080/api/v1/knowledge-bases?page=1&pageSize=5"
```

浏览器访问 http://localhost:5173 ，在「模型管理」配置提供商，在「知识库」创建知识库并上传文档。

## 模型提供商配置说明

### 认证字段

`authConfig` 统一使用 **`apiKey`** 字段（兼容历史 `api_key`）。编辑提供商时 API Key 留空则**保留原密钥**。

### DeepSeek（对话）

| 项 | 值 |
|----|-----|
| 类型 | **OpenAI Compatible** |
| API 地址 | `https://api.deepseek.com`（不要带 `/anthropic`） |
| API Key | DeepSeek 控制台密钥 |
| 模型 ID | 如 `deepseek-chat` |

> DeepSeek 的 Anthropic 兼容入口（`/anthropic`）用于 Claude 协议对话；日常对话推荐使用 **OpenAI Compatible** 类型。

### Embedding（知识库向量化）

知识库文档处理会调用 **`/v1/embeddings`**，需单独配置支持 Embedding 的提供商：

| 提供商 | 类型 | 说明 |
|--------|------|------|
| OpenAI | `openai` / `openai_compatible` | 如 `text-embedding-3-small` |
| 阿里云百炼等 | `openai_compatible` | 兼容 OpenAI Embedding 接口的服务 |
| DeepSeek | — | **不支持** `/v1/embeddings`，不能用于知识库向量化 |

系统会优先使用知识库上配置的 `embedModel`；若为空则默认 `text-embedding-3-small`，并回退到首个启用的 `openai` / `openai_compatible` 提供商。

### 创建提供商检查清单

1. 填写 API 地址与 API Key，并填写**模型 ID**
2. 开启「启用」
3. 点击「测试连接」确认成功
4. 知识库场景：额外添加一个 **Embedding 专用** 的 OpenAI 兼容提供商

## 知识库说明

### 文档处理流程

```
上传文档 → PENDING → 解析文本 → 分块 → 调用 Embedding API → 写入 PostgreSQL → DONE
```

- 元数据（知识库、文档记录）存 **MySQL**
- 分块原文与向量存 **PostgreSQL** `t_knowledge_chunk`（`vector(1536)`）
- 支持格式：`.txt`、`.md`、`.pdf`（单文件最大 10MB）
- 上传目录默认 `./upload`（可通过 `hify.knowledge.upload-dir` 配置）

### 双数据源架构

| 数据源 | Bean | 用途 |
|--------|------|------|
| MySQL | `@Primary dataSource` | MyBatis-Plus 业务表 |
| PostgreSQL | `pgDataSource` / `pgJdbcTemplate` | 向量分块读写 |

配置类：`hify-boot/src/main/java/com/hify/config/DataSourceConfiguration.java`

## 模块结构

| 模块 | 说明 |
|------|------|
| hify-common | 公共模块 |
| hify-provider | 模型提供商与模型配置 |
| hify-agent | Agent 配置 |
| hify-chat | 对话引擎 |
| hify-knowledge | 知识库 RAG |
| hify-workflow | 工作流（规划中） |
| hify-mcp | MCP 工具（规划中） |
| hify-boot | 启动入口、双数据源装配 |
| hify-web | 管理控制台前端 |

## API 概览

### Provider 管理

```
GET    /api/v1/providers
GET    /api/v1/providers/model-configs
POST   /api/v1/providers
GET    /api/v1/providers/{id}
PUT    /api/v1/providers/{id}
DELETE /api/v1/providers/{id}
POST   /api/v1/providers/{id}/test-connection
GET    /api/v1/providers/{id}/model-configs
POST   /api/v1/providers/{id}/model-configs
```

### Agent 管理

```
GET    /api/v1/agents
POST   /api/v1/agents
GET    /api/v1/agents/{id}
PUT    /api/v1/agents/{id}
DELETE /api/v1/agents/{id}
```

### Conversation 对话

```
GET    /api/v1/conversations
GET    /api/v1/conversations/{id}/messages
POST   /api/v1/conversations/stream              # SSE 流式
```

### Knowledge 知识库

```
GET    /api/v1/knowledge-bases
POST   /api/v1/knowledge-bases
GET    /api/v1/knowledge-bases/{id}
PUT    /api/v1/knowledge-bases/{id}
DELETE /api/v1/knowledge-bases/{id}
POST   /api/v1/knowledge-bases/{kbId}/documents  # multipart 上传
GET    /api/v1/knowledge-bases/{kbId}/documents
GET    /api/v1/documents/{id}
GET    /api/v1/documents/{id}/chunks
DELETE /api/v1/documents/{id}
```

统一响应格式 `Result<T>`：`{ "code": 200, "message": "success", "data": {...} }`

详细说明见 [docs/provider-api.md](docs/provider-api.md)

## 常见问题

| 现象 | 处理 |
|------|------|
| 删除知识库报「服务器内部错误」 | 确认后端已重启，日志中有 `HifyPgPool`；旧进程未加载双数据源修复 |

