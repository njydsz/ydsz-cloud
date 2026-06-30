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

const loading = ref(false)
const list = ref<InvoiceVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  invoiceType: '',
  customerId: undefined as number | undefined,
  initiationId: undefined as number | undefined,
})

const statusMap = {
  DRAFT: { label: '草稿', type: 'info' as const },
  SUBMITTED: { label: '已提交', type: 'warning' as const },
  APPROVED: { label: '已审批', type: 'success' as const },
  REJECTED: { label: '已驳回', type: 'danger' as const },
  ISSUED: { label: '已开票', type: 'primary' as const },
  RED_REVERSED: { label: '已红冲', type: 'danger' as const },
  CANCELLED: { label: '已取消', type: 'info' as const },
}

const typeMap = {
  NORMAL: { label: '蓝字发票' },
  RED_REVERSE: { label: '红字发票' },
}

const basisMap = {
  MILESTONE: { label: '里程碑' },
  OUTSOURCING: { label: '外协人天' },
  MONTHLY: { label: '按月' },
  FINAL: { label: '终验' },
  OTHER: { label: '其他' },
}

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
  } finally {
    loading.value = false
  }
}

function handleReset() {
  query.keyword = ''
  query.status = ''
  query.invoiceType = ''
  query.customerId = undefined
  query.initiationId = undefined
  query.page = 1
  fetchList()
}

const dialogVisible = ref(false)
const formRef = ref<any>()
const form = reactive<Partial<InvoiceCreateDTO>>({
  invoiceCode: '',
  invoiceType: 'NORMAL',
  invoiceBasis: 'MILESTONE',
  customerId: 0,
  initiationId: 0,
  amount: 0,
  taxRate: 0.06,
})

const formRules = {
  invoiceCode: [{ required: true, message: '发票编码必填', trigger: 'blur' }],
  invoiceType: [{ required: true, message: '发票类型必填', trigger: 'change' }],
  invoiceBasis: [{ required: true, message: '开票依据必填', trigger: 'change' }],
  customerId: [{ required: true, message: '客户 ID 必填', trigger: 'blur' }],
  initiationId: [{ required: true, message: '项目 ID 必填', trigger: 'blur' }],
  amount: [{ required: true, message: '金额必填', trigger: 'blur' }],
}

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

async function submitForm() {
  await formRef.value?.validate()
  await createInvoice(form as InvoiceCreateDTO)
  ElMessage.success('已创建')
  dialogVisible.value = false
  fetchList()
}

async function handleApprove(row: InvoiceVO, action: 'APPROVED' | 'REJECTED') {
  const text = action === 'APPROVED' ? '通过' : '驳回'
  try {
    await ElMessageBox.confirm(`确认${text}该发票？`, '提示', { type: 'warning' })
    await approveInvoice({ id: row.id, approverId: 1, approverName: '系统' })
    ElMessage.success(`已${text}`)
    fetchList()
  } catch { /* 取消 */ }
}

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

async function handleDelete(row: InvoiceVO) {
  try {
    await ElMessageBox.confirm(`确认删除该发票？`, '提示', { type: 'warning' })
    await deleteInvoice(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 取消 */ }
}

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
      <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="编码/发票号" clearable /></el-form-item>
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
          <el-col :span="12"><el-form-item label="发票编码" prop="invoiceCode"><el-input v-model="form.invoiceCode" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="发票类型" prop="invoiceType"><el-select v-model="form.invoiceType" style="width: 100%"><el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" /></el-select></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="客户 ID" prop="customerId"><el-input-number v-model="form.customerId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户名称"><el-input v-model="form.customerName" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="项目 ID" prop="initiationId"><el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="合同 ID"><el-input-number v-model="form.contractId" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="开票依据" prop="invoiceBasis">
          <el-select v-model="form.invoiceBasis" style="width: 100%">
            <el-option v-for="(v, k) in basisMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="金额" prop="amount"><el-input-number v-model="form.amount" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="税率"><el-input-number v-model="form.taxRate" :min="0" :max="1" :step="0.01" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="税额"><el-input-number :model-value="Number(form.amount || 0) * Number(form.taxRate || 0)" :min="0" disabled style="width: 100%" /></el-form-item></el-col>
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
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
