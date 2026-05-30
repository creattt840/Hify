import http from './http'
import type { WorkflowCreateRequest, WorkflowListItem, WorkflowResponse } from '@/types/workflow'
import type { Result, PageResult } from '@/types'

const BASE = '/v1/workflows'

/** 获取工作流分页列表 */
export function getWorkflowList(params?: { page?: number; pageSize?: number }) {
  return http.get<PageResult<WorkflowListItem[]>>(BASE, { params })
}

/** 获取工作流详情（含节点和边） */
export function getWorkflowDetail(id: number) {
  return http.get<Result<WorkflowResponse>>(`${BASE}/${id}`)
}

/** 创建工作流 */
export function createWorkflow(data: WorkflowCreateRequest) {
  return http.post<Result<WorkflowResponse>>(BASE, data)
}

/** 更新工作流 */
export function updateWorkflow(id: number, data: WorkflowCreateRequest) {
  return http.put<Result<WorkflowResponse>>(`${BASE}/${id}`, data)
}

/** 删除工作流 */
export function deleteWorkflow(id: number) {
  return http.delete<Result<null>>(`${BASE}/${id}`)
}
