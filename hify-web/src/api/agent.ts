import http from './http'
import type { AgentRequest, AgentResponse, AgentDetailResponse } from '@/types/agent'
import type { Result, PageResult } from '@/types'

const BASE = '/v1/agents'

/** 获取 Agent 分页列表 */
export function getAgentList(params?: { page?: number; pageSize?: number }) {
  return http.get<PageResult<AgentResponse[]>>(BASE, { params })
}

/** 获取 Agent 详情 */
export function getAgentDetail(id: number) {
  return http.get<Result<AgentDetailResponse>>(`${BASE}/${id}`)
}

/** 创建 Agent */
export function createAgent(data: AgentRequest) {
  return http.post<Result<AgentResponse>>(BASE, data)
}

/** 更新 Agent */
export function updateAgent(id: number, data: AgentRequest) {
  return http.put<Result<AgentResponse>>(`${BASE}/${id}`, data)
}

/** 删除 Agent */
export function deleteAgent(id: number) {
  return http.delete<Result<null>>(`${BASE}/${id}`)
}
