import { ElMessageBox } from 'element-plus'
import { notifySuccess, notifyError } from './notify'

export interface ConfirmOptions {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  type?: 'warning' | 'info' | 'error'
}

export function useConfirm() {
  /**
   * 一行确认 → 调接口 → 成功提示
   * @param options 弹窗文案配置
   * @param api 确认后执行的 API 方法
   * @param onSuccess 成功回调
   */
  async function confirm<T>(
    options: ConfirmOptions,
    api: () => Promise<T>,
    onSuccess?: (result: T) => void,
  ) {
    try {
      await ElMessageBox.confirm(options.message, options.title ?? '确认操作', {
        confirmButtonText: options.confirmText ?? '确定',
        cancelButtonText: options.cancelText ?? '取消',
        type: options.type ?? 'warning',
      })
      const result = await api()
      notifySuccess('操作成功')
      onSuccess?.(result)
    } catch (err) {
      if (err !== 'cancel' && err !== 'close') {
        notifyError('操作失败')
      }
    }
  }

  return { confirm }
}
