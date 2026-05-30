// ─────────────────────── 请求 ───────────────────────

/** 节点请求 */
export interface NodeRequest {
  nodeKey: string
  nodeType: string
  title?: string
  config?: Record<string, unknown>
  sortOrder?: number
}

/** 边请求 */
export interface EdgeRequest {
  source: string
  target: string
  condition?: string
  sortOrder?: number
}

/** 创建/更新工作流请求 */
export interface WorkflowCreateRequest {
  name: string
  description?: string
  isEnabled?: boolean
  nodes: NodeRequest[]
  edges?: EdgeRequest[]
}

// ─────────────────────── 响应 ───────────────────────

/** 工作流列表项 */
export interface WorkflowListItem {
  id: number
  name: string
  description: string
  version: number
  isEnabled: boolean
  isPublished: boolean
  nodeCount: number
  createdAt: string
  updatedAt: string
}

/** 工作流详情（含节点和边） */
export interface WorkflowResponse extends WorkflowListItem {
  nodes: NodeResponse[]
  edges: EdgeResponse[]
}

/** 节点响应 */
export interface NodeResponse {
  id: number
  workflowId: number
  nodeKey: string
  nodeType: string
  title: string
  config: Record<string, unknown>
  sortOrder: number
}

/** 边响应 */
export interface EdgeResponse {
  id: number
  workflowId: number
  source: string
  target: string
  condition: string
  sortOrder: number
}
