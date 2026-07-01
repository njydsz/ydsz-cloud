<!--
  @file 合同管理
  @description 合同的查询、新增、编辑与状态流转；状态按 DRAFT → UNDER_REVIEW → APPROVED → SIGNED → EXECUTING → CLOSED 流转
  @module views/project/contract
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageContracts,
  createContract,
  updateContract,
  changeContractStatus,
  deleteContract,
} from '@/api/project/contract'
import type { ContractVO, ContractCreateDTO, ContractStatusDTO } from '@/api/project/contract/types'
import { PC } from '@/constants/permissionCodes'

// ===== 列表查询状态 =====
const loading = ref(false)
const list = ref<ContractVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  customerId: undefined as number | undefined,
})

const statusMap = {
  DRAFT: { label: '草稿', type: 'info' as const },
  UNDER_REVIEW: { label: '审批中', type: 'warning' as const },
  APPROVED: { label: '已审批', type: 'success' as const },
  SIGNED: { label: '已签订', type: 'primary' as const },
  EXECUTING: { label: '执行中', type: 'primary' as const },
  CLOSED: { label: '已关闭', type: 'info' as const },
  REJECTED: { label: '已驳回', type: 'danger' as const },
}

const typeMap = {
  FIXED_PRICE: { label: '固定总价' },
  T_M: { label: '人月计费' },
  MILESTONE: { label: '里程碑' },
  RETAINER: { label: '框架协议' },
  LICENSE: { label: '授权' },
  SAAS: { label: 'SaaS' },
  MAINTENANCE: { label: '运维' },
  OTHER: { label: '其他' },
}

const riskMap = {
  LOW: { label: '低', type: 'success' as const },
  MEDIUM: { label: '中', type: 'warning' as const },
  HIGH: { label: '高', type: 'danger' as const },
}

// ===== 列表加载 =====
/** 拉取合同分页列表 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageContracts(query.page, query.size, {
      keyword: query.keyword,
      status: query.status,
      customerId: query.customerId,
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
  query.customerId = undefined
  query.page = 1
  fetchList()
}

const dialogVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formRef = ref<any>()
const form = reactive<Partial<ContractCreateDTO> & { id?: number }>({
  contractCode: '',
  contractName: '',
  customerId: 0,
  customerName: '',
  contractType: 'FIXED_PRICE',
  amount: 0,
  currency: 'CNY',
  signDate: '',
  effectiveDate: '',
  expireDate: '',
  paymentTerms: '',
})

const formRules = {
  contractCode: [{ required: true, message: '合同编码必填', trigger: 'blur' }],
  contractName: [{ required: true, message: '合同名称必填', trigger: 'blur' }],
  customerId: [{ required: true, message: '客户 ID 必填', trigger: 'blur' }],
  contractType: [{ required: true, message: '合同类型必填', trigger: 'change' }],
  amount: [{ required: true, message: '金额必填', trigger: 'blur' }],
}

function openCreate() {
  formMode.value = 'create'
  Object.assign(form, {
    id: undefined,
    contractCode: '',
    contractName: '',
    customerId: 0,
    customerName: '',
    contractType: 'FIXED_PRICE',
    amount: 0,
    currency: 'CNY',
    signDate: '',
    effectiveDate: '',
    expireDate: '',
    paymentTerms: '',
    description: '',
  })
  dialogVisible.value = true
}

async function openEdit(row: ContractVO) {
  formMode.value = 'edit'
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  if (formMode.value === 'create') {
    const dto: ContractCreateDTO = {
      contractCode: form.contractCode!,
      contractName: form.contractName!,
      customerId: form.customerId!,
      customerName: form.customerName,
      contractType: form.contractType!,
      amount: form.amount!,
      currency: form.currency,
      signDate: form.signDate,
      effectiveDate: form.effectiveDate,
      expireDate: form.expireDate,
      paymentTerms: form.paymentTerms,
      description: form.description,
    }
    await createContract(dto)
    ElMessage.success('创建成功')
  } else if (form.id) {
    await updateContract({
      id: form.id,
      contractName: form.contractName,
      amount: form.amount,
      signDate: form.signDate,
      effectiveDate: form.effectiveDate,
      expireDate: form.expireDate,
      paymentTerms: form.paymentTerms,
      description: form.description,
    } as any)
    ElMessage.success('更新成功')
  }
  dialogVisible.value = false
  fetchList()
}

async function handleDelete(row: ContractVO) {
  try {
    await ElMessageBox.confirm(`确认删除合同「${row.contractName}」吗？`, '提示', { type: 'warning' })
    await deleteContract(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 取消 */ }
}

async function handleStatus(row: ContractVO, target: string) {
  const targetText = (statusMap as any)[target]?.label || target
  try {
    await ElMessageBox.confirm(`确认将状态变更为「${targetText}」吗？`, '提示', { type: 'warning' })
    const dto: ContractStatusDTO = { id: row.id, targetStatus: target }
    await changeContractStatus(dto)
    ElMessage.success('状态已更新')
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
      <el-form-item label="关键字">
        <el-input v-model="query.keyword" placeholder="编码/名称" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.PROJECT_CONTRACT_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        新增合同
      </el-button>
    </template>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="contractCode" title="编码" width="160" />
        <vxe-column field="contractName" title="合同名称" min-width="200" show-overflow />
        <vxe-column field="customerName" title="客户" width="160" show-overflow />
        <vxe-column field="initiationName" title="关联项目" width="160" show-overflow />
        <vxe-column field="contractType" title="类型" width="110">
          <template #default="{ row }">
            {{ typeMap[row.contractType as keyof typeof typeMap]?.label || row.contractType || '-' }}
          </template>
        </vxe-column>
        <vxe-column field="amount" title="金额" width="130" align="right" :formatter="({ cellValue, row }: any) => cellValue != null ? `${row?.currency || '¥'} ${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="riskLevel" title="风险" width="80" align="center">
          <template #default="{ row }">
            <StatusTag v-if="row.riskLevel" :value="row.riskLevel" :map="riskMap" />
            <span v-else>-</span>
          </template>
        </vxe-column>
        <vxe-column field="signDate" title="签订日期" width="110" />
        <vxe-column field="effectiveDate" title="生效日期" width="110" />
        <vxe-column field="expireDate" title="到期日期" width="110" />
        <vxe-column field="status" title="状态" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.status" :map="statusMap" />
          </template>
        </vxe-column>
        <vxe-column title="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="[PC.PROJECT_CONTRACT_UPDATE]" link type="primary" size="small" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-if="row.status === 'DRAFT'" v-permission="[PC.PROJECT_CONTRACT_STATUS]" link type="warning" size="small" @click="handleStatus(row, 'UNDER_REVIEW')">
              提交评审
            </el-button>
            <el-button v-if="row.status === 'UNDER_REVIEW'" v-permission="[PC.PROJECT_CONTRACT_STATUS]" link type="success" size="small" @click="handleStatus(row, 'APPROVED')">
              审批通过
            </el-button>
            <el-button v-if="row.status === 'APPROVED'" v-permission="[PC.PROJECT_CONTRACT_STATUS]" link type="primary" size="small" @click="handleStatus(row, 'SIGNED')">
              签订
            </el-button>
            <el-button v-if="row.status === 'SIGNED'" v-permission="[PC.PROJECT_CONTRACT_STATUS]" link type="primary" size="small" @click="handleStatus(row, 'EXECUTING')">
              启动执行
            </el-button>
            <el-button v-if="row.status === 'EXECUTING'" v-permission="[PC.PROJECT_CONTRACT_STATUS]" link type="info" size="small" @click="handleStatus(row, 'CLOSED')">
              关闭
            </el-button>
            <el-button v-permission="[PC.PROJECT_CONTRACT_DELETE]" link type="danger" size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" :title="formMode === 'create' ? '新增合同' : '编辑合同'" width="720px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="合同编码" prop="contractCode">
              <el-input v-model="form.contractCode" :disabled="formMode === 'edit'" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合同名称" prop="contractName">
              <el-input v-model="form.contractName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="客户 ID" prop="customerId">
              <el-input-number v-model="form.customerId" :min="1" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户名称">
              <el-input v-model="form.customerName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="合同类型" prop="contractType">
              <el-select v-model="form.contractType" style="width: 100%">
                <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合同金额" prop="amount">
              <el-input-number v-model="form.amount" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="币种">
              <el-select v-model="form.currency" style="width: 100%">
                <el-option label="人民币 CNY" value="CNY" />
                <el-option label="美元 USD" value="USD" />
                <el-option label="欧元 EUR" value="EUR" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="签订日期">
              <el-date-picker v-model="form.signDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="生效日期">
              <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="到期日期">
              <el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="付款条款">
          <el-input v-model="form.paymentTerms" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="描述">
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
