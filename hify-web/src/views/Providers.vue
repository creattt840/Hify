<template>
  <div class="page">
    <header class="page__header">
      <div class="page__title-block">
        <h2>模型管理</h2>
        <p class="page__desc">配置 LLM 提供商、API 密钥与模型 ID，支持连通性测试</p>
      </div>
      <el-button type="primary" @click="handleCreate">新增提供商</el-button>
    </header>

    <div class="page__card">
      <HifyTable
        ref="tableRef"
        :columns="columns"
        :api="fetchProviders as any"
      >
        <template #providerType="{ row }">
          <el-tag size="small" type="info">{{ providerTypeLabel(row.providerType) }}</el-tag>
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
            <el-button
              size="small"
              type="warning"
              plain
              :loading="testingMap[row.id]"
              @click="handleTestConnection(row)"
            >
              测试连接
            </el-button>
            <el-button size="small" type="danger" plain @click="handleDelete(row)">
              删除
            </el-button>
          </div>
        </template>
      </HifyTable>
    </div>

    <HifyFormDialog
      ref="dialogRef"
      v-model="dialogVisible"
      title="提供商"
      :rules="formRules"
      @submit="(d) => handleSubmit(d as unknown as ProviderFormData)"
    >
      <template #default="{ data }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="data.name" placeholder="如 DeepSeek" />
        </el-form-item>

        <el-form-item label="类型" prop="providerType">
          <el-select v-model="data.providerType" placeholder="请选择提供商类型" style="width: 100%">
            <el-option label="OpenAI" value="openai" />
            <el-option label="OpenAI Compatible" value="openai_compatible" />
            <el-option label="Anthropic" value="anthropic" />
            <el-option label="Gemini" value="gemini" />
            <el-option label="Ollama" value="ollama" />
          </el-select>
        </el-form-item>

        <el-form-item label="API 地址" prop="baseUrl">
          <el-input v-model="data.baseUrl" :placeholder="baseUrlPlaceholder(String(data.providerType))" />
          <p class="form-hint">{{ baseUrlHint(String(data.providerType)) }}</p>
        </el-form-item>

        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="data.apiKey"
            type="password"
            show-password
            :placeholder="editingId ? '留空则保持原密钥不变' : 'sk-xxx'"
          />
        </el-form-item>

        <el-form-item label="模型 ID" prop="modelId">
          <el-input v-model="data.modelId" placeholder="如 deepseek-chat" />
          <p class="form-hint">保存后可在 Agent 管理中绑定此模型</p>
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
import { get, post, put, del } from '@/utils/request'
import { getProviderList } from '@/api/provider'
import type { ProviderResponse, ProviderHealthResponse } from '@/types/provider'
import type { PageResult } from '@/types'

const tableRef = ref<{ refresh: () => void }>()

const columns = [
  { prop: 'name', label: '名称', minWidth: 140 },
  { prop: 'providerType', label: '类型', width: 150, slot: 'providerType' },
  { prop: 'baseUrl', label: 'API 地址', minWidth: 200 },
  { prop: 'isEnabled', label: '状态', width: 80, slot: 'isEnabled' },
  { prop: 'health', label: '健康状态', width: 150, slot: 'health' },
  { prop: 'modelCount', label: '模型数', width: 80 },
  { prop: 'actions', label: '操作', width: 240, slot: 'actions' },
]

const providerTypeLabels: Record<string, string> = {
  openai: 'OpenAI',
  openai_compatible: 'OpenAI Compatible',
  anthropic: 'Anthropic',
  gemini: 'Gemini',
  ollama: 'Ollama',
}

function providerTypeLabel(type: string): string {
  return providerTypeLabels[type] ?? type
}

const baseUrlHints: Record<string, { placeholder: string; hint: string }> = {
  openai: {
    placeholder: 'https://api.openai.com/v1',
    hint: 'OpenAI 官方地址，通常以 /v1 结尾',
  },
  openai_compatible: {
    placeholder: 'https://api.deepseek.com',
    hint: 'DeepSeek 等兼容 OpenAI 的服务请选此项，地址不要带 /anthropic',
  },
  anthropic: {
    placeholder: 'https://api.anthropic.com',
    hint: '仅用于 Anthropic 官方 API；DeepSeek 请选 OpenAI Compatible',
  },
  gemini: {
    placeholder: 'https://generativelanguage.googleapis.com/v1beta',
    hint: 'Gemini 官方 API 根地址',
  },
  ollama: {
    placeholder: 'http://localhost:11434',
    hint: '本地 Ollama 服务地址，无需 API Key',
  },
}

function baseUrlPlaceholder(type: string): string {
  return baseUrlHints[type]?.placeholder ?? 'https://api.example.com'
}

function baseUrlHint(type: string): string {
  return baseUrlHints[type]?.hint ?? ''
}

function readApiKey(authConfig?: Record<string, unknown> | null): string {
  if (!authConfig) return ''
  const key = authConfig.apiKey ?? authConfig.api_key
  return key != null ? String(key) : ''
}

async function fetchProviders(params: { page: number; pageSize: number }) {
  const res = await getProviderList({ page: params.page, pageSize: params.pageSize })
  const body: PageResult<ProviderResponse[]> = res.data
  return { list: body.data ?? [], total: body.total ?? 0 }
}

function healthTag(health?: ProviderHealthResponse | null): { type: string; text: string } {
  if (!health) return { type: 'info', text: '未测试' }
  if (health.failCount === 0 && health.lastSuccessAt) return { type: 'success', text: '正常' }
  if (health.failCount >= 3) return { type: 'danger', text: '异常' }
  if (health.failCount > 0) return { type: 'warning', text: '降级' }
  return { type: 'info', text: '未知' }
}

interface ProviderFormData {
  name: string
  providerType: string
  baseUrl: string
  apiKey: string
  modelId: string
  isEnabled: boolean
}

const dialogVisible = ref(false)
const dialogRef = ref<{ open: (data?: ProviderFormData) => void }>()
const editingId = ref<number | null>(null)

const formRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  providerType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  modelId: [{ required: true, message: '请输入模型 ID', trigger: 'blur' }],
}

const defaultFormData = (): ProviderFormData => ({
  name: '',
  providerType: 'openai_compatible',
  baseUrl: '',
  apiKey: '',
  modelId: '',
  isEnabled: true,
})

function openDialog(data?: ProviderFormData) {
  dialogVisible.value = true
  setTimeout(() => {
    dialogRef.value?.open(data ?? defaultFormData())
  })
}

function handleCreate() {
  editingId.value = null
  openDialog()
}

async function handleEdit(row: ProviderResponse) {
  editingId.value = row.id
  const configs = await get<{ modelId: string }[]>(`/v1/providers/${row.id}/model-configs`)
  openDialog({
    name: row.name,
    providerType: row.providerType,
    baseUrl: row.baseUrl,
    apiKey: readApiKey(row.authConfig),
    modelId: configs.length > 0 ? configs[0].modelId : '',
    isEnabled: row.isEnabled,
  })
}

async function handleSubmit(data: ProviderFormData) {
  const payload = {
    name: data.name,
    providerType: data.providerType,
    baseUrl: data.baseUrl || '',
    authConfig: data.apiKey ? { apiKey: data.apiKey } : {},
    isEnabled: data.isEnabled,
  }

  try {
    let providerId = editingId.value
    if (editingId.value) {
      await put(`/v1/providers/${editingId.value}`, payload)
    } else {
      if (!data.apiKey && data.providerType !== 'ollama') {
        ElMessage.warning('请填写 API Key')
        return
      }
      const created = await post<{ id: number }>('/v1/providers', payload)
      providerId = created.id
    }

    if (data.modelId && providerId) {
      const configs = await get<{ id: number }[]>(`/v1/providers/${providerId}/model-configs`)
      if (configs.length > 0) {
        await put(`/v1/providers/${providerId}/model-configs/${configs[0].id}`, {
          name: data.name,
          modelId: data.modelId,
        })
      } else {
        await post(`/v1/providers/${providerId}/model-configs`, {
          name: data.name,
          modelId: data.modelId,
        })
      }
    }

    dialogVisible.value = false
    tableRef.value?.refresh()
    ElMessage.success(editingId.value ? '保存成功' : '创建成功')
  } catch {
    // request.ts 拦截器已弹错误提示
  }
}

const { confirm } = useConfirm()

function handleDelete(row: ProviderResponse) {
  confirm(
    { message: `确定删除提供商「${row.name}」吗？删除后数据不可恢复。` },
    () => del(`/v1/providers/${row.id}`),
    () => tableRef.value?.refresh(),
  )
}

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

.health-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.health-latency {
  font-size: 12px;
  color: var(--hify-text-secondary);
}

.form-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--hify-text-secondary);
}

.actions-cell {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
</style>
