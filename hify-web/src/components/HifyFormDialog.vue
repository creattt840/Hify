<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? `编辑${title}` : `新增${title}`"
    width="560px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="100px"
    >
      <slot :data="formData" />
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" generic="T extends Record<string, unknown>">
import { ref, reactive, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

const props = defineProps<{
  modelValue: boolean
  title: string
  rules?: FormRules
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [data: T]
}>()

const formRef = ref<FormInstance>()
const isEdit = ref(false)
const submitting = ref(false)
const defaults = {} as T
const formData = reactive({ ...defaults } as T)

function open(data?: T) {
  if (data) {
    isEdit.value = true
    Object.assign(formData, data)
  } else {
    isEdit.value = false
    Object.assign(formData, { ...defaults })
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    submitting.value = true
    emit('submit', { ...formData } as T)
  } catch {
    // 校验失败不做处理
  } finally {
    submitting.value = false
  }
}

function handleClose() {
  emit('update:modelValue', false)
  formRef.value?.resetFields()
}

watch(() => props.modelValue, (val) => {
  if (val) {
    isEdit.value = false
    Object.assign(formData, { ...defaults })
  }
})

defineExpose({ open, formData })
</script>
