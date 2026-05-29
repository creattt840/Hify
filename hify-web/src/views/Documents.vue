<template>
  <div class="page">
    <header class="page__header">
      <div class="page__title-block">
        <el-button text @click="goBack">
          <span style="font-size:16px">←</span>&nbsp; 返回
        </el-button>
        <h2 v-if="kbName">{{ kbName }}</h2>
        <h2 v-else>文档管理</h2>
      </div>
      <el-button type="primary" @click="uploadDialogVisible = true">上传文档</el-button>
    </header>

    <div class="page__card">
      <HifyTable ref="tableRef" :columns="columns" :api="fetchDocuments as any">
        <template #fileType="{ row }">
          <el-tag size="small" type="info">{{ row.fileType.toUpperCase() }}</el-tag>
        </template>

        <template #fileSize="{ row }">
          <span>{{ formatFileSize(row.fileSize) }}</span>
        </template>

        <template #status="{ row }">
          <span v-if="row.status === 'PROCESSING'" class="status-processing">
            <span class="processing-dot" />
            <span>处理中</span>
          </span>
          <el-tooltip
            v-else-if="row.status === 'FAILED'"
            :content="row.errorMsg || '未知错误'"
            placement="top"
            :disabled="!row.errorMsg"
          >
            <el-tag type="danger" size="small">失败</el-tag>
          </el-tooltip>
          <el-tag v-else :type="statusType(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>

        <template #chunkCount="{ row }">
          <span>{{ row.chunkCount ?? '-' }}</span>
        </template>

        <template #actions="{ row }">
          <div class="actions-cell">
            <el-button size="small" @click="handleViewChunks(row)">查看分块</el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :disabled="row.status === 'PROCESSING'"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </div>
        </template>
      </HifyTable>
    </div>

    <!-- 上传弹窗 -->
    <el-dialog
      v-model="uploadDialogVisible"
      title="上传文档"
      width="480px"
      :close-on-click-modal="false"
      @closed="uploadRef?.clearFiles()"
    >
      <el-upload
        ref="uploadRef"
        drag
        :http-request="handleUpload"
        :before-upload="beforeUpload"
        :limit="1"
        accept=".txt,.md,.pdf"
      >
        <div class="upload-placeholder">
          <p class="upload-placeholder__icon">📄</p>
          <p>将文件拖到此处，或<em>点击上传</em></p>
          <p class="upload-hint">支持 txt、md、pdf，单文件不超过 10MB</p>
        </div>
      </el-upload>
    </el-dialog>

    <!-- 分块弹窗 -->
    <el-dialog
      v-model="chunksDialogVisible"
      title="分块列表"
      width="700px"
      @closed="handleChunksDialogClosed"
    >
      <div v-if="chunksLoading" v-loading="chunksLoading" style="min-height:120px" />
      <el-empty v-else-if="chunks.length === 0" description="暂无分块数据" />
      <div v-else class="chunk-list">
        <div
          v-for="chunk in chunks"
          :key="chunk.id"
          class="chunk-item"
        >
          <div class="chunk-item__header">
            <el-tag size="small" type="info">#{{ chunk.chunkIndex }}</el-tag>
            <span class="chunk-item__tokens">{{ chunk.tokenCount }} tokens</span>
          </div>
          <div class="chunk-item__content">
            <p>{{ expandedChunks[chunk.id] ? chunk.content : truncate(chunk.content, 200) }}</p>
            <el-button
              v-if="chunk.content.length > 200"
              link
              type="primary"
              size="small"
              @click="expandedChunks[chunk.id] = !expandedChunks[chunk.id]"
            >
              {{ expandedChunks[chunk.id] ? '收起' : '展开全文' }}
            </el-button>
          </div>
        </div>
        <p class="chunk-list__summary">
          共 {{ chunks.length }} 个分块
        </p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadInstance, UploadRequestOptions } from 'element-plus'
import HifyTable from '@/components/HifyTable.vue'
import { useConfirm } from '@/components/useConfirm'
import { del } from '@/utils/request'
import {
  getKnowledgeBaseDetail,
  getDocumentList,
  uploadDocument,
  getDocumentDetail,
  getDocumentChunks,
} from '@/api/knowledge'
import type { DocumentResponse, DocumentChunkResponse } from '@/types/knowledge'
import type { PageResult } from '@/types'

const route = useRoute()
const router = useRouter()

const kbId = Number(route.params.kbId)
const kbName = ref('')

// ─────────────────────── KB 名称 ───────────────────────

async function loadKbName() {
  try {
    const res = await getKnowledgeBaseDetail(kbId)
    kbName.value = res.data?.data?.name ?? ''
  } catch {
    kbName.value = ''
  }
}

function goBack() {
  router.push('/knowledge-bases')
}

// ─────────────────────── 表格 ───────────────────────

const tableRef = ref<{ refresh: () => void }>()

const columns = [
  { prop: 'name', label: '文件名', minWidth: 180 },
  { prop: 'fileType', label: '类型', width: 70, slot: 'fileType' },
  { prop: 'fileSize', label: '大小', width: 90, slot: 'fileSize' },
  { prop: 'status', label: '状态', width: 110, slot: 'status' },
  { prop: 'chunkCount', label: '分块数', width: 90, slot: 'chunkCount' },
  { prop: 'createdAt', label: '上传时间', width: 170 },
  { prop: 'actions', label: '操作', width: 180, slot: 'actions' },
]

async function fetchDocuments(params: { page: number; pageSize: number }) {
  const res = await getDocumentList(kbId, { page: params.page, pageSize: params.pageSize })
  const body: PageResult<DocumentResponse[]> = res.data
  const list = body.data ?? []
  // 将未完成的文档加入轮询
  for (const doc of list) {
    if (doc.status === 'PENDING' || doc.status === 'PROCESSING') {
      addPollingId(doc.id)
    }
  }
  if (pollingIds.value.length > 0) ensurePolling()
  return { list, total: body.total ?? 0 }
}

// ─────────────────────── 状态标签 ───────────────────────

function statusType(status: string) {
  const map: Record<string, string> = {
    PENDING: 'info',
    DONE: 'success',
    FAILED: 'danger',
  }
  return map[status] ?? 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    PENDING: '等待中',
    DONE: '已完成',
    FAILED: '失败',
  }
  return map[status] ?? status
}

// ─────────────────────── 上传 ───────────────────────

const uploadDialogVisible = ref(false)
const uploadRef = ref<UploadInstance>()

function beforeUpload(file: File) {
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!ext || !['txt', 'md', 'pdf'].includes(ext)) {
    ElMessage.error('仅支持 txt、md、pdf 格式')
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  return true
}

async function handleUpload(options: UploadRequestOptions) {
  const formData = new FormData()
  formData.append('file', options.file)
  try {
    const res = await uploadDocument(kbId, formData)
    const doc = res.data?.data as DocumentResponse | undefined
    if (doc) {
      addPollingId(doc.id)
      ensurePolling()
    }
    options.onSuccess(res.data)
    ElMessage.success('上传成功，正在处理中')
    uploadDialogVisible.value = false
    tableRef.value?.refresh()
  } catch (e) {
    options.onError(e)
  }
}

// ─────────────────────── 轮询 ───────────────────────

const pollingIds = ref<number[]>([])
let pollingTimer: ReturnType<typeof setInterval> | null = null

function addPollingId(id: number) {
  if (!pollingIds.value.includes(id)) {
    pollingIds.value.push(id)
  }
}

function removePollingId(id: number) {
  pollingIds.value = pollingIds.value.filter(i => i !== id)
}

function ensurePolling() {
  if (pollingTimer !== null) return
  pollingTimer = setInterval(async () => {
    if (pollingIds.value.length === 0) {
      stopPolling()
      return
    }
    let changed = false
    const ids = [...pollingIds.value]
    for (const id of ids) {
      try {
        const res = await getDocumentDetail(id)
        const doc: DocumentResponse | undefined = res.data?.data
        if (doc && (doc.status === 'DONE' || doc.status === 'FAILED')) {
          removePollingId(id)
          changed = true
        }
      } catch {
        // 单次轮询失败不影响其他文档
      }
    }
    if (pollingIds.value.length === 0) {
      stopPolling()
    }
    if (changed) {
      tableRef.value?.refresh()
    }
  }, 3000)
}

function stopPolling() {
  if (pollingTimer !== null) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

// ─────────────────────── 查看分块 ───────────────────────

const chunksDialogVisible = ref(false)
const chunksLoading = ref(false)
const chunks = ref<DocumentChunkResponse[]>([])
const expandedChunks = reactive<Record<number, boolean>>({})

async function handleViewChunks(row: DocumentResponse) {
  chunksDialogVisible.value = true
  chunksLoading.value = true
  chunks.value = []
  try {
    const res = await getDocumentChunks(row.id)
    chunks.value = res.data?.data ?? []
  } catch {
    chunks.value = []
  } finally {
    chunksLoading.value = false
  }
}

function handleChunksDialogClosed() {
  Object.keys(expandedChunks).forEach(k => delete expandedChunks[k as unknown as number])
}

function truncate(text: string, maxLen: number): string {
  if (!text) return ''
  return text.length <= maxLen ? text : text.substring(0, maxLen) + '…'
}

// ─────────────────────── 删除 ───────────────────────

const { confirm } = useConfirm()

function handleDelete(row: DocumentResponse) {
  confirm(
    { message: `确定删除文档「${row.name}」吗？关联的分块数据也会一并删除。` },
    () => del(`/v1/documents/${row.id}`),
    () => tableRef.value?.refresh(),
  )
}

// ─────────────────────── 工具 ───────────────────────

function formatFileSize(bytes: number): string {
  if (bytes == null) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

// ─────────────────────── 生命周期 ───────────────────────

onMounted(() => {
  loadKbName()
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<style scoped>
.page__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--hify-spacing-lg);
  gap: var(--hify-spacing-base);
}

.page__title-block {
  display: flex;
  align-items: center;
  gap: var(--hify-spacing-base);
}

.page__title-block h2 {
  margin: 0;
  font-size: var(--hify-font-size-xxl);
  font-weight: 600;
  color: var(--hify-text-primary);
}

.page__card {
  background: var(--hify-bg-container);
  border: 1px solid var(--hify-border);
  border-radius: var(--hify-radius-lg);
  padding: var(--hify-spacing-base);
  box-shadow: var(--hify-shadow-sm);
}

/* ── 状态标签 ── */

.status-processing {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  font-size: 12px;
  border-radius: 4px;
  color: var(--hify-primary);
  background: var(--hify-primary-light, #e8f4fd);
  border: 1px solid var(--hify-primary-light-border, rgba(64, 158, 255, 0.2));
}

.processing-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--hify-primary);
  animation: processing-pulse 1.4s ease-in-out infinite;
}

@keyframes processing-pulse {
  0%, 100% { opacity: 0.25; transform: scale(0.85); }
  50% { opacity: 1; transform: scale(1.1); }
}

/* ── 上传 ── */

.upload-placeholder {
  padding: 16px 0;
  text-align: center;
  color: var(--hify-text-secondary);
}

.upload-placeholder__icon {
  font-size: 36px;
  margin-bottom: 8px;
}

.upload-placeholder em {
  color: var(--hify-primary);
  font-style: normal;
}

.upload-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--hify-text-placeholder);
}

/* ── 分块列表 ── */

.chunk-list {
  max-height: 480px;
  overflow-y: auto;
}

.chunk-item {
  border: 1px solid var(--hify-border);
  border-radius: var(--hify-radius-base, 6px);
  padding: 12px;
  margin-bottom: 10px;
}

.chunk-item__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.chunk-item__tokens {
  font-size: 12px;
  color: var(--hify-text-placeholder);
}

.chunk-item__content {
  font-size: 13px;
  line-height: 1.65;
  color: var(--hify-text-regular);
  word-break: break-word;
}

.chunk-item__content p {
  margin: 0 0 4px;
}

.chunk-list__summary {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--hify-text-secondary);
  text-align: center;
}

/* ── 操作列 ── */

.actions-cell {
  display: flex;
  gap: 4px;
}
</style>
