<template>
  <div class="page">
    <h2>对话</h2>
    <p>多轮对话、历史记录、SSE 流式响应。</p>
    <el-divider />
    <el-card class="health-card">
      <template #header>
        <span>健康检查</span>
      </template>
      <div class="health-result">
        <el-tag :type="statusTag" effect="dark" size="large">
          {{ healthText }}
        </el-tag>
        <el-progress
          :percentage="elapsed"
          :stroke-width="8"
          :color="progressColor"
          :class="{ hidden: !elapsed }"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { get } from '../utils/request'

const healthText = ref('检测中...')
const elapsed = ref(0)
const success = ref(false)

const statusTag = computed(() => (success.value ? 'success' : 'danger'))
const progressColor = computed(() => (success.value ? '#34D399' : '#F87171'))

onMounted(async () => {
  const start = performance.now()
  try {
    const text = await get<string>('/health')
    healthText.value = text ?? '无响应'
    success.value = true
  } catch {
    healthText.value = '连接失败'
  }
  elapsed.value = Math.round(performance.now() - start)
})
</script>

<style scoped>
.health-card {
  max-width: 480px;
  margin-top: var(--hify-spacing-base);
}

.health-result {
  display: flex;
  flex-direction: column;
  gap: var(--hify-spacing-base);
}

.hidden {
  visibility: hidden;
}
</style>
