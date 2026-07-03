<script setup lang="ts">
/**
 * @file 抄送 Tab
 * @module views/workflow/approval-center/tabs/CCTab
 * @description 从原 index.vue 拆分，负责"抄送我的"列表展示、已读状态筛选与标记已读。
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { pageCc, ccMarkRead, ccMarkAllRead } from '@/api/workflow'
import type { FlowCcDTO, FlowCcQuery } from '@/api/workflow/types'
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
    ElMessage.success(t('workflow.approval.messages.markAllReadSuccess', { count: res.data.data }))
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
    <el-table v-loading="ccLoading" :data="ccList" stripe>
      <el-table-column prop="title" :label="t('workflow.approval.columns.ccTitle')" min-width="220" show-overflow-tooltip />
      <el-table-column prop="flowName" :label="t('workflow.approval.columns.flowName')" width="160" />
      <el-table-column prop="nodeName" :label="t('workflow.approval.columns.triggerNode')" width="120" />
      <el-table-column prop="triggerUserName" :label="t('workflow.approval.columns.triggerUserName')" width="100" />
      <el-table-column prop="content" :label="t('workflow.approval.columns.content')" min-width="200" show-overflow-tooltip />
      <el-table-column :label="t('workflow.approval.columns.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.readStatus === 'READ' ? 'info' : 'danger'" size="small">
            {{ row.readStatus === 'READ' ? t('workflow.approval.status.read') : t('workflow.approval.status.unread') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('workflow.approval.columns.readTime')" width="160">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column :label="t('workflow.approval.columns.operation')" width="180" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.readStatus === 'UNREAD'"
            size="small"
            text
            type="primary"
            @click="quickCcRead(row)"
          >
            {{ t('workflow.approval.buttons.markRead') }}
          </el-button>
          <el-button size="small" text @click="goInstance(row.instanceId)">{{ t('workflow.approval.actions.viewFlow') }}</el-button>
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
