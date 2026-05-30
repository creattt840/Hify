<template>
  <div class="wf-canvas-wrap" @drop="onDrop" @dragover.prevent>
    <VueFlow
      v-model:nodes="localNodes as any"
      v-model:edges="localEdges as any"
      :node-types="nodeTypes"
      :default-edge-options="defaultEdgeOptions"
      :nodes-draggable="!readonly"
      :nodes-connectable="!readonly"
      :elements-selectable="!readonly"
      :edges-updatable="!readonly"
      :delete-key-code="readonly ? null : ['Backspace', 'Delete']"
      fit-view-on-init
      @nodes-change="onNodesChange"
      @edges-change="onEdgesChange"
      @node-click="onNodeClick"
      @edge-click="onEdgeClick"
      @pane-click="onPaneClick"
      @connect="onConnect"
      @edge-update="onEdgeUpdate"
    >
      <Background pattern-color="#D8DCE6" :gap="20" />
      <Controls v-if="!readonly" />
    </VueFlow>
  </div>
</template>

<script setup lang="ts">
import { markRaw, watch } from 'vue'
import {
  VueFlow,
  useVueFlow,
  applyEdgeChanges,
  applyNodeChanges,
  addEdge,
  updateEdge,
  connectionExists,
  type Connection,
  type EdgeChange,
  type NodeChange,
  type EdgeUpdateEvent,
  type NodeMouseEvent,
  type EdgeMouseEvent,
} from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import WfNode from './WfNode.vue'
import {
  createGraphNode,
  buildGraphEdge,
  type WfGraphNode,
  type WfGraphEdge,
} from '@/types/workflow-editor'

const props = defineProps<{
  nodes: WfGraphNode[]
  edges: WfGraphEdge[]
  readonly?: boolean
}>()

const emit = defineEmits<{
  'node-click': [id: string]
  'edge-click': [id: string]
  'pane-click': []
}>()

const nodeTypes = { wfNode: markRaw(WfNode) } as any

const defaultEdgeOptions = {
  animated: true,
  selectable: true,
  deletable: true,
  updatable: true,
  style: { stroke: '#6366F1', strokeWidth: 2 },
}

const { project, fitView } = useVueFlow()

const localNodes = defineModel<WfGraphNode[]>('nodes', { required: true })
const localEdges = defineModel<WfGraphEdge[]>('edges', { required: true })

watch(
  () => props.nodes.length,
  () => setTimeout(() => fitView({ padding: 0.2 }), 80),
  { immediate: true },
)

function onNodesChange(changes: NodeChange[]) {
  if (props.readonly) return
  localNodes.value = applyNodeChanges(changes, localNodes.value as any) as WfGraphNode[]
}

function onEdgesChange(changes: EdgeChange[]) {
  if (props.readonly) return
  localEdges.value = applyEdgeChanges(changes, localEdges.value as any) as WfGraphEdge[]
}

function onNodeClick({ node }: NodeMouseEvent) {
  emit('node-click', node.id)
}

function onEdgeClick({ edge }: EdgeMouseEvent) {
  emit('edge-click', edge.id)
}

function onPaneClick() {
  emit('pane-click')
}

function onDrop(event: DragEvent) {
  if (props.readonly) return
  const nodeType = event.dataTransfer?.getData('application/wf-node')
  if (!nodeType) return

  const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const position = project({
    x: event.clientX - bounds.left,
    y: event.clientY - bounds.top,
  })

  const newNode = createGraphNode(nodeType, position)
  localNodes.value = [...localNodes.value, newNode]
  emit('node-click', newNode.id)
}

function onConnect(connection: Connection) {
  if (props.readonly) return
  if (connectionExists(connection, localEdges.value as any)) return

  const sourceNode = localNodes.value.find((n) => n.id === connection.source)
  const newEdge = buildGraphEdge({
    source: connection.source!,
    target: connection.target!,
    sourceHandle: connection.sourceHandle ?? undefined,
    sourceNodeType: sourceNode?.data?.nodeType,
  })
  localEdges.value = addEdge(newEdge as any, localEdges.value as any) as WfGraphEdge[]
}

function onEdgeUpdate({ edge, connection }: EdgeUpdateEvent) {
  if (props.readonly) return
  let updated = updateEdge(edge as any, connection as any, localEdges.value as any) as WfGraphEdge[]
  updated = updated.map((e) => {
    if (e.id !== edge.id) return e
    const src = localNodes.value.find((n) => n.id === e.source)
    if (src?.data?.nodeType === 'CONDITION') {
      const condition = e.sourceHandle === 'false' ? 'false' : 'true'
      return { ...e, label: condition, data: { condition } }
    }
    return { ...e, label: undefined, data: { condition: '' } }
  })
  localEdges.value = updated
}

defineExpose({ fitView })
</script>

<style scoped>
.wf-canvas-wrap {
  flex: 1;
  min-width: 0;
  height: 100%;
  position: relative;
}

/* 加宽连线点击区域，便于选中 */
.wf-canvas-wrap :deep(.vue-flow__edge-path) {
  stroke-width: 2;
}

.wf-canvas-wrap :deep(.vue-flow__edge.selected .vue-flow__edge-path) {
  stroke: var(--hify-primary) !important;
  stroke-width: 3 !important;
}
</style>
