<script setup lang="ts">
import { ref, watch } from 'vue'
import { getOperationLogPage, getOperationLogDiff } from '@/api/audit'

interface FieldDiff {
  field: string
  oldValue: string
  newValue: string
  changeType: 'ADD' | 'DELETE' | 'MODIFY'
}

interface OperationLog {
  id: number
  operationType: string
  operatorName: string
  operatedAt: string
  description: string
}

const props = defineProps<{
  visible: boolean
  entityType: string
  entityId: number
}>()

const emit = defineEmits<{ 'update:visible': [val: boolean] }>()

const loading = ref(false)
const logs = ref<OperationLog[]>([])
const currentDiff = ref<FieldDiff[]>([])
const diffVisible = ref(false)
const currentLogId = ref<number>(0)

const fetchHistory = async () => {
  if (!props.entityId) return
  loading.value = true
  try {
    const { data } = await getOperationLogPage({
      entityType: props.entityType,
      entityId: props.entityId,
      page: 1,
      size: 50,
    })
    logs.value = (data?.list as unknown as OperationLog[]) ?? []
  } catch {
    logs.value = []
  } finally {
    loading.value = false
  }
}

const showDiff = async (logId: number) => {
  currentLogId.value = logId
  diffVisible.value = true
  try {
    const { data } = await getOperationLogDiff(logId)
    currentDiff.value = (data as unknown as FieldDiff[]) ?? []
  } catch {
    currentDiff.value = []
  }
}

const getChangeTypeColor = (type: string) => {
  switch (type) {
    case 'ADD': return 'success'
    case 'DELETE': return 'danger'
    case 'MODIFY': return 'warning'
    default: return 'info'
  }
}

const getChangeTypeLabel = (type: string) => {
  switch (type) {
    case 'ADD': return '新增'
    case 'DELETE': return '删除'
    case 'MODIFY': return '修改'
    default: return type
  }
}

watch(() => props.visible, (val) => {
  if (val) fetchHistory()
})
</script>

<template>
  <el-drawer
    :model-value="visible"
    title="变更历史"
    size="50%"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-table v-loading="loading" :data="logs" stripe>
      <el-table-column prop="operatedAt" label="时间" width="180" />
      <el-table-column prop="operatorName" label="操作人" width="120" />
      <el-table-column prop="operationType" label="操作类型" width="100" />
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column label="变更详情" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDiff(row.id)">查看 Diff</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="diffVisible" title="字段级变更对比" width="700px" append-to-body>
      <el-table :data="currentDiff" stripe>
        <el-table-column prop="field" label="字段" width="150" />
        <el-table-column prop="oldValue" label="原值" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'diff-old': row.changeType !== 'ADD' }">{{ row.oldValue || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="newValue" label="新值" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'diff-new': row.changeType !== 'DELETE' }">{{ row.newValue || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="changeType" label="变更类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getChangeTypeColor(row.changeType)" size="small">
              {{ getChangeTypeLabel(row.changeType) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.diff-old { color: var(--el-color-danger); text-decoration: line-through; }
.diff-new { color: var(--el-color-success); font-weight: 600; }
</style>
