import http from './http'
import type {
  ProviderRequest,
  ProviderListParams,
  ProviderResponse,
  ProviderDetailResponse,
  ConnectionTestResult,
} from '@/types/provider'
import type { Result, PageResult } from '@/types'

const BASE = '/v1/providers'

/**
 * 获取提供商分页列表
 */
export function getProviderList(params?: ProviderListParams) {
  return http.get<PageResult<ProviderResponse[]>>(BASE, { params })
}

/**
 * 获取提供商详情（含 modelConfigs 和 health）
 */
export function getProviderDetail(id: number) {
  return http.get<Result<ProviderDetailResponse>>(`${BASE}/${id}`)
}

/**
 * 创建提供商
 */
export function createProvider(data: ProviderRequest) {
  return http.post<Result<ProviderResponse>>(BASE, data)
}

/**
 * 更新提供商
 */
export function updateProvider(id: number, data: ProviderRequest) {
  return http.put<Result<ProviderResponse>>(`${BASE}/${id}`, data)
}

/**
 * 删除提供商（逻辑删除）
 */
export function deleteProvider(id: number) {
  return http.delete<Result<null>>(`${BASE}/${id}`)
}

/**
 * 连通性测试
 */
export function testConnection(id: number) {
  return http.post<Result<ConnectionTestResult>>(`${BASE}/${id}/test-connection`)
}
