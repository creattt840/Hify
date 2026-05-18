import { ElMessage } from 'element-plus'

export function notifySuccess(message: string) {
  ElMessage.success({ message, showClose: true, duration: 3000 })
}

export function notifyError(message: string) {
  ElMessage.error({ message, showClose: true, duration: 5000 })
}

export function notifyWarning(message: string) {
  ElMessage.warning({ message, showClose: true, duration: 4000 })
}
