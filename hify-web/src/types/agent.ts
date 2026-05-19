// ─────────────────────── 请求 ───────────────────────

/** 创建/更新 Agent 请求 */
export interface AgentRequest {
  name: string
  description?: string
  systemPrompt: string
  modelConfigId: number
  temperature: number
  isEnabled: boolean
}

// ─────────────────────── 响应 ───────────────────────

/** Agent 列表项 */
export interface AgentResponse {
  id: number
  name: string
  description: string
  systemPrompt: string
  modelConfigId: number
  temperature: number
  /** 绑定的模型名称，如 "GPT-4o" */
  modelName: string
  /** 绑定的模型 ID，如 "gpt-4o" */
  modelId: string
  /** 所属提供商名称 */
  providerName: string
  /** 所属提供商类型 */
  providerType: string
  isEnabled: boolean
  createdAt: string
  updatedAt: string
}

/** Agent 详情（继承列表字段） */
export interface AgentDetailResponse extends AgentResponse {
  /** 关联知识库（一期空数组） */
  knowledgeBases: Record<string, unknown>[]
  /** 关联 MCP 工具（一期空数组） */
  mcpTools: Record<string, unknown>[]
}
