<template>
  <div class="page">
    <header class="page__header">
      <div class="page__title-block">
        <h2>新建工作流</h2>
        <p class="page__desc">填写基本信息，粘贴 JSON 配置创建工作流</p>
      </div>
      <el-button @click="router.back()">返回列表</el-button>
    </header>

    <div class="page__card">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        class="workflow-form"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入工作流名称" maxlength="128" />
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="可选，简要描述工作流用途"
            maxlength="512"
          />
        </el-form-item>

        <el-form-item label="JSON 配置" prop="configJson" class="json-editor">
          <div class="json-editor__toolbar">
            <el-button size="small" @click="handleFormat">格式化</el-button>
          </div>
          <el-input
            v-model="form.configJson"
            type="textarea"
            :rows="20"
            placeholder="粘贴工作流 JSON 配置（含 nodes 和 edges）"
            class="json-editor__input"
          />
          <p v-if="jsonError" class="json-editor__error">{{ jsonError }}</p>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            创建
          </el-button>
          <el-button @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { createWorkflow } from '@/api/workflow'
import type { WorkflowCreateRequest } from '@/types/workflow'

// ── 预填示例：智能客服分类工作流 ──
const EXAMPLE_JSON = JSON.stringify(
  {
    nodes: [
      { nodeKey: 'start', nodeType: 'START', title: '开始', config: {}, sortOrder: 0 },
      {
        nodeKey: 'classify',
        nodeType: 'LLM',
        title: '投诉判断',
        config: {
          modelConfigId: 1,
          prompt:
            '请判断以下用户消息是否属于投诉。只回答一个字："是"或"否"。\n\n用户消息：{{start.userMessage}}',
          outputVariable: 'isComplaint',
        },
        sortOrder: 1,
      },
      {
        nodeKey: 'cond',
        nodeType: 'CONDITION',
        title: '是否投诉',
        config: {
          expression: '{{classify.isComplaint}} == 是',
          outputVariable: 'isComplaint',
        },
        sortOrder: 2,
      },
      {
        nodeKey: 'complaint_reply',
        nodeType: 'LLM',
        title: '投诉回复',
        config: {
          modelConfigId: 1,
          prompt:
            '你是客服主管。用户提出了投诉，请给出诚恳的道歉和解决方案。\n\n用户消息：{{start.userMessage}}',
          outputVariable: 'reply',
        },
        sortOrder: 3,
      },
      {
        nodeKey: 'general_reply',
        nodeType: 'LLM',
        title: '常规回复',
        config: {
          modelConfigId: 1,
          prompt:
            '你是友好的客服助手。请针对用户的问题给出专业回复。\n\n用户消息：{{start.userMessage}}',
          outputVariable: 'reply',
        },
        sortOrder: 4,
      },
      { nodeKey: 'end', nodeType: 'END', title: '结束', config: {}, sortOrder: 5 },
    ],
    edges: [
      { source: 'start', target: 'classify', sortOrder: 0 },
      { source: 'classify', target: 'cond', sortOrder: 0 },
      { source: 'cond', target: 'complaint_reply', condition: 'true', sortOrder: 0 },
      { source: 'cond', target: 'general_reply', condition: 'false', sortOrder: 1 },
      { source: 'complaint_reply', target: 'end', sortOrder: 0 },
      { source: 'general_reply', target: 'end', sortOrder: 0 },
    ],
  },
  null,
  2,
)

const router = useRouter()

// ── 表单 ──

const formRef = ref<FormInstance>()
const submitting = ref(false)
const jsonError = ref('')

const form = reactive({
  name: '',
  description: '',
  configJson: EXAMPLE_JSON,
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入工作流名称', trigger: 'blur' }],
  configJson: [
    { required: true, message: '请输入 JSON 配置', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!value) {
          callback(new Error('JSON 配置不能为空'))
          return
        }
        try {
          JSON.parse(value)
          jsonError.value = ''
          callback()
        } catch {
          jsonError.value = 'JSON 格式不合法，请检查'
          callback(new Error('JSON 格式不合法'))
        }
      },
      trigger: 'blur',
    },
  ],
}

// ── 格式化 ──

function handleFormat() {
  if (!form.configJson) return
  try {
    const parsed = JSON.parse(form.configJson)
    form.configJson = JSON.stringify(parsed, null, 2)
    jsonError.value = ''
  } catch {
    jsonError.value = 'JSON 格式不合法，无法格式化'
  }
}

// ── 提交 ──

async function handleSubmit() {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  try {
    submitting.value = true
    const parsed = JSON.parse(form.configJson)
    const payload: WorkflowCreateRequest = {
      name: form.name,
      description: form.description || undefined,
      nodes: parsed.nodes || [],
      edges: parsed.edges || [],
    }
    await createWorkflow(payload)
    ElMessage.success('工作流创建成功')
    router.push({ name: 'workflows' })
  } catch (e: unknown) {
    if (e instanceof SyntaxError) {
      jsonError.value = 'JSON 格式不合法，请检查'
    }
    // API error is handled by the http interceptor
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.workflow-form {
  max-width: 900px;
}

.json-editor :deep(.el-form-item__content) {
  flex-direction: column;
  align-items: stretch;
}

.json-editor__toolbar {
  margin-bottom: 8px;
}

.json-editor__input :deep(textarea) {
  font-family: 'Cascadia Code', 'Fira Code', 'JetBrains Mono', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.json-editor__error {
  margin-top: 8px;
  color: var(--el-color-danger);
  font-size: 13px;
}
</style>
