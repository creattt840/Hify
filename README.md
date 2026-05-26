# Hify

简版 AI Agent 开发平台，本地部署，面向 20–50 人团队内部使用。

仓库地址：[https://github.com/creattt840/Hify](https://github.com/creattt840/Hify)

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.3、MyBatis-Plus、MySQL 8.x、Redis 7.x |
| 前端 | Vue 3、TypeScript、Vite、Element Plus |
| 向量库 | pgvector（知识库模块规划中） |

## 功能概览

- **模型管理**：配置 OpenAI / Anthropic / Gemini / Ollama 及 OpenAI 兼容服务（如 DeepSeek），支持连通性测试与模型 ID 配置
- **Agent 管理**：系统提示词、温度、绑定已启用提供商下的模型
- **对话**：SSE 流式输出、多轮会话、Markdown 渲染

## 开发进度

| 模块 | 状态 | 说明 |
|------|:----:|------|
| hify-common | ✅ | BaseEntity、Result/PageResult、BizException、MyBatis-Plus 配置 |
| hify-provider | ✅ | CRUD、连通性测试、健康状态、模型配置、DeepSeek 探测适配 |
| hify-agent | ✅ | CRUD、temperature、跨模块模型绑定 |
| hify-chat | ✅ | SSE 流式、四种 LLM 适配器、会话/消息存储 |
| hify-knowledge | ⏳ | 知识库 RAG（pgvector） |
| hify-workflow | ⏳ | 简版工作流（JSON 配置） |
| hify-mcp | ⏳ | MCP 工具接入 |

## 快速启动

**环境要求**：JDK 17+、Maven 3.8+、Node.js 18+、MySQL 8.x、Redis 7.x

### 1. 准备数据库

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS hify DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -uroot -p hify < hify-boot/src/main/resources/schema.sql
```

后端默认连接见 `hify-boot/src/main/resources/application.yml`（可按本机修改用户名、密码）。

### 2. 启动后端

```bash
mvn clean install -DskipTests
cd hify-boot
mvn spring-boot:run
```

服务默认监听 **http://localhost:8080**。

### 3. 启动前端

```bash
cd hify-web
npm install
npm run dev
```

开发地址：**http://localhost:5173**（`/api` 代理至后端 8080）。

### 4. 验证

```bash
curl "http://localhost:8080/api/v1/providers?page=1&pageSize=5"
```

浏览器访问 http://localhost:5173 ，在「模型管理」中配置提供商并测试连接，再在「Agent 管理」中创建 Agent 并开始对话。

## 模型提供商配置说明

### 认证字段

`authConfig` 统一使用 **`apiKey`** 字段（兼容历史 `api_key`）。编辑提供商时 API Key 留空则**保留原密钥**。

### DeepSeek 推荐配置

| 项 | 值 |
|----|-----|
| 类型 | **OpenAI Compatible** |
| API 地址 | `https://api.deepseek.com`（不要带 `/anthropic`） |
| API Key | DeepSeek 控制台密钥 |
| 模型 ID | 如 `deepseek-chat` |

> DeepSeek 的 Anthropic 兼容入口（`/anthropic`）不提供模型列表接口；连通性测试会自动改用 OpenAI 兼容的 `/v1/models` 探测。日常对话也建议使用 **OpenAI Compatible** 类型。

### 创建提供商检查清单

1. 填写 API 地址与 API Key，并填写**模型 ID**（会写入 `model_config`，供 Agent 绑定）
2. 开启「启用」
3. 点击「测试连接」确认成功
4. 在 Agent 管理中应能看到对应模型选项

## 模块结构

| 模块 | 说明 |
|------|------|
| hify-common | 公共模块 |
| hify-provider | 模型提供商与模型配置 |
| hify-agent | Agent 配置 |
| hify-chat | 对话引擎 |
| hify-knowledge | 知识库 RAG（规划中） |
| hify-workflow | 工作流（规划中） |
| hify-mcp | MCP 工具（规划中） |
| hify-boot | 启动入口 |
| hify-web | 管理控制台前端 |

## API 概览

### Provider 管理

```
GET    /api/v1/providers                         # 分页列表
GET    /api/v1/providers/model-configs             # 已启用提供商下的模型（Agent 绑定用）
POST   /api/v1/providers                         # 创建
GET    /api/v1/providers/{id}                    # 详情
PUT    /api/v1/providers/{id}                    # 更新
DELETE /api/v1/providers/{id}                    # 删除
POST   /api/v1/providers/{id}/test-connection    # 连通性测试
GET    /api/v1/providers/{id}/model-configs      # 某提供商的模型配置
POST   /api/v1/providers/{id}/model-configs      # 新增模型配置
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

统一响应格式 `Result<T>`：`{ "code": 200, "message": "success", "data": {...} }`

详细说明见 [docs/provider-api.md](docs/provider-api.md)

## 常见问题

| 现象 | 处理 |
|------|------|
| 测试连接 HTTP 404（DeepSeek） | 类型改为 OpenAI Compatible，地址用 `https://api.deepseek.com` |
| Agent 下拉无模型 | 确认提供商已启用且保存时填写了模型 ID |
| 对话页无 Agent | 先创建并启用 Agent；刷新页面 |
| 端口 8080 被占用 | 结束旧 Java 进程后重新 `mvn spring-boot:run` |

## License

内部项目，按需使用。
