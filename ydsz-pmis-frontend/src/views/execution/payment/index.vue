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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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

/** 列表加载状态 */
const loading = ref(false)
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
const statusMap = {
  PENDING: { label: '待确认', type: 'warning' as const },
  CONFIRMED: { label: '已确认', type: 'primary' as const },
  ALLOCATED: { label: '已核销', type: 'success' as const },
  CANCELLED: { label: '已取消', type: 'info' as const },
}

/** 支付方式 → 中文标签映射 */
const methodMap = {
  BANK_TRANSFER: { label: '银行转账' },
  CHECK: { label: '支票' },
  CASH: { label: '现金' },
  OTHER: { label: '其他' },
}

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
const formRef = ref<any>()
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
const formRules = {
  paymentCode: [{ required: true, message: '回款单号必填', trigger: 'blur' }],
  customerId: [{ required: true, message: '客户 ID 必填', trigger: 'blur' }],
  initiationId: [{ required: true, message: '项目 ID 必填', trigger: 'blur' }],
  amount: [{ required: true, message: '金额必填', trigger: 'blur' }],
}

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
  await formRef.value?.validate()
  await createPayment(form as PaymentCreateDTO)
  ElMessage.success('已创建')
  dialogVisible.value = false
  fetchList()
}

/**
 * 变更回款状态（确认/取消），需二次确认
 * @param row 回款记录
 * @param target 目标状态
 */
async function handleStatus(row: PaymentVO, target: string) {
  const targetText = (statusMap as any)[target]?.label || target
  try {
    await ElMessageBox.confirm(`确认将状态变更为「${targetText}」吗？`, '提示', { type: 'warning' })
    await changePaymentStatus(row.id, target, 1, '系统')
    ElMessage.success('状态已更新')
    fetchList()
  } catch { /* 取消 */ }
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
    ElMessage.warning('请填写发票 ID 和金额')
    return
  }
  try {
    await allocatePayment(allocForm)
    ElMessage.success('核销成功')
    allocDialogVisible.value = false
    fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '核销失败')
  }
}

/**
 * 删除指定回款记录，需二次确认
 * @param row 回款记录
 */
async function handleDelete(row: PaymentVO) {
  try {
    await ElMessageBox.confirm(`确认删除该回款？`, '提示', { type: 'warning' })
    await deletePayment(row.id)
    ElMessage.success('已删除')
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
    @query="query.page = 1; fetchList()"
    @reset="handleReset"
    @page-change="fetchList"
    @refresh="fetchList"
  >
    <template #search>
      <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="单号/银行流水" clearable /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目 ID"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.FINANCE_PAYMENT_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        新增回款
      </el-button>
    </template>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="paymentCode" title="单号" width="160" />
        <vxe-column field="customerName" title="客户" width="160" show-overflow />
        <vxe-column field="initiationName" title="项目" width="160" show-overflow />
        <vxe-column field="amount" title="金额" width="130" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="unallocatedAmount" title="未核销" width="130" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="paymentMethod" title="方式" width="100">
          <template #default="{ row }">{{ methodMap[row.paymentMethod as keyof typeof methodMap]?.label || row.paymentMethod || '-' }}</template>
        </vxe-column>
        <vxe-column field="paymentDate" title="到账日期" width="110" />
        <vxe-column field="bankRef" title="银行流水" width="160" />
        <vxe-column field="status" title="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column title="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="primary" size="small" @click="handleStatus(row, 'CONFIRMED')">确认</el-button>
            <el-button v-if="row.status === 'CONFIRMED'" v-permission="[PC.FINANCE_PAYMENT_ALLOCATE]" link type="success" size="small" @click="openAllocate(row)">核销</el-button>
            <el-button v-if="['PENDING', 'CONFIRMED'].includes(row.status || '')" link type="info" size="small" @click="handleStatus(row, 'CANCELLED')">取消</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" title="新增回款" width="520px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="回款单号" prop="paymentCode"><el-input v-model="form.paymentCode" /></el-form-item>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12"><el-form-item label="客户 ID" prop="customerId"><el-input-number v-model="form.customerId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="客户名称"><el-input v-model="form.customerName" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12"><el-form-item label="项目 ID" prop="initiationId"><el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="合同 ID"><el-input-number v-model="form.contractId" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12"><el-form-item label="金额" prop="amount"><el-input-number v-model="form.amount" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="支付方式"><el-select v-model="form.paymentMethod" style="width: 100%"><el-option v-for="(v, k) in methodMap" :key="k" :label="v.label" :value="k" /></el-select></el-form-item></el-col>
        </el-row>
        <el-form-item label="到账日期">
          <el-date-picker v-model="form.paymentDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="银行账号"><el-input v-model="form.bankAccount" /></el-form-item>
        <el-form-item label="银行流水"><el-input v-model="form.bankRef" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="allocDialogVisible" title="回款核销" width="480px">
      <el-alert v-if="allocPayment" type="info" :closable="false" show-icon style="margin-bottom: 12px">
        回款单号: {{ allocPayment.paymentCode }} | 剩余可核销: ¥{{ Number(allocPayment.unallocatedAmount ?? allocPayment.amount).toLocaleString() }}
      </el-alert>
      <el-form :model="allocForm" label-width="100px">
        <el-form-item label="发票 ID">
          <el-input-number v-model="allocForm.invoiceId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="核销金额">
          <el-input-number v-model="allocForm.amount" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="allocDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAllocate">确认核销</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
