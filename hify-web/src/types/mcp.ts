// ─────────────────────── 请求 ───────────────────────

/** 创建 MCP Server 请求 */
export interface McpServerCreateRequest {
  name: string
  endpoint: string
  isEnabled: boolean
}

/** 更新 MCP Server 请求 */
export interface McpServerUpdateRequest {
  name: string
  endpoint: string
  isEnabled: boolean
}

/** 工具调试请求 */
export interface McpDebugRequest {
  toolName: string
  arguments: Record<string, unknown>
}

// ─────────────────────── 响应 ───────────────────────

/** MCP 工具响应 */
export interface McpToolResponse {
  id: number
  serverId: number
  name: string
  description: string
  inputSchema: Record<string, unknown> | null
}

/** MCP Server 详情响应 */
export interface McpServerResponse {
  id: number
  name: string
  endpoint: string
  isEnabled: boolean
  tools: McpToolResponse[]
  createdAt: string
  updatedAt: string
}

/** MCP Server 列表项响应 */
export interface McpServerListItemResponse {
  id: number
  name: string
  endpoint: string
  isEnabled: boolean
  toolCount: number
  createdAt: string
  updatedAt: string
}

/** 连通性测试结果 */
export interface ConnectionTestResult {
  success: boolean
  message: string
  toolCount: number
}

/** 工具调试结果 */
export interface McpDebugResponse {
  result: string
  elapsedMs: number
}

/** Agent 绑定时可选的 MCP 工具 */
export interface McpToolOption {
  id: number
  name: string
  description: string
  serverId: number
  serverName: string
}

// ─────────────────────── 调试面板状态 ───────────────────────

/** 单次调用记录 */
export interface DebugCallRecord {
  id: number
  toolName: string
  arguments: Record<string, unknown>
  result: string
  elapsedMs: number
  isError: boolean
  calledAt: string
}
