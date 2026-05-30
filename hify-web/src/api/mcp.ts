import { get, post, put, del } from '@/utils/request'
import http, { getSilent } from './http'
import type {
  McpServerCreateRequest,
  McpServerUpdateRequest,
  McpServerResponse,
  McpServerListItemResponse,
  ConnectionTestResult,
  McpDebugRequest,
  McpDebugResponse,
  McpToolOption,
} from '@/types/mcp'
import type { PageResult } from '@/types'

const BASE = '/v1/mcp-servers'

/** 分页列表（PageResult 需保留 total，走原始 http） */
export function getMcpServerList(params?: { page?: number; pageSize?: number }) {
  return http.get<PageResult<McpServerListItemResponse[]>>(BASE, { params })
}

/** 详情（含工具列表） */
export function getMcpServerDetail(id: number) {
  return get<McpServerResponse>(`${BASE}/${id}`)
}

/** 创建 */
export function createMcpServer(data: McpServerCreateRequest) {
  return post<McpServerResponse>(BASE, data)
}

/** 更新 */
export function updateMcpServer(id: number, data: McpServerUpdateRequest) {
  return put<McpServerResponse>(`${BASE}/${id}`, data)
}

/** 删除 */
export function deleteMcpServer(id: number) {
  return del(`${BASE}/${id}`)
}

/** 连通性测试 */
export function testMcpConnection(id: number) {
  return post<ConnectionTestResult>(`${BASE}/${id}/test`)
}

/** 工具调试 */
export function debugMcpTool(id: number, data: McpDebugRequest) {
  return post<McpDebugResponse>(`${BASE}/${id}/debug`, data)
}

/** 获取所有已启用 MCP Server 下的工具（静默，失败返回空数组） */
export async function getAvailableMcpTools(): Promise<McpToolOption[]> {
  return (await getSilent<McpToolOption[]>(`${BASE}/available-tools`)) ?? []
}
