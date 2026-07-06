<!--
  @file 运维工单管理
  @description 售后运维工单的统一管理页面，支持工单创建、派单、状态流转、SLA 达成率统计与客户评价闭环。
  @module views/aftersales/ops-ticket
-->
<script setup lang="ts">
/**
 * 运维工单管理 (P7)
 *
 * 优先级: P1/P2/P3/P4 (SLA 响应/解决时限不同)
 * 状态: OPEN/ASSIGNED/IN_PROGRESS/RESOLVED/CLOSED/CANCELLED
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import {
  pageOpsTickets,
  createOpsTicket,
  assignOpsTicket,
  changeOpsTicketStatus,
  closeAndEvaluateOpsTicket,
  scanOpsTicketSlaBreaches,
  slaSummaryOpsTicket,
  aggregateOpsTicketByStatus,
} from '@/api/execution/aftersales/ops-ticket'
import type {
  OpsTicketVO,
  OpsTicketCreateDTO,
  OpsTicketAssignDTO,
  OpsTicketStatusDTO,
} from '@/api/execution/aftersales/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

/** 列表加载状态 */
const loading = ref(false)
/** 工单列表数据 */
const list = ref<OpsTicketVO[]>([])
/** 列表总条数（用于分页） */
const total = ref(0)
/** SLA 达成率统计列表 */
const slaList = ref<Array<Record<string, unknown>>>([])
/** 按状态聚合的工单统计 */
const statusAgg = ref<Array<Record<string, unknown>>>([])
/** 列表查询条件 */
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  priority: '',
  initiationId: undefined as number | undefined,
  assigneeId: undefined as number | undefined,
})

const statusMap = computed(() => ({
  OPEN: { label: t('aftersales.opsTicket.status.OPEN'), type: 'warning' as const },
  ASSIGNED: { label: t('aftersales.opsTicket.status.ASSIGNED'), type: 'primary' as const },
  IN_PROGRESS: { label: t('aftersales.opsTicket.status.IN_PROGRESS'), type: 'primary' as const },
  RESOLVED: { label: t('aftersales.opsTicket.status.RESOLVED'), type: 'success' as const },
  CLOSED: { label: t('aftersales.opsTicket.status.CLOSED'), type: 'info' as const },
  CANCELLED: { label: t('aftersales.opsTicket.status.CANCELLED'), type: 'danger' as const },
}))

const priorityMap = computed(() => ({
  P1: { label: t('aftersales.opsTicket.priority.P1'), type: 'danger' as const },
  P2: { label: t('aftersales.opsTicket.priority.P2'), type: 'warning' as const },
  P3: { label: t('aftersales.opsTicket.priority.P3'), type: 'primary' as const },
  P4: { label: t('aftersales.opsTicket.priority.P4'), type: 'info' as const },
}))

const categoryMap = computed<Record<string, string>>(() => ({
  BUG: t('aftersales.opsTicket.category.BUG'),
  DATA: t('aftersales.opsTicket.category.DATA'),
  CONFIG: t('aftersales.opsTicket.category.CONFIG'),
  PROCESS: t('aftersales.opsTicket.category.PROCESS'),
  OTHER: t('aftersales.opsTicket.category.OTHER'),
}))

/** 拉取运维工单分页列表 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageOpsTickets({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      priority: query.priority || undefined,
      initiationId: query.initiationId,
      assigneeId: query.assigneeId,
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 拉取 SLA 达成率与状态聚合统计 */
async function fetchStats() {
  try {
    slaList.value = await slaSummaryOpsTicket().then((r) => r.data as Array<Record<string, unknown>>)
  } catch {
    slaList.value = []
  }
  try {
    statusAgg.value = await aggregateOpsTicketByStatus().then((r) => r.data as Array<Record<string, unknown>>)
  } catch {
    statusAgg.value = []
  }
}

/** 重置查询条件并重新加载列表 */
function handleReset() {
  query.keyword = ''
  query.status = ''
  query.priority = ''
  query.initiationId = undefined
  query.assigneeId = undefined
  query.page = 1
  fetchList()
}

/** 是否处于空态: 非加载中且列表无数据 */
const isEmpty = computed(() => !loading.value && list.value.length === 0)

/** 新增工单弹窗显隐 */
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
/** 提交中状态（防重复提交） */
const submittingForm = ref(false)
/** 新增工单表单数据 */
const form = reactive<Partial<OpsTicketCreateDTO>>({
  initiationId: 0,
  priority: 'P3',
  title: '',
  category: 'OTHER',
})

const formRules = {
  initiationId: [{ required: true, message: t('aftersales.opsTicket.rules.initiationIdRequired'), trigger: 'blur' }],
  title: [{ required: true, message: t('aftersales.opsTicket.rules.titleRequired'), trigger: 'blur' }],
  priority: [{ required: true, message: t('aftersales.opsTicket.rules.priorityRequired'), trigger: 'change' }],
}

/** 打开新增工单弹窗，重置表单为默认值 */
function openCreate() {
  Object.assign(form, {
    initiationId: 0,
    priority: 'P3',
    title: '',
    description: '',
    category: 'OTHER',
    warrantyId: undefined,
    reporterId: undefined,
  })
  dialogVisible.value = true
}

/** 提交新增工单表单，校验通过后调用创建接口 */
async function submitForm() {
  await formRef.value?.validate()
  submittingForm.value = true
  try {
    await createOpsTicket(form as OpsTicketCreateDTO)
    ElMessage.success(t('aftersales.opsTicket.messages.created'))
    dialogVisible.value = false
    fetchList()
  } finally {
    submittingForm.value = false
  }
}

/** 派单弹窗显隐 */
const assignVisible = ref(false)
/** 派单提交中状态（防重复提交） */
const submittingAssign = ref(false)
/** 派单表单数据 */
const assignForm = reactive<{ id?: number; assigneeId: number; comment: string }>({
  id: undefined,
  assigneeId: 0,
  comment: '',
})

/** 打开派单弹窗，回填当前工单处理人 */
function openAssign(row: OpsTicketVO) {
  assignForm.id = row.id
  assignForm.assigneeId = row.assigneeId || 0
  assignForm.comment = ''
  assignVisible.value = true
}

/** 提交派单，将工单指派给指定处理人 */
async function submitAssign() {
  if (!assignForm.id || !assignForm.assigneeId) {
    ElMessage.warning(t('aftersales.opsTicket.messages.assigneeRequired'))
    return
  }
  const dto: OpsTicketAssignDTO = { id: assignForm.id, assigneeId: assignForm.assigneeId, comment: assignForm.comment }
  submittingAssign.value = true
  try {
    await assignOpsTicket(dto)
    ElMessage.success(t('aftersales.opsTicket.messages.assigned'))
    assignVisible.value = false
    fetchList()
  } finally {
    submittingAssign.value = false
  }
}

/**
 * 变更工单状态
 * @param row 当前行工单数据
 * @param target 目标状态（RESOLVED/CANCELLED 等需补充说明）
 */
async function handleStatus(row: OpsTicketVO, target: string) {
  const targetText = (statusMap.value as any)[target]?.label || target
  try {
    let dto: OpsTicketStatusDTO = { id: row.id, targetStatus: target }
    if (target === 'RESOLVED') {
      const { value } = await ElMessageBox.prompt(t('aftersales.opsTicket.messages.resolvePrompt'), t('aftersales.opsTicket.messages.resolveTitle'), {
        inputValidator: (v) => !!v || t('aftersales.opsTicket.messages.noteRequired'),
      })
      dto = { ...dto, resolutionNote: value }
      // 通过 changeStatus 提交
      await changeOpsTicketStatus({
        id: row.id,
        targetStatus: target,
        resolutionNote: value,
      } as any)
    } else if (target === 'CANCELLED') {
      const { value } = await ElMessageBox.prompt(t('aftersales.opsTicket.messages.cancelPrompt'), t('aftersales.opsTicket.messages.cancelTitle'), {
        inputValidator: (v) => !!v || t('aftersales.opsTicket.messages.reasonRequired'),
      })
      await changeOpsTicketStatus({ ...dto, comment: value })
    } else {
      await changeOpsTicketStatus(dto)
    }
    ElMessage.success(t('aftersales.opsTicket.messages.statusChanged', { target: targetText }))
    fetchList()
  } catch { /* 取消 */ }
}

/** 关闭评价弹窗显隐 */
const evalVisible = ref(false)
/** 关闭评价提交中状态（防重复提交） */
const submittingEvaluate = ref(false)
/** 客户评价表单数据 */
const evalForm = reactive<{ id?: number; score: number; comment: string }>({
  id: undefined,
  score: 5,
  comment: '',
})

/** 打开关闭评价弹窗，重置评分与评价说明 */
function openEvaluate(row: OpsTicketVO) {
  evalForm.id = row.id
  evalForm.score = 5
  evalForm.comment = ''
  evalVisible.value = true
}

/** 提交关闭并评价，将工单置为 CLOSED 并记录客户评分 */
async function submitEvaluate() {
  if (!evalForm.id) return
  submittingEvaluate.value = true
  try {
    await closeAndEvaluateOpsTicket({
      id: evalForm.id,
      targetStatus: 'CLOSED',
      customerScore: evalForm.score,
      customerComment: evalForm.comment,
    } as any)
    ElMessage.success(t('aftersales.opsTicket.messages.evaluated'))
    evalVisible.value = false
    fetchList()
  } finally {
    submittingEvaluate.value = false
  }
}

/** 触发 SLA 超时扫描，并刷新列表与统计 */
async function handleScan() {
  const n = await scanOpsTicketSlaBreaches()
  ElMessage.success(t('aftersales.opsTicket.messages.scannedSla', { count: n }))
  fetchList()
  fetchStats()
}

function slaRateText(row: Record<string, unknown>, key: string) {
  const v = Number(row[key] || 0)
  if (!isFinite(v)) return '0%'
  return (v * 100).toFixed(1) + '%'
}

onMounted(() => {
  fetchList()
  fetchStats()
})
</script>

<template>
  <PageLayout
    v-model:query="query"
    :list="list"
    :total="total"
    :loading="loading"
    @query="query.page = 1; fetchList()"
    @reset="handleReset"
    @page-change="fetchList"
    @refresh="() => { fetchList(); fetchStats(); }"
  >
    <!-- 搜索栏 -->
    <template #search>
      <el-form-item :label="t('aftersales.opsTicket.search.keyword')"><el-input v-model="query.keyword" :placeholder="t('aftersales.opsTicket.search.keywordPlaceholder')" clearable @keyup.enter="query.page = 1; fetchList()" /></el-form-item>
      <el-form-item :label="t('aftersales.opsTicket.search.status')">
        <el-select v-model="query.status" :placeholder="t('common.all')" clearable style="width: 130px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('aftersales.opsTicket.search.priority')">
        <el-select v-model="query.priority" :placeholder="t('common.all')" clearable style="width: 110px">
          <el-option v-for="(v, k) in priorityMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('aftersales.opsTicket.search.initiationId')"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
      <el-form-item :label="t('aftersales.opsTicket.search.assigneeId')"><el-input-number v-model="query.assigneeId" :min="0" :controls="false" /></el-form-item>
    </template>

    <!-- 工具栏 -->
    <template #toolbar>
      <el-button v-permission="[PC.AFTERSALES_OPS_TICKET_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        {{ t('aftersales.opsTicket.buttons.create') }}
      </el-button>
      <el-button v-permission="[PC.AFTERSALES_OPS_TICKET_SCAN]" type="warning" :icon="'Bell'" @click="handleScan">
        {{ t('aftersales.opsTicket.buttons.scanSla') }}
      </el-button>
    </template>

    <!-- SLA 达成率 -->
    <el-row v-if="slaList.length" :gutter="12" class="mb-3">
      <el-col v-for="row in slaList" :key="String(row.priority)" :span="6">
        <el-card shadow="hover">
          <div class="text-sm text-gray-500">
            <StatusTag :value="String(row.priority)" :map="priorityMap" />
            <span class="ml-2">{{ t('aftersales.opsTicket.sla.total', { count: row.totalCount }) }}</span>
          </div>
          <div class="text-xs mt-2">
            {{ t('aftersales.opsTicket.sla.responseRate') }}<b :class="Number(row.responseSlaRate) < 0.8 ? 'text-red-500' : 'text-green-600'">{{ slaRateText(row, 'responseSlaRate') }}</b>
          </div>
          <div class="text-xs">
            {{ t('aftersales.opsTicket.sla.resolveRate') }}<b :class="Number(row.resolveSlaRate) < 0.8 ? 'text-red-500' : 'text-green-600'">{{ slaRateText(row, 'resolveSlaRate') }}</b>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 工单表格 -->
    <template #table="scope">
      <EmptyState
        v-if="isEmpty"
        preset="search"
        :title="query.keyword || query.status || query.priority || query.initiationId || query.assigneeId ? t('aftersales.opsTicket.empty.searchTitle') : t('aftersales.opsTicket.empty.listTitle')"
        :description="query.keyword || query.status || query.priority || query.initiationId || query.assigneeId ? t('aftersales.opsTicket.empty.searchDesc') : t('aftersales.opsTicket.empty.listDesc')"
        :action-text="t('aftersales.opsTicket.empty.actionCreate')"
        @action="openCreate"
      />
      <vxe-table v-else :data="list" :loading="loading" border stripe :height="scope.tableProps.height" :scroll-y="scope.tableProps.scrollY">
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="ticketCode" :title="t('aftersales.opsTicket.columns.ticketCode')" width="180" />
        <vxe-column field="title" :title="t('aftersales.opsTicket.columns.title')" min-width="220" show-overflow />
        <vxe-column field="initiationName" :title="t('aftersales.opsTicket.columns.initiationName')" min-width="160" show-overflow />
        <vxe-column field="category" :title="t('aftersales.opsTicket.columns.category')" width="90">
          <template #default="{ row }">{{ categoryMap[row.category as string] || row.category || '-' }}</template>
        </vxe-column>
        <vxe-column field="priority" :title="t('aftersales.opsTicket.columns.priority')" width="100">
          <template #default="{ row }"><StatusTag :value="row.priority" :map="priorityMap" /></template>
        </vxe-column>
        <vxe-column field="reporterName" :title="t('aftersales.opsTicket.columns.reporterName')" width="100" />
        <vxe-column field="assigneeName" :title="t('aftersales.opsTicket.columns.assigneeName')" width="100" />
        <vxe-column field="responseDueAt" :title="t('aftersales.opsTicket.columns.responseDueAt')" width="170" />
        <vxe-column field="resolveDueAt" :title="t('aftersales.opsTicket.columns.resolveDueAt')" width="170" />
        <vxe-column :label="t('aftersales.opsTicket.columns.sla')" width="160">
          <template #default="{ row }">
            <el-tag v-if="row.responseSlaBreached" type="danger" size="small">{{ t('aftersales.opsTicket.slaBreach.response') }}</el-tag>
            <el-tag v-if="row.resolveSlaBreached" type="danger" size="small" class="ml-1">{{ t('aftersales.opsTicket.slaBreach.resolve') }}</el-tag>
            <span v-if="!row.responseSlaBreached && !row.resolveSlaBreached" class="text-gray-400">{{ t('aftersales.opsTicket.slaBreach.normal') }}</span>
          </template>
        </vxe-column>
        <vxe-column field="status" :title="t('aftersales.opsTicket.columns.status')" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column field="customerScore" :title="t('aftersales.opsTicket.columns.customerScore')" width="70" align="center" />
        <vxe-column :title="t('aftersales.opsTicket.columns.action')" width="300" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'OPEN'"
              v-permission="[PC.AFTERSALES_OPS_TICKET_ASSIGN]"
              link
              type="primary"
              size="small"
              @click="openAssign(row)"
            >{{ t('aftersales.opsTicket.actions.assign') }}</el-button>
            <el-button
              v-if="row.status === 'ASSIGNED'"
              v-permission="[PC.AFTERSALES_OPS_TICKET_STATUS]"
              link
              type="primary"
              size="small"
              @click="handleStatus(row, 'IN_PROGRESS')"
            >{{ t('aftersales.opsTicket.actions.startProcess') }}</el-button>
            <el-button
              v-if="row.status === 'IN_PROGRESS'"
              v-permission="[PC.AFTERSALES_OPS_TICKET_STATUS]"
              link
              type="success"
              size="small"
              @click="handleStatus(row, 'RESOLVED')"
            >{{ t('aftersales.opsTicket.actions.markResolved') }}</el-button>
            <el-button
              v-if="row.status === 'RESOLVED'"
              v-permission="[PC.AFTERSALES_OPS_TICKET_EVALUATE]"
              link
              type="success"
              size="small"
              @click="openEvaluate(row)"
            >{{ t('aftersales.opsTicket.actions.closeEvaluate') }}</el-button>
            <el-button
              v-if="['OPEN', 'ASSIGNED', 'IN_PROGRESS'].includes(row.status || '')"
              v-permission="[PC.AFTERSALES_OPS_TICKET_STATUS]"
              link
              type="danger"
              size="small"
              @click="handleStatus(row, 'CANCELLED')"
            >{{ t('aftersales.opsTicket.actions.cancel') }}</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <!-- 新增工单弹窗 -->
    <el-dialog v-model="dialogVisible" :title="t('aftersales.opsTicket.dialog.createTitle')" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="t('aftersales.opsTicket.form.initiationId')" prop="initiationId">
          <el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('aftersales.opsTicket.form.title')" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item :label="t('aftersales.opsTicket.form.category')">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="(v, k) in categoryMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('aftersales.opsTicket.form.priority')" prop="priority">
          <el-select v-model="form.priority" style="width: 100%">
            <el-option v-for="(v, k) in priorityMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('aftersales.opsTicket.form.reporterId')">
          <el-input-number v-model="form.reporterId" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('aftersales.opsTicket.form.warrantyId')">
          <el-input-number v-model="form.warrantyId" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('aftersales.opsTicket.form.description')">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submittingForm" :disabled="submittingForm" @click="submitForm">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>

    <!-- 派单弹窗 -->
    <el-dialog v-model="assignVisible" :title="t('aftersales.opsTicket.dialog.assignTitle')" width="460px">
      <el-form label-width="100px">
        <el-form-item :label="t('aftersales.opsTicket.form.assignId')"><el-input :model-value="assignForm.id" disabled /></el-form-item>
        <el-form-item :label="t('aftersales.opsTicket.form.assigneeId')" required>
          <el-input-number v-model="assignForm.assigneeId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('aftersales.opsTicket.form.comment')">
          <el-input v-model="assignForm.comment" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submittingAssign" :disabled="submittingAssign" @click="submitAssign">{{ t('aftersales.opsTicket.actions.assign') }}</el-button>
      </template>
    </el-dialog>

    <!-- 关闭评价弹窗 -->
    <el-dialog v-model="evalVisible" :title="t('aftersales.opsTicket.dialog.evalTitle')" width="460px">
      <el-form label-width="100px">
        <el-form-item :label="t('aftersales.opsTicket.form.evalId')"><el-input :model-value="evalForm.id" disabled /></el-form-item>
        <el-form-item :label="t('aftersales.opsTicket.form.score')" required>
          <el-rate v-model="evalForm.score" :max="5" />
        </el-form-item>
        <el-form-item :label="t('aftersales.opsTicket.form.evalComment')">
          <el-input v-model="evalForm.comment" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="evalVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submittingEvaluate" :disabled="submittingEvaluate" @click="submitEvaluate">{{ t('aftersales.opsTicket.actions.closeEvaluate') }}</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
