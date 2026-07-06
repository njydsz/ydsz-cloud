<!--
  @file 回款管理
  @description 项目执行过程中的回款管理页面，覆盖回款单的全生命周期：待确认 → 已确认 → 已核销/已取消；
               支持回款核销（PaymentAllocation）将回款金额分摊到多张发票，余额耗尽自动转为 ALLOCATED；
               状态流转: PENDING → CONFIRMED → ALLOCATED / CANCELLED。
  @module views/execution/payment
-->
<script setup lang="ts">
/**
 * 回款管理
 *
 * 状态: PENDING -> CONFIRMED -> ALLOCATED / CANCELLED
 * 核销：支持多发票分摊，余额耗尽自动转 ALLOCATED
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pagePayments,
  createPayment,
  changePaymentStatus,
  allocatePayment,
  deletePayment,
} from '@/api/execution/payment'
import type { PaymentVO, PaymentCreateDTO, PaymentAllocationDTO } from '@/api/execution/payment/types'
import { PC } from '@/constants/permissionCodes'
import { handleError, confirmAction, showSuccess } from '@/utils/error'

const { t } = useI18n()

/** 列表加载状态 */
const loading = ref(false)
/** H17.1 修复：提交按钮 loading 状态，防止重复提交 */
const submitting = ref(false)
/** H17.1 修复：核销按钮 loading 状态 */
const allocating = ref(false)
/** 回款记录列表 */
const list = ref<PaymentVO[]>([])
/** 记录总数（分页用） */
const total = ref(0)
/** 查询条件：关键字 + 状态 + 客户 ID + 项目 ID */
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  customerId: undefined as number | undefined,
  initiationId: undefined as number | undefined,
})

/** 回款状态 → 标签/样式映射 */
const statusMap = computed(() => ({
  PENDING: { label: t('finance.payment.status.PENDING'), type: 'warning' as const },
  CONFIRMED: { label: t('finance.payment.status.CONFIRMED'), type: 'primary' as const },
  ALLOCATED: { label: t('finance.payment.status.ALLOCATED'), type: 'success' as const },
  CANCELLED: { label: t('finance.payment.status.CANCELLED'), type: 'info' as const },
}))

/** 支付方式 → 中文标签映射 */
const methodMap = computed(() => ({
  BANK_TRANSFER: { label: t('finance.payment.method.BANK_TRANSFER') },
  CHECK: { label: t('finance.payment.method.CHECK') },
  CASH: { label: t('finance.payment.method.CASH') },
  OTHER: { label: t('finance.payment.method.OTHER') },
}))

/** 分页查询回款列表 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await pagePayments(query.page, query.size, {
      keyword: query.keyword,
      status: query.status,
      customerId: query.customerId,
      initiationId: query.initiationId,
    })
    list.value = data.list
    total.value = data.total
  } catch (e) {
    // H16.1 修复：查询失败时清空陈旧数据
    list.value = []
    total.value = 0
    handleError(e, 'fetchList')
  } finally {
    loading.value = false
  }
}

/** 重置查询条件并回到首页刷新 */
function handleReset() {
  query.keyword = ''
  query.status = ''
  query.customerId = undefined
  query.initiationId = undefined
  query.page = 1
  fetchList()
}

/** 新增回款弹窗可见性 */
const dialogVisible = ref(false)
/** 表单引用（用于校验） */
const formRef = ref<FormInstance>()
/** 新增回款表单数据 */
const form = reactive<Partial<PaymentCreateDTO>>({
  paymentCode: '',
  customerId: 0,
  initiationId: 0,
  amount: 0,
  paymentMethod: 'BANK_TRANSFER',
  paymentDate: new Date().toISOString().slice(0, 10),
})

/** 表单校验规则 */
const formRules = computed(() => ({
  paymentCode: [{ required: true, message: t('finance.payment.rules.codeRequired'), trigger: 'blur' }],
  customerId: [{ required: true, message: t('finance.payment.rules.customerIdRequired'), trigger: 'blur' }],
  initiationId: [{ required: true, message: t('finance.payment.rules.initiationIdRequired'), trigger: 'blur' }],
  amount: [{ required: true, message: t('finance.payment.rules.amountRequired'), trigger: 'blur' }],
}))

/** 打开新增弹窗并重置表单为默认值 */
function openCreate() {
  Object.assign(form, {
    paymentCode: '',
    customerId: 0,
    customerName: '',
    initiationId: 0,
    contractId: undefined,
    amount: 0,
    paymentMethod: 'BANK_TRANSFER',
    paymentDate: new Date().toISOString().slice(0, 10),
    bankAccount: '',
    bankRef: '',
    remark: '',
  })
  dialogVisible.value = true
}

/** 提交新建回款，校验通过后创建并刷新列表 */
async function submitForm() {
  // H16.2 修复：try/catch 包裹避免 success 误报；H17.1 修复：submitting 防重复
  try {
    submitting.value = true
    await formRef.value?.validate()
    await createPayment(form as PaymentCreateDTO)
    showSuccess(t('finance.payment.messages.created'))
    dialogVisible.value = false
    fetchList()
  } catch (e) {
    // 校验或创建失败：拦截器已弹错，保持弹窗打开
    handleError(e, 'submitForm')
  } finally {
    submitting.value = false
  }
}

/**
 * 变更回款状态（确认/取消），需二次确认
 * @param row 回款记录
 * @param target 目标状态
 */
async function handleStatus(row: PaymentVO, target: string) {
  const targetText = (statusMap.value as any)[target]?.label || target
  const confirmed = await confirmAction(
    t('finance.payment.messages.statusConfirmPrompt', { target: targetText }),
    t('common.tip'),
  )
  if (!confirmed) return
  try {
    await changePaymentStatus(row.id, target, 1, t('finance.payment.systemApprover'))
    showSuccess(t('finance.payment.messages.statusUpdated'))
    fetchList()
  } catch (e) {
    handleError(e, 'handleStatus')
  }
}

/** 核销弹窗可见性 */
const allocDialogVisible = ref(false)
/** 当前核销的回款记录 */
const allocPayment = ref<PaymentVO | null>(null)
/** 核销表单：回款 ID + 发票 ID + 核销金额 */
const allocForm = reactive<PaymentAllocationDTO>({ paymentId: 0, invoiceId: 0, amount: 0 })

/**
 * 打开核销弹窗，默认填入剩余可核销金额
 * @param row 回款记录
 */
function openAllocate(row: PaymentVO) {
  allocPayment.value = row
  Object.assign(allocForm, { paymentId: row.id, invoiceId: 0, amount: Number(row.unallocatedAmount ?? row.amount) })
  allocDialogVisible.value = true
}

/** 提交核销，将回款金额分摊到指定发票 */
async function submitAllocate() {
  if (!allocForm.invoiceId || !allocForm.amount) {
    ElMessage.warning(t('finance.payment.messages.allocPrompt'))
    return
  }
  // H17.1 修复：allocating 防重复提交
  try {
    allocating.value = true
    await allocatePayment(allocForm)
    showSuccess(t('finance.payment.messages.allocSuccess'))
    allocDialogVisible.value = false
    fetchList()
  } catch (e) {
    handleError(e, 'submitAllocate')
  } finally {
    allocating.value = false
  }
}

/**
 * 删除指定回款记录，需二次确认
 * @param row 回款记录
 */
async function handleDelete(row: PaymentVO) {
  const confirmed = await confirmAction(
    t('finance.payment.messages.deletePrompt'),
    t('common.tip'),
  )
  if (!confirmed) return
  try {
    await deletePayment(row.id)
    showSuccess(t('finance.payment.messages.deleted'))
    fetchList()
  } catch (e) {
    handleError(e, 'handleDelete')
  }
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
      <el-form-item :label="t('finance.payment.search.keyword')"><el-input v-model="query.keyword" :placeholder="t('finance.payment.search.keywordPlaceholder')" clearable :aria-label="t('common.search')" @clear="query.page = 1; fetchList()" @keyup.enter="query.page = 1; fetchList()" /></el-form-item>
      <el-form-item :label="t('finance.payment.search.status')">
        <el-select v-model="query.status" :placeholder="t('common.all')" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('finance.payment.search.initiationId')"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.FINANCE_PAYMENT_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        {{ t('finance.payment.buttons.create') }}
      </el-button>
    </template>

    <template #table="scope">
      <vxe-table :data="list" :loading="loading" border stripe :height="scope.tableProps.height" :scroll-y="scope.tableProps.scrollY">
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="paymentCode" :title="t('finance.payment.columns.code')" width="160" />
        <vxe-column field="customerName" :title="t('finance.payment.columns.customer')" width="160" show-overflow />
        <vxe-column field="initiationName" :title="t('finance.payment.columns.project')" width="160" show-overflow />
        <vxe-column field="amount" :title="t('finance.payment.columns.amount')" width="130" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="unallocatedAmount" :title="t('finance.payment.columns.unallocated')" width="130" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="paymentMethod" :title="t('finance.payment.columns.method')" width="100">
          <template #default="{ row }">{{ methodMap[row.paymentMethod as keyof typeof methodMap]?.label || row.paymentMethod || '-' }}</template>
        </vxe-column>
        <vxe-column field="paymentDate" :title="t('finance.payment.columns.paymentDate')" width="110" />
        <vxe-column field="bankRef" :title="t('finance.payment.columns.bankRef')" width="160" />
        <vxe-column field="status" :title="t('finance.payment.columns.status')" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column :title="t('finance.payment.columns.action')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="primary" size="small" @click="handleStatus(row, 'CONFIRMED')">{{ t('finance.payment.buttons.confirm') }}</el-button>
            <el-button v-if="row.status === 'CONFIRMED'" v-permission="[PC.FINANCE_PAYMENT_ALLOCATE]" link type="success" size="small" @click="openAllocate(row)">{{ t('finance.payment.buttons.allocate') }}</el-button>
            <el-button v-if="['PENDING', 'CONFIRMED'].includes(row.status || '')" link type="info" size="small" @click="handleStatus(row, 'CANCELLED')">{{ t('finance.payment.buttons.cancel') }}</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">{{ t('finance.payment.buttons.delete') }}</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" :title="t('finance.payment.dialog.createTitle')" width="520px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="t('finance.payment.form.code')" prop="paymentCode"><el-input v-model="form.paymentCode" /></el-form-item>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12"><el-form-item :label="t('finance.payment.form.customerId')" prop="customerId"><el-input-number v-model="form.customerId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item :label="t('finance.payment.form.customerName')"><el-input v-model="form.customerName" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12"><el-form-item :label="t('finance.payment.form.initiationId')" prop="initiationId"><el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item :label="t('finance.payment.form.contractId')"><el-input-number v-model="form.contractId" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12"><el-form-item :label="t('finance.payment.form.amount')" prop="amount"><el-input-number v-model="form.amount" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item :label="t('finance.payment.form.method')"><el-select v-model="form.paymentMethod" style="width: 100%"><el-option v-for="(v, k) in methodMap" :key="k" :label="v.label" :value="k" /></el-select></el-form-item></el-col>
        </el-row>
        <el-form-item :label="t('finance.payment.form.paymentDate')">
          <el-date-picker v-model="form.paymentDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('finance.payment.form.bankAccount')"><el-input v-model="form.bankAccount" /></el-form-item>
        <el-form-item :label="t('finance.payment.form.bankRef')"><el-input v-model="form.bankRef" /></el-form-item>
        <el-form-item :label="t('finance.payment.form.remark')"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="allocDialogVisible" :title="t('finance.payment.dialog.allocateTitle')" width="480px">
      <el-alert v-if="allocPayment" type="info" :closable="false" show-icon style="margin-bottom: 12px">
        {{ t('finance.payment.allocInfo', { code: allocPayment.paymentCode, amount: `¥${Number(allocPayment.unallocatedAmount ?? allocPayment.amount).toLocaleString()}` }) }}
      </el-alert>
      <el-form :model="allocForm" label-width="100px">
        <el-form-item :label="t('finance.payment.allocForm.invoiceId')">
          <el-input-number v-model="allocForm.invoiceId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('finance.payment.allocForm.amount')">
          <el-input-number v-model="allocForm.amount" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="allocDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="allocating" @click="submitAllocate">{{ t('finance.payment.messages.allocateConfirm') }}</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
