/**
 * 统一响应类型（对应后端 Result<T>）
 */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

/**
 * 分页响应类型（对应后端 PageResult<T>）
 */
export interface PageResult<T = unknown> extends Result<T> {
  total: number
  page: number
  size: number
}
