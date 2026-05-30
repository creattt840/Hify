<template>
  <div class="page">
    <header class="page__header">
      <div class="page__title-block">
        <h2>MCP 服务</h2>
        <p class="page__desc">管理 MCP 工具服务器，提供 Agent 可调用的外部工具能力</p>
      </div>
      <el-button type="primary" @click="handleCreate">新增服务</el-button>
    </header>

    <div class="page__card">
      <HifyTable
        ref="tableRef"
        :columns="columns"
        :api="fetchList as any"
      >
        <template #isEnabled="{ row }">
          <el-tag :type="row.isEnabled ? 'success' : 'info'" size="small">
            {{ row.isEnabled ? '启用' : '禁用' }}
          </el-tag>
        </template>

        <template #actions="{ row }">
          <div class="actions-cell">
            <div class="actions-cell__group">
              <el-button link type="primary" size="small" @click="handleDetail(row)">
                详情
              </el-button>
              <span class="actions-cell__sep" />
              <el-button link type="primary" size="small" @click="handleEdit(row)">
                编辑
              </el-button>
            </div>
            <el-button
              size="small"
              type="primary"
              plain
              :loading="testingMap[row.id]"
              @click="handleTest(row)"
            >
              测试
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </div>
        </template>
      </HifyTable>
    </div>

    <!-- 创建/编辑对话框 -->
    <HifyFormDialog
      ref="dialogRef"
      v-model="dialogVisible"
      title="MCP 服务"
      :rules="formRules"
      @submit="(d) => handleSubmit(d as unknown as McpFormData)"
    >
      <template #default="{ data }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="data.name" placeholder="如 退款服务" />
        </el-form-item>
        <el-form-item label="端点" prop="endpoint">
          <el-input
            v-model="data.endpoint"
            placeholder="Streamable HTTP: http://127.0.0.1:8082/mcp；SSE: https://xxx/sse"
          />
        </el-form-item>
        <el-form-item label="启用" prop="isEnabled">
          <el-switch v-model="data.isEnabled" />
        </el-form-item>
      </template>
    </HifyFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormRules } from 'element-plus'
import HifyTable from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import { useConfirm } from '@/components/useConfirm'
import { post, put, del } from '@/utils/request'
import { getMcpServerList, testMcpConnection } from '@/api/mcp'
import type { McpServerListItemResponse } from '@/types/mcp'

const router = useRouter()
const tableRef = ref<{ refresh: () => void }>()

const columns = [
  { prop: 'name', label: '名称', minWidth: 140 },
  { prop: 'endpoint', label: '端点', minWidth: 240 },
  { prop: 'isEnabled', label: '状态', width: 80, slot: 'isEnabled' },
  { prop: 'toolCount', label: '工具数', width: 80 },
  { prop: 'actions', label: '操作', width: 220, slot: 'actions' },
]

async function fetchList(params: { page: number; pageSize: number }) {
  const res = await getMcpServerList({ page: params.page, pageSize: params.pageSize })
  const body = res.data
  // 兼容 PageResult 直接返回 / Result 包裹 PageResult 两种格式
  const page = body.total != null ? body : (body.data as typeof body)
  return { list: page.data ?? [], total: page.total ?? 0 }
}

interface McpFormData {
  name: string
  endpoint: string
  isEnabled: boolean
}

const dialogVisible = ref(false)
const dialogRef = ref<{ open: (data?: McpFormData) => void }>()
const editingId = ref<number | null>(null)

const formRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  endpoint: [{ required: true, message: '请输入端点地址', trigger: 'blur' }],
}

function defaultForm(): McpFormData {
  return { name: '', endpoint: '', isEnabled: true }
}

function openDialog(data?: McpFormData) {
  dialogVisible.value = true
  setTimeout(() => dialogRef.value?.open(data ?? defaultForm()))
}

function handleCreate() {
  editingId.value = null
  openDialog()
}

function handleDetail(row: McpServerListItemResponse) {
  router.push(`/mcp-servers/${row.id}`)
}

function handleEdit(row: McpServerListItemResponse) {
  editingId.value = row.id
  openDialog({
    name: row.name,
    endpoint: row.endpoint,
    isEnabled: row.isEnabled,
  })
}

async function handleSubmit(data: McpFormData) {
  const payload = {
    name: data.name,
    endpoint: data.endpoint,
    isEnabled: data.isEnabled,
  }
  if (editingId.value) {
    await put(`/v1/mcp-servers/${editingId.value}`, payload)
  } else {
    await post('/v1/mcp-servers', payload)
  }
  dialogVisible.value = false
  tableRef.value?.refresh()
  ElMessage.success(editingId.value ? '保存成功' : '创建成功')
}

const { confirm } = useConfirm()
function handleDelete(row: McpServerListItemResponse) {
  confirm(
    { message: `确定删除「${row.name}」吗？` },
    () => del(`/v1/mcp-servers/${row.id}`),
    () => tableRef.value?.refresh(),
  )
}

const testingMap = reactive<Record<number, boolean>>({})
async function handleTest(row: McpServerListItemResponse) {
  if (testingMap[row.id]) return
  testingMap[row.id] = true
  try {
    const r = await testMcpConnection(row.id)
    if (r.success) {
      ElMessage.success(`连接成功！发现 ${r.toolCount} 个工具`)
    } else {
      ElMessage.warning(`连接失败：${r.message}`)
    }
    tableRef.value?.refresh()
  } catch {
    // 拦截器已提示
  } finally {
    testingMap[row.id] = false
  }
}
</script>

<style scoped>
.page__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--hify-spacing-lg);
  gap: var(--hify-spacing-base);
}

.page__title-block h2 {
  margin: 0 0 4px;
  font-size: var(--hify-font-size-xxl);
  font-weight: 600;
  color: var(--hify-text-primary);
}

.page__desc {
  margin: 0;
  font-size: var(--hify-font-size-sm);
  color: var(--hify-text-secondary);
}

.page__card {
  background: var(--hify-bg-container);
  border: 1px solid var(--hify-border);
  border-radius: var(--hify-radius-lg);
  padding: var(--hify-spacing-base);
  box-shadow: var(--hify-shadow-sm);
}

.actions-cell {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.actions-cell__group {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.actions-cell__sep {
  width: 1px;
  height: 14px;
  background: var(--hify-border);
  flex-shrink: 0;
}

.actions-cell :deep(.el-button) {
  padding: 4px 6px;
  margin: 0;
  height: auto;
}

.actions-cell :deep(.el-button.is-plain) {
  padding: 5px 12px;
}
</style>
