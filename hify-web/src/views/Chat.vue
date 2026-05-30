<template>
  <div class="chat-layout">
    <!-- ========== 左侧会话列表面板 ========== -->
    <aside class="chat-sidebar">
      <!-- 头部 -->
      <div class="sidebar-top">
        <div class="sidebar-brand">Hify AI</div>
        <el-select
          v-model="selectedAgentId"
          placeholder="选择 Agent"
          size="default"
          class="agent-picker"
          filterable
        >
          <el-option
            v-for="a in agents"
            :key="a.id"
            :label="a.name"
            :value="a.id"
          />
        </el-select>
        <p v-if="agents.length === 0" class="sidebar-hint">请先在 Agent 管理中创建并启用 Agent</p>
        <el-button
          class="new-chat-btn"
          @click="newConversation"
          :disabled="!selectedAgentId"
        >
          <span class="plus">+</span> 新对话
        </el-button>
      </div>

      <!-- 会话列表 -->
      <div class="conv-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: conv.id === currentConversationId }"
          @click="switchConversation(conv.id)"
        >
          <div class="conv-icon">💬</div>
          <div class="conv-body">
            <div class="conv-agent">{{ resolveAgentName(conv) }}</div>
            <div class="conv-title">{{ conv.title || '新对话' }}</div>
            <div class="conv-meta">
              <span>{{ conv.messageCount }} 条消息</span>
              <span>{{ formatTime(conv.updatedAt) }}</span>
            </div>
          </div>
          <button
            class="conv-delete"
            title="删除对话"
            @click.stop="handleDeleteConversation(conv)"
          >
            ×
          </button>
        </div>
        <div v-if="conversations.length === 0" class="conv-empty">
          暂无对话记录
        </div>
      </div>
    </aside>

    <!-- ========== 右侧聊天主区域 ========== -->
    <div class="chat-main">
      <div v-if="currentConversationAgentName" class="chat-header">
        <span class="chat-header-label">当前 Agent</span>
        <span class="chat-header-agent">{{ currentConversationAgentName }}</span>
      </div>
      <!-- 消息滚动区 -->
      <div class="messages-area" ref="messagesRef">
        <div v-if="displayMessages.length === 0 && !isLoading" class="chat-empty">
          <div class="empty-graphic">✨</div>
          <div class="empty-title">
            {{ selectedAgentId ? '开始对话' : '选一个 Agent，开始对话' }}
          </div>
          <div class="empty-hint">
            {{ selectedAgentId ? '在下方输入消息，按 Enter 发送' : '在左侧面板中选择一个 Agent' }}
          </div>
        </div>

        <!-- 历史消息 -->
        <div
          v-for="msg in displayMessages"
          :key="msg.id ?? `${msg.role}-${msg.createdAt}`"
          class="msg-row"
          :class="msg.role"
        >
          <div class="msg-avatar" :class="msg.role">
            <span v-if="msg.role === 'user'">U</span>
            <span v-else>AI</span>
          </div>
          <div class="msg-body">
            <div class="msg-label">{{ msg.role === 'user' ? '你' : 'Hify AI' }}</div>
            <div class="msg-bubble" :class="msg.role">
              <template v-if="msg.role === 'user'">
                {{ msg.content }}
              </template>
              <div
                v-else-if="msg.content?.trim()"
                class="markdown-body"
                v-html="renderMarkdown(msg.content)"
              />
            </div>
          </div>
        </div>

        <!-- 流式 AI 气泡：loading → 内容 → 完成 → 错误，始终同一个 DOM 元素 -->
        <div v-if="isLoading || streamError" class="msg-row assistant">
          <div class="msg-avatar assistant"><span>AI</span></div>
          <div class="msg-body">
            <div class="msg-label">Hify AI</div>
            <div class="msg-bubble assistant" :class="{ live: streamingContent && !streamError, error: streamError }">
              <!-- 等待首 token -->
              <span v-if="!streamingContent && !streamError" class="loading-dots"><i /><i /><i /></span>
              <!-- 流式渲染 -->
              <template v-else-if="streamingContent && !streamError">
                <div class="markdown-body" v-html="renderMarkdown(streamingContent)" />
                <span class="typing-cursor" />
              </template>
              <!-- 错误 -->
              <div v-else-if="streamError" class="error-msg">
                <span class="error-icon">!</span>
                {{ streamError }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部输入区域 -->
      <div class="input-area">
        <div class="input-row">
          <el-input
            ref="inputRef"
            v-model="inputText"
            type="textarea"
            :rows="1"
            :autosize="{ minRows: 1, maxRows: 5 }"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            :disabled="isLoading || !selectedAgentId"
            resize="none"
            class="msg-input"
            @keydown.enter.exact="sendMessage"
          />
          <el-button
            type="primary"
            class="action-btn"
            :disabled="isLoading || !inputText.trim() || !selectedAgentId"
            @click="sendMessage"
          >
            发送
          </el-button>
        </div>
        <div class="input-hint">
          Enter 发送 · Shift + Enter 换行
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import { marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.min.css'
import { listConversations, getMessages, sendMessageStream, deleteConversation } from '@/api/chat'
import { getAgentList } from '@/api/agent'
import { useConfirm } from '@/components/useConfirm'
import type { Conversation, ChatMessage } from '@/types/chat'
import type { AgentResponse } from '@/types/agent'

// ── marked 配置 ──
marked.use(
  markedHighlight({
    langPrefix: 'hljs language-',
    highlight(code: string, lang: string) {
      if (lang && hljs.getLanguage(lang)) {
        return hljs.highlight(code, { language: lang }).value
      }
      return hljs.highlightAuto(code).value
    },
  }),
)
marked.setOptions({ breaks: true, gfm: true })

function renderMarkdown(content: string): string {
  if (!content) return ''
  return marked.parse(content) as string
}

/** 过滤 MCP 工具调用产生的中间消息，避免对话区出现空白/重复气泡 */
function filterDisplayMessages(msgs: ChatMessage[]): ChatMessage[] {
  const result: ChatMessage[] = []
  for (let i = 0; i < msgs.length; i++) {
    const msg = msgs[i]
    if (msg.role === 'tool') continue

    if (msg.role === 'assistant') {
      const trimmed = msg.content?.trim() ?? ''
      if (!trimmed || trimmed === '[调用工具]') continue
      // 工具调用前的过渡语（下一条是 tool 消息）不展示
      if (msgs[i + 1]?.role === 'tool') continue
    }

    result.push(msg)
  }
  return result
}

// ── 状态 ──
const agents = ref<AgentResponse[]>([])
const allAgents = ref<AgentResponse[]>([])
const selectedAgentId = ref<number | null>(null)
const conversations = ref<Conversation[]>([])
const currentConversationId = ref<number | null>(null)
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const streamingContent = ref('')
const streamError = ref('')
const isLoading = ref(false)

const messagesRef = ref<HTMLElement | null>(null)
const inputRef = ref<any>(null)
const { confirm } = useConfirm()

/** 实际渲染的消息列表（隐藏 tool 中间态） */
const displayMessages = computed(() => filterDisplayMessages(messages.value))

/** 当前会话绑定的 Agent 名称 */
const currentConversationAgentName = computed(() => {
  if (!currentConversationId.value) return ''
  const conv = conversations.value.find((c) => c.id === currentConversationId.value)
  return conv ? resolveAgentName(conv) : ''
})

function resolveAgentName(conv: Conversation): string {
  if (conv.agentName) return conv.agentName
  const agent = allAgents.value.find((a) => a.id === conv.agentId)
  return agent?.name || (conv.agentId ? `Agent #${conv.agentId}` : '未知 Agent')
}

// ── 初始化 ──
onMounted(async () => {
  try {
    const res = await getAgentList({ page: 1, pageSize: 100 })
    const body = res.data
    allAgents.value = body.data ?? []
    agents.value = allAgents.value.filter((a) => a.isEnabled)
    if (agents.value.length > 0) {
      selectedAgentId.value = agents.value[0].id
    }
    await loadConversations()
  } catch {
    agents.value = []
    allAgents.value = []
  }
})

async function loadConversations() {
  try {
    conversations.value = await listConversations()
  } catch {
    conversations.value = []
  }
}

// ── 会话操作 ──
function newConversation() {
  if (isLoading.value) return
  currentConversationId.value = null
  messages.value = []
  streamingContent.value = ''
  inputText.value = ''
  nextTick(() => inputRef.value?.focus())
}

async function switchConversation(convId: number) {
  if (isLoading.value) return
  currentConversationId.value = convId
  const conv = conversations.value.find((c) => c.id === convId)
  if (conv?.agentId) {
    selectedAgentId.value = conv.agentId
  }
  messages.value = []
  streamingContent.value = ''
  try {
    messages.value = await getMessages(convId)
  } catch {
    messages.value = []
  }
  await nextTick()
  scrollToBottom()
}

function handleDeleteConversation(conv: Conversation) {
  confirm(
    { message: `确定删除对话「${conv.title || '新对话'}」吗？删除后不可恢复。` },
    () => deleteConversation(conv.id),
    async () => {
      if (currentConversationId.value === conv.id) {
        currentConversationId.value = null
        messages.value = []
        streamingContent.value = ''
      }
      await loadConversations()
    },
  )
}

// ── 发送消息 ──
async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || isLoading.value || !selectedAgentId.value) return

  inputText.value = ''
  streamError.value = ''

  messages.value.push({ id: Date.now(), role: 'user', content: text })
  await nextTick()
  scrollToBottom()

  isLoading.value = true
  streamingContent.value = ''

  let doneConvId: number | null = currentConversationId.value

  try {
    const response = await sendMessageStream({
      agentId: selectedAgentId.value,
      conversationId: currentConversationId.value ?? undefined,
      content: text,
    })

    if (!response.ok) throw new Error(`HTTP ${response.status}`)

    const reader = response.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let currentEvent = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.substring(6).trim()
        } else if (line.startsWith('data:')) {
          const raw = line.substring(5).trim()
          if (currentEvent === 'delta' && raw) {
            streamingContent.value += raw
            await nextTick()
            scrollToBottom()
          } else if (currentEvent === 'done') {
            try {
              const payload = JSON.parse(raw)
              if (payload.convId) {
                doneConvId = payload.convId
                currentConversationId.value = payload.convId
              }
            } catch { /* */ }
          } else if (currentEvent === 'error') {
            streamError.value = raw || '对话请求失败'
          }
        }
      }
    }
  } catch (err: any) {
    streamError.value = err?.message || '网络异常，请重试'
  } finally {
    // 流结束后从服务端同步消息，避免 MCP 工具路径无 delta 时需手动刷新
    if (doneConvId && !streamError.value) {
      try {
        messages.value = await getMessages(doneConvId)
      } catch {
        if (streamingContent.value) {
          messages.value.push({
            id: Date.now() + 1,
            role: 'assistant',
            content: streamingContent.value,
          })
        }
      }
    } else if (streamingContent.value && !streamError.value) {
      messages.value.push({
        id: Date.now() + 1,
        role: 'assistant',
        content: streamingContent.value,
      })
    }

    streamingContent.value = ''
    isLoading.value = false
    await nextTick()
    scrollToBottom()
    await loadConversations()
  }
}

// ── 滚动 ──
function scrollToBottom() {
  nextTick(() => {
    const el = messagesRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// ── 时间格式化 ──
function formatTime(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
  return d.toLocaleDateString('zh-CN')
}
</script>

<style scoped>
/* ═══════════════════════════════════════════════
   Layout
   ═══════════════════════════════════════════════ */
.chat-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* ═══════════════════════════════════════════════
   Sidebar (会话列表)
   ═══════════════════════════════════════════════ */
.chat-sidebar {
  width: 280px;
  min-width: 280px;
  background: #FFFFFF;
  border-right: 1px solid var(--hify-border-light);
  display: flex;
  flex-direction: column;
  box-shadow: 2px 0 12px rgba(15, 23, 42, 0.04);
}

.sidebar-top {
  padding: 20px 16px 16px;
  border-bottom: 1px solid var(--hify-border-light);
  background: #FAFBFD;
}

.sidebar-brand {
  font-size: 18px;
  font-weight: 700;
  color: var(--hify-primary);
  margin-bottom: 14px;
  letter-spacing: -0.3px;
}

.agent-picker {
  width: 100%;
  margin-bottom: 8px;
}

.sidebar-hint {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--hify-warning);
}

.new-chat-btn {
  width: 100%;
  border-radius: 8px;
  font-weight: 500;
  border: 1px dashed var(--hify-border);
  color: var(--hify-text-secondary);
  background: #FFFFFF;
  transition: all var(--hify-transition-fast);
}

.new-chat-btn:not(:disabled):hover {
  border-color: var(--hify-primary);
  color: var(--hify-primary);
  background: #F5F3FF;
}

.plus {
  font-size: 16px;
  margin-right: 2px;
}

/* 会话列表 */
.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  background: #FAFBFD;
}

.conv-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background var(--hify-transition-fast), box-shadow var(--hify-transition-fast);
  margin-bottom: 4px;
  border: 1px solid transparent;
}

.conv-item:hover {
  background: #FFFFFF;
  border-color: var(--hify-border-light);
  box-shadow: var(--hify-shadow-sm);
}

.conv-item.active {
  background: linear-gradient(135deg, var(--hify-primary) 0%, var(--hify-primary-dark) 100%);
  box-shadow: var(--hify-shadow-glow);
  border-color: transparent;
}

.conv-item.active .conv-icon,
.conv-item.active .conv-title,
.conv-item.active .conv-agent,
.conv-item.active .conv-meta {
  color: #fff;
}

.conv-item.active .conv-delete {
  color: rgba(255, 255, 255, 0.7);
}

.conv-item.active .conv-delete:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.15);
}

.conv-delete {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--hify-text-placeholder);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  opacity: 0;
  transition: opacity var(--hify-transition-fast), background var(--hify-transition-fast), color var(--hify-transition-fast);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  margin-top: 2px;
}

.conv-item:hover .conv-delete {
  opacity: 1;
}

.conv-delete:hover {
  background: #FEE2E2;
  color: var(--hify-danger);
}

.conv-icon {
  font-size: 18px;
  line-height: 1;
  padding-top: 2px;
  flex-shrink: 0;
}

.conv-body {
  flex: 1;
  min-width: 0;
}

.conv-agent {
  font-size: 11px;
  font-weight: 600;
  color: var(--hify-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 2px;
  letter-spacing: 0.02em;
}

.conv-item.active .conv-agent {
  color: rgba(255, 255, 255, 0.92);
}

.conv-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--hify-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}

.conv-meta {
  font-size: 12px;
  color: var(--hify-text-placeholder);
  display: flex;
  gap: 8px;
}

.conv-empty {
  text-align: center;
  color: var(--hify-text-placeholder);
  padding: 40px 16px;
  font-size: 13px;
}

/* ═══════════════════════════════════════════════
   Chat Main Area
   ═══════════════════════════════════════════════ */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #EEF0F5;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: #fff;
  border-bottom: 1px solid var(--hify-border-light);
  flex-shrink: 0;
}

.chat-header-label {
  font-size: 12px;
  color: var(--hify-text-placeholder);
}

.chat-header-agent {
  font-size: 13px;
  font-weight: 600;
  color: var(--hify-primary);
}

/* ── 消息滚动区 ── */
.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 32px 0;
  scroll-behavior: smooth;
  background:
    linear-gradient(180deg, #F5F6FA 0%, #EEF0F5 100%);
}

/* 空状态 */
.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 60px 20px;
}

.empty-graphic {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--hify-text-primary);
  margin-bottom: 6px;
}

.empty-hint {
  font-size: 14px;
  color: var(--hify-text-placeholder);
}

/* ── 消息行 ── */
.msg-row {
  display: flex;
  gap: 14px;
  padding: 16px 32px;
  max-width: 860px;
  margin: 0 auto;
  width: 100%;
}

.msg-row:nth-child(even) {
  background: rgba(255, 255, 255, 0.45);
  border-radius: var(--hify-radius-lg);
}

.msg-row.user {
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 32px;
  height: 32px;
  min-width: 32px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.5px;
  flex-shrink: 0;
  margin-top: 2px;
}

.msg-avatar.user {
  background: var(--hify-primary);
  color: #fff;
}

.msg-avatar.assistant {
  background: #FFFFFF;
  color: var(--hify-primary);
  border: 1px solid var(--hify-border-light);
  box-shadow: var(--hify-shadow-sm);
}

.msg-body {
  flex: 1;
  min-width: 0;
}

.msg-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--hify-text-secondary);
  margin-bottom: 4px;
  padding: 0 2px;
}

.msg-row.user .msg-label {
  text-align: right;
}

.msg-bubble {
  font-size: 14px;
  line-height: 1.65;
  padding: 12px 16px;
  border-radius: 12px;
  word-break: break-word;
}

.msg-bubble.user {
  background: linear-gradient(135deg, var(--hify-primary) 0%, var(--hify-primary-dark) 100%);
  color: #fff;
  border-bottom-right-radius: 4px;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.25);
}

.msg-bubble.assistant {
  background: #FFFFFF;
  border: 1px solid var(--hify-border-light);
  border-bottom-left-radius: 4px;
  box-shadow: var(--hify-shadow-sm);
  color: var(--hify-text-regular);
}

.msg-bubble.assistant.live {
  border-color: var(--hify-primary-light);
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.12), var(--hify-shadow-sm);
}

.msg-bubble.assistant.error {
  border-color: var(--hify-danger);
  background: #FEF2F2;
}

.error-msg {
  color: var(--hify-danger);
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.error-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  min-width: 20px;
  border-radius: 50%;
  background: var(--hify-danger);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

/* ── 加载动画（三点跳动） ── */
.loading-dots {
  display: flex;
  gap: 5px;
  align-items: center;
  padding: 2px 0;
}

.loading-dots i {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--hify-text-placeholder);
  animation: dotBounce 1.2s infinite ease-in-out;
}

.loading-dots i:nth-child(2) { animation-delay: 0.15s; }
.loading-dots i:nth-child(3) { animation-delay: 0.3s; }

@keyframes dotBounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.3; }
  40% { transform: translateY(-6px); opacity: 1; }
}

/* ── 打字光标 ── */
.typing-cursor {
  display: inline-block;
  width: 2px;
  height: 16px;
  background: var(--hify-primary);
  margin-left: 1px;
  vertical-align: text-bottom;
  animation: cursorBlink 0.7s infinite;
}

@keyframes cursorBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* ── Markdown ── */
.markdown-body {
  font-size: 14px;
  color: var(--hify-text-regular);
  line-height: 1.65;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  color: var(--hify-text-primary);
  margin: 16px 0 8px;
  font-weight: 600;
}

.markdown-body :deep(h1) { font-size: 1.4em; }
.markdown-body :deep(h2) { font-size: 1.25em; }
.markdown-body :deep(h3) { font-size: 1.1em; }

.markdown-body :deep(p) {
  margin: 0 0 10px;
}

.markdown-body :deep(a) {
  color: var(--hify-primary);
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  color: var(--hify-primary-dark);
  text-decoration: underline;
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--hify-primary);
  padding: 4px 12px;
  margin: 8px 0;
  color: var(--hify-text-secondary);
  background: #F5F3FF;
  border-radius: 0 6px 6px 0;
}

.markdown-body :deep(pre) {
  background: #F6F8FA;
  border-radius: 8px;
  margin: 8px 0;
  border: 1px solid var(--hify-border-light);
  padding: 12px;
  overflow-x: auto;
}

.markdown-body :deep(:not(pre) > code) {
  background: #F0F1F8;
  padding: 2px 6px;
  border-radius: 4px;
  color: #C026D3;
}

.markdown-body :deep(code) {
  font-size: 13px;
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(ol),
.markdown-body :deep(ul) {
  padding-left: 1.6em;
  margin: 8px 0;
  list-style-position: outside;
}

.markdown-body :deep(li) {
  display: list-item;
  margin: 6px 0;
  padding-left: 0.2em;
  line-height: 1.6;
}

.markdown-body :deep(li > p) {
  margin: 0;
  display: inline;
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--hify-border);
  padding: 6px 12px;
}

.markdown-body :deep(th) {
  background: var(--hify-bg-hover);
  color: var(--hify-text-primary);
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--hify-border);
  margin: 16px 0;
}

/* ═══════════════════════════════════════════════
   底部输入区
   ═══════════════════════════════════════════════ */
.input-area {
  padding: 16px 32px 20px;
  background: #FFFFFF;
  border-top: 1px solid var(--hify-border-light);
  box-shadow: 0 -4px 16px rgba(15, 23, 42, 0.04);
}

.input-row {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  max-width: 860px;
  margin: 0 auto;
}

.msg-input :deep(.el-textarea__inner) {
  background: #F8F9FC;
  border: 1px solid var(--hify-border);
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  padding: 10px 16px;
  color: var(--hify-text-primary);
  box-shadow: none;
  transition: border-color var(--hify-transition-fast), box-shadow var(--hify-transition-fast);
}

.msg-input :deep(.el-textarea__inner):focus {
  border-color: var(--hify-primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
  background: #FFFFFF;
}

.msg-input :deep(.el-textarea__inner)::placeholder {
  color: var(--hify-text-placeholder);
}

.action-btn {
  height: 42px;
  padding: 0 20px;
  border-radius: 10px;
  font-weight: 500;
  font-size: 14px;
  flex-shrink: 0;
  min-width: 64px;
}

.input-hint {
  text-align: center;
  font-size: 12px;
  color: var(--hify-text-placeholder);
  margin-top: 8px;
  max-width: 860px;
  margin-left: auto;
  margin-right: auto;
}
</style>
