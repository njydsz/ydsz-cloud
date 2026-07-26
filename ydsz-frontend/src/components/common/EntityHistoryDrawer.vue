<!--
  @fileoverview 实体变更历史抽屉
  @description 业务详情页中调用的右侧抽屉，展示某条记录的操作日志与字段级 diff。
  - Props: visible / entityType / entityId
  - Emits: update:visible
  - 数据来源: @/api/audit（getOperationLogByBiz / getOperationLogDiff）
  - 场景: 审计、问题排查、变更追溯
  @module components/common/EntityHistoryDrawer
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * 实体变更历史抽屉
 *
 * 业务详情页中调用的右侧抽屉，展示某条记录的操作日志与字段级 diff。
 * 数据来源: @/api/audit（getOperationLogByBiz / getOperationLogDiff）
 * 场景: 审计、问题排查、变更追溯
 */
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getOperationLogByBiz, getOperationLogDiff } from '@/api/audit'
import type { OperationLogVO, FieldDiffVO } from '@/api/audit'

const { t } = useI18n()

const props = defineProps<{
  /** 弹窗可见性 */
  visible: boolean
  /** 实体类型（如 'project', 'contract'） */
  entityType: string
  /** 实体 ID */
  entityId: number
}>()

const emit = defineEmits<{
  /** 关闭弹窗 */
  'update:visible': [val: boolean]
}>()

/** 加载状态 */
const loading = ref(false)
/** 操作日志列表 */
const logs = ref<OperationLogVO[]>([])
/** 当前查看的字段级 diff */
const currentDiff = ref<FieldDiffVO[]>([])
/** Diff 弹窗显隐 */
const diffVisible = ref(false)
/** 当前查看 Diff 的日志 ID */
const currentLogId = ref<number>(0)

/** 拉取变更历史 */
const fetchHistory = async () => {
  if (!props.entityId) return
  loading.value = true
  try {
    const { data } = await getOperationLogByBiz(props.entityType, props.entityId, 50)
    logs.value = data ?? []
  } catch {
    logs.value = []
  } finally {
    loading.value = false
  }
}

/** 查看指定日志的字段级 Diff */
const showDiff = async (logId: number) => {
  currentLogId.value = logId
  diffVisible.value = true
  try {
    const { data } = await getOperationLogDiff(logId)
    currentDiff.value = data ?? []
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
      <el-table-column prop="createdAt" :label="t('common.entityHistory.colTime')" width="180" />
      <el-table-column prop="username" :label="t('common.entityHistory.colOperator')" width="120" />
      <el-table-column prop="action" :label="t('common.entityHistory.colOperationType')" width="120" />
      <el-table-column prop="module" :label="t('common.entityHistory.colDescription')" show-overflow-tooltip />
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
