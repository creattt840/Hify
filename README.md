# Hify

简版 AI Agent 开发平台，本地部署，面向 20–50 人团队内部使用。

仓库地址：[https://github.com/creattt840/Hify](https://github.com/creattt840/Hify)

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.3、MyBatis-Plus、MySQL 8.x、Redis 7.x |
| 向量库 | PostgreSQL 18 + pgvector（知识库分块与 Embedding 存储） |
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Vue Flow（工作流可视化编辑） |
| 工具协议 | MCP（Model Context Protocol），支持 Streamable HTTP / SSE 传输 |

## 功能概览

- **模型管理**：配置 OpenAI / Anthropic / Gemini / Ollama 及 OpenAI 兼容服务，支持连通性测试与模型 ID 配置
- **Agent 管理**：系统提示词、温度、绑定模型；可选绑定工作流；可选绑定 MCP 工具（最多 10 个）
- **对话**：SSE 流式输出、多轮会话、Markdown 渲染、MCP 工具调用（多轮同步循环）、删除会话；会话列表显示 Agent 名称
- **知识库 RAG**：知识库 CRUD、文档上传（txt/md/pdf）、异步分块、向量化写入 pgvector、分块查询与删除
- **工作流**：可视化拖拽编辑器（START / LLM / CONDITION / END），查看/编辑/删除；Agent 绑定后通过对话触发 `WorkflowEngine` 执行
- **MCP 工具**：MCP Server 注册、连通性测试、工具同步、调试调用；Agent 对话中自动加载已绑定工具
- **管理控制台**：深浅交替 UI（深色导航侧栏 + 浅色内容区）

## 开发进度

| 模块 | 状态 | 说明 |
|------|:----:|------|
| hify-common | ✅ | BaseEntity、Result/PageResult、BizException、MyBatis-Plus 配置 |
| hify-provider | ✅ | CRUD、连通性测试、健康状态、模型配置、Embedding API、工具调用 Schema |
| hify-agent | ✅ | CRUD、temperature、模型绑定、工作流绑定、MCP 工具绑定 |
| hify-chat | ✅ | SSE 流式、LLM 适配器、MCP 工具调用循环、会话/消息存储、工作流对话路由 |
| hify-knowledge | ✅ | 知识库/文档 CRUD、分块管线、MySQL + PostgreSQL 双数据源 |
| hify-workflow | ✅ | 工作流 CRUD、WorkflowEngine 执行、节点运行记录 |
| hify-mcp | ✅ | MCP Server 管理、客户端连接、工具发现与调用、调试接口 |
| hify-web | ✅ | 模型/Agent/对话/知识库/工作流/MCP 管理页、可视化工作流编辑器 |

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

服务默认监听 **http://localhost:8080**（内置 Mock 天气 MCP 服务监听 **8082**）。启动日志中应出现 `HifyMySQLPool` 与 `HifyPgPool` 两个连接池。

> **重启注意**：若报 `Port 8080 was already in use`，需先结束旧 Java 进程再启动，否则新代码（如新 API）不会生效。
>
> ```powershell
> netstat -ano | findstr :8080          # 查看占用 PID
> Stop-Process -Id <PID> -Force         # 结束旧进程
> mvn install -DskipTests               # 编译各模块
> cd hify-boot; mvn spring-boot:run     # 启动
> ```

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

# MCP Server 列表
curl "http://localhost:8080/api/v1/mcp-servers?page=1&pageSize=5"
```

浏览器访问 http://localhost:5173 ，在「模型管理」配置提供商，在「Agent 管理」创建 Agent 并绑定 MCP 工具，在「对话」开始聊天；可在「工作流」使用可视化编辑器创建工作流并绑定到 Agent。

## 工作流说明

### 节点类型

| 类型 | 说明 |
|------|------|
| START | 入口，接收用户输入（nodeKey 必须为 `start`） |
| LLM | 调用绑定的 LLM 模型 |
| CONDITION | 条件分支（表达式支持 `==` / `!=`） |
| KNOWLEDGE | 知识库检索（RAG，引擎支持） |
| API_CALL | 调用外部 HTTP 接口（引擎支持） |
| END | 结束并输出结果 |

### 可视化编辑器

- 路由：`/workflows/create`（新建）、`/workflows/:id`（查看）、`/workflows/:id/edit`（编辑）
- 左侧节点面板拖拽到画布，右侧配置节点属性，支持连线/改边/删除边
- CONDITION 节点的出边需标注 `condition: "true"` / `"false"`

### 触发方式

1. 在「工作流」页面创建并保存工作流（可视化编辑器或 API）
2. 在「Agent 管理」编辑 Agent，绑定 `workflowId`
3. 在「对话」页选择该 Agent 发送消息 → 自动走 `WorkflowEngine` 同步执行，完成后经 SSE 返回 `done` 事件

示例 JSON 见 `docs/test-workflow.json`、`docs/wf-simple-qa.json`、`docs/wf-sentiment-routing.json`、`docs/wf-cs-routing.json`。

执行记录可通过 `GET /api/v1/workflows/{id}/runs/latest` 查询。

## MCP 说明

### 功能

- 注册 MCP Server（名称、URL、传输方式自动检测：URL 以 `/sse` 结尾时使用 SSE 传输）
- 测试连通性、同步工具列表、在线调试工具调用
- Agent 绑定工具后，对话中 LLM 可自动发起 tool call（最多 8 轮循环）

### 本地 Mock 服务

后端内置两个 Mock MCP 服务，便于开发调试：

| 服务 | 端口 | 说明 |
|------|------|------|
| 退款查询 Mock | 8080（同主服务） | 路径 `/mock/mcp` |
| 天气查询 Mock | 8082 | 独立 Tomcat Connector |

### Agent 绑定工具

1. 在「MCP 管理」添加 Server 并测试连通性
2. 在「Agent 管理」编辑 Agent，通过工具选择器绑定（最多 10 个）
3. 对话时系统自动加载有效工具，过滤已删除或禁用的绑定

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
| hify-agent | Agent 配置与 MCP 工具绑定 |
| hify-chat | 对话引擎（LLM + MCP + 工作流路由） |
| hify-knowledge | 知识库 RAG |
| hify-workflow | 工作流 CRUD 与执行引擎 |
| hify-mcp | MCP Server 管理与客户端 |
| hify-boot | 启动入口、双数据源装配、Mock MCP |
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
PUT    /api/v1/agents/{id}/tools              # 绑定 MCP 工具（全量替换，最多 10 个）
GET    /api/v1/agents/{id}/tools              # 获取已绑定工具 ID 列表
```

### Conversation 对话

```
GET    /api/v1/conversations                  # 返回 agentName 等字段
GET    /api/v1/conversations/{id}/messages
DELETE /api/v1/conversations/{id}             # 删除会话及消息（逻辑删除）
POST   /api/v1/conversations/stream           # SSE 流式，body 字段为 content
```

### Workflow 工作流

```
GET    /api/v1/workflows
POST   /api/v1/workflows
GET    /api/v1/workflows/{id}
PUT    /api/v1/workflows/{id}
DELETE /api/v1/workflows/{id}
GET    /api/v1/workflows/{id}/runs/latest     # 最新执行记录
```

### MCP 管理

```
GET    /api/v1/mcp-servers
POST   /api/v1/mcp-servers
GET    /api/v1/mcp-servers/available-tools    # 全局可选工具列表（Agent 绑定用）
GET    /api/v1/mcp-servers/{id}
PUT    /api/v1/mcp-servers/{id}
DELETE /api/v1/mcp-servers/{id}
POST   /api/v1/mcp-servers/{id}/test          # 连通性测试
POST   /api/v1/mcp-servers/{id}/debug         # 在线调试工具调用
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
| 删除对话报「服务器内部错误」 | 8080 被旧进程占用，新后端未启动；结束旧进程后 `mvn install` 再重启 |
| 重启报 Port 8080 already in use | 用 `netstat -ano \| findstr :8080` 找到 PID，`Stop-Process -Id <PID> -Force` |
| 知识库文档一直 FAILED | DeepSeek 不支持 Embedding；需单独配置 OpenAI 兼容 Embedding 提供商 |
| Agent 对话不调用 MCP 工具 | 确认 Agent 已绑定工具且 Server 启用；查看后端日志是否加载工具列表 |
| MCP Server 连接失败 | 检查 URL 格式；SSE 类型 URL 需以 `/sse` 结尾；百度地图等需有效 AK |
| 工作流对话无流式输出 | 工作流路径同步执行，完成后推送 `done` 事件，前端自动刷新消息列表 |
