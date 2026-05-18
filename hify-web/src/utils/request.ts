import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

http.interceptors.response.use(
  (response) => {
    const { code, message, data } = response.data
    if (code === 200) {
      return data
    }
    ElMessage.error(message || '请求失败')
    return Promise.reject(response.data)
  },
  (error) => {
    ElMessage.error('网络异常，请稍后重试')
    return Promise.reject(error)
  },
)

export function get<T = unknown>(url: string, params?: Record<string, unknown>) {
  return http.get<T, T>(url, { params })
}

export function post<T = unknown>(url: string, data?: unknown) {
  return http.post<T, T>(url, data)
}

export function put<T = unknown>(url: string, data?: unknown) {
  return http.put<T, T>(url, data)
}

export function del<T = unknown>(url: string, params?: Record<string, unknown>) {
  return http.delete<T, T>(url, { params })
}

export default http
