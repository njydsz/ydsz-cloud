<script setup lang="ts">
/**
 * @file 全部流程实例 Tab（管理员视图）
 * @module views/workflow/approval-center/tabs/AllTab
 * @description
 *   GAP-P0-1: 对标钉钉/飞书/企微审批中心"全部"Tab。
 *   仅 workflow:monitor:view 权限可见，展示当前租户下所有流程实例。
 *   复用 listAllInstances API（/workflow/engine/instance/all）。
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { listAllInstances } from '@/api/workflow'
import type { FlowInstanceDTO } from '@/api/workflow/types'
import {
  formatTime,
  instanceStatusLabel,
  instanceStatusType,
} from '../composables/useApprovalActions'

const router = useRouter()
const { t } = useI18n()

const query = reactive({
  page: 1,
  size: 20,
  businessType: undefined as string | undefined,
  flowStatus: undefined as string | undefined,
})
const list = ref<FlowInstanceDTO[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await listAllInstances(query)
    if (res.data?.code === 0) {
      // 后端返回 List<Map>（非分页对象），直接作为数组使用
      const data = res.data.data as unknown as FlowInstanceDTO[] | undefined
      list.value = data || []
      // 后端未返回 total，使用数组长度作为本地 total（前端分页友好降级）
      total.value = list.value.length
    }
  } finally {
    loading.value = false
  }
}

function goInstance(id: number) {
  router.push({ path: '/workflow/instance', query: { id: String(id) } })
}

onMounted(load)
</script>

<template>
  <div class="all-tab">
    <div class="filter-bar">
      <el-input
        v-model="query.businessType"
        :placeholder="t('workflow.approval.filter.businessTypePlaceholder')"
        clearable
        style="width: 200px"
      />
      <el-select
        v-model="query.flowStatus"
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
      <el-button type="primary" @click="load">{{ t('workflow.approval.buttons.query') }}</el-button>
    </div>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="title" :label="t('workflow.approval.columns.title')" min-width="220" show-overflow-tooltip />
      <el-table-column prop="flowName" :label="t('workflow.approval.columns.flowName')" width="160" />
      <el-table-column prop="businessNo" :label="t('workflow.approval.columns.businessNo')" width="160" />
      <el-table-column prop="initiatorName" :label="t('workflow.approval.columns.initiatorName')" width="120" />
      <el-table-column :label="t('workflow.approval.columns.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="instanceStatusType(row.flowStatus || row.status)" size="small">
            {{ instanceStatusLabel(row.flowStatus || row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="currentNodeName" :label="t('workflow.approval.columns.currentNodeName')" width="120" />
      <el-table-column :label="t('workflow.approval.columns.startTime')" width="160">
        <template #default="{ row }">{{ formatTime(row.startAt || row.startTime) }}</template>
      </el-table-column>
      <el-table-column :label="t('workflow.approval.columns.operation')" width="120" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="goInstance(row.id)">{{ t('workflow.approval.actions.viewFlow') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      class="pagination"
      @current-change="load"
      @size-change="load"
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

/* 移动端 H5 适配 */
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
