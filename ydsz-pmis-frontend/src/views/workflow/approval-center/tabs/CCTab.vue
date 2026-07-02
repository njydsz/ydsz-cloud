<script setup lang="ts">
/**
 * @file 抄送 Tab
 * @module views/workflow/approval-center/tabs/CCTab
 * @description 从原 index.vue 拆分，负责"抄送我的"列表展示、已读状态筛选与标记已读。
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { pageCc, ccMarkRead, ccMarkAllRead } from '@/api/workflow'
import type { FlowCcDTO, FlowCcQuery } from '@/api/workflow/types'
import { formatTime } from '../composables/useApprovalActions'

const emit = defineEmits<{
  /** 抄送未读数变化后通知父组件刷新角标 */
  (e: 'refresh-badge'): void
}>()

const router = useRouter()

const ccQuery = reactive<FlowCcQuery>({
  readStatus: undefined,
  pageNum: 1,
  pageSize: 20,
})
const ccList = ref<FlowCcDTO[]>([])
const ccTotal = ref(0)
const ccLoading = ref(false)

async function loadCc() {
  ccLoading.value = true
  try {
    const res = await pageCc(ccQuery)
    if (res.data?.code === 0) {
      ccList.value = res.data.data?.records || []
      ccTotal.value = res.data.data?.total || 0
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
    ElMessage.success(`已全部标记为已读（${res.data.data} 条）`)
    loadCc()
    emit('refresh-badge')
  }
}

function goInstance(instanceId: number) {
  router.push({ path: '/workflow/instance', query: { id: String(instanceId) } })
}

onMounted(loadCc)
</script>

<template>
  <div class="cc-tab">
    <div class="filter-bar">
      <el-select
        v-model="ccQuery.readStatus"
        placeholder="已读状态"
        clearable
        style="width: 140px"
        @change="loadCc"
      >
        <el-option label="未读" value="UNREAD" />
        <el-option label="已读" value="READ" />
      </el-select>
      <el-button type="primary" @click="loadCc">查询</el-button>
      <el-button type="warning" @click="markAllCcRead">全部标为已读</el-button>
    </div>
    <el-table v-loading="ccLoading" :data="ccList" stripe>
      <el-table-column prop="title" label="抄送标题" min-width="220" show-overflow-tooltip />
      <el-table-column prop="flowName" label="流程" width="160" />
      <el-table-column prop="nodeName" label="触发节点" width="120" />
      <el-table-column prop="triggerUserName" label="发起人" width="100" />
      <el-table-column prop="content" label="意见/内容" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.readStatus === 'READ' ? 'info' : 'danger'" size="small">
            {{ row.readStatus === 'READ' ? '已读' : '未读' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="抄送时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.readStatus === 'UNREAD'"
            size="small"
            text
            type="primary"
            @click="quickCcRead(row)"
          >
            标为已读
          </el-button>
          <el-button size="small" text @click="goInstance(row.instanceId)">查看流程</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="ccQuery.pageNum"
      v-model:page-size="ccQuery.pageSize"
      :total="ccTotal"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      class="pagination"
      @current-change="loadCc"
      @size-change="loadCc"
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
