/** 知识库列表项 */
export interface KnowledgeBaseResponse {
  id: number
  name: string
  description: string
  embedModel: string
  chunkSize: number
  chunkOverlap: number
  isEnabled: boolean
  documentCount: number
  createdAt: string
  updatedAt: string
}

/** 创建知识库 */
export interface KnowledgeBaseCreateRequest {
  name: string
  description?: string
}

/** 更新知识库 */
export interface KnowledgeBaseUpdateRequest {
  name: string
  description?: string
  isEnabled?: boolean
}

// ─────────────────────── 文档 ───────────────────────

/** 文档列表项 */
export interface DocumentResponse {
  id: number
  kbId: number
  name: string
  fileType: string
  fileSize: number
  status: 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED'
  chunkCount: number
  errorMsg: string
  createdAt: string
  updatedAt: string
}

/** 文档分块 */
export interface DocumentChunkResponse {
  id: number
  documentId: number
  kbId: number
  chunkIndex: number
  content: string
  tokenCount: number
  createdAt: string
}
