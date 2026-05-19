# 模型提供商 API

**Base URL**: `/api/v1/providers`

---

## 通用约定

### 响应格式

所有接口返回统一格式 `Result<T>`：

```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

### 分页响应

列表接口返回 `PageResult<T>`，在 `Result` 基础上增加分页字段：

```json
{
  "code": 200,
  "message": "success",
  "data": [ ],
  "total": 7,
  "page": 1,
  "size": 20
}
```

### 错误码

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 1000 | 服务器内部错误 |
| 1001 | 参数校验失败 |
| 2000 | 模型提供商不存在 |
| 2001 | 模型提供商连接失败 |
| 2002 | 模型提供商限流 |
| 2003 | 模型提供商认证失败 |
| 2004 | 模型提供商响应超时 |

### 提供商类型 (providerType)

| 值 | 说明 |
|----|------|
| `openai` | OpenAI |
| `openai_compatible` | OpenAI 兼容接口 |
| `anthropic` | Anthropic Claude |
| `gemini` | Google Gemini |
| `ollama` | Ollama（本地部署） |

### 认证配置 (authConfig)

不同提供商的认证 key 名不同，统一使用 `apiKey` 字段：

```json
// OpenAI / Gemini
{ "apiKey": "sk-xxx" }

// Anthropic
{ "apiKey": "sk-ant-xxx" }

// Ollama（无需认证）
{}
```

---

## 1. 创建提供商

```
POST /api/v1/providers
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 是 | 提供商名称 |
| providerType | string | 是 | 提供商类型，见枚举 |
| baseUrl | string | 否 | API 地址，默认空字符串 |
| authConfig | object | 是 | 认证配置，至少传 `{}` |
| isEnabled | boolean | 是 | 是否启用 |

### 请求示例

```bash
curl -X POST http://localhost:8080/api/v1/providers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My OpenAI",
    "providerType": "openai",
    "baseUrl": "https://api.openai.com",
    "authConfig": { "apiKey": "sk-xxx" },
    "isEnabled": true
  }'
```

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "My OpenAI",
    "providerType": "openai",
    "baseUrl": "https://api.openai.com",
    "authConfig": { "apiKey": "sk-xxx" },
    "isEnabled": true,
    "createdAt": "2026-05-19T11:27:43.563",
    "updatedAt": "2026-05-19T11:27:43.563"
  }
}
```

---

## 2. 获取提供商列表

```
GET /api/v1/providers
```

### 查询参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | int | 1 | 页码（从 1 开始） |
| pageSize | int | 20 | 每页条数（最大 100） |

### 请求示例

```bash
curl "http://localhost:8080/api/v1/providers?page=1&pageSize=10"
```

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "My OpenAI",
      "providerType": "openai",
      "baseUrl": "https://api.openai.com",
      "authConfig": { "apiKey": "sk-xxx" },
      "isEnabled": true,
      "createdAt": "2026-05-19T11:27:43.563",
      "updatedAt": "2026-05-19T11:27:43.563"
    }
  ],
  "total": 7,
  "page": 1,
  "size": 10
}
```

> 列表按 `createdAt` 倒序排列。

---

## 3. 获取提供商详情

```
GET /api/v1/providers/{id}
```

### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| id | long | 提供商 ID |

### 请求示例

```bash
curl "http://localhost:8080/api/v1/providers/1"
```

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "My OpenAI",
    "providerType": "openai",
    "baseUrl": "https://api.openai.com",
    "authConfig": { "apiKey": "sk-xxx" },
    "isEnabled": true,
    "createdAt": "2026-05-19T11:27:43.563",
    "updatedAt": "2026-05-19T11:27:43.563",
    "modelConfigs": [
      {
        "id": 1,
        "providerId": 1,
        "name": "GPT-4o",
        "modelId": "gpt-4o",
        "extraParams": { "temperature": 0.7 }
      }
    ],
    "health": {
      "failCount": 0,
      "latencyMs": 856,
      "lastSuccessAt": "2026-05-19T11:28:00.000"
    }
  }
}
```

| 字段 | 说明 |
|------|------|
| modelConfigs | 关联的模型配置列表，无数据时返回 `[]` |
| health | 健康状态，无测试记录时返回 `null` |

### 错误场景

```json
{ "code": 2000, "message": "模型提供商不存在", "data": null }
```

---

## 4. 更新提供商

```
PUT /api/v1/providers/{id}
```

### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| id | long | 提供商 ID |

### 请求体

同 [创建提供商](#1-创建提供商)，全量更新。

### 请求示例

```bash
curl -X PUT http://localhost:8080/api/v1/providers/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Updated OpenAI",
    "providerType": "openai",
    "baseUrl": "https://api.openai.com/v1",
    "authConfig": { "apiKey": "sk-new" },
    "isEnabled": false
  }'
```

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "Updated OpenAI",
    "providerType": "openai",
    "baseUrl": "https://api.openai.com/v1",
    "authConfig": { "apiKey": "sk-new" },
    "isEnabled": false,
    "createdAt": "2026-05-19T11:27:43.563",
    "updatedAt": "2026-05-19T11:30:00.000"
  }
}
```

> `updatedAt` 会在每次更新时自动刷新。

---

## 5. 删除提供商

```
DELETE /api/v1/providers/{id}
```

### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| id | long | 提供商 ID |

### 请求示例

```bash
curl -X DELETE "http://localhost:8080/api/v1/providers/1"
```

### 响应示例

```json
{ "code": 200, "message": "success", "data": null }
```

> 逻辑删除，删除后查询详情返回 `2000` 错误码。

---

## 6. 连通性测试

```
POST /api/v1/providers/{id}/test-connection
```

### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| id | long | 提供商 ID |

### 连通性校验规则

| 类型 | 请求地址 | 认证方式 |
|------|----------|----------|
| openai / openai_compatible / gemini | `{baseUrl}/v1/models` | `Authorization: Bearer {apiKey}` |
| anthropic | `{baseUrl}/v1/models` | `x-api-key: {apiKey}` |
| ollama | `{baseUrl}/api/tags` | 无需认证 |

超时时间：10 秒（连接 + 读取）。

### 请求示例

```bash
curl -X POST "http://localhost:8080/api/v1/providers/1/test-connection"
```

### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "latencyMs": 856,
    "modelCount": 12,
    "errorMessage": null
  }
}
```

### 失败响应（网络不可达）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": false,
    "latencyMs": 0,
    "modelCount": 0,
    "errorMessage": "网络异常: Connect timed out"
  }
}
```

| 字段 | 说明 |
|------|------|
| success | 连通性测试是否通过 |
| latencyMs | 请求延迟（毫秒） |
| modelCount | 可用模型数量 |
| errorMessage | 失败原因，成功时为 `null` |

> 测试结果会写入 `t_provider_health` 表，之后查询详情时可在 `health` 字段中看到最新的失败次数和延迟。
