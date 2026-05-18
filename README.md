# Hify

简版 AI Agent 开发平台，本地部署，面向 20-50 人团队内部使用。

## 技术栈

后端：Spring Boot 3.3 + MyBatis-Plus + MySQL 8.x + Redis 7.x + pgvector
前端：Vue 3 + TypeScript + Vite + Element Plus + Pinia

## 快速启动

**环境要求**：JDK 17+、Maven 3.8+、Node.js 18+、MySQL 8.x、Redis 7.x

### 后端

```bash
# 1. 编译安装全部模块
mvn install -DskipTests

# 2. 启动服务（默认 8080 端口）
cd hify-boot
mvn spring-boot:run

# 3. 验证
curl http://localhost:8080/health
# → success
```

### 前端

```bash
cd hify-web

# 安装依赖
npm install

# 启动开发服务器（默认 5173 端口）
npm run dev
```

**启动前**：确保本地 MySQL 和 Redis 已运行，数据库 `hify` 已创建。如暂时不用数据库，application.yml 中已排除 DataSource/MyBatis-Plus 自动配置，仅依赖 Redis 即可启动。

## 模块结构

| 模块 | 说明 |
|------|------|
| hify-common | 公共模块（Result、PageResult、BizException、GlobalExceptionHandler） |
| hify-provider | 模型提供商管理（OpenAI / Claude / Gemini / Ollama） |
| hify-agent | Agent 管理与配置 |
| hify-chat | 对话引擎（SSE 流式响应） |
| hify-knowledge | 知识库 RAG（pgvector 向量检索） |
| hify-workflow | 简版工作流（JSON 配置） |
| hify-mcp | MCP 工具接入 |
| hify-boot | 启动模块（装配所有后端模块） |
| hify-web | 前端 SPA（Vue 3 + Element Plus） |
