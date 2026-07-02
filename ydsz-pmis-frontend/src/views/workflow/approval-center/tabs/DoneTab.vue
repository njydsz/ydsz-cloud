<script setup lang="ts">
/**
 * @file 已办任务 Tab
 * @module views/workflow/approval-center/tabs/DoneTab
 * @description 从原 index.vue 拆分，负责"我的已办"列表展示与查询。
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { pageDoneTasks } from '@/api/workflow'
import type { FlowTaskDTO, FlowTaskQuery } from '@/api/workflow/types'
import { formatTime, durationLabel } from '../composables/useApprovalActions'

const router = useRouter()

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
        placeholder="流程编码"
        clearable
        style="width: 200px"
        @keyup.enter="loadDone"
      />
      <el-button type="primary" @click="loadDone">查询</el-button>
    </div>
    <el-table :data="doneList" v-loading="doneLoading" stripe>
      <el-table-column prop="title" label="审批事项" min-width="220" show-overflow-tooltip />
      <el-table-column prop="flowName" label="流程" width="160" />
      <el-table-column prop="nodeName" label="节点" width="120" />
      <el-table-column prop="comment" label="审批意见" min-width="180" show-overflow-tooltip />
      <el-table-column label="耗时" width="100">
        <template #default="{ row }">{{ durationLabel(row.durationMs) }}</template>
      </el-table-column>
      <el-table-column label="完成时间" width="160">
        <template #default="{ row }">{{ formatTime(row.finishAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="goInstance(row.instanceId)">查看流程</el-button>
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
</style>
