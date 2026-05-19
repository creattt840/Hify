<template>
  <div class="page">
    <div class="page__header">
      <h2>Agent 管理</h2>
      <el-button type="primary" @click="handleCreate">新增 Agent</el-button>
    </div>

    <HifyTable ref="tableRef" :columns="columns" :api="fetchAgents">
      <template #isEnabled="{ row }">
        <el-tag :type="row.isEnabled ? 'success' : 'info'" size="small">
          {{ row.isEnabled ? '启用' : '禁用' }}
        </el-tag>
      </template>

      <template #model="{ row }">
        <span>{{ row.modelName }} ({{ row.modelId }})</span>
      </template>

      <template #temperature="{ row }">
        <span>{{ row.temperature ?? 0.7 }}</span>
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

    <!-- 新增 / 编辑弹窗 -->
    <HifyFormDialog
      ref="dialogRef"
      v-model="dialogVisible"
      title="Agent"
      :rules="formRules"
      @submit="handleSubmit"
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
          <el-input-number v-model="data.modelConfigId" :min="1" placeholder="模型配置 ID" />
        </el-form-item>

        <el-form-item label="温度" prop="temperature">
          <el-slider v-model="data.temperature" :min="0" :max="2" :step="0.1" show-input :show-input-controls="false" />
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
import type { FormRules } from 'element-plus'
import HifyTable from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import { useConfirm } from '@/components/useConfirm'
import { get, post, put, del } from '@/utils/request'
import { getAgentList } from '@/api/agent'
import type { AgentResponse } from '@/types/agent'
import type { PageResult } from '@/types'

// ─────────────────────── 表格 ───────────────────────

const tableRef = ref<InstanceType<typeof HifyTable>>()

const columns = [
  { prop: 'name', label: '名称', minWidth: 140 },
  { prop: 'description', label: '描述', minWidth: 160 },
  { prop: 'model', label: '绑定模型', minWidth: 180, slot: 'model' },
  { prop: 'temperature', label: '温度', width: 80, slot: 'temperature' },
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
}

const dialogVisible = ref(false)
const dialogRef = ref<InstanceType<typeof HifyFormDialog>>()
const editingId = ref<number | null>(null)

const formRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  systemPrompt: [{ required: true, message: '请输入系统提示词', trigger: 'blur' }],
  modelConfigId: [{ required: true, message: '请输入模型配置 ID', trigger: 'blur' }],
}

function handleCreate() {
  editingId.value = null
  dialogVisible.value = true
}

function handleEdit(row: AgentResponse) {
  editingId.value = row.id
  dialogVisible.value = true
  setTimeout(() => {
    dialogRef.value?.open({
      name: row.name,
      description: row.description,
      systemPrompt: row.systemPrompt,
      modelConfigId: row.modelConfigId,
      temperature: row.temperature ?? 0.7,
      isEnabled: row.isEnabled,
    } as AgentFormData)
  })
}

async function handleSubmit(data: AgentFormData) {
  const payload = {
    name: data.name,
    description: data.description || '',
    systemPrompt: data.systemPrompt,
    modelConfigId: data.modelConfigId as number,
    temperature: data.temperature,
    isEnabled: data.isEnabled,
  }

  try {
    if (editingId.value) {
      await put(`/v1/agents/${editingId.value}`, payload)
    } else {
      await post('/v1/agents', payload)
    }
    dialogVisible.value = false
    tableRef.value?.refresh()
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
  align-items: center;
  margin-bottom: var(--hify-spacing-base);
}

.page__header h2 {
  margin: 0;
}

.actions-cell {
  display: flex;
  gap: 4px;
}
</style>
