import { ref, type Ref } from 'vue'

export interface UseRequestReturn<T> {
  data: Ref<T | null>
  loading: Ref<boolean>
  error: Ref<string | null>
  execute: (...args: unknown[]) => Promise<T | null>
}

export function useRequest<T>(
  fn: (...args: unknown[]) => Promise<T>,
): UseRequestReturn<T> {
  const data = ref<T | null>(null) as Ref<T | null>
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function execute(...args: unknown[]): Promise<T | null> {
    loading.value = true
    error.value = null
    try {
      const result = await fn(...args)
      data.value = result
      return result
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '请求失败'
      error.value = msg
      return null
    } finally {
      loading.value = false
    }
  }

  return { data, loading, error, execute }
}
