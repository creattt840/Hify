<template>
  <div class="page">
    <!-- 页头 -->
    <header class="page__header">
      <div class="page__title-block">
        <el-button text @click="goBack" class="back-btn">
          ← 返回
        </el-button>
        <h2>{{ server?.name ?? 'MCP 服务详情' }}</h2>
        <p class="page__desc">{{ server?.endpoint }}</p>
      </div>
      <el-tag :type="server?.isEnabled ? 'success' : 'info'" size="large">
        {{ server?.isEnabled ? '已启用' : '已禁用' }}
      </el-tag>
    </header>

    <!-- Tab 切换 -->
    <el-tabs v-model="activeTab" class="page__tabs">
      <el-tab-pane label="基本信息" name="info">
        <div class="info-card">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="服务名称">{{ server?.name }}</el-descriptions-item>
            <el-descriptions-item label="服务 ID">{{ server?.id }}</el-descriptions-item>
            <el-descriptions-item label="端点地址" :span="2">{{ server?.endpoint }}</el-descriptions-item>
            <el-descriptions-item label="工具数量">{{ server?.tools?.length ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ server?.createdAt }}</el-descriptions-item>
          </el-descriptions>

          <!-- 工具列表 -->
          <div class="info-section" v-if="server?.tools?.length">
            <h4>已注册工具</h4>
            <el-table :data="server!.tools" size="small">
              <el-table-column prop="name" label="名称" width="200" />
              <el-table-column prop="description" label="描述" min-width="300" show-overflow-tooltip />
            </el-table>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="调试" name="debug">
        <div class="debug-layout">
          <!-- 左侧：工具列表 -->
          <aside class="debug-sidebar">
            <div class="debug-sidebar__title">工具列表</div>
            <div
              v-for="tool in server?.tools ?? []"
              :key="tool.id"
              class="debug-tool-item"
              :class="{ 'is-active': selectedTool?.id === tool.id }"
              @click="selectTool(tool)"
            >
              <span class="debug-tool-item__name">{{ tool.name }}</span>
              <span class="debug-tool-item__desc">{{ tool.description }}</span>
            </div>
            <div v-if="!server?.tools?.length" class="debug-sidebar__empty">
              暂无工具，请先测试连接
            </div>
          </aside>

          <!-- 右侧：调试面板 -->
          <main class="debug-main">
            <div class="debug-content">
              <template v-if="selectedTool">
                <!-- 工具描述 -->
                <div class="debug-desc">
                  <h4>{{ selectedTool.name }}</h4>
                  <p>{{ selectedTool.description }}</p>
                </div>

                <!-- 动态参数表单 -->
                <div class="debug-form" v-if="formFields.length > 0">
                  <div class="debug-form__title">调用参数</div>
                  <el-form label-width="100px" @submit.prevent>
                    <el-form-item
                      v-for="field in formFields"
                      :key="field.name"
                      :label="field.name"
                      :required="field.required"
                    >
                      <!-- string → 文本输入框 -->
                      <el-input
                        v-if="field.type === 'string'"
                        v-model="formValues[field.name]"
                        :placeholder="field.description ?? ''"
                      />
                      <!-- number / integer → 数字输入框 -->
                      <el-input-number
                        v-else-if="field.type === 'number' || field.type === 'integer'"
                        v-model="formValues[field.name]"
                        :precision="field.type === 'integer' ? 0 : 2"
                        style="width: 100%"
                      />
                      <!-- boolean → 开关 -->
                      <el-switch
                        v-else-if="field.type === 'boolean'"
                        v-model="formValues[field.name]"
                      />
                      <!-- 默认 → 文本输入框 -->
                      <el-input
                        v-else
                        v-model="formValues[field.name]"
                        :placeholder="field.description ?? ''"
                      />
                    </el-form-item>
                  </el-form>
                </div>

                <!-- 无参数提示 -->
                <div v-else class="debug-form--empty">
                  此工具无需参数，可直接调用
                </div>

                <!-- 调用按钮 -->
                <div class="debug-actions">
                  <el-button
                    type="primary"
                    :loading="calling"
                    :disabled="calling"
                    @click="handleCall"
                  >
                    {{ calling ? '调用中...' : '▶ 调用工具' }}
                  </el-button>
                  <span v-if="lastElapsed != null" class="debug-elapsed">
                    最近耗时：{{ lastElapsed }}ms
                  </span>
                </div>

                <!-- 调用结果 -->
                <div class="debug-result" v-if="currentResult != null">
                  <div class="debug-result__header">
                    <span class="debug-result__label">返回结果</span>
                    <el-tag
                      :type="currentIsError ? 'danger' : 'success'"
                      size="small"
                    >
                      {{ currentIsError ? '错误' : '成功' }}
                    </el-tag>
                  </div>
                  <pre class="debug-result__body">{{ currentResult }}</pre>
                </div>
              </template>

              <!-- 未选工具 -->
              <div v-else class="debug-placeholder">
                <p>← 请从左侧列表选择一个工具开始调试</p>
              </div>
            </div>

            <!-- 调用历史（固定在底部，不挤压结果区） -->
            <div class="debug-history" v-if="callHistory.length > 0">
              <h4>调用记录（最近 5 次）</h4>
              <div class="debug-history__list">
                <div
                  v-for="(record, idx) in callHistory"
                  :key="record.id"
                  class="debug-history__item"
                  @click="restoreRecord(record)"
                >
                  <div class="debug-history__top">
                    <el-tag
                      :type="record.isError ? 'danger' : 'success'"
                      size="small"
                      effect="dark"
                    >
                      {{ idx + 1 }}
                    </el-tag>
                    <span class="debug-history__tool">{{ record.toolName }}</span>
                    <span class="debug-history__time">{{ record.elapsedMs }}ms</span>
                  </div>
                  <div class="debug-history__args">
                    {{ JSON.stringify(record.arguments) }}
                  </div>
                </div>
              </div>
            </div>
          </main>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMcpServerDetail, debugMcpTool } from '@/api/mcp'
import type { McpServerResponse, McpToolResponse, DebugCallRecord } from '@/types/mcp'

const route = useRoute()
const router = useRouter()

const serverId = computed(() => Number(route.params.id))
const server = ref<McpServerResponse | null>(null)
const activeTab = ref('info')

function goBack() {
  router.push('/mcp-servers')
}

// ── 加载详情 ──
async function loadDetail() {
  try {
    server.value = await getMcpServerDetail(serverId.value)
  } catch {
    // 拦截器已提示
  }
}

onMounted(loadDetail)

// ═══════════════════════════════════════════════════════════
// 调试面板状态
// ═══════════════════════════════════════════════════════════

const selectedTool = ref<McpToolResponse | null>(null)

// 动态表单字段
interface FormField {
  name: string
  type: string
  description: string
  required: boolean
}

const formFields = ref<FormField[]>([])
const formValues = ref<Record<string, unknown>>({})

// 调用状态
const calling = ref(false)
const currentResult = ref<string | null>(null)
const currentIsError = ref(false)
const lastElapsed = ref<number | null>(null)

// 调用历史
let historyId = 0
const callHistory = reactive<DebugCallRecord[]>([])

/** 选中工具，解析 inputSchema 生成动态表单 */
function selectTool(tool: McpToolResponse) {
  selectedTool.value = tool
  currentResult.value = null
  lastElapsed.value = null

  // 解析 inputSchema
  const schema = tool.inputSchema as Record<string, unknown> | null | undefined
  const props = (schema?.properties ?? {}) as Record<string, Record<string, unknown>>
  const required: string[] = (schema?.required as string[]) ?? []

  formFields.value = Object.entries(props).map(([name, def]) => ({
    name,
    type: (def.type as string) ?? 'string',
    description: (def.description as string) ?? '',
    required: required.includes(name),
  }))

  // 初始化表单值
  const vals: Record<string, unknown> = {}
  for (const f of formFields.value) {
    if (f.type === 'boolean') vals[f.name] = false
    else if (f.type === 'number' || f.type === 'integer') vals[f.name] = undefined
    else vals[f.name] = ''
  }
  formValues.value = vals
}

/** 调用工具 */
async function handleCall() {
  if (!selectedTool.value || calling.value) return

  // 构建 arguments：过滤掉空字符串和 undefined
  const args: Record<string, unknown> = {}
  for (const f of formFields.value) {
    const v = formValues.value[f.name]
    if (v === '' || v === undefined || v === null) continue
    args[f.name] = v
  }

  calling.value = true
  try {
    const data = await debugMcpTool(serverId.value, {
      toolName: selectedTool.value.name,
      arguments: args,
    })
    currentResult.value = data.result

    // 判断是否错误：以 {"error" 开头
    currentIsError.value = data.result.trim().startsWith('{"error"')
    lastElapsed.value = data.elapsedMs

    // 记录历史（最多 5 条）
    callHistory.unshift({
      id: ++historyId,
      toolName: selectedTool.value.name,
      arguments: { ...args },
      result: data.result,
      elapsedMs: data.elapsedMs,
      isError: currentIsError.value,
      calledAt: new Date().toISOString(),
    })
    if (callHistory.length > 5) {
      callHistory.pop()
    }
  } catch {
    // 拦截器已提示
  } finally {
    calling.value = false
  }
}

/** 恢复历史记录 */
function restoreRecord(record: DebugCallRecord) {
  // 找到对应工具
  const tool = server.value?.tools?.find(t => t.name === record.toolName)
  if (tool) {
    selectTool(tool)
    // 回填参数
    for (const key of Object.keys(record.arguments)) {
      if (key in formValues.value) {
        formValues.value[key] = record.arguments[key]
      }
    }
  }
  currentResult.value = record.result
  currentIsError.value = record.isError
  lastElapsed.value = record.elapsedMs
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
  margin: 0;
  font-size: var(--hify-font-size-xxl);
  font-weight: 600;
  color: var(--hify-text-primary);
}

.page__desc {
  margin: 4px 0 0;
  font-size: var(--hify-font-size-sm);
  color: var(--hify-text-secondary);
  font-family: monospace;
}

.back-btn {
  font-size: var(--hify-font-size-sm);
  padding: 0;
}

.page__tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* ── 基本信息 ── */
.info-card {
  background: var(--hify-bg-container);
  border: 1px solid var(--hify-border);
  border-radius: var(--hify-radius-lg);
  padding: var(--hify-spacing-lg);
}

.info-section {
  margin-top: var(--hify-spacing-lg);
}

.info-section h4 {
  margin: 0 0 var(--hify-spacing-sm);
  font-size: var(--hify-font-size-base);
  color: var(--hify-text-primary);
}

/* ── 调试布局 ── */
.debug-layout {
  display: flex;
  gap: var(--hify-spacing-base);
  height: calc(100vh - 200px);
  min-height: 520px;
  overflow: hidden;
}

/* 左侧工具列表 */
.debug-sidebar {
  width: 280px;
  flex-shrink: 0;
  background: var(--hify-bg-container);
  border: 1px solid var(--hify-border);
  border-radius: var(--hify-radius-lg);
  overflow-y: auto;
}

.debug-sidebar__title {
  padding: 14px 16px;
  font-size: 13px;
  font-weight: 600;
  color: var(--hify-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid var(--hify-border);
}

.debug-sidebar__empty {
  padding: 32px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--hify-text-placeholder);
}

.debug-tool-item {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--hify-border-light, var(--hify-border));
  transition: background var(--hify-transition-fast);
}

.debug-tool-item:hover {
  background: var(--hify-bg-hover);
}

.debug-tool-item.is-active {
  background: var(--hify-primary-light, rgba(64, 158, 255, 0.1));
  border-left: 3px solid var(--hify-primary);
}

.debug-tool-item__name {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--hify-text-primary);
  font-family: monospace;
}

.debug-tool-item__desc {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--hify-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 右侧调试面板 */
.debug-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  background: var(--hify-bg-container);
  border: 1px solid var(--hify-border);
  border-radius: var(--hify-radius-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.debug-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--hify-spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--hify-spacing-base);
}

.debug-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--hify-text-placeholder);
  font-size: 14px;
  min-height: 200px;
}

.debug-desc h4 {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 600;
  color: var(--hify-text-primary);
  font-family: monospace;
}

.debug-desc p {
  margin: 0;
  font-size: 13px;
  color: var(--hify-text-secondary);
  line-height: 1.6;
}

.debug-form {
  background: var(--hify-bg-page);
  border: 1px solid var(--hify-border);
  border-radius: var(--hify-radius-base);
  padding: var(--hify-spacing-base);
}

.debug-form__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--hify-text-secondary);
  margin-bottom: var(--hify-spacing-sm);
}

.debug-form--empty {
  font-size: 13px;
  color: var(--hify-text-placeholder);
  padding: var(--hify-spacing-base);
  background: var(--hify-bg-page);
  border-radius: var(--hify-radius-base);
  text-align: center;
}

.debug-actions {
  display: flex;
  align-items: center;
  gap: var(--hify-spacing-base);
}

.debug-elapsed {
  font-size: 13px;
  color: var(--hify-text-secondary);
}

/* 结果展示 */
.debug-result {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--hify-border);
  border-radius: var(--hify-radius-base);
  overflow: hidden;
  min-height: 160px;
}

.debug-result__header {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--hify-bg-page);
  border-bottom: 1px solid var(--hify-border);
}

.debug-result__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--hify-text-secondary);
}

.debug-result__body {
  flex: 1;
  margin: 0;
  padding: 14px;
  font-size: 13px;
  font-family: 'Cascadia Code', 'Fira Code', monospace;
  white-space: pre-wrap;
  word-break: break-word;
  min-height: 120px;
  max-height: 320px;
  overflow-y: auto;
  background: #1a1b26;
  color: #a9b1d6;
  line-height: 1.7;
}

/* ── 调用历史 ── */
.debug-history {
  flex-shrink: 0;
  border-top: 1px solid var(--hify-border);
  padding: var(--hify-spacing-sm) var(--hify-spacing-lg) var(--hify-spacing-base);
  background: var(--hify-bg-page);
}

.debug-history h4 {
  margin: 0 0 var(--hify-spacing-sm);
  font-size: 13px;
  font-weight: 600;
  color: var(--hify-text-secondary);
}

.debug-history__list {
  max-height: 160px;
  overflow-y: auto;
}

.debug-history__item {
  padding: 8px 10px;
  border-radius: var(--hify-radius-base);
  cursor: pointer;
  transition: background var(--hify-transition-fast);
  margin-bottom: 6px;
}

.debug-history__item:hover {
  background: var(--hify-bg-hover);
}

.debug-history__top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.debug-history__tool {
  font-size: 13px;
  font-weight: 600;
  font-family: monospace;
  color: var(--hify-text-primary);
}

.debug-history__time {
  margin-left: auto;
  font-size: 12px;
  color: var(--hify-text-placeholder);
}

.debug-history__args {
  margin-top: 4px;
  font-size: 12px;
  color: var(--hify-text-secondary);
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
