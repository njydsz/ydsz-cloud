<!--
  @fileoverview 抄送 Tab
  @description
    从原 approval-center/index.vue 拆分而来。
    负责"抄送我的"列表展示、已读 / 未读状态筛选、标记已读 / 全部已读。
    通过 emit('refresh-badge') 通知父组件刷新未读角标。
    配套自研工作流 v2 引擎，PC 端专用。
  @module views/workflow/approval-center/tabs/CCTab
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * @file 抄送 Tab
 * @module views/workflow/approval-center/tabs/CCTab
 * @description 从原 index.vue 拆分，负责"抄送我的"列表展示、已读状态筛选与标记已读。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { pageCc, ccMarkRead, ccMarkAllRead } from '@/api/workflow'
import type { FlowCcDTO, FlowCcQuery } from '@/api/workflow/types'
import { ProTable } from '@/components/common'
import type { ProTableColumn } from '@/components/common'
import { formatTime } from '../composables/useApprovalActions'

const emit = defineEmits<{
  /** 抄送未读数变化后通知父组件刷新角标 */
  (e: 'refresh-badge'): void
}>()

const router = useRouter()
const { t } = useI18n()

const ccQuery = reactive<FlowCcQuery>({
  readStatus: undefined,
  pageNum: 1,
  pageSize: 20,
})
const ccList = ref<FlowCcDTO[]>([])
const ccTotal = ref(0)
const ccLoading = ref(false)

const columns = computed<ProTableColumn<FlowCcDTO>[]>(() => [
  { prop: 'title', label: t('workflow.approval.columns.ccTitle'), minWidth: 220, showOverflowTooltip: true },
  { prop: 'flowName', label: t('workflow.approval.columns.flowName'), width: 160 },
  { prop: 'nodeName', label: t('workflow.approval.columns.triggerNode'), width: 120 },
  { prop: 'triggerUserName', label: t('workflow.approval.columns.triggerUserName'), width: 100 },
  { prop: 'content', label: t('workflow.approval.columns.content'), minWidth: 200, showOverflowTooltip: true },
  { prop: 'status', label: t('workflow.approval.columns.status'), width: 100, slot: 'status' },
  { prop: 'readTime', label: t('workflow.approval.columns.readTime'), width: 160, slot: 'readTime' },
  { prop: 'operation', label: t('workflow.approval.columns.operation'), width: 180, fixed: 'right', slot: 'operation' },
])

async function loadCc() {
  ccLoading.value = true
  try {
    const res = await pageCc(ccQuery)
    if (res.data?.code === 0) {
      const pageData = res.data?.data
      ccList.value = pageData?.list || []
      ccTotal.value = pageData?.total || 0
    }
  } finally {
    ccLoading.value = false
  }
}

/** 标记单条抄送为已读 */
async function quickCcRead(row: FlowCcDTO) {
  if (row.readStatus === 'READ') return
  const res = await ccMarkRead(row.id)
  if (res.data?.code === 0) {
    row.readStatus = 'READ'
    row.readAt = new Date().toISOString()
    emit('refresh-badge')
  }
}

/** 全部标记为已读 */
async function markAllCcRead() {
  const res = await ccMarkAllRead()
  if (res.data?.code === 0) {
    ElMessage.success(t('workflow.approval.messages.markAllReadSuccess', { count: res.data.data }))
    loadCc()
    emit('refresh-badge')
  }
}

function goInstance(instanceId: string) {
  router.push({ path: '/workflow/instance', query: { id: String(instanceId) } })
}

onMounted(loadCc)
</script>

<template>
  <div class="cc-tab">
    <div class="filter-bar">
      <el-select
        v-model="ccQuery.readStatus"
        :placeholder="t('workflow.approval.filter.readStatus')"
        clearable
        style="width: 140px"
        @change="loadCc"
      >
        <el-option :label="t('workflow.approval.status.unread')" value="UNREAD" />
        <el-option :label="t('workflow.approval.status.read')" value="READ" />
      </el-select>
      <el-button type="primary" @click="loadCc">{{ t('workflow.approval.buttons.query') }}</el-button>
      <el-button type="warning" @click="markAllCcRead">{{ t('workflow.approval.buttons.markAllRead') }}</el-button>
    </div>
    <ProTable
      :columns="columns"
      :data="ccList"
      :loading="ccLoading"
      :total="ccTotal"
      v-model:page="ccQuery.pageNum"
      v-model:size="ccQuery.pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :stripe="true"
      :border="false"
      :toolbar="false"
      row-key="id"
      @page-change="loadCc"
      @size-change="loadCc"
    >
      <template #status="{ row }">
        <el-tag :type="row.readStatus === 'READ' ? 'info' : 'danger'" size="small">
          {{ row.readStatus === 'READ' ? t('workflow.approval.status.read') : t('workflow.approval.status.unread') }}
        </el-tag>
      </template>
      <template #readTime="{ row }">{{ formatTime(row.createTime) }}</template>
      <template #operation="{ row }">
        <el-button
          v-if="row.readStatus === 'UNREAD'"
          size="small"
          text
          type="primary"
          @click="quickCcRead(row as FlowCcDTO)"
        >
          {{ t('workflow.approval.buttons.markRead') }}
        </el-button>
        <el-button size="small" text @click="goInstance(row.instanceId)">{{ t('workflow.approval.actions.viewFlow') }}</el-button>
      </template>
    </ProTable>
  </div>
</template>

<style scoped lang="scss">
.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

/* P2-6: 移动端 H5 适配 */
@media (max-width: 768px) {
  .filter-bar {
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: 8px;

    :deep(.el-select) {
      width: 100% !important;
      flex: 1 1 100%;
    }

    .el-button {
      flex: 1 1 calc(50% - 6px);
    }
  }

  :deep(.el-table) {
    .el-table__cell {
      padding: 6px 4px;
    }

    .cell {
      font-size: 13px;
    }
  }

  :deep(.pro-table__pagination) {
    margin-top: 8px;
    justify-content: center;

    .el-pagination__total,
    .el-pagination__sizes,
    .el-pagination__jump {
      display: none;
    }

    .el-pagination__pages {
      flex-wrap: wrap;
      justify-content: center;
    }
  }
}
</style>
