<!--
  @fileoverview 我发起的 Tab
  @description
    从原 approval-center/index.vue 拆分而来。
    负责"我发起的"流程实例列表展示、查询（流程编码 / 状态筛选）。
    配套自研工作流 v2 引擎，PC 端专用。
  @module views/workflow/approval-center/tabs/InitiatedTab
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * @file 我发起的 Tab
 * @module views/workflow/approval-center/tabs/InitiatedTab
 * @description 从原 index.vue 拆分，负责"我发起的"流程实例列表展示与查询。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { pageMyInstances } from '@/api/workflow'
import type { FlowInstanceDTO } from '@/api/workflow/types'
import { ProTable } from '@/components/common'
import type { ProTableColumn } from '@/components/common'
import {
  formatTime,
  instanceStatusLabel,
  instanceStatusType,
} from '../composables/useApprovalActions'

const router = useRouter()
const { t } = useI18n()

const myQuery = reactive({
  pageNum: 1,
  pageSize: 20,
  flowCode: undefined as string | undefined,
  status: undefined as string | undefined,
})
const myList = ref<FlowInstanceDTO[]>([])
const myTotal = ref(0)
const myLoading = ref(false)

const columns = computed<ProTableColumn<FlowInstanceDTO>[]>(() => [
  { prop: 'title', label: t('workflow.approval.columns.title'), minWidth: 220, showOverflowTooltip: true },
  { prop: 'flowName', label: t('workflow.approval.columns.flowName'), width: 160 },
  { prop: 'businessNo', label: t('workflow.approval.columns.businessNo'), width: 160 },
  { prop: 'status', label: t('workflow.approval.columns.status'), width: 100, slot: 'status' },
  { prop: 'currentNodeName', label: t('workflow.approval.columns.currentNodeName'), width: 120 },
  { prop: 'startTime', label: t('workflow.approval.columns.startTime'), width: 160, slot: 'startTime' },
  { prop: 'operation', label: t('workflow.approval.columns.operation'), width: 120, fixed: 'right', slot: 'operation' },
])

async function loadMy() {
  myLoading.value = true
  try {
    const res = await pageMyInstances(myQuery)
    if (res.data?.code === 0) {
      const pageData = res.data?.data
      myList.value = pageData?.list || []
      myTotal.value = pageData?.total || 0
    }
  } finally {
    myLoading.value = false
  }
}

function goInstance(id: number) {
  router.push({ path: '/workflow/instance', query: { id: String(id) } })
}

onMounted(loadMy)
</script>

<template>
  <div class="initiated-tab">
    <div class="filter-bar">
      <el-input
        v-model="myQuery.flowCode"
        :placeholder="t('workflow.approval.filter.flowCodePlaceholder')"
        clearable
        style="width: 200px"
      />
      <el-select
        v-model="myQuery.status"
        :placeholder="t('workflow.approval.columns.status')"
        clearable
        style="width: 140px"
      >
        <el-option :label="t('workflow.instance.status.RUNNING')" value="RUNNING" />
        <el-option :label="t('workflow.instance.status.SUSPENDED')" value="SUSPENDED" />
        <el-option :label="t('workflow.instance.status.COMPLETED')" value="COMPLETED" />
        <el-option :label="t('workflow.instance.status.TERMINATED')" value="TERMINATED" />
        <el-option :label="t('workflow.instance.status.REJECTED')" value="REJECTED" />
      </el-select>
      <el-button type="primary" @click="loadMy">{{ t('workflow.approval.buttons.query') }}</el-button>
    </div>
    <ProTable
      :columns="columns"
      :data="myList"
      :loading="myLoading"
      :total="myTotal"
      v-model:page="myQuery.pageNum"
      v-model:size="myQuery.pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :stripe="true"
      :border="false"
      :toolbar="false"
      row-key="id"
      @page-change="loadMy"
      @size-change="loadMy"
    >
      <template #status="{ row }">
        <el-tag :type="instanceStatusType(row.status)" size="small">
          {{ instanceStatusLabel(row.status) }}
        </el-tag>
      </template>
      <template #startTime="{ row }">{{ formatTime(row.startTime) }}</template>
      <template #operation="{ row }">
        <el-button size="small" text @click="goInstance(row.id)">{{ t('workflow.approval.actions.viewFlow') }}</el-button>
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

    :deep(.el-input),
    :deep(.el-select) {
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
