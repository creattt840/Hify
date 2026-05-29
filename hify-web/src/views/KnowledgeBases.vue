<template>
  <div class="page">
    <header class="page__header">
      <div class="page__title-block">
        <h2>知识库管理</h2>
        <p class="page__desc">管理知识库，上传文档并自动向量化，为 Agent 提供 RAG 能力</p>
      </div>
      <el-button type="primary" @click="handleCreate">新建知识库</el-button>
    </header>

    <div class="page__card">
      <div class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="按名称搜索知识库"
          clearable
          style="width: 280px"
          @keyup.enter="tableRef?.refresh()"
          @clear="tableRef?.refresh()"
        >
          <template #prefix>
            <span style="opacity:0.5">🔍</span>
          </template>
        </el-input>
      </div>

      <HifyTable ref="tableRef" :columns="columns" :api="fetchKnowledgeBases as any">
        <template #name="{ row }">
          <span class="name-link" @click="toDocuments(row)">{{ row.name }}</span>
        </template>

        <template #isEnabled="{ row }">
          <el-tag :type="row.isEnabled ? 'success' : 'info'" size="small">
            {{ row.isEnabled ? '启用' : '禁用' }}
          </el-tag>
        </template>

        <template #documentCount="{ row }">
          <span>{{ row.documentCount ?? 0 }}</span>
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
      title="知识库"
      :rules="formRules"
      @submit="(d) => handleSubmit(d as unknown as KnowledgeBaseFormData)"
    >
      <template #default="{ data }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="data.name" placeholder="如 技术文档库" />
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="data.description"
            type="textarea"
            :rows="3"
            placeholder="知识库用途说明（可选）"
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
import { useRouter } from 'vue-router'
import type { FormRules } from 'element-plus'
import HifyTable from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import { useConfirm } from '@/components/useConfirm'
import { post, put, del } from '@/utils/request'
import { getKnowledgeBaseList } from '@/api/knowledge'
import type { KnowledgeBaseResponse } from '@/types/knowledge'
import type { PageResult } from '@/types'

const router = useRouter()

// ─────────────────────── 搜索 ───────────────────────

const searchKeyword = ref('')

// ─────────────────────── 表格 ───────────────────────

const tableRef = ref<{ refresh: () => void }>()

const columns = [
  { prop: 'name', label: '名称', minWidth: 160, slot: 'name' },
  { prop: 'description', label: '描述', minWidth: 200 },
  { prop: 'isEnabled', label: '状态', width: 80, slot: 'isEnabled' },
  { prop: 'documentCount', label: '文档数量', width: 100, slot: 'documentCount' },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'actions', label: '操作', width: 160, slot: 'actions' },
]

async function fetchKnowledgeBases(params: { page: number; pageSize: number }) {
  const res = await getKnowledgeBaseList({
    page: params.page,
    pageSize: params.pageSize,
    name: searchKeyword.value || undefined,
  })
  const body: PageResult<KnowledgeBaseResponse[]> = res.data
  return { list: body.data ?? [], total: body.total ?? 0 }
}

// ─────────────────────── 跳转文档页 ───────────────────────

function toDocuments(row: KnowledgeBaseResponse) {
  router.push(`/knowledge-bases/${row.id}/documents`)
}

// ─────────────────────── 弹窗表单 ───────────────────────

interface KnowledgeBaseFormData {
  name: string
  description: string
  isEnabled: boolean
}

const dialogVisible = ref(false)
const dialogRef = ref<{ open: (data?: KnowledgeBaseFormData) => void }>()
const editingId = ref<number | null>(null)

const formRules: FormRules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
}

const defaultFormData = (): KnowledgeBaseFormData => ({
  name: '',
  description: '',
  isEnabled: true,
})

function openDialog(data?: KnowledgeBaseFormData) {
  dialogVisible.value = true
  setTimeout(() => {
    dialogRef.value?.open(data ?? defaultFormData())
  })
}

function handleCreate() {
  editingId.value = null
  openDialog()
}

function handleEdit(row: KnowledgeBaseResponse) {
  editingId.value = row.id
  openDialog({
    name: row.name,
    description: row.description,
    isEnabled: row.isEnabled,
  })
}

async function handleSubmit(data: KnowledgeBaseFormData) {
  const payload = {
    name: data.name,
    description: data.description || '',
    isEnabled: data.isEnabled,
  }

  try {
    if (editingId.value) {
      await put(`/v1/knowledge-bases/${editingId.value}`, payload)
    } else {
      await post('/v1/knowledge-bases', payload)
    }
    dialogVisible.value = false
    tableRef.value?.refresh()
  } catch {
    // request.ts interceptor shows error message
  }
}

// ─────────────────────── 删除 ───────────────────────

const { confirm } = useConfirm()

function handleDelete(row: KnowledgeBaseResponse) {
  confirm(
    { message: `确定删除知识库「${row.name}」吗？关联的文档和向量数据也会一并删除，不可恢复。` },
    () => del(`/v1/knowledge-bases/${row.id}`),
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

.search-bar {
  margin-bottom: var(--hify-spacing-base);
}

.name-link {
  color: var(--hify-primary);
  cursor: pointer;
  font-weight: 500;
}

.name-link:hover {
  text-decoration: underline;
}

.actions-cell {
  display: flex;
  gap: 4px;
}
</style>
