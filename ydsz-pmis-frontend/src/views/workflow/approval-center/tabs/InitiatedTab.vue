<script setup lang="ts">
/**
 * @file 我发起的 Tab
 * @module views/workflow/approval-center/tabs/InitiatedTab
 * @description 从原 index.vue 拆分，负责"我发起的"流程实例列表展示与查询。
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { pageMyInstances } from '@/api/workflow'
import type { FlowInstanceDTO } from '@/api/workflow/types'
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

async function loadMy() {
  myLoading.value = true
  try {
    const res = await pageMyInstances(myQuery)
    if (res.data?.code === 0) {
      myList.value = res.data.data?.records || []
      myTotal.value = res.data.data?.total || 0
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
    <el-table v-loading="myLoading" :data="myList" stripe>
      <el-table-column prop="title" :label="t('workflow.approval.columns.title')" min-width="220" show-overflow-tooltip />
      <el-table-column prop="flowName" :label="t('workflow.approval.columns.flowName')" width="160" />
      <el-table-column prop="businessNo" :label="t('workflow.approval.columns.businessNo')" width="160" />
      <el-table-column :label="t('workflow.approval.columns.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="(instanceStatusType(row.status) as any)" size="small">
            {{ instanceStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="currentNodeName" :label="t('workflow.approval.columns.currentNodeName')" width="120" />
      <el-table-column :label="t('workflow.approval.columns.startTime')" width="160">
        <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
      </el-table-column>
      <el-table-column :label="t('workflow.approval.columns.operation')" width="120" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="goInstance(row.id)">{{ t('workflow.approval.actions.viewFlow') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="myQuery.pageNum"
      v-model:page-size="myQuery.pageSize"
      :total="myTotal"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      class="pagination"
      @current-change="loadMy"
      @size-change="loadMy"
    />
  </div>
</template>

<style scoped lang="scss">
.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
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

  .pagination {
    margin-top: 8px;
    justify-content: center;

    :deep(.el-pagination__total),
    :deep(.el-pagination__sizes),
    :deep(.el-pagination__jump) {
      display: none;
    }

    :deep(.el-pagination__pages) {
      flex-wrap: wrap;
      justify-content: center;
    }
  }
}
</style>
