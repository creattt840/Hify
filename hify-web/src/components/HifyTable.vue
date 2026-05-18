<template>
  <div class="hify-table">
    <el-table
      v-loading="loading"
      :data="tableData"
      :border="false"
      stripe
      @sort-change="handleSortChange"
    >
      <el-table-column
        v-for="col in columns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        :sortable="col.sortable ?? false"
      >
        <template v-if="col.slot" #default="scope">
          <slot :name="col.slot" v-bind="scope" />
        </template>
      </el-table-column>
    </el-table>

    <div v-if="showPagination" class="hify-table__pagination">
      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @change="fetchData"
      />
    </div>
  </div>
</template>

<script setup lang="ts" generic="T extends Record<string, unknown>">
import { ref, reactive, onMounted, computed } from 'vue'

export interface ColumnConfig {
  prop: string
  label: string
  width?: string | number
  minWidth?: string | number
  sortable?: boolean
  slot?: string
}

const props = defineProps<{
  columns: ColumnConfig[]
  api: (params: { page: number; pageSize: number }) => Promise<{ list: T[]; total: number }>
  pageSize?: number
  showPagination?: boolean
}>()

const loading = ref(false)
const tableData = ref<T[]>([])

const pagination = reactive({
  current: 1,
  size: props.pageSize ?? 20,
  total: 0,
})

const showPagination = computed(() => props.showPagination !== false)

async function fetchData() {
  loading.value = true
  try {
    const res = await props.api({
      page: pagination.current,
      pageSize: pagination.size,
    })
    tableData.value = res.list ?? []
    pagination.total = res.total ?? 0
  } finally {
    loading.value = false
  }
}

function handleSortChange(_sort: { prop: string; order: string | null }) {
  // 预留排序回调，由父组件通过 api 参数处理
}

function refresh() {
  pagination.current = 1
  fetchData()
}

defineExpose({ refresh })

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.hify-table__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--hify-spacing-base);
}
</style>
