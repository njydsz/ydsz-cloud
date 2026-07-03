<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getOperationLogPage, getOperationLogDiff } from '@/api/audit'

const { t } = useI18n()

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
    case 'ADD': return t('common.entityHistory.typeAdd')
    case 'DELETE': return t('common.entityHistory.typeDelete')
    case 'MODIFY': return t('common.entityHistory.typeModify')
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
    :title="t('common.entityHistory.title')"
    size="50%"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-table v-loading="loading" :data="logs" stripe>
      <el-table-column prop="operatedAt" :label="t('common.entityHistory.colTime')" width="180" />
      <el-table-column prop="operatorName" :label="t('common.entityHistory.colOperator')" width="120" />
      <el-table-column prop="operationType" :label="t('common.entityHistory.colOperationType')" width="100" />
      <el-table-column prop="description" :label="t('common.entityHistory.colDescription')" show-overflow-tooltip />
      <el-table-column :label="t('common.entityHistory.colChangeDetail')" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDiff(row.id)">{{ t('common.entityHistory.viewDiff') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="diffVisible" :title="t('common.entityHistory.diffTitle')" width="700px" append-to-body>
      <el-table :data="currentDiff" stripe>
        <el-table-column prop="field" :label="t('common.entityHistory.colField')" width="150" />
        <el-table-column prop="oldValue" :label="t('common.entityHistory.colOldValue')" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'diff-old': row.changeType !== 'ADD' }">{{ row.oldValue || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="newValue" :label="t('common.entityHistory.colNewValue')" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'diff-new': row.changeType !== 'DELETE' }">{{ row.newValue || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="changeType" :label="t('common.entityHistory.colChangeType')" width="100">
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
