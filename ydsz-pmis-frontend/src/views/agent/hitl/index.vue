<!--
  @fileoverview HITL 人工审批中心
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { approve, cancel, page, pending, reject } from '@/api/agent/hitl'
import type { HitlApprovalRequest } from '@/api/agent/hitl/types'
import { PC } from '@/constants/permissionCodes'
import type { PageResult } from '@/utils/request'

const { t } = useI18n()

const loading = ref(false)
const list = ref<HitlApprovalRequest[]>([])
const total = ref(0)
const filter = reactive({
  status: '' as string,
  agentType: '' as string,
})
const pageNo = ref(1)
const pageSize = ref(20)
const pendingCount = ref(0)

const STATUS_OPTIONS = computed(() => [
  { value: '', label: t('agent.hitl.status.ALL') },
  { value: 'PENDING', label: t('agent.hitl.status.PENDING') },
  { value: 'APPROVED', label: t('agent.hitl.status.APPROVED') },
  { value: 'REJECTED', label: t('agent.hitl.status.REJECTED') },
  { value: 'CANCELLED', label: t('agent.hitl.status.CANCELLED') },
])

const AGENT_TYPE_OPTIONS = computed(() => [
  { value: '', label: t('agent.hitl.agentType.ALL') },
  { value: 'RISK_WARNING', label: t('agent.hitl.agentType.RISK_WARNING') },
  { value: 'RESOURCE_RECOMMEND', label: t('agent.hitl.agentType.RESOURCE_RECOMMEND') },
  { value: 'PROFIT_FORECAST', label: t('agent.hitl.agentType.PROFIT_FORECAST') },
  { value: 'APPROVER_RECOMMEND', label: t('agent.hitl.agentType.APPROVER_RECOMMEND') },
  { value: 'COMMENT_DRAFT', label: t('agent.hitl.agentType.COMMENT_DRAFT') },
  { value: 'FLOW_GENERATOR', label: t('agent.hitl.agentType.FLOW_GENERATOR') },
])

async function loadList() {
  loading.value = true
  try {
    const { data } = await page(pageNo.value, pageSize.value, {
      status: filter.status || undefined,
      agentType: filter.agentType || undefined,
    })
    const result = data as unknown as PageResult<HitlApprovalRequest> | undefined
    list.value = result?.list ?? []
    total.value = result?.total ?? 0
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.hitl.messages.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function loadPendingCount() {
  try {
    const { data } = await pending(1)
    pendingCount.value = (data as HitlApprovalRequest[])?.length || 0
  } catch { /* 静默 */ }
}

function onFilterChange() { pageNo.value = 1; loadList() }
function onPageChange(p: number) { pageNo.value = p; loadList() }
function onSizeChange(s: number) { pageSize.value = s; pageNo.value = 1; loadList() }

// ============= 审批操作 =============
const actionDialogVisible = ref(false)
const actionType = ref<'approve' | 'reject' | 'cancel' | 'batch-approve' | 'batch-reject'>('approve')
const currentRow = ref<HitlApprovalRequest | null>(null)
const comment = ref('')
const actionLoading = ref(false)

// ============= 批量审批 =============
/** 选中的待审批行 */
const selectedRows = ref<HitlApprovalRequest[]>([])
/** 批量操作 loading */
const batchLoading = ref(false)
/** vxe-table ref */
const tableRef = ref<any>(null)

/** 表格选择变更事件 */
function onCheckboxChange({ records }: { records: HitlApprovalRequest[] }) {
  selectedRows.value = (records || []).filter(r => r.status === 'PENDING')
}

/** 批量批准 */
function openBatchApprove() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning(t('agent.hitl.messages.selectFirst'))
    return
  }
  actionType.value = 'batch-approve'
  comment.value = ''
  actionDialogVisible.value = true
}

/** 批量拒绝 */
function openBatchReject() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning(t('agent.hitl.messages.selectFirst'))
    return
  }
  actionType.value = 'batch-reject'
  comment.value = ''
  actionDialogVisible.value = true
}

/** 执行批量操作 */
async function handleBatchAction() {
  if (selectedRows.value.length === 0) return
  batchLoading.value = true
  actionLoading.value = true
  const dto = {
    approverId: 'current-user',
    approverName: 'current-user',
    comment: comment.value,
  }
  const isApprove = actionType.value === 'batch-approve'
  const action = isApprove ? approve : reject
  const successKey = isApprove ? 'agent.hitl.messages.batchApproveSuccess' : 'agent.hitl.messages.batchRejectSuccess'
  const failKey = isApprove ? 'agent.hitl.messages.batchApproveFail' : 'agent.hitl.messages.batchRejectFail'
  try {
    const results = await Promise.allSettled(
      selectedRows.value.map(r => action(r.id, dto)),
    )
    const succeeded = results.filter(r => r.status === 'fulfilled').length
    const failed = results.filter(r => r.status === 'rejected').length
    if (failed > 0) {
      ElMessage.warning(t(failKey, { succeeded, failed }))
    } else {
      ElMessage.success(t(successKey, { count: succeeded }))
    }
    actionDialogVisible.value = false
    selectedRows.value = []
    if (tableRef.value) {
      tableRef.value.clearCheckboxRow()
    }
    loadList()
    loadPendingCount()
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.hitl.messages.actionFailed'))
  } finally {
    batchLoading.value = false
    actionLoading.value = false
  }
}

function openActionDialog(row: HitlApprovalRequest, type: 'approve' | 'reject' | 'cancel') {
  currentRow.value = row
  actionType.value = type
  comment.value = ''
  actionDialogVisible.value = true
}

async function handleAction() {
  if (!currentRow.value) return
  // 批量操作走单独路径
  if (actionType.value === 'batch-approve' || actionType.value === 'batch-reject') {
    await handleBatchAction()
    return
  }
  actionLoading.value = true
  try {
    const dto = {
      approverId: 'current-user',
      approverName: 'current-user',
      comment: comment.value,
    }
    if (actionType.value === 'approve') {
      await approve(currentRow.value.id, dto)
      ElMessage.success(t('agent.hitl.messages.approveSuccess'))
    } else if (actionType.value === 'reject') {
      await reject(currentRow.value.id, dto)
      ElMessage.success(t('agent.hitl.messages.rejectSuccess'))
    } else {
      await cancel(currentRow.value.id, dto)
      ElMessage.success(t('agent.hitl.messages.cancelSuccess'))
    }
    actionDialogVisible.value = false
    loadList()
    loadPendingCount()
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.hitl.messages.actionFailed'))
  } finally {
    actionLoading.value = false
  }
}

// ============= 详情 =============
const detailVisible = ref(false)
const detailRow = ref<HitlApprovalRequest | null>(null)

function openDetail(row: HitlApprovalRequest) {
  detailRow.value = row
  detailVisible.value = true
}

function statusTagType(status: string): 'success' | 'info' | 'warning' | 'danger' {
  switch (status) {
    case 'PENDING': return 'warning'
    case 'APPROVED': return 'success'
    case 'REJECTED': return 'danger'
    case 'CANCELLED': return 'info'
    case 'EXPIRED': return 'info'
    default: return 'info'
  }
}

function snapshotFmt(snapshot?: string): string {
  if (!snapshot) return ''
  try { return JSON.stringify(JSON.parse(snapshot), null, 2) } catch { return snapshot }
}

onMounted(() => {
  loadList()
  loadPendingCount()
})
</script>

<template>
  <div class="hitl-page">
    <!-- KPI -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card warning">
          <div class="kpi-title">{{ t('agent.hitl.kpi.pending') }}</div>
          <div class="kpi-value">{{ pendingCount }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索栏 -->
    <el-card shadow="never" class="filter-card" style="margin-top: 16px">
      <el-form inline>
        <el-form-item :label="t('agent.hitl.search.status')">
          <el-select v-model="filter.status" @change="onFilterChange" style="width: 140px">
            <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('agent.hitl.search.agentType')">
          <el-select v-model="filter.agentType" @change="onFilterChange" style="width: 180px">
            <el-option v-for="o in AGENT_TYPE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button :icon="'Refresh'" @click="loadList">{{ t('agent.hitl.buttons.refresh') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button-group>
          <el-button v-permission="[PC.AGENT_HITL_APPROVE]" type="success" size="small" :icon="'Check'"
            :disabled="selectedRows.length === 0" :loading="batchLoading" @click="openBatchApprove">
            {{ t('agent.hitl.buttons.batchApprove') }} ({{ selectedRows.length }})
          </el-button>
          <el-button v-permission="[PC.AGENT_HITL_APPROVE]" type="danger" size="small" :icon="'Close'"
            :disabled="selectedRows.length === 0" :loading="batchLoading" @click="openBatchReject">
            {{ t('agent.hitl.buttons.batchReject') }} ({{ selectedRows.length }})
          </el-button>
        </el-button-group>
      </div>
      <vxe-table ref="tableRef" :data="list" :loading="loading" stripe :checkbox-config="{ highlight: true }" @checkbox-change="onCheckboxChange" @checkbox-all="onCheckboxChange">
        <vxe-column type="checkbox" width="48" />
        <vxe-column type="seq" width="56" title="#" />
        <vxe-column field="agentType" :title="t('agent.hitl.columns.agentType')" width="160">
          <template #default="{ row }">
            <el-tag size="small">{{ row.agentType }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="bizType" :title="t('agent.hitl.columns.bizType')" width="100" />
        <vxe-column field="bizRef" :title="t('agent.hitl.columns.bizRef')" width="140" show-overflow />
        <vxe-column field="question" :title="t('agent.hitl.columns.question')" min-width="200" show-overflow />
        <vxe-column field="recommendation" :title="t('agent.hitl.columns.recommendation')" min-width="150" show-overflow />
        <vxe-column field="status" :title="t('agent.hitl.columns.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="dark">{{ row.status }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="createdAt" :title="t('agent.hitl.columns.createdAt')" width="170" />
        <vxe-column :title="t('agent.hitl.columns.action')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">
              {{ t('agent.hitl.buttons.detail') }}
            </el-button>
            <template v-if="row.status === 'PENDING'">
              <el-button v-permission="[PC.AGENT_HITL_APPROVE]" link type="success" size="small" :icon="'Check'"
                @click="openActionDialog(row, 'approve')">
                {{ t('agent.hitl.buttons.approve') }}
              </el-button>
              <el-button v-permission="[PC.AGENT_HITL_APPROVE]" link type="danger" size="small" :icon="'Close'"
                @click="openActionDialog(row, 'reject')">
                {{ t('agent.hitl.buttons.reject') }}
              </el-button>
              <el-button v-permission="[PC.AGENT_HITL_APPROVE]" link type="info" size="small"
                @click="openActionDialog(row, 'cancel')">
                {{ t('agent.hitl.buttons.cancel') }}
              </el-button>
            </template>
          </template>
        </vxe-column>
      </vxe-table>
      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </el-card>

    <!-- 审批操作对话框 -->
    <el-dialog v-model="actionDialogVisible" :title="t(`agent.hitl.action.${actionType}`)" width="500px">
      <el-alert v-if="actionType.startsWith('batch')" type="info" :closable="false" style="margin-bottom: 12px">
        {{ t('agent.hitl.action.batchHint', { count: selectedRows.length }) }}
      </el-alert>
      <el-form label-width="80px">
        <el-form-item :label="t('agent.hitl.action.comment')">
          <el-input v-model="comment" type="textarea" :rows="4"
            :placeholder="t('agent.hitl.action.commentPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button :type="actionType === 'approve' || actionType === 'batch-approve' ? 'success' : actionType === 'reject' || actionType === 'batch-reject' ? 'danger' : 'info'"
          :loading="actionLoading" @click="handleAction">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="t('agent.hitl.detail.title')" size="560px">
      <template v-if="detailRow">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="t('agent.hitl.detail.agentType')">
            <el-tag size="small">{{ detailRow.agentType }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('agent.hitl.detail.bizType')">{{ detailRow.bizType }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.hitl.detail.bizId')">{{ detailRow.bizId }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.hitl.detail.bizRef')">{{ detailRow.bizRef || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.hitl.detail.status')">
            <el-tag :type="statusTagType(detailRow.status)" size="small" effect="dark">{{ detailRow.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('agent.hitl.detail.question')">{{ detailRow.question || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.hitl.detail.recommendation')">{{ detailRow.recommendation || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="detailRow.options" :label="t('agent.hitl.detail.options')">
            <el-tag v-for="opt in detailRow.options" :key="opt" size="small" style="margin-right: 4px">{{ opt }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('agent.hitl.detail.approver')">{{ detailRow.approverName || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.hitl.detail.comment')">{{ detailRow.comment || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.hitl.detail.createdAt')">{{ detailRow.createdAt }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.hitl.detail.updatedAt')">{{ detailRow.updatedAt }}</el-descriptions-item>
        </el-descriptions>
        <el-collapse v-if="detailRow.snapshot" style="margin-top: 12px">
          <el-collapse-item :title="t('agent.hitl.detail.snapshot')" name="snapshot">
            <pre class="json-pre">{{ snapshotFmt(detailRow.snapshot) }}</pre>
          </el-collapse-item>
        </el-collapse>
      </template>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
.hitl-page {
  .table-toolbar {
    margin-bottom: 12px;
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .kpi-row { margin-bottom: 0; }
  .kpi-card {
    text-align: center;
    .kpi-title { font-size: 12px; color: var(--el-text-color-secondary); }
    .kpi-value { font-size: 22px; font-weight: 600; margin-top: 8px; }
    &.warning .kpi-value { color: var(--el-color-warning); }
  }
  .json-pre {
    background: var(--el-fill-color-light);
    padding: 8px;
    border-radius: 4px;
    font-size: 12px;
    max-height: 240px;
    overflow: auto;
    margin: 0;
  }
}
</style>
