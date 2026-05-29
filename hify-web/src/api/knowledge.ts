import http from './http'
import type {
  KnowledgeBaseResponse,
  KnowledgeBaseCreateRequest,
  KnowledgeBaseUpdateRequest,
  DocumentResponse,
  DocumentChunkResponse,
} from '@/types/knowledge'
import type { Result, PageResult } from '@/types'

const BASE = '/v1/knowledge-bases'

/** 获取知识库分页列表，支持按名称搜索 */
export function getKnowledgeBaseList(params?: {
  page?: number
  pageSize?: number
  name?: string
}) {
  return http.get<PageResult<KnowledgeBaseResponse[]>>(BASE, { params })
}

/** 获取知识库详情 */
export function getKnowledgeBaseDetail(id: number) {
  return http.get<Result<KnowledgeBaseResponse>>(`${BASE}/${id}`)
}

/** 创建知识库 */
export function createKnowledgeBase(data: KnowledgeBaseCreateRequest) {
  return http.post<Result<KnowledgeBaseResponse>>(BASE, data)
}

/** 更新知识库 */
export function updateKnowledgeBase(id: number, data: KnowledgeBaseUpdateRequest) {
  return http.put<Result<KnowledgeBaseResponse>>(`${BASE}/${id}`, data)
}

/** 删除知识库 */
export function deleteKnowledgeBase(id: number) {
  return http.delete<Result<null>>(`${BASE}/${id}`)
}

// ─────────────────────── 文档 ───────────────────────

/** 获取文档分页列表 */
export function getDocumentList(kbId: number, params?: { page?: number; pageSize?: number }) {
  return http.get<PageResult<DocumentResponse[]>>(`${BASE}/${kbId}/documents`, { params })
}

/** 上传文档（axios 自动设置 multipart/form-data 的 boundary） */
export function uploadDocument(kbId: number, formData: FormData) {
  return http.post<Result<DocumentResponse>>(`${BASE}/${kbId}/documents`, formData)
}

/** 获取文档详情（轮询用） */
export function getDocumentDetail(id: number) {
  return http.get<Result<DocumentResponse>>(`/v1/documents/${id}`)
}

/** 获取文档分块列表 */
export function getDocumentChunks(id: number) {
  return http.get<Result<DocumentChunkResponse[]>>(`/v1/documents/${id}/chunks`)
}

/** 删除文档 */
export function deleteDocument(id: number) {
  return http.delete<Result<null>>(`/v1/documents/${id}`)
}
