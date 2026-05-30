<template>
  <div class="page">
    <header class="page__header">
      <div class="page__title-block">
        <h2>{{ pageTitle }}</h2>
        <p class="page__desc">{{ pageDesc }}</p>
      </div>
      <div class="page__actions">
        <el-button @click="router.push({ name: 'workflows' })">返回列表</el-button>
        <el-button v-if="!isView" type="primary" :loading="submitting" @click="handleSave">
          {{ isEdit ? '保存' : '创建' }}
        </el-button>
        <el-button v-if="isView" type="primary" @click="goEdit">编辑</el-button>
      </div>
    </header>

    <div v-if="loading" class="page__loading">
      <el-skeleton :rows="8" animated />
    </div>

    <WorkflowEditor
      v-else
      ref="editorRef"
      :readonly="isView"
      :initial-workflow="workflow"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import WorkflowEditor from '@/components/workflow/WorkflowEditor.vue'
import { createWorkflow, getWorkflowDetail, updateWorkflow } from '@/api/workflow'
import type { WorkflowResponse } from '@/types/workflow'

const route = useRoute()
const router = useRouter()

const editorRef = ref<InstanceType<typeof WorkflowEditor>>()
const workflow = ref<WorkflowResponse | null>(null)
const loading = ref(false)
const submitting = ref(false)

const mode = computed(() => {
  if (route.name === 'workflowCreate') return 'create'
  if (route.name === 'workflowEdit') return 'edit'
  return 'view'
})

const isView = computed(() => mode.value === 'view')
const isEdit = computed(() => mode.value === 'edit')

const pageTitle = computed(() => {
  if (mode.value === 'create') return '新建工作流'
  if (mode.value === 'edit') return '编辑工作流'
  return '查看工作流'
})

const pageDesc = computed(() => {
  if (isView.value) return '只读模式，点击节点查看配置'
  return '从左侧面板拖拽节点到画布，连线后在右侧面板配置 Prompt 和参数'
})

onMounted(async () => {
  const id = route.params.id
  if (id && mode.value !== 'create') {
    loading.value = true
    try {
      const res = await getWorkflowDetail(Number(id))
      workflow.value = (res.data as { data?: WorkflowResponse })?.data ?? null
      if (!workflow.value) {
        throw new Error('not found')
      }
    } catch {
      ElMessage.error('加载工作流失败')
      router.push({ name: 'workflows' })
    } finally {
      loading.value = false
    }
  }
})

function goEdit() {
  router.push({ name: 'workflowEdit', params: { id: route.params.id } })
}

async function handleSave() {
  const editor = editorRef.value
  if (!editor) return

  const err = editor.validate()
  if (err) {
    ElMessage.warning(err)
    return
  }

  submitting.value = true
  try {
    const payload = editor.getPayload()
    if (isEdit.value) {
      await updateWorkflow(Number(route.params.id), payload)
      ElMessage.success('保存成功')
    } else {
      await createWorkflow(payload)
      ElMessage.success('创建成功')
    }
    router.push({ name: 'workflows' })
  } catch {
    // http interceptor handles API errors
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--hify-spacing-base);
  gap: var(--hify-spacing-base);
}

.page__title-block h2 {
  margin: 0 0 4px;
  font-size: var(--hify-font-size-xxl);
  font-weight: 600;
}

.page__desc {
  margin: 0;
  font-size: var(--hify-font-size-sm);
  color: var(--hify-text-secondary);
}

.page__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.page__loading {
  padding: 24px;
  background: var(--hify-bg-container);
  border-radius: var(--hify-radius-lg);
  border: 1px solid var(--hify-border-light);
}
</style>
