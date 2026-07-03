<script setup lang="ts">
/**
 * @file 已办任务 Tab
 * @module views/workflow/approval-center/tabs/DoneTab
 * @description 从原 index.vue 拆分，负责"我的已办"列表展示与查询。
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { pageDoneTasks } from '@/api/workflow'
import type { FlowTaskDTO, FlowTaskQuery } from '@/api/workflow/types'
import { formatTime, durationLabel } from '../composables/useApprovalActions'

const router = useRouter()
const { t } = useI18n()

const doneQuery = reactive<FlowTaskQuery>({
  pageNum: 1,
  pageSize: 20,
})
const doneList = ref<FlowTaskDTO[]>([])
const doneTotal = ref(0)
const doneLoading = ref(false)

async function loadDone() {
  doneLoading.value = true
  try {
    const res = await pageDoneTasks(doneQuery)
    if (res.data?.code === 0) {
      doneList.value = res.data.data?.records || []
      doneTotal.value = res.data.data?.total || 0
    }
  } finally {
    doneLoading.value = false
  }
}

function goInstance(instanceId: number) {
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
    <el-table v-loading="doneLoading" :data="doneList" stripe>
      <el-table-column prop="title" :label="t('workflow.approval.columns.title')" min-width="220" show-overflow-tooltip />
      <el-table-column prop="flowName" :label="t('workflow.approval.columns.flowName')" width="160" />
      <el-table-column prop="nodeName" :label="t('workflow.approval.columns.nodeName')" width="120" />
      <el-table-column prop="comment" :label="t('workflow.approval.columns.comment')" min-width="180" show-overflow-tooltip />
      <el-table-column :label="t('workflow.approval.columns.duration')" width="100">
        <template #default="{ row }">{{ durationLabel(row.durationMs) }}</template>
      </el-table-column>
      <el-table-column :label="t('workflow.approval.columns.finishAt')" width="160">
        <template #default="{ row }">{{ formatTime(row.finishAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('workflow.approval.columns.operation')" width="120" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="goInstance(row.instanceId)">{{ t('workflow.approval.actions.viewFlow') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="doneQuery.pageNum"
      v-model:page-size="doneQuery.pageSize"
      :total="doneTotal"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      class="pagination"
      @current-change="loadDone"
      @size-change="loadDone"
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
