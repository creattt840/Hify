<template>
  <div class="page">
    <div class="page__header">
      <h2>模型管理</h2>
      <el-button type="primary" @click="handleCreate">新增提供商</el-button>
    </div>

    <HifyTable
      ref="tableRef"
      :columns="columns"
      :api="fetchProviders"
    >
      <template #providerType="{ row }">
        <el-tag size="small">{{ row.providerType }}</el-tag>
      </template>

      <template #isEnabled="{ row }">
        <el-tag :type="row.isEnabled ? 'success' : 'info'" size="small">
          {{ row.isEnabled ? '启用' : '禁用' }}
        </el-tag>
      </template>

      <template #health="{ row }">
        <div class="health-cell">
          <el-tag :type="healthTag(row.health).type" size="small">
            {{ healthTag(row.health).text }}
          </el-tag>
          <span v-if="row.health?.latencyMs != null" class="health-latency">
            {{ row.health.latencyMs }}ms
          </span>
        </div>
      </template>

      <template #actions="{ row }">
        <div class="actions-cell">
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" type="warning" plain @click="handleTestConnection(row)">
            测试连接
          </el-button>
          <el-button size="small" type="danger" plain @click="handleDelete(row)">
            删除
          </el-button>
        </div>
      </template>
    </HifyTable>

    <!-- 新增 / 编辑弹窗 -->
    <HifyFormDialog
      ref="dialogRef"
      v-model="dialogVisible"
      title="提供商"
      :rules="formRules"
      @submit="handleSubmit"
    >
      <template #default="{ data }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="data.name" placeholder="如 My OpenAI" />
        </el-form-item>

        <el-form-item label="类型" prop="providerType">
          <el-select v-model="data.providerType" placeholder="请选择提供商类型">
            <el-option label="OpenAI" value="openai" />
            <el-option label="OpenAI Compatible" value="openai_compatible" />
            <el-option label="Anthropic" value="anthropic" />
            <el-option label="Gemini" value="gemini" />
            <el-option label="Ollama" value="ollama" />
          </el-select>
        </el-form-item>

        <el-form-item label="API 地址" prop="baseUrl">
          <el-input v-model="data.baseUrl" placeholder="https://api.openai.com" />
        </el-form-item>

        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="data.apiKey"
            type="password"
            show-password
            placeholder="sk-xxx"
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
import { ElMessage } from 'element-plus'
import type { FormRules } from 'element-plus'
import HifyTable from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import { useConfirm } from '@/components/useConfirm'
import { notifySuccess } from '@/components/notify'
import { get, post, put, del } from '@/utils/request'
import { getProviderList } from '@/api/provider'
import type { ProviderResponse, ProviderHealthResponse } from '@/types/provider'
import type { PageResult } from '@/types'

// ─────────────────────── 表格 ───────────────────────

const tableRef = ref<InstanceType<typeof HifyTable>>()

const columns = [
  { prop: 'name', label: '名称', minWidth: 140 },
  { prop: 'providerType', label: '类型', width: 130, slot: 'providerType' },
  { prop: 'baseUrl', label: 'API 地址', minWidth: 200 },
  { prop: 'isEnabled', label: '状态', width: 80, slot: 'isEnabled' },
  { prop: 'health', label: '健康状态', width: 140, slot: 'health' },
  { prop: 'modelCount', label: '模型数', width: 80 },
  { prop: 'actions', label: '操作', width: 260, slot: 'actions' },
]

/**
 * 适配 getProviderList 返回值为 HifyTable 期望的 { list, total } 格式
 */
async function fetchProviders(params: { page: number; pageSize: number }) {
  const res = await getProviderList({ page: params.page, pageSize: params.pageSize })
  const body: PageResult<ProviderResponse[]> = res.data
  return { list: body.data ?? [], total: body.total ?? 0 }
}

// ─────────────────────── 健康状态 ───────────────────────

type HealthStatus = 'UP' | 'DOWN' | 'DEGRADED' | 'UNKNOWN'

function healthTag(health?: ProviderHealthResponse | null): { type: string; text: string } {
  if (!health) return { type: 'info', text: '未测试' }
  if (health.failCount === 0 && health.lastSuccessAt) return { type: 'success', text: '正常' }
  if (health.failCount >= 3) return { type: 'danger', text: '异常' }
  if (health.failCount > 0) return { type: 'warning', text: '降级' }
  return { type: 'info', text: '未知' }
}

// ─────────────────────── 弹窗表单 ───────────────────────

interface ProviderFormData {
  name: string
  providerType: string
  baseUrl: string
  apiKey: string
  isEnabled: boolean
}

const dialogVisible = ref(false)
const dialogRef = ref<InstanceType<typeof HifyFormDialog>>()
const editingId = ref<number | null>(null)

const formRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  providerType: [{ required: true, message: '请选择类型', trigger: 'change' }],
}

function handleCreate() {
  editingId.value = null
  dialogVisible.value = true
}

function handleEdit(row: ProviderResponse) {
  editingId.value = row.id
  dialogVisible.value = true
  // 下一帧填充编辑数据（等 dialog watch 初始化 formData 后）
  setTimeout(() => {
    dialogRef.value?.open({
      name: row.name,
      providerType: row.providerType,
      baseUrl: row.baseUrl,
      apiKey: (row.authConfig?.apiKey as string) || '',
      isEnabled: row.isEnabled,
    } as ProviderFormData)
  })
}

async function handleSubmit(data: ProviderFormData) {
  // 当前 HifyFormDialog 的 submit emit 不等待异步，
  // 这里手动控制：提交失败时 dialog 保持打开，调用方可感知
  const payload = {
    name: data.name,
    providerType: data.providerType,
    baseUrl: data.baseUrl || '',
    authConfig: data.apiKey ? { apiKey: data.apiKey } : {},
    isEnabled: data.isEnabled,
  }

  try {
    if (editingId.value) {
      await put(`/v1/providers/${editingId.value}`, payload)
    } else {
      await post('/v1/providers', payload)
    }
    dialogVisible.value = false
    tableRef.value?.refresh()
  } catch {
    // request.ts 拦截器已弹错误提示，这里仅阻止关闭弹窗
  }
}

// ─────────────────────── 删除 ───────────────────────

const { confirm } = useConfirm()

function handleDelete(row: ProviderResponse) {
  confirm(
    { message: `确定删除提供商「${row.name}」吗？删除后数据不可恢复。` },
    () => del(`/v1/providers/${row.id}`),
    () => tableRef.value?.refresh(),
  )
}

// ─────────────────────── 连通性测试 ───────────────────────

const testingMap = reactive<Record<number, boolean>>({})

async function handleTestConnection(row: ProviderResponse) {
  if (testingMap[row.id]) return
  testingMap[row.id] = true
  try {
    const result = await post<{
      success: boolean
      latencyMs: number
      modelCount: number
      errorMessage: string | null
    }>(`/v1/providers/${row.id}/test-connection`)

    if (result.success) {
      ElMessage.success(`连接成功！延迟 ${result.latencyMs}ms，可用模型 ${result.modelCount} 个`)
    } else {
      ElMessage.warning(`连接失败：${result.errorMessage}`)
    }
    tableRef.value?.refresh()
  } catch {
    // request.ts 拦截器已弹错误提示
  } finally {
    testingMap[row.id] = false
  }
}
</script>

<style scoped>
.page__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--hify-spacing-base);
}

.page__header h2 {
  margin: 0;
}

.health-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.health-latency {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.actions-cell {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
</style>
