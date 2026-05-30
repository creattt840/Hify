<template>
  <div class="mcp-picker" :class="{ 'is-loading': loading }">
    <!-- 搜索 + 计数 -->
    <div class="mcp-picker__toolbar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索工具或服务名称"
        class="mcp-picker__search"
        :disabled="loading || tools.length === 0"
      />
      <div class="mcp-picker__counter" :class="{ 'is-full': selectedCount >= max }">
        <span class="mcp-picker__counter-num">{{ selectedCount }}</span>
        <span class="mcp-picker__counter-sep">/</span>
        <span>{{ max }}</span>
      </div>
    </div>

    <!-- 已选标签 -->
    <div class="mcp-picker__selected" :class="{ 'is-empty': selectedCount === 0 }">
      <TransitionGroup name="chip" tag="div" class="mcp-picker__chips">
        <button
          v-for="tool in selectedTools"
          :key="tool.id"
          type="button"
          class="mcp-picker__chip"
          @click="toggleTool(tool.id)"
        >
          <span class="mcp-picker__chip-server">{{ tool.serverName }}</span>
          <span class="mcp-picker__chip-dot">·</span>
          <span class="mcp-picker__chip-name">{{ tool.name }}</span>
          <span class="mcp-picker__chip-remove" aria-hidden="true">×</span>
        </button>
      </TransitionGroup>
      <p v-if="selectedCount === 0 && !loading" class="mcp-picker__selected-hint">
        点击下方卡片选择 MCP 工具
      </p>
    </div>

    <!-- 加载 -->
    <div v-if="loading" class="mcp-picker__skeleton">
      <div v-for="i in 3" :key="i" class="mcp-picker__skeleton-row" />
    </div>

    <!-- 空状态 -->
    <div v-else-if="tools.length === 0" class="mcp-picker__empty">
      <p class="mcp-picker__empty-title">暂无可用工具</p>
      <p class="mcp-picker__empty-desc">请先在「MCP 服务」中配置并测试连接以同步工具</p>
    </div>

    <!-- 工具列表（按服务分组） -->
    <div v-else class="mcp-picker__groups">
      <section
        v-for="group in filteredGroups"
        :key="group.serverName"
        class="mcp-picker__group"
      >
        <header class="mcp-picker__group-head">
          <span class="mcp-picker__group-name">{{ group.serverName }}</span>
          <span class="mcp-picker__group-count">{{ group.tools.length }} 个工具</span>
        </header>

        <div class="mcp-picker__grid">
          <button
            v-for="tool in group.tools"
            :key="tool.id"
            type="button"
            class="mcp-picker__card"
            :class="{
              'is-selected': isSelected(tool.id),
              'is-disabled': isDisabled(tool.id),
            }"
            :disabled="isDisabled(tool.id)"
            @click="toggleTool(tool.id)"
          >
            <span class="mcp-picker__check" aria-hidden="true">
              <svg v-if="isSelected(tool.id)" viewBox="0 0 12 12" fill="none">
                <path
                  d="M2.5 6L5 8.5L9.5 3.5"
                  stroke="currentColor"
                  stroke-width="1.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </span>
            <span class="mcp-picker__card-body">
              <span class="mcp-picker__card-name">{{ tool.name }}</span>
              <span v-if="tool.description" class="mcp-picker__card-desc">{{ tool.description }}</span>
            </span>
          </button>
        </div>
      </section>

      <p v-if="filteredGroups.length === 0" class="mcp-picker__no-match">
        未找到匹配「{{ keyword }}」的工具
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { McpToolOption } from '@/types/mcp'

const props = withDefaults(
  defineProps<{
    modelValue?: number[]
    tools: McpToolOption[]
    loading?: boolean
    max?: number
  }>(),
  {
    modelValue: () => [],
    loading: false,
    max: 10,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: number[]]
}>()

const keyword = ref('')

const selectedIds = computed(() => props.modelValue ?? [])

const selectedCount = computed(() => selectedIds.value.length)

const toolMap = computed(() => new Map(props.tools.map((t) => [t.id, t])))

const selectedTools = computed(() =>
  selectedIds.value
    .map((id) => toolMap.value.get(id))
    .filter((t): t is McpToolOption => t != null),
)

interface ToolGroup {
  serverName: string
  tools: McpToolOption[]
}

const groupedTools = computed<ToolGroup[]>(() => {
  const map = new Map<string, McpToolOption[]>()
  for (const tool of props.tools) {
    const list = map.get(tool.serverName) ?? []
    list.push(tool)
    map.set(tool.serverName, list)
  }
  return [...map.entries()]
    .sort(([a], [b]) => a.localeCompare(b, 'zh-CN'))
    .map(([serverName, tools]) => ({
      serverName,
      tools: tools.sort((a, b) => a.name.localeCompare(b.name, 'zh-CN')),
    }))
})

const filteredGroups = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return groupedTools.value
  return groupedTools.value
    .map((group) => ({
      ...group,
      tools: group.tools.filter(
        (t) =>
          t.name.toLowerCase().includes(q) ||
          t.serverName.toLowerCase().includes(q) ||
          t.description.toLowerCase().includes(q),
      ),
    }))
    .filter((group) => group.tools.length > 0)
})

function isSelected(id: number): boolean {
  return selectedIds.value.includes(id)
}

function isDisabled(id: number): boolean {
  return !isSelected(id) && selectedCount.value >= props.max
}

function toggleTool(id: number) {
  const current = selectedIds.value
  if (isSelected(id)) {
    emit(
      'update:modelValue',
      current.filter((v) => v !== id),
    )
    return
  }
  if (selectedCount.value >= props.max) {
    ElMessage.warning(`最多只能选择 ${props.max} 个 MCP 工具`)
    return
  }
  emit('update:modelValue', [...current, id])
}
</script>

<style scoped>
.mcp-picker {
  display: flex;
  flex-direction: column;
  gap: var(--hify-spacing-sm);
  width: 100%;
}

.mcp-picker__toolbar {
  display: flex;
  align-items: center;
  gap: var(--hify-spacing-sm);
}

.mcp-picker__search {
  flex: 1;
}

.mcp-picker__counter {
  flex-shrink: 0;
  display: inline-flex;
  align-items: baseline;
  gap: 1px;
  padding: 4px 10px;
  font-size: var(--hify-font-size-xs);
  font-weight: 600;
  color: var(--hify-text-secondary);
  background: var(--hify-bg-hover);
  border: 1px solid var(--hify-border-light);
  border-radius: 999px;
  transition:
    color var(--hify-transition-fast),
    background var(--hify-transition-fast),
    border-color var(--hify-transition-fast);
}

.mcp-picker__counter.is-full {
  color: var(--hify-warning);
  background: rgba(245, 158, 11, 0.08);
  border-color: rgba(245, 158, 11, 0.25);
}

.mcp-picker__counter-num {
  font-size: var(--hify-font-size-sm);
  color: var(--hify-primary);
}

.mcp-picker__counter.is-full .mcp-picker__counter-num {
  color: var(--hify-warning);
}

.mcp-picker__selected {
  min-height: 36px;
  padding: 8px 10px;
  background: var(--hify-bg-hover);
  border: 1px dashed var(--hify-border);
  border-radius: var(--hify-radius-base);
  transition: border-color var(--hify-transition-base);
}

.mcp-picker__selected:not(.is-empty) {
  border-style: solid;
  border-color: var(--hify-border-light);
  background: var(--hify-bg-container);
}

.mcp-picker__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mcp-picker__chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 4px 8px 4px 10px;
  font: inherit;
  font-size: var(--hify-font-size-xs);
  color: var(--hify-text-regular);
  background: rgba(99, 102, 241, 0.08);
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 999px;
  cursor: pointer;
  transition:
    transform var(--hify-transition-fast),
    background var(--hify-transition-fast),
    border-color var(--hify-transition-fast),
    box-shadow var(--hify-transition-fast);
}

.mcp-picker__chip:hover {
  background: rgba(99, 102, 241, 0.14);
  border-color: rgba(99, 102, 241, 0.35);
  box-shadow: var(--hify-shadow-sm);
}

.mcp-picker__chip:active {
  transform: scale(0.97);
}

.mcp-picker__chip-server {
  color: var(--hify-primary);
  font-weight: 500;
  white-space: nowrap;
}

.mcp-picker__chip-dot {
  color: var(--hify-text-placeholder);
}

.mcp-picker__chip-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mcp-picker__chip-remove {
  margin-left: 2px;
  font-size: 14px;
  line-height: 1;
  color: var(--hify-text-secondary);
  transition: color var(--hify-transition-fast);
}

.mcp-picker__chip:hover .mcp-picker__chip-remove {
  color: var(--hify-danger);
}

.mcp-picker__selected-hint {
  margin: 0;
  font-size: var(--hify-font-size-xs);
  color: var(--hify-text-placeholder);
  line-height: 20px;
}

.mcp-picker__skeleton {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 0;
}

.mcp-picker__skeleton-row {
  height: 52px;
  border-radius: var(--hify-radius-base);
  background: linear-gradient(
    90deg,
    var(--hify-bg-hover) 25%,
    var(--hify-bg-active) 50%,
    var(--hify-bg-hover) 75%
  );
  background-size: 200% 100%;
  animation: mcp-shimmer 1.2s ease-in-out infinite;
}

@keyframes mcp-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.mcp-picker__empty {
  padding: 20px 12px;
  text-align: center;
  border: 1px solid var(--hify-border-light);
  border-radius: var(--hify-radius-base);
  background: var(--hify-bg-hover);
}

.mcp-picker__empty-title {
  margin: 0 0 4px;
  font-size: var(--hify-font-size-sm);
  font-weight: 500;
  color: var(--hify-text-regular);
}

.mcp-picker__empty-desc {
  margin: 0;
  font-size: var(--hify-font-size-xs);
  color: var(--hify-text-secondary);
  line-height: 1.5;
}

.mcp-picker__groups {
  display: flex;
  flex-direction: column;
  gap: var(--hify-spacing-sm);
  max-height: 280px;
  overflow-y: auto;
  padding-right: 2px;
}

.mcp-picker__groups::-webkit-scrollbar {
  width: 5px;
}

.mcp-picker__groups::-webkit-scrollbar-thumb {
  background: var(--hify-border-hover);
  border-radius: 999px;
}

.mcp-picker__group {
  animation: mcp-group-in var(--hify-transition-slow) ease both;
}

.mcp-picker__group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
  padding: 0 2px;
}

.mcp-picker__group-name {
  font-size: var(--hify-font-size-xs);
  font-weight: 600;
  color: var(--hify-text-secondary);
  letter-spacing: 0.02em;
}

.mcp-picker__group-count {
  font-size: 11px;
  color: var(--hify-text-placeholder);
}

.mcp-picker__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 6px;
}

.mcp-picker__card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  text-align: left;
  font: inherit;
  color: inherit;
  background: var(--hify-bg-container);
  border: 1px solid var(--hify-border-light);
  border-radius: var(--hify-radius-base);
  cursor: pointer;
  transition:
    transform var(--hify-transition-fast),
    border-color var(--hify-transition-base),
    background var(--hify-transition-base),
    box-shadow var(--hify-transition-base);
}

.mcp-picker__card:hover:not(:disabled) {
  border-color: var(--hify-border-hover);
  background: var(--hify-bg-hover);
  box-shadow: var(--hify-shadow-sm);
  transform: translateY(-1px);
}

.mcp-picker__card:active:not(:disabled) {
  transform: translateY(0);
}

.mcp-picker__card.is-selected {
  border-color: rgba(99, 102, 241, 0.45);
  background: rgba(99, 102, 241, 0.06);
  box-shadow: 0 0 0 1px rgba(99, 102, 241, 0.08);
}

.mcp-picker__card.is-disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.mcp-picker__check {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  margin-top: 1px;
  border: 1.5px solid var(--hify-border-hover);
  border-radius: 50%;
  color: transparent;
  transition:
    border-color var(--hify-transition-fast),
    background var(--hify-transition-fast),
    color var(--hify-transition-fast),
    transform var(--hify-transition-fast);
}

.mcp-picker__card.is-selected .mcp-picker__check {
  background: var(--hify-primary);
  border-color: var(--hify-primary);
  color: #fff;
  transform: scale(1.05);
}

.mcp-picker__check svg {
  width: 11px;
  height: 11px;
}

.mcp-picker__card-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mcp-picker__card-name {
  font-size: var(--hify-font-size-sm);
  font-weight: 500;
  color: var(--hify-text-primary);
  line-height: 1.35;
}

.mcp-picker__card-desc {
  font-size: var(--hify-font-size-xs);
  color: var(--hify-text-secondary);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.mcp-picker__no-match {
  margin: 8px 0 0;
  text-align: center;
  font-size: var(--hify-font-size-xs);
  color: var(--hify-text-placeholder);
}

/* 已选标签进出场动画 */
.chip-enter-active {
  transition:
    opacity var(--hify-transition-base),
    transform var(--hify-transition-base);
}

.chip-leave-active {
  transition:
    opacity var(--hify-transition-fast),
    transform var(--hify-transition-fast);
  position: absolute;
}

.chip-enter-from,
.chip-leave-to {
  opacity: 0;
  transform: scale(0.85) translateY(4px);
}

.chip-move {
  transition: transform var(--hify-transition-base);
}

@keyframes mcp-group-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
