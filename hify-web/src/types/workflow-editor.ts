import type { NodeRequest, EdgeRequest, WorkflowResponse } from '@/types/workflow'

export type WorkflowEditorMode = 'create' | 'edit' | 'view'

export interface WfNodeData {
  nodeKey: string
  nodeType: string
  title: string
  config: Record<string, unknown>
}

/** 画布节点（简化类型，避免 VueFlow 泛型过深） */
export interface WfGraphNode {
  id: string
  type: string
  position: { x: number; y: number }
  data: WfNodeData
  selected?: boolean
}

/** 画布连线 */
export interface WfGraphEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string
  targetHandle?: string
  label?: string
  data?: { condition?: string }
  animated?: boolean
  style?: Record<string, unknown>
  selectable?: boolean
  deletable?: boolean
  updatable?: boolean
}

export interface WorkflowMeta {
  name: string
  description: string
}

export const NODE_PALETTE = [
  { type: 'START', label: '开始', desc: '工作流入口', color: '#10B981' },
  { type: 'LLM', label: '大模型', desc: '调用 LLM 生成', color: '#6366F1' },
  { type: 'CONDITION', label: '条件分支', desc: 'true/false 分支', color: '#F59E0B' },
  { type: 'END', label: '结束', desc: '工作流出口', color: '#EF4444' },
] as const

const DEFAULT_TITLES: Record<string, string> = {
  START: '开始',
  LLM: '大模型',
  CONDITION: '条件判断',
  END: '结束',
}

const DEFAULT_CONFIGS: Record<string, Record<string, unknown>> = {
  START: {},
  LLM: {
    modelConfigId: null,
    prompt: '',
    outputVariable: 'output',
    temperature: 0.7,
  },
  CONDITION: {
    expression: '',
    outputVariable: 'result',
  },
  END: {},
}

let nodeCounter = 0

export function buildGraphEdge(params: {
  source: string
  target: string
  sourceHandle?: string
  sourceNodeType?: string
  id?: string
}): WfGraphEdge {
  let condition = ''
  if (params.sourceNodeType === 'CONDITION') {
    condition = params.sourceHandle === 'false' ? 'false' : 'true'
  }
  return {
    id: params.id ?? `e-${params.source}-${params.target}-${Date.now()}`,
    source: params.source,
    target: params.target,
    sourceHandle: params.sourceHandle,
    label: condition || undefined,
    data: { condition },
    animated: true,
    selectable: true,
    deletable: true,
    updatable: true,
    style: { stroke: '#6366F1', strokeWidth: 2 },
  }
}

export function resetNodeCounter() {
  nodeCounter = 0
}

export function createGraphNode(
  nodeType: string,
  position: { x: number; y: number },
  existingKey?: string,
): WfGraphNode {
  if (!existingKey) {
    nodeCounter += 1
  }
  const nodeKey = existingKey ?? `${nodeType.toLowerCase()}_${nodeCounter}`
  return {
    id: nodeKey,
    type: 'wfNode',
    position,
    data: {
      nodeKey,
      nodeType,
      title: DEFAULT_TITLES[nodeType] ?? nodeType,
      config: { ...(DEFAULT_CONFIGS[nodeType] ?? {}) },
    },
  }
}

export function workflowToGraph(workflow: WorkflowResponse): {
  nodes: WfGraphNode[]
  edges: WfGraphEdge[]
} {
  resetNodeCounter()
  const nodes: WfGraphNode[] = workflow.nodes.map((n, i) => {
    const ui = (n.config?.uiPosition as { x?: number; y?: number }) ?? {}
    return createGraphNode(
      n.nodeType,
      { x: ui.x ?? 80 + (i % 3) * 220, y: ui.y ?? 80 + Math.floor(i / 3) * 140 },
      n.nodeKey,
    )
  })
  for (const n of workflow.nodes) {
    const graphNode = nodes.find((g) => g.id === n.nodeKey)
    if (graphNode?.data) {
      graphNode.data.title = n.title || graphNode.data.title
      graphNode.data.config = { ...n.config }
      delete graphNode.data.config.uiPosition
    }
  }

  const edges: WfGraphEdge[] = workflow.edges.map((e, i) => {
    const condition = e.condition || ''
    let sourceHandle: string | undefined
    if (condition === 'true') sourceHandle = 'true'
    else if (condition === 'false') sourceHandle = 'false'
    const srcNode = workflow.nodes.find((n) => n.nodeKey === e.source)
    return buildGraphEdge({
      id: `edge-${e.source}-${e.target}-${i}`,
      source: e.source,
      target: e.target,
      sourceHandle,
      sourceNodeType: srcNode?.nodeType === 'CONDITION' ? 'CONDITION' : undefined,
    })
  })

  return { nodes, edges }
}

export function graphToPayload(
  nodes: WfGraphNode[],
  edges: WfGraphEdge[],
): { nodes: NodeRequest[]; edges: EdgeRequest[] } {
  const nodeRequests: NodeRequest[] = nodes.map((n, i) => ({
    nodeKey: n.data!.nodeKey,
    nodeType: n.data!.nodeType,
    title: n.data!.title,
    config: {
      ...n.data!.config,
      uiPosition: { x: Math.round(n.position.x), y: Math.round(n.position.y) },
    },
    sortOrder: i,
  }))

  const edgeRequests: EdgeRequest[] = edges.map((e, i) => ({
    source: e.source,
    target: e.target,
    condition: e.data?.condition ?? (typeof e.label === 'string' ? e.label : '') ?? '',
    sortOrder: i,
  }))

  return { nodes: nodeRequests, edges: edgeRequests }
}

export function createDefaultGraph(): { nodes: WfGraphNode[]; edges: WfGraphEdge[] } {
  resetNodeCounter()
  const start = createGraphNode('START', { x: 80, y: 160 }, 'start')
  const llm = createGraphNode('LLM', { x: 300, y: 160 }, 'classify')
  if (llm.data) {
    llm.data.title = '投诉判断'
    llm.data.config = {
      modelConfigId: null,
      prompt: '请判断以下用户消息是否属于投诉。只回答一个字："是"或"否"。\n\n用户消息：{{start.userMessage}}',
      outputVariable: 'isComplaint',
      temperature: 0.7,
    }
  }
  const cond = createGraphNode('CONDITION', { x: 520, y: 160 }, 'cond')
  if (cond.data) {
    cond.data.title = '是否投诉'
    cond.data.config = {
      expression: '{{classify.isComplaint}} == 是',
      outputVariable: 'isComplaint',
    }
  }
  const end = createGraphNode('END', { x: 740, y: 160 }, 'end')

  return {
    nodes: [start, llm, cond, end],
    edges: [
      buildGraphEdge({ id: 'e1', source: 'start', target: 'classify' }),
      buildGraphEdge({ id: 'e2', source: 'classify', target: 'cond' }),
      buildGraphEdge({ id: 'e3', source: 'cond', target: 'end', sourceHandle: 'true', sourceNodeType: 'CONDITION' }),
    ],
  }
}
