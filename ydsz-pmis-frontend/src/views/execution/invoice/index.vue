<!--
  @file 发票管理
  @description 项目执行过程中的发票管理页面，覆盖发票全生命周期：草稿 → 提交 → 审批 → 开票 → 红冲/取消；
               支持蓝字发票与红字发票（红冲），开票依据包括里程碑、外协人天、按月、终验等；
               状态流转: DRAFT → SUBMITTED → APPROVED → ISSUED → RED_REVERSED / CANCELLED；
               开票后由后端自动生成发票号（invoiceNo）。
  @module views/execution/invoice
-->
<script setup lang="ts">
/**
 * 发票管理
 *
 * 状态: DRAFT -> SUBMITTED -> APPROVED -> ISSUED -> RED_REVERSED / CANCELLED
 * 开票依据: MILESTONE(需验收证明) / OUTSOURCING(需人天确认单) / MONTHLY / FINAL / OTHER
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageInvoices,
  createInvoice,
  approveInvoice,
  issueInvoice,
  reverseInvoice,
  deleteInvoice,
} from '@/api/execution/invoice'
import type { InvoiceVO, InvoiceCreateDTO } from '@/api/execution/invoice/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

/** 列表加载状态 */
const loading = ref(false)
/** H17.1 修复：提交按钮 loading 状态，防止重复提交 */
const submitting = ref(false)
/** 发票记录列表 */
const list = ref<InvoiceVO[]>([])
/** 记录总数（分页用） */
const total = ref(0)
/** 查询条件：关键字 + 状态 + 类型 + 客户 ID + 项目 ID */
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  invoiceType: '',
  customerId: undefined as number | undefined,
  initiationId: undefined as number | undefined,
})

/** 发票状态 → 标签/样式映射（i18n 响应式） */
const statusMap = computed(() => ({
  DRAFT: { label: t('finance.invoice.status.DRAFT'), type: 'info' as const },
  SUBMITTED: { label: t('finance.invoice.status.SUBMITTED'), type: 'warning' as const },
  APPROVED: { label: t('finance.invoice.status.APPROVED'), type: 'success' as const },
  REJECTED: { label: t('finance.invoice.status.REJECTED'), type: 'danger' as const },
  ISSUED: { label: t('finance.invoice.status.ISSUED'), type: 'primary' as const },
  RED_REVERSED: { label: t('finance.invoice.status.RED_REVERSED'), type: 'danger' as const },
  CANCELLED: { label: t('finance.invoice.status.CANCELLED'), type: 'info' as const },
}))

/** 发票类型 → 标签映射（蓝字/红字，i18n 响应式） */
const typeMap = computed(() => ({
  NORMAL: { label: t('finance.invoice.invoiceType.NORMAL') },
  RED_REVERSE: { label: t('finance.invoice.invoiceType.RED_REVERSE') },
}))

/** 开票依据 → 标签映射（i18n 响应式） */
const basisMap = computed(() => ({
  MILESTONE: { label: t('finance.invoice.basis.MILESTONE') },
  OUTSOURCING: { label: t('finance.invoice.basis.OUTSOURCING') },
  MONTHLY: { label: t('finance.invoice.basis.MONTHLY') },
  FINAL: { label: t('finance.invoice.basis.FINAL') },
  OTHER: { label: t('finance.invoice.basis.OTHER') },
}))

/** 分页查询发票列表 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageInvoices(query.page, query.size, {
      keyword: query.keyword,
      status: query.status,
      invoiceType: query.invoiceType,
      customerId: query.customerId,
      initiationId: query.initiationId,
    })
    list.value = data.list
    total.value = data.total
  } catch {
    // H16.1 修复：查询失败时清空陈旧数据，避免用户误以为是当前页结果
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 重置查询条件并回到首页刷新 */
function handleReset() {
  query.keyword = ''
  query.status = ''
  query.invoiceType = ''
  query.customerId = undefined
  query.initiationId = undefined
  query.page = 1
  fetchList()
}

/** 新增发票弹窗可见性 */
const dialogVisible = ref(false)
/** 表单引用（用于校验） */
const formRef = ref<FormInstance>()
/** 新增发票表单数据 */
const form = reactive<Partial<InvoiceCreateDTO>>({
  invoiceCode: '',
  invoiceType: 'NORMAL',
  invoiceBasis: 'MILESTONE',
  customerId: 0,
  initiationId: 0,
  amount: 0,
  taxRate: 0.06,
})

/** 表单校验规则 */
const formRules = computed(() => ({
  invoiceCode: [{ required: true, message: t('finance.invoice.rules.codeRequired'), trigger: 'blur' }],
  invoiceType: [{ required: true, message: t('finance.invoice.rules.typeRequired'), trigger: 'change' }],
  invoiceBasis: [{ required: true, message: t('finance.invoice.rules.basisRequired'), trigger: 'change' }],
  customerId: [{ required: true, message: t('finance.invoice.rules.customerIdRequired'), trigger: 'blur' }],
  initiationId: [{ required: true, message: t('finance.invoice.rules.initiationIdRequired'), trigger: 'blur' }],
  amount: [{ required: true, message: t('finance.invoice.rules.amountRequired'), trigger: 'blur' }],
}))

/** 打开新增弹窗并重置表单为默认值 */
function openCreate() {
  Object.assign(form, {
    invoiceCode: '',
    invoiceType: 'NORMAL',
    invoiceBasis: 'MILESTONE',
    customerId: 0,
    customerName: '',
    initiationId: 0,
    contractId: undefined,
    amount: 0,
    taxRate: 0.06,
    dueDate: '',
    description: '',
    acceptanceProof: '',
    personDaySheet: '',
  })
  dialogVisible.value = true
}

/** 提交新建发票，校验通过后创建并刷新列表 */
async function submitForm() {
  // H16.2 修复：用 try/catch 包裹，失败时不弹 success；H17.1 修复：submitting 防重复
  try {
    submitting.value = true
    await formRef.value?.validate()
    await createInvoice(form as InvoiceCreateDTO)
    ElMessage.success(t('finance.invoice.messages.created'))
    dialogVisible.value = false
    fetchList()
  } catch {
    // 校验失败或创建失败：拦截器已弹错，此处保持弹窗打开供用户修正
  } finally {
    submitting.value = false
  }
}

/**
 * 审批发票（通过/驳回），需二次确认
 * @param row 发票记录
 * @param action 审批动作（APPROVED 通过 / REJECTED 驳回）
 */
async function handleApprove(row: InvoiceVO, action: 'APPROVED' | 'REJECTED') {
  const text = action === 'APPROVED' ? t('finance.invoice.messages.approvePass') : t('finance.invoice.messages.approveReject')
  try {
    await ElMessageBox.confirm(t('finance.invoice.messages.approvePrompt', { text }), t('common.tip'), { type: 'warning' })
    await approveInvoice({ id: row.id, approverId: 1, approverName: t('finance.invoice.systemApprover') })
    ElMessage.success(t('finance.invoice.messages.approved', { text }))
    fetchList()
  } catch { /* 取消 */ }
}

/**
 * 开票操作，后端将自动生成发票号（invoiceNo）
 * @param row 发票记录
 */
async function handleIssue(row: InvoiceVO) {
  try {
    await ElMessageBox.confirm(t('finance.invoice.messages.issuePrompt'), t('common.tip'), { type: 'warning' })
    await issueInvoice({ id: row.id })
    ElMessage.success(t('finance.invoice.messages.issued'))
    fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || t('finance.invoice.messages.issueFailed'))
  }
}

/**
 * 红冲发票，需输入被红冲的原发票 ID
 * @param row 发票记录
 */
async function handleReverse(row: InvoiceVO) {
  try {
    const { value } = await ElMessageBox.prompt(t('finance.invoice.messages.reversePrompt'), t('finance.invoice.messages.reverseTitle'), {
      inputPattern: /^\d+$/,
      inputErrorMessage: t('finance.invoice.messages.reversePattern'),
    })
    await reverseInvoice({ id: row.id, reversedById: Number(value) })
    ElMessage.success(t('finance.invoice.messages.reversed'))
    fetchList()
  } catch { /* 取消 */ }
}

/**
 * 删除指定发票，需二次确认
 * @param row 发票记录
 */
async function handleDelete(row: InvoiceVO) {
  try {
    await ElMessageBox.confirm(t('finance.invoice.messages.deletePrompt'), t('common.tip'), { type: 'warning' })
    await deleteInvoice(row.id)
    ElMessage.success(t('finance.invoice.messages.deleted'))
    fetchList()
  } catch { /* 取消 */ }
}

/** 页面挂载时加载列表 */
onMounted(fetchList)
</script>

<template>
  <PageLayout
    v-model:query="query"
    :list="list"
    :total="total"
    :loading="loading"
    loading-type="skeleton"
    empty-preset="list"
    @query="query.page = 1; fetchList()"
    @reset="handleReset"
    @page-change="fetchList"
    @refresh="fetchList"
  >
    <template #search>
      <el-form-item :label="t('finance.invoice.search.keyword')"><el-input v-model="query.keyword" :placeholder="t('finance.invoice.search.keywordPlaceholder')" clearable :aria-label="t('common.search')" @clear="query.page = 1; fetchList()" @keyup.enter="query.page = 1; fetchList()" /></el-form-item>
      <el-form-item :label="t('finance.invoice.search.status')">
        <el-select v-model="query.status" :placeholder="t('common.all')" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('finance.invoice.search.type')">
        <el-select v-model="query.invoiceType" :placeholder="t('common.all')" clearable style="width: 120px">
          <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('finance.invoice.search.initiationId')"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.FINANCE_INVOICE_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        {{ t('finance.invoice.buttons.create') }}
      </el-button>
    </template>

    <template #table="scope">
      <vxe-table :data="list" :loading="loading" border stripe :height="scope.tableProps.height" :scroll-y="scope.tableProps.scrollY">
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="invoiceCode" :title="t('finance.invoice.columns.code')" width="160" />
        <vxe-column field="invoiceNo" :title="t('finance.invoice.columns.no')" width="160" />
        <vxe-column field="invoiceType" :title="t('finance.invoice.columns.type')" width="100">
          <template #default="{ row }">{{ typeMap[row.invoiceType as keyof typeof typeMap]?.label || row.invoiceType || '-' }}</template>
        </vxe-column>
        <vxe-column field="invoiceBasis" :title="t('finance.invoice.columns.basis')" width="100">
          <template #default="{ row }">{{ basisMap[row.invoiceBasis as keyof typeof basisMap]?.label || row.invoiceBasis || '-' }}</template>
        </vxe-column>
        <vxe-column field="customerName" :title="t('finance.invoice.columns.customer')" width="160" show-overflow />
        <vxe-column field="initiationName" :title="t('finance.invoice.columns.project')" width="160" show-overflow />
        <vxe-column field="amount" :title="t('finance.invoice.columns.amount')" width="130" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="taxAmount" :title="t('finance.invoice.columns.taxAmount')" width="120" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="issueDate" :title="t('finance.invoice.columns.issueDate')" width="110" />
        <vxe-column field="status" :title="t('finance.invoice.columns.status')" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column :title="t('finance.invoice.columns.action')" width="320" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.FINANCE_INVOICE_APPROVE]" link type="success" size="small" @click="handleApprove(row, 'APPROVED')">{{ t('finance.invoice.buttons.approve') }}</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.FINANCE_INVOICE_APPROVE]" link type="danger" size="small" @click="handleApprove(row, 'REJECTED')">{{ t('finance.invoice.buttons.reject') }}</el-button>
            <el-button v-if="row.status === 'APPROVED'" v-permission="[PC.FINANCE_INVOICE_ISSUE]" link type="primary" size="small" @click="handleIssue(row)">{{ t('finance.invoice.buttons.issue') }}</el-button>
            <el-button v-if="row.status === 'ISSUED'" v-permission="[PC.FINANCE_INVOICE_REVERSE]" link type="danger" size="small" @click="handleReverse(row)">{{ t('finance.invoice.buttons.reverse') }}</el-button>
            <el-button v-permission="[PC.FINANCE_INVOICE_CREATE]" link type="danger" size="small" @click="handleDelete(row)">{{ t('finance.invoice.buttons.delete') }}</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" :title="t('finance.invoice.dialog.createTitle')" width="640px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12"><el-form-item :label="t('finance.invoice.form.code')" prop="invoiceCode"><el-input v-model="form.invoiceCode" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item :label="t('finance.invoice.form.type')" prop="invoiceType"><el-select v-model="form.invoiceType" style="width: 100%"><el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" /></el-select></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12"><el-form-item :label="t('finance.invoice.form.customerId')" prop="customerId"><el-input-number v-model="form.customerId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item :label="t('finance.invoice.form.customerName')"><el-input v-model="form.customerName" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12"><el-form-item :label="t('finance.invoice.form.initiationId')" prop="initiationId"><el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item :label="t('finance.invoice.form.contractId')"><el-input-number v-model="form.contractId" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item :label="t('finance.invoice.form.basis')" prop="invoiceBasis">
          <el-select v-model="form.invoiceBasis" style="width: 100%">
            <el-option v-for="(v, k) in basisMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12" :md="8"><el-form-item :label="t('finance.invoice.form.amount')" prop="amount"><el-input-number v-model="form.amount" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12" :md="8"><el-form-item :label="t('finance.invoice.form.taxRate')"><el-input-number v-model="form.taxRate" :min="0" :max="1" :step="0.01" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12" :md="8"><el-form-item :label="t('finance.invoice.form.taxAmount')"><el-input-number :model-value="Number(form.amount || 0) * Number(form.taxRate || 0)" :min="0" disabled style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item v-if="form.invoiceBasis === 'MILESTONE'" :label="t('finance.invoice.form.acceptanceProof')">
          <el-input v-model="form.acceptanceProof" :placeholder="t('finance.invoice.form.proofPlaceholder')" />
        </el-form-item>
        <el-form-item v-if="form.invoiceBasis === 'OUTSOURCING'" :label="t('finance.invoice.form.personDaySheet')">
          <el-input v-model="form.personDaySheet" :placeholder="t('finance.invoice.form.proofPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('finance.invoice.form.dueDate')">
          <el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('finance.invoice.form.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
