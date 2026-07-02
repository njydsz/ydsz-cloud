<script setup lang="ts">
/**
 * @file 我发起的 Tab
 * @module views/workflow/approval-center/tabs/InitiatedTab
 * @description 从原 index.vue 拆分，负责"我发起的"流程实例列表展示与查询。
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { pageMyInstances } from '@/api/workflow'
import type { FlowInstanceDTO } from '@/api/workflow/types'
import {
  formatTime,
  instanceStatusLabel,
  instanceStatusType,
} from '../composables/useApprovalActions'

const router = useRouter()

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
        placeholder="流程编码"
        clearable
        style="width: 200px"
      />
      <el-select
        v-model="myQuery.status"
        placeholder="状态"
        clearable
        style="width: 140px"
      >
        <el-option label="审批中" value="RUNNING" />
        <el-option label="已挂起" value="SUSPENDED" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已终止" value="TERMINATED" />
        <el-option label="已驳回" value="REJECTED" />
      </el-select>
      <el-button type="primary" @click="loadMy">查询</el-button>
    </div>
    <el-table v-loading="myLoading" :data="myList" stripe>
      <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
      <el-table-column prop="flowName" label="流程" width="160" />
      <el-table-column prop="businessNo" label="业务单号" width="160" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="(instanceStatusType(row.status) as any)" size="small">
            {{ instanceStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="currentNodeName" label="当前节点" width="120" />
      <el-table-column label="发起时间" width="160">
        <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="goInstance(row.id)">查看流程</el-button>
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
</style>
