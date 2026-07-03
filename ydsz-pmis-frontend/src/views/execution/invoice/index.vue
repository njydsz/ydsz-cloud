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
import { ref, reactive, onMounted } from 'vue'
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

/** 发票状态 → 标签/样式映射 */
const statusMap = {
  DRAFT: { label: '草稿', type: 'info' as const },
  SUBMITTED: { label: '已提交', type: 'warning' as const },
  APPROVED: { label: '已审批', type: 'success' as const },
  REJECTED: { label: '已驳回', type: 'danger' as const },
  ISSUED: { label: '已开票', type: 'primary' as const },
  RED_REVERSED: { label: '已红冲', type: 'danger' as const },
  CANCELLED: { label: '已取消', type: 'info' as const },
}

/** 发票类型 → 中文标签映射（蓝字/红字） */
const typeMap = {
  NORMAL: { label: '蓝字发票' },
  RED_REVERSE: { label: '红字发票' },
}

/** 开票依据 → 中文标签映射 */
const basisMap = {
  MILESTONE: { label: '里程碑' },
  OUTSOURCING: { label: '外协人天' },
  MONTHLY: { label: '按月' },
  FINAL: { label: '终验' },
  OTHER: { label: '其他' },
}

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
const formRef = ref<any>()
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
const formRules = {
  invoiceCode: [{ required: true, message: '发票编码必填', trigger: 'blur' }],
  invoiceType: [{ required: true, message: '发票类型必填', trigger: 'change' }],
  invoiceBasis: [{ required: true, message: '开票依据必填', trigger: 'change' }],
  customerId: [{ required: true, message: '客户 ID 必填', trigger: 'blur' }],
  initiationId: [{ required: true, message: '项目 ID 必填', trigger: 'blur' }],
  amount: [{ required: true, message: '金额必填', trigger: 'blur' }],
}

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
    ElMessage.success('已创建')
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
  const text = action === 'APPROVED' ? '通过' : '驳回'
  try {
    await ElMessageBox.confirm(`确认${text}该发票？`, '提示', { type: 'warning' })
    await approveInvoice({ id: row.id, approverId: 1, approverName: '系统' })
    ElMessage.success(`已${text}`)
    fetchList()
  } catch { /* 取消 */ }
}

/**
 * 开票操作，后端将自动生成发票号（invoiceNo）
 * @param row 发票记录
 */
async function handleIssue(row: InvoiceVO) {
  try {
    await ElMessageBox.confirm('确认开票？开票后将自动生成发票号 (invoiceNo)。', '提示', { type: 'warning' })
    await issueInvoice({ id: row.id })
    ElMessage.success('已开票')
    fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '开票失败')
  }
}

/**
 * 红冲发票，需输入被红冲的原发票 ID
 * @param row 发票记录
 */
async function handleReverse(row: InvoiceVO) {
  try {
    const { value } = await ElMessageBox.prompt('请输入被红冲的原发票 ID', '红冲发票', {
      inputPattern: /^\d+$/,
      inputErrorMessage: '请输入数字',
    })
    await reverseInvoice({ id: row.id, reversedById: Number(value) })
    ElMessage.success('已红冲')
    fetchList()
  } catch { /* 取消 */ }
}

/**
 * 删除指定发票，需二次确认
 * @param row 发票记录
 */
async function handleDelete(row: InvoiceVO) {
  try {
    await ElMessageBox.confirm(`确认删除该发票？`, '提示', { type: 'warning' })
    await deleteInvoice(row.id)
    ElMessage.success('删除成功')
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
      <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="编码/发票号" clearable aria-label="搜索关键字" @clear="query.page = 1; fetchList()" @keyup.enter="query.page = 1; fetchList()" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.invoiceType" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目 ID"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.FINANCE_INVOICE_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        新增发票
      </el-button>
    </template>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="invoiceCode" title="编码" width="160" />
        <vxe-column field="invoiceNo" title="发票号" width="160" />
        <vxe-column field="invoiceType" title="类型" width="100">
          <template #default="{ row }">{{ typeMap[row.invoiceType as keyof typeof typeMap]?.label || row.invoiceType || '-' }}</template>
        </vxe-column>
        <vxe-column field="invoiceBasis" title="开票依据" width="100">
          <template #default="{ row }">{{ basisMap[row.invoiceBasis as keyof typeof basisMap]?.label || row.invoiceBasis || '-' }}</template>
        </vxe-column>
        <vxe-column field="customerName" title="客户" width="160" show-overflow />
        <vxe-column field="initiationName" title="项目" width="160" show-overflow />
        <vxe-column field="amount" title="金额(含税)" width="130" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="taxAmount" title="税额" width="120" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="issueDate" title="开票日期" width="110" />
        <vxe-column field="status" title="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column title="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.FINANCE_INVOICE_APPROVE]" link type="success" size="small" @click="handleApprove(row, 'APPROVED')">通过</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.FINANCE_INVOICE_APPROVE]" link type="danger" size="small" @click="handleApprove(row, 'REJECTED')">驳回</el-button>
            <el-button v-if="row.status === 'APPROVED'" v-permission="[PC.FINANCE_INVOICE_ISSUE]" link type="primary" size="small" @click="handleIssue(row)">开票</el-button>
            <el-button v-if="row.status === 'ISSUED'" v-permission="[PC.FINANCE_INVOICE_REVERSE]" link type="danger" size="small" @click="handleReverse(row)">红冲</el-button>
            <el-button v-permission="[PC.FINANCE_INVOICE_CREATE]" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" title="新增发票" width="640px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12"><el-form-item label="发票编码" prop="invoiceCode"><el-input v-model="form.invoiceCode" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="发票类型" prop="invoiceType"><el-select v-model="form.invoiceType" style="width: 100%"><el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" /></el-select></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12"><el-form-item label="客户 ID" prop="customerId"><el-input-number v-model="form.customerId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="客户名称"><el-input v-model="form.customerName" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12"><el-form-item label="项目 ID" prop="initiationId"><el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="合同 ID"><el-input-number v-model="form.contractId" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="开票依据" prop="invoiceBasis">
          <el-select v-model="form.invoiceBasis" style="width: 100%">
            <el-option v-for="(v, k) in basisMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12" :md="8"><el-form-item label="金额" prop="amount"><el-input-number v-model="form.amount" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12" :md="8"><el-form-item label="税率"><el-input-number v-model="form.taxRate" :min="0" :max="1" :step="0.01" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12" :md="8"><el-form-item label="税额"><el-input-number :model-value="Number(form.amount || 0) * Number(form.taxRate || 0)" :min="0" disabled style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item v-if="form.invoiceBasis === 'MILESTONE'" label="验收证明">
          <el-input v-model="form.acceptanceProof" placeholder="URL 或文件标识" />
        </el-form-item>
        <el-form-item v-if="form.invoiceBasis === 'OUTSOURCING'" label="人天确认单">
          <el-input v-model="form.personDaySheet" placeholder="URL 或文件标识" />
        </el-form-item>
        <el-form-item label="到期日">
          <el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
