import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('Request error:', error)
    return Promise.reject(error)
  },
)

/** 静默 GET：失败时不弹全局错误，返回 null（用于编辑页可选数据加载） */
export async function getSilent<T>(url: string): Promise<T | null> {
  try {
    const res = await http.get<{ code: number; data: T }>(url)
    if (res.data?.code === 200) {
      return res.data.data ?? null
    }
    return null
  } catch {
    return null
  }
}

export default http
