import http, { getSilent } from './http'
import { get, put } from '@/utils/request'
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

/** 获取 Agent 已绑定的 MCP 工具 ID 列表（静默，失败返回空数组） */
export async function getAgentBoundTools(id: number): Promise<number[]> {
  return (await getSilent<number[]>(`${BASE}/${id}/tools`)) ?? []
}

/** 绑定 MCP 工具（全量替换，最多 10 个） */
export function bindAgentTools(id: number, toolIds: number[]) {
  return put(`${BASE}/${id}/tools`, { toolIds })
}
