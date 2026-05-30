<template>
  <div class="workflow-editor" :class="{ 'is-readonly': readonly }">
    <!-- 左侧节点面板 -->
    <aside v-if="!readonly" class="wf-panel wf-panel--left">
      <h3 class="wf-panel__title">节点库</h3>
      <p class="wf-panel__hint">拖拽到画布添加节点</p>
      <div
        v-for="item in NODE_PALETTE"
        :key="item.type"
        class="wf-palette-item"
        :style="{ '--accent': item.color }"
        draggable="true"
        @dragstart="onPaletteDragStart($event, item.type)"
      >
        <span class="wf-palette-item__dot" />
        <div>
          <div class="wf-palette-item__label">{{ item.label }}</div>
          <div class="wf-palette-item__desc">{{ item.desc }}</div>
        </div>
      </div>
    </aside>

    <!-- 中间画布 -->
    <WorkflowCanvas
      v-model:nodes="nodes"
      v-model:edges="edges"
      :readonly="readonly"
      @node-click="onNodeClick"
      @edge-click="onEdgeClick"
      @pane-click="onPaneClick"
    />

    <!-- 右侧配置面板 -->
    <aside class="wf-panel wf-panel--right">
      <h3 class="wf-panel__title">
        {{ panelTitle }}
      </h3>

      <!-- 未选中：工作流信息 -->
      <div v-if="!selectedNode && !selectedEdge" class="wf-config">
        <el-form label-position="top" :disabled="readonly">
          <el-form-item label="名称">
            <el-input v-model="meta.name" placeholder="工作流名称" maxlength="128" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input
              v-model="meta.description"
              type="textarea"
              :rows="3"
              placeholder="可选描述"
              maxlength="512"
            />
          </el-form-item>
        </el-form>
        <p class="wf-config__tip">点击节点配置 Prompt；点击连线可修改或删除</p>
      </div>

      <!-- 选中连线 -->
      <div v-else-if="selectedEdge" class="wf-config">
        <el-form label-position="top" :disabled="readonly">
          <el-form-item label="连线 ID">
            <el-input :model-value="selectedEdge.id" disabled />
          </el-form-item>
          <el-form-item label="来源节点">
            <el-input :model-value="selectedEdge.source" disabled />
          </el-form-item>
          <el-form-item label="目标节点">
            <el-input :model-value="selectedEdge.target" disabled />
          </el-form-item>
          <el-form-item v-if="edgeFromCondition" label="分支条件">
            <el-select v-model="edgeCondition" style="width: 100%">
              <el-option label="是 (true)" value="true" />
              <el-option label="否 (false)" value="false" />
            </el-select>
          </el-form-item>
        </el-form>
        <p class="wf-config__tip">
          拖拽连线端点可重新连接目标节点；选中后按 Delete 或 Backspace 删除
        </p>
        <el-button
          v-if="!readonly"
          type="danger"
          plain
          size="small"
          class="wf-config__delete"
          @click="deleteSelectedEdge"
        >
          删除连线
        </el-button>
      </div>

      <!-- 选中节点 -->
      <div v-else-if="selectedNode" class="wf-config">
        <el-form label-position="top" :disabled="readonly">
          <el-form-item label="节点 Key">
            <el-input :model-value="selectedNode.data!.nodeKey" disabled />
          </el-form-item>
          <el-form-item label="节点类型">
            <el-tag size="small">{{ selectedNode.data!.nodeType }}</el-tag>
          </el-form-item>
          <el-form-item label="标题">
            <el-input v-model="selectedNode.data!.title" placeholder="节点显示名称" />
          </el-form-item>

          <template v-if="selectedNode.data!.nodeType === 'LLM'">
            <el-form-item label="绑定模型">
              <el-select
                v-model="llmConfig.modelConfigId"
                placeholder="选择模型"
                filterable
                style="width: 100%"
                :loading="modelsLoading"
              >
                <el-option
                  v-for="mc in modelOptions"
                  :key="mc.id"
                  :label="modelLabel(mc)"
                  :value="mc.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="Prompt">
              <el-input
                v-model="llmConfig.prompt"
                type="textarea"
                :rows="8"
                placeholder="支持 {{nodeKey.variable}} 模板变量"
              />
            </el-form-item>
            <el-form-item label="输出变量名">
              <el-input v-model="llmConfig.outputVariable" placeholder="如 reply" />
            </el-form-item>
            <el-form-item label="温度">
              <el-slider v-model="llmConfig.temperature" :min="0" :max="2" :step="0.1" show-input />
            </el-form-item>
          </template>

          <template v-if="selectedNode.data!.nodeType === 'CONDITION'">
            <el-form-item label="条件表达式">
              <el-input
                v-model="condConfig.expression"
                type="textarea"
                :rows="3"
                placeholder="如 {{classify.isComplaint}} == 是"
              />
            </el-form-item>
            <el-form-item label="输出变量名">
              <el-input v-model="condConfig.outputVariable" placeholder="如 result" />
            </el-form-item>
            <p class="wf-config__tip">
              从节点右侧拖连线：上方 handle 为「是」分支，下方为「否」分支
            </p>
          </template>

          <template v-if="selectedNode.data!.nodeType === 'START'">
            <p class="wf-config__tip">
              开始节点接收用户输入，变量名为 userMessage，引用方式：{{ '{' }}{{ '{' }}start.userMessage{{ '}' }}{{ '}' }}
            </p>
          </template>
        </el-form>

        <el-button
          v-if="!readonly && selectedNode.data!.nodeType !== 'START'"
          type="danger"
          plain
          size="small"
          class="wf-config__delete"
          @click="deleteSelectedNode"
        >
          删除节点
        </el-button>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'

import WorkflowCanvas from './WorkflowCanvas.vue'
import { listModelConfigs } from '@/api/provider'
import type { ModelConfigResponse } from '@/types/provider'
import {
  NODE_PALETTE,
  createDefaultGraph,
  workflowToGraph,
  graphToPayload,
  type WfGraphNode,
  type WfGraphEdge,
  type WorkflowMeta,
} from '@/types/workflow-editor'
import type { WorkflowResponse } from '@/types/workflow'

const props = defineProps<{
  readonly?: boolean
  initialWorkflow?: WorkflowResponse | null
}>()

const nodes = ref<WfGraphNode[]>([])
const edges = ref<WfGraphEdge[]>([])
const selectedNodeId = ref<string | null>(null)
const selectedEdgeId = ref<string | null>(null)
const meta = reactive<WorkflowMeta>({ name: '', description: '' })

const modelOptions = ref<ModelConfigResponse[]>([])
const modelsLoading = ref(false)

const selectedNode = computed((): WfGraphNode | null => {
  const id = selectedNodeId.value
  if (!id) return null
  return nodes.value.find((n) => n.id === id) ?? null
})

const selectedEdge = computed((): WfGraphEdge | null => {
  const id = selectedEdgeId.value
  if (!id) return null
  return edges.value.find((e) => e.id === id) ?? null
})

const panelTitle = computed(() => {
  if (selectedEdge.value) return '连线配置'
  if (selectedNode.value) return '节点配置'
  return '工作流信息'
})

const edgeFromCondition = computed(() => {
  const edge = selectedEdge.value
  if (!edge) return false
  const src = nodes.value.find((n) => n.id === edge.source)
  return src?.data?.nodeType === 'CONDITION'
})

const edgeCondition = computed({
  get() {
    return selectedEdge.value?.data?.condition ?? selectedEdge.value?.label ?? 'true'
  },
  set(v: string) {
    const edge = selectedEdge.value
    if (!edge) return
    const idx = edges.value.findIndex((e) => e.id === edge.id)
    if (idx < 0) return
    const updated = { ...edges.value[idx] }
    updated.label = v
    updated.data = { condition: v }
    updated.sourceHandle = v === 'false' ? 'false' : 'true'
    edges.value = edges.value.map((e, i) => (i === idx ? updated : e))
  },
})

function onNodeClick(id: string) {
  selectedNodeId.value = id
  selectedEdgeId.value = null
}

function onEdgeClick(id: string) {
  selectedEdgeId.value = id
  selectedNodeId.value = null
}

function onPaneClick() {
  selectedNodeId.value = null
  selectedEdgeId.value = null
}

const llmConfig = computed(() => {
  const cfg = selectedNode.value?.data?.config ?? {}
  return {
    get modelConfigId() { return cfg.modelConfigId as number | null },
    set modelConfigId(v) { cfg.modelConfigId = v },
    get prompt() { return (cfg.prompt as string) ?? '' },
    set prompt(v) { cfg.prompt = v },
    get outputVariable() { return (cfg.outputVariable as string) ?? 'output' },
    set outputVariable(v) { cfg.outputVariable = v },
    get temperature() { return (cfg.temperature as number) ?? 0.7 },
    set temperature(v) { cfg.temperature = v },
  }
})

const condConfig = computed(() => {
  const cfg = selectedNode.value?.data?.config ?? {}
  return {
    get expression() { return (cfg.expression as string) ?? '' },
    set expression(v) { cfg.expression = v },
    get outputVariable() { return (cfg.outputVariable as string) ?? 'result' },
    set outputVariable(v) { cfg.outputVariable = v },
  }
})

function modelLabel(mc: ModelConfigResponse): string {
  const p = mc.providerName ? `${mc.providerName} · ` : ''
  return `${p}${mc.name} (${mc.modelId})`
}

function initGraph() {
  if (props.initialWorkflow) {
    meta.name = props.initialWorkflow.name
    meta.description = props.initialWorkflow.description ?? ''
    const graph = workflowToGraph(props.initialWorkflow)
    nodes.value = graph.nodes
    edges.value = graph.edges
  } else {
    const graph = createDefaultGraph()
    nodes.value = graph.nodes
    edges.value = graph.edges
  }
}

watch(() => props.initialWorkflow, initGraph, { immediate: true })

onMounted(async () => {
  modelsLoading.value = true
  try {
    const res = await listModelConfigs()
    modelOptions.value = res.data?.data ?? []
  } finally {
    modelsLoading.value = false
  }
})

function onPaletteDragStart(event: DragEvent, nodeType: string) {
  event.dataTransfer?.setData('application/wf-node', nodeType)
  event.dataTransfer!.effectAllowed = 'move'
}

function deleteSelectedNode() {
  if (!selectedNodeId.value) return
  const id = selectedNodeId.value
  nodes.value = nodes.value.filter((n) => n.id !== id)
  edges.value = edges.value.filter((e) => e.source !== id && e.target !== id)
  selectedNodeId.value = null
}

function deleteSelectedEdge() {
  if (!selectedEdgeId.value) return
  const id = selectedEdgeId.value
  edges.value = edges.value.filter((e) => e.id !== id)
  selectedEdgeId.value = null
}

function validate(): string | null {
  if (!meta.name.trim()) return '请输入工作流名称'
  if (nodes.value.length === 0) return '请至少添加一个节点'
  if (!nodes.value.some((n) => n.data?.nodeType === 'START')) return '工作流需要至少一个「开始」节点'
  if (!nodes.value.some((n) => n.data?.nodeType === 'END')) return '工作流需要至少一个「结束」节点'
  return null
}

function getPayload() {
  const err = validate()
  if (err) throw new Error(err)
  const { nodes: nodeReqs, edges: edgeReqs } = graphToPayload(nodes.value, edges.value)
  return {
    name: meta.name.trim(),
    description: meta.description.trim() || undefined,
    nodes: nodeReqs,
    edges: edgeReqs,
  }
}

defineExpose({ getPayload, validate, meta, nodes, edges })
</script>

<style scoped>
.workflow-editor {
  display: flex;
  height: calc(100vh - 120px);
  min-height: 560px;
  border: 1px solid var(--hify-border-light);
  border-radius: var(--hify-radius-lg);
  overflow: hidden;
  background: #fff;
}

.wf-panel {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #FAFBFD;
  border-right: 1px solid var(--hify-border-light);
  overflow-y: auto;
}

.wf-panel--left { width: 200px; padding: 16px 12px; }
.wf-panel--right {
  width: 300px;
  padding: 16px;
  border-right: none;
  border-left: 1px solid var(--hify-border-light);
}

.wf-panel__title {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--hify-text-primary);
}

.wf-panel__hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--hify-text-placeholder);
}

.wf-palette-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px;
  margin-bottom: 8px;
  border-radius: 8px;
  border: 1px solid var(--hify-border-light);
  background: #fff;
  cursor: grab;
  transition: transform var(--hify-transition-fast), box-shadow var(--hify-transition-fast);
}

.wf-palette-item:hover {
  transform: translateY(-1px);
  box-shadow: var(--hify-shadow-sm);
  border-color: var(--accent);
}

.wf-palette-item:active { cursor: grabbing; }

.wf-palette-item__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--accent);
  margin-top: 4px;
  flex-shrink: 0;
}

.wf-palette-item__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--hify-text-primary);
}

.wf-palette-item__desc {
  font-size: 11px;
  color: var(--hify-text-secondary);
  margin-top: 2px;
}

.wf-config__tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--hify-text-secondary);
  line-height: 1.5;
}

.wf-config__delete {
  margin-top: 16px;
  width: 100%;
}

.is-readonly .wf-palette-item {
  pointer-events: none;
  opacity: 0.5;
}
</style>
