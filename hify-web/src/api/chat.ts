import { get } from '@/utils/request'
import type { Conversation, ChatMessage, ChatSendRequest } from '@/types/chat'

const BASE = '/v1/conversations'

export function listConversations(): Promise<Conversation[]> {
  return get<Conversation[]>(BASE)
}

export function getMessages(conversationId: number): Promise<ChatMessage[]> {
  return get<ChatMessage[]>(`${BASE}/${conversationId}/messages`)
}

/**
 * 发送消息并返回 SSE ReadableStream。
 * 不使用 axios（axios 不支持流式读取），直接用 fetch。
 */
export function sendMessageStream(
  data: ChatSendRequest,
  signal?: AbortSignal,
): Promise<Response> {
  return fetch('/api' + BASE + '/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
    signal,
  })
}
