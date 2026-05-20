# Hify

简版 AI Agent 开发平台，本地部署，面向 20-50 人团队内部使用。

## 技术栈

后端：Spring Boot 3.3 + MyBatis-Plus + MySQL 8.x + Redis 7.x + pgvector
前端：Vue 3 + TypeScript + Vite + Element Plus

## 开发进度

| 模块 | 状态 | 说明 |
|------|:----:|------|
| hify-common | ✅ | BaseEntity、Result/PageResult、BizException、MyBatis-Plus 配置 |
| hify-provider | ✅ | CRUD + 连通性测试 + 健康状态 + 模型配置管理 |
| hify-agent | ✅ | CRUD + temperature 温度参数 + 跨模块模型绑定 |
| hify-chat | ✅ | 对话引擎（SSE 流式 + 四种 LLM 适配器 + 会话/消息存储） |
| hify-knowledge | ⏳ | 知识库 RAG（pgvector 向量检索） |
| hify-workflow | ⏳ | 简版工作流（JSON 配置） |
| hify-mcp | ⏳ | MCP 工具接入 |

## 快速启动

**环境要求**：JDK 17+、Maven 3.8+、Node.js 18+、MySQL 8.x、Redis 7.x

### 1. 准备数据库

```bash
# 创建数据库
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS hify DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 导入表结构
mysql -uroot -p hify < hify-boot/src/main/resources/schema.sql
```

### 2. 启动后端

```bash
# 编译安装全部模块
mvn install -DskipTests

# 启动服务（默认 8080 端口）
cd hify-boot
mvn spring-boot:run
```

### 3. 启动前端

```bash
cd hify-web
npm install
npm run dev          # 默认 http://localhost:5173
```

### 4. 验证

```bash
# 后端健康检查
curl http://localhost:8080/api/v1/providers?page=1&pageSize=5

# 前端
open http://localhost:5173
```

## 模块结构

| 模块 | 说明 |
|------|------|
| hify-common | 公共模块（BaseEntity、Result、BizException、MyBatis-Plus 配置） |
| hify-provider | 模型提供商管理（CRUD + 连通性测试 + 健康状态 + 模型配置） |
| hify-agent | Agent 管理（CRUD + 系统提示词 + temperature + 模型绑定） |
| hify-chat | 对话引擎（SSE 流式 + OpenAI/Claude/Gemini/Ollama 适配器） |
| hify-knowledge | 知识库 RAG（pgvector 向量检索） |
| hify-workflow | 简版工作流（JSON 配置） |
| hify-mcp | MCP 工具接入 |
| hify-boot | 启动模块（装配所有后端模块） |
| hify-web | 前端 SPA（Vue 3 + Element Plus） |

## API 概览

### Provider 管理

```
GET    /api/v1/providers                    # 分页列表
POST   /api/v1/providers                    # 创建
GET    /api/v1/providers/{id}               # 详情（含模型配置和健康状态）
PUT    /api/v1/providers/{id}               # 更新
DELETE /api/v1/providers/{id}               # 删除
POST   /api/v1/providers/{id}/test-connection # 连通性测试
```

### Agent 管理

```
GET    /api/v1/agents                       # 分页列表
POST   /api/v1/agents                       # 创建
GET    /api/v1/agents/{id}                  # 详情
PUT    /api/v1/agents/{id}                  # 更新
DELETE /api/v1/agents/{id}                  # 删除
```

所有接口返回统一格式 `Result<T>`：`{ "code": 200, "message": "success", "data": {...} }`

### Conversation 对话

```
GET    /api/v1/conversations                    # 会话列表
GET    /api/v1/conversations/{id}/messages      # 消息历史（时间升序）
POST   /api/v1/conversations/stream             # 发送消息（SSE 流式响应）
```

详细文档见 [docs/provider-api.md](docs/provider-api.md)
