<template>
  <div class="page">
    <header class="page__header">
      <div class="page__title-block">
        <h2>工作流管理</h2>
        <p class="page__desc">创建和配置工作流，将 Agent 从自由对话切换为结构化多步执行</p>
      </div>
      <el-button type="primary" @click="handleCreate">新建工作流</el-button>
    </header>

    <div class="page__card">
      <HifyTable ref="tableRef" :columns="columns" :api="fetchWorkflows as any">
        <template #status="{ row }">
          <el-tag :type="row.isPublished ? 'success' : 'info'" size="small">
            {{ row.isPublished ? '已发布' : '草稿' }}
          </el-tag>
        </template>

        <template #nodeCount="{ row }">
          <span>{{ row.nodeCount ?? '-' }}</span>
        </template>

        <template #actions="{ row }">
          <el-button type="danger" size="small" text @click="handleDelete(row)">
            删除
          </el-button>
        </template>
      </HifyTable>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import HifyTable from '@/components/HifyTable.vue'
import { useConfirm } from '@/components/useConfirm'
import { getWorkflowList, deleteWorkflow } from '@/api/workflow'
import type { WorkflowListItem } from '@/types/workflow'
import type { ColumnConfig } from '@/components/HifyTable.vue'

const router = useRouter()
const { confirm } = useConfirm()
const tableRef = ref<{ refresh: () => void }>()

const columns: ColumnConfig[] = [
  { prop: 'id', label: 'ID', width: '80' },
  { prop: 'name', label: '名称', minWidth: '180' },
  { prop: 'status', label: '状态', width: '100', slot: 'status' },
  { prop: 'nodeCount', label: '节点数', width: '80', slot: 'nodeCount' },
  { prop: 'createdAt', label: '创建时间', width: '180' },
  { prop: 'actions', label: '操作', width: '100', slot: 'actions' },
]

async function fetchWorkflows(params: { page: number; pageSize: number }) {
  const res = await getWorkflowList({ page: params.page, pageSize: params.pageSize })
  const body = res.data
  return { list: (body.data ?? []) as WorkflowListItem[], total: body.total ?? 0 }
}

function handleCreate() {
  router.push({ name: 'workflowCreate' })
}

function handleDelete(row: WorkflowListItem) {
  confirm(
    { message: `确定删除「${row.name}」吗？删除后不可恢复。` },
    () => deleteWorkflow(row.id),
    () => tableRef.value?.refresh(),
  )
}
</script>
