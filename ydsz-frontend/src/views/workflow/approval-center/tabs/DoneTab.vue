<!--
  @fileoverview 已办任务 Tab
  @description
    从原 approval-center/index.vue 拆分而来。
    负责"我的已办"列表展示、查询（含时间范围 / 流程类型筛选）。
    配套自研工作流 v2 引擎，PC 端专用。
  @module views/workflow/approval-center/tabs/DoneTab
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * @file 已办任务 Tab
 * @module views/workflow/approval-center/tabs/DoneTab
 * @description 从原 index.vue 拆分，负责"我的已办"列表展示与查询。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { pageDoneTasks } from '@/api/workflow'
import type { FlowTaskDTO, FlowTaskQuery } from '@/api/workflow/types'
import { ProTable } from '@/components/common'
import type { ProTableColumn } from '@/components/common'
import { formatTime, durationLabel } from '../composables/useApprovalActions'

const router = useRouter()
const { t } = useI18n()

const doneQuery = reactive<FlowTaskQuery>({
  pageNum: 1,
  pageSize: 20,
})
/** 已办任务列表数据 */
const doneList = ref<FlowTaskDTO[]>([])
/** 已办任务列表总数 */
const doneTotal = ref(0)
/** 已办任务加载状态 */
const doneLoading = ref(false)

const columns = computed<ProTableColumn<FlowTaskDTO>[]>(() => [
  { prop: 'title', label: t('workflow.approval.columns.title'), minWidth: 220, showOverflowTooltip: true },
  { prop: 'flowName', label: t('workflow.approval.columns.flowName'), width: 160 },
  { prop: 'nodeName', label: t('workflow.approval.columns.nodeName'), width: 120 },
  { prop: 'comment', label: t('workflow.approval.columns.comment'), minWidth: 180, showOverflowTooltip: true },
  { prop: 'duration', label: t('workflow.approval.columns.duration'), width: 100, slot: 'duration' },
  { prop: 'finishAt', label: t('workflow.approval.columns.finishAt'), width: 160, slot: 'finishAt' },
  { prop: 'operation', label: t('workflow.approval.columns.operation'), width: 120, fixed: 'right', slot: 'operation' },
])

async function loadDone() {
  doneLoading.value = true
  try {
    const res = await pageDoneTasks(doneQuery)
    if (res.data?.code === 0) {
      const pageData = res.data?.data
      doneList.value = pageData?.list || []
      doneTotal.value = pageData?.total || 0
    }
  } finally {
    doneLoading.value = false
  }
}

function goInstance(instanceId: string) {
  router.push({ path: '/workflow/instance', query: { id: String(instanceId) } })
}

onMounted(loadDone)
</script>

<template>
  <div class="done-tab">
    <div class="filter-bar">
      <el-input
        v-model="doneQuery.flowCode"
        :placeholder="t('workflow.approval.filter.flowCodePlaceholder')"
        clearable
        style="width: 200px"
        @keyup.enter="loadDone"
      />
      <el-button type="primary" @click="loadDone">{{ t('workflow.approval.buttons.query') }}</el-button>
    </div>
    <ProTable
      :columns="columns"
      :data="doneList"
      :loading="doneLoading"
      :total="doneTotal"
      v-model:page="doneQuery.pageNum"
      v-model:size="doneQuery.pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :stripe="true"
      :border="false"
      :toolbar="false"
      row-key="id"
      @page-change="loadDone"
      @size-change="loadDone"
    >
      <template #duration="{ row }">{{ durationLabel(row.durationMs) }}</template>
      <template #finishAt="{ row }">{{ formatTime(row.finishAt) }}</template>
      <template #operation="{ row }">
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

    :deep(.el-input) {
      width: 100% !important;
      flex: 1 1 100%;
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
