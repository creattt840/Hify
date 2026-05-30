<template>
  <div class="wf-node" :class="[`wf-node--${data.nodeType.toLowerCase()}`, { 'is-selected': selected }]">
    <Handle v-if="data.nodeType !== 'START'" type="target" :position="Position.Left" />

    <div class="wf-node__header">
      <span class="wf-node__icon">{{ icon }}</span>
      <span class="wf-node__title">{{ data.title || data.nodeKey }}</span>
    </div>
    <div class="wf-node__type">{{ typeLabel }}</div>

    <template v-if="data.nodeType === 'CONDITION'">
      <Handle id="true" type="source" :position="Position.Right" class="handle-true" />
      <Handle id="false" type="source" :position="Position.Right" class="handle-false" />
      <span class="wf-node__branch wf-node__branch--true">是</span>
      <span class="wf-node__branch wf-node__branch--false">否</span>
    </template>
    <Handle v-else-if="data.nodeType !== 'END'" type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import type { WfNodeData } from '@/types/workflow-editor'

const props = defineProps<{
  data: WfNodeData
  selected?: boolean
}>()

const ICONS: Record<string, string> = {
  START: '▶',
  LLM: '✦',
  CONDITION: '◇',
  END: '■',
}

const LABELS: Record<string, string> = {
  START: '开始',
  LLM: '大模型',
  CONDITION: '条件分支',
  END: '结束',
}

const icon = computed(() => ICONS[props.data.nodeType] ?? '●')
const typeLabel = computed(() => LABELS[props.data.nodeType] ?? props.data.nodeType)
</script>

<style scoped>
.wf-node {
  min-width: 148px;
  padding: 10px 14px 12px;
  border-radius: 10px;
  border: 2px solid var(--hify-border);
  background: #fff;
  box-shadow: var(--hify-shadow-sm);
  transition: border-color var(--hify-transition-fast), box-shadow var(--hify-transition-fast);
}

.wf-node.is-selected {
  border-color: var(--hify-primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15), var(--hify-shadow-sm);
}

.wf-node--start { border-color: #10B981; background: #F0FDF4; }
.wf-node--llm { border-color: #6366F1; background: #F5F3FF; }
.wf-node--condition { border-color: #F59E0B; background: #FFFBEB; min-height: 72px; }
.wf-node--end { border-color: #EF4444; background: #FEF2F2; }

.wf-node__header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.wf-node__icon {
  font-size: 14px;
  line-height: 1;
}

.wf-node__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--hify-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 110px;
}

.wf-node__type {
  font-size: 11px;
  color: var(--hify-text-secondary);
}

.wf-node__branch {
  position: absolute;
  right: -22px;
  font-size: 10px;
  font-weight: 600;
  color: var(--hify-text-secondary);
}

.wf-node__branch--true { top: 28%; }
.wf-node__branch--false { top: 68%; }

:deep(.handle-true) { top: 30% !important; }
:deep(.handle-false) { top: 70% !important; }
</style>
