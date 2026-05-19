// ─────────────────────── 枚举 ───────────────────────

/** 提供商类型 */
export type ProviderType = 'openai' | 'openai_compatible' | 'anthropic' | 'gemini' | 'ollama'

// ─────────────────────── 请求 ───────────────────────

/** 创建/更新提供商请求 */
export interface ProviderRequest {
  /** 提供商名称 */
  name: string
  /** 提供商类型 */
  providerType: ProviderType | string
  /** API 地址 */
  baseUrl?: string
  /** 认证配置，Ollama 传 {} */
  authConfig: Record<string, unknown>
  /** 是否启用 */
  isEnabled: boolean
}

/** 提供商列表查询参数 */
export interface ProviderListParams {
  /** 页码，从 1 开始 */
  page?: number
  /** 每页条数，默认 20，最大 100 */
  pageSize?: number
  /** 按提供商类型筛选 */
  type?: ProviderType | string
  /** 按启用状态筛选 */
  enabled?: boolean
}

// ─────────────────────── 响应 ───────────────────────

/** 模型配置响应 */
export interface ModelConfigResponse {
  id: number
  providerId: number
  /** 配置名称，如 "GPT-4o" */
  name: string
  /** 模型 ID，如 "gpt-4o" */
  modelId: string
  /** 额外参数，如 temperature */
  extraParams: Record<string, unknown>
}

/** 提供商健康状态 */
export interface ProviderHealthResponse {
  /** 连续失败次数 */
  failCount: number
  /** 最近一次请求延迟（毫秒） */
  latencyMs: number
  /** 最近一次成功时间 */
  lastSuccessAt: string | null
}

/** 提供商响应（列表/创建/更新） */
export interface ProviderResponse {
  id: number
  name: string
  providerType: string
  baseUrl: string
  authConfig: Record<string, unknown>
  isEnabled: boolean
  createdAt: string
  updatedAt: string
  /** 健康状态，列表查询时填充 */
  health?: ProviderHealthResponse | null
  /** 已启用模型数量，列表查询时填充 */
  modelCount?: number
}

/** 提供商详情响应（继承列表字段，增加模型配置和健康状态） */
export interface ProviderDetailResponse extends ProviderResponse {
  /** 关联模型配置列表，无数据时为空数组 */
  modelConfigs: ModelConfigResponse[]
  /** 健康状态，无测试记录时为 null */
  health: ProviderHealthResponse | null
}

/** 连通性测试结果 */
export interface ConnectionTestResult {
  /** 测试是否通过 */
  success: boolean
  /** 请求延迟（毫秒） */
  latencyMs: number
  /** 可用模型数量 */
  modelCount: number
  /** 失败原因，成功时为 null */
  errorMessage: string | null
}
