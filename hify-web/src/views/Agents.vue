<template>
  <div class="page">
    <header class="page__header">
      <div class="page__title-block">
        <h2>Agent 管理</h2>
        <p class="page__desc">配置智能体角色、系统提示词，并绑定已启用的模型</p>
      </div>
      <el-button type="primary" @click="handleCreate">新增 Agent</el-button>
    </header>

    <div class="page__card">
      <HifyTable ref="tableRef" :columns="columns" :api="fetchAgents as any">
        <template #isEnabled="{ row }">
          <el-tag :type="row.isEnabled ? 'success' : 'info'" size="small">
            {{ row.isEnabled ? '启用' : '禁用' }}
          </el-tag>
        </template>

        <template #model="{ row }">
          <span v-if="row.modelName" class="model-cell">
            <span class="model-cell__name">{{ row.modelName }}</span>
            <span class="model-cell__id">{{ row.modelId }}</span>
          </span>
          <span v-else class="text-muted">未绑定</span>
        </template>

        <template #temperature="{ row }">
          <el-tag size="small" type="info">{{ row.temperature ?? 0.7 }}</el-tag>
        </template>

        <template #actions="{ row }">
          <div class="actions-cell">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
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
      title="Agent"
      width="640px"
      :rules="formRules"
      @submit="(d) => handleSubmit(d as unknown as AgentFormData)"
    >
      <template #default="{ data }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="data.name" placeholder="如 客服 Agent" />
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input v-model="data.description" placeholder="简要描述 Agent 用途" />
        </el-form-item>

        <el-form-item label="系统提示词" prop="systemPrompt">
          <el-input
            v-model="data.systemPrompt"
            type="textarea"
            :rows="4"
            placeholder="定义 Agent 的行为和角色"
          />
        </el-form-item>

        <el-form-item label="绑定模型" prop="modelConfigId">
          <el-select
            v-model="data.modelConfigId"
            placeholder="请选择模型"
            filterable
            style="width: 100%"
            :loading="modelConfigsLoading"
            :disabled="modelConfigOptions.length === 0"
          >
            <el-option
              v-for="mc in modelConfigOptions"
              :key="mc.id"
              :label="modelOptionLabel(mc)"
              :value="mc.id"
            />
          </el-select>
          <p v-if="!modelConfigsLoading && modelConfigOptions.length === 0" class="form-hint form-hint--warn">
            请先在「模型管理」中启用提供商并配置模型 ID
          </p>
        </el-form-item>

        <el-form-item label="温度" prop="temperature">
          <el-slider
            v-model="data.temperature"
            :min="0"
            :max="2"
            :step="0.1"
            show-input
            :show-input-controls="false"
          />
        </el-form-item>

        <el-form-item label="MCP 工具" prop="toolIds">
          <McpToolPicker
            :model-value="Array.isArray(data.toolIds) ? data.toolIds : []"
            :tools="mcpToolOptions"
            :loading="mcpToolsLoading"
            :max="MAX_MCP_TOOLS"
            @update:model-value="(v) => { data.toolIds = v }"
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
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormRules } from 'element-plus'
import HifyTable from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import McpToolPicker from '@/components/McpToolPicker.vue'
import { useConfirm } from '@/components/useConfirm'
import { post, put, del } from '@/utils/request'
import { getAgentList, bindAgentTools, getAgentBoundTools } from '@/api/agent'
import { listModelConfigs } from '@/api/provider'
import { getAvailableMcpTools } from '@/api/mcp'
import type { AgentResponse } from '@/types/agent'
import type { ModelConfigResponse } from '@/types/provider'
import type { McpToolOption } from '@/types/mcp'
import type { PageResult } from '@/types'

const MAX_MCP_TOOLS = 10

const modelConfigOptions = ref<ModelConfigResponse[]>([])
const modelConfigsLoading = ref(false)
const mcpToolOptions = ref<McpToolOption[]>([])
const mcpToolsLoading = ref(false)

function modelOptionLabel(mc: ModelConfigResponse): string {
  const provider = mc.providerName ? `${mc.providerName} · ` : ''
  return `${provider}${mc.name} (${mc.modelId})`
}

async function loadModelConfigs() {
  modelConfigsLoading.value = true
  try {
    const res = await listModelConfigs()
    modelConfigOptions.value = res.data?.data ?? []
  } catch {
    modelConfigOptions.value = []
  } finally {
    modelConfigsLoading.value = false
  }
}

async function loadMcpTools() {
  mcpToolsLoading.value = true
  try {
    mcpToolOptions.value = await getAvailableMcpTools()
  } finally {
    mcpToolsLoading.value = false
  }
}

// ─────────────────────── 表格 ───────────────────────

const tableRef = ref<{ refresh: () => void }>()

const columns = [
  { prop: 'name', label: '名称', minWidth: 140 },
  { prop: 'description', label: '描述', minWidth: 160 },
  { prop: 'model', label: '绑定模型', minWidth: 200, slot: 'model' },
  { prop: 'temperature', label: '温度', width: 90, slot: 'temperature' },
  { prop: 'isEnabled', label: '状态', width: 80, slot: 'isEnabled' },
  { prop: 'actions', label: '操作', width: 160, slot: 'actions' },
]

async function fetchAgents(params: { page: number; pageSize: number }) {
  const res = await getAgentList({ page: params.page, pageSize: params.pageSize })
  const body: PageResult<AgentResponse[]> = res.data
  return { list: body.data ?? [], total: body.total ?? 0 }
}

// ─────────────────────── 弹窗表单 ───────────────────────

interface AgentFormData {
  name: string
  description: string
  systemPrompt: string
  modelConfigId: number | null
  temperature: number
  isEnabled: boolean
  toolIds: number[]
}

const dialogVisible = ref(false)
const dialogRef = ref<{ open: (data?: AgentFormData) => void }>()
const editingId = ref<number | null>(null)

const formRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  systemPrompt: [{ required: true, message: '请输入系统提示词', trigger: 'blur' }],
  modelConfigId: [{ required: true, message: '请选择绑定模型', trigger: 'change' }],
}

const defaultFormData = (): AgentFormData => ({
  name: '',
  description: '',
  systemPrompt: '',
  modelConfigId: null,
  temperature: 0.7,
  isEnabled: true,
  toolIds: [],
})

async function openDialog(data?: AgentFormData) {
  await Promise.all([loadModelConfigs(), loadMcpTools()])
  dialogVisible.value = true
  setTimeout(async () => {
    const initial = data ? { ...data, toolIds: [...(data.toolIds ?? [])] } : defaultFormData()
    if (!data && modelConfigOptions.value.length > 0) {
      initial.modelConfigId = modelConfigOptions.value[0].id
    }
    if (data && editingId.value) {
      const bound = await getAgentBoundTools(editingId.value)
      const availableIds = new Set(mcpToolOptions.value.map((t) => t.id))
      initial.toolIds = bound.filter((id) => availableIds.has(id))
    }
    dialogRef.value?.open(initial)
  })
}

function handleCreate() {
  editingId.value = null
  openDialog()
}

function handleEdit(row: AgentResponse) {
  editingId.value = row.id
  openDialog({
    name: row.name,
    description: row.description,
    systemPrompt: row.systemPrompt,
    modelConfigId: row.modelConfigId,
    temperature: row.temperature ?? 0.7,
    isEnabled: row.isEnabled,
    toolIds: [],
  })
}

async function handleSubmit(data: AgentFormData) {
  if (!data.modelConfigId) {
    ElMessage.warning('请选择绑定模型')
    return
  }
  if ((data.toolIds ?? []).length > MAX_MCP_TOOLS) {
    ElMessage.warning(`最多只能绑定 ${MAX_MCP_TOOLS} 个 MCP 工具`)
    return
  }

  const payload = {
    name: data.name,
    description: data.description || '',
    systemPrompt: data.systemPrompt,
    modelConfigId: data.modelConfigId,
    temperature: data.temperature,
    isEnabled: data.isEnabled,
  }

  try {
    let agentId = editingId.value
    if (agentId) {
      await put(`/v1/agents/${agentId}`, payload)
    } else {
      const created = await post<AgentResponse>('/v1/agents', payload)
      agentId = created.id
    }
    await bindAgentTools(agentId!, data.toolIds ?? [])
    dialogVisible.value = false
    tableRef.value?.refresh()
    ElMessage.success(editingId.value ? '保存成功' : '创建成功')
  } catch {
    // request.ts interceptor shows error message
  }
}

// ─────────────────────── 删除 ───────────────────────

const { confirm } = useConfirm()

function handleDelete(row: AgentResponse) {
  confirm(
    { message: `确定删除 Agent「${row.name}」吗？删除后数据不可恢复。` },
    () => del(`/v1/agents/${row.id}`),
    () => tableRef.value?.refresh(),
  )
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

.model-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.model-cell__name {
  font-weight: 500;
  color: var(--hify-text-primary);
}

.model-cell__id {
  font-size: 12px;
  color: var(--hify-text-secondary);
}

.text-muted {
  color: var(--hify-text-placeholder);
  font-size: 13px;
}

.form-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--hify-text-secondary);
}

.form-hint--warn {
  color: var(--hify-warning);
}

.actions-cell {
  display: flex;
  gap: 4px;
}
</style>
