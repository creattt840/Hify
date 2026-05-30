export interface Conversation {
  id: number
  agentId: number
  agentName: string
  title: string
  status: string
  messageCount: number
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  id?: number
  conversationId?: number
  role: 'user' | 'assistant' | 'system' | 'tool'
  content: string
  tokenCount?: number
  createdAt?: string
}

export interface ChatSendRequest {
  agentId: number
  conversationId?: number
  content: string
}

export interface SseDeltaEvent {
  delta: string
}

export interface SseDoneEvent {
  convId: number
}
