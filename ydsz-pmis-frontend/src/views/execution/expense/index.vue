<script setup lang="ts">
/**
 * 费用报销管理
 *
 * 状态: DRAFT -> SUBMITTED -> APPROVED -> PAID / REJECTED / CANCELLED
 * 涉及预算强管控。
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageExpenses,
  createExpense,
  changeExpenseStatus,
  deleteExpense,
} from '@/api/execution/expense'
import type { ExpenseVO, ExpenseCreateDTO } from '@/api/execution/expense/types'
import { PC } from '@/constants/permissionCodes'

const loading = ref(false)
const list = ref<ExpenseVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  expenseType: '',
  employeeId: undefined as number | undefined,
  initiationId: undefined as number | undefined,
})

const statusMap = {
  DRAFT: { label: '草稿', type: 'info' as const },
  SUBMITTED: { label: '已提交', type: 'warning' as const },
  APPROVED: { label: '已通过', type: 'success' as const },
  REJECTED: { label: '已驳回', type: 'danger' as const },
  PAID: { label: '已支付', type: 'success' as const },
  CANCELLED: { label: '已取消', type: 'info' as const },
}

const typeMap = {
  TRAVEL: { label: '差旅' },
  OFFICE: { label: '办公' },
  EQUIPMENT: { label: '设备' },
  TRAINING: { label: '培训' },
  MEAL: { label: '餐饮' },
  OTHER: { label: '其他' },
}

async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageExpenses(query.page, query.size, {
      keyword: query.keyword,
      status: query.status,
      expenseType: query.expenseType,
      employeeId: query.employeeId,
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
  query.expenseType = ''
  query.employeeId = undefined
  query.initiationId = undefined
  query.page = 1
  fetchList()
}

const dialogVisible = ref(false)
const formRef = ref<any>()
const form = reactive<Partial<ExpenseCreateDTO>>({
  expenseCode: '',
  employeeId: 0,
  initiationId: undefined,
  expenseType: 'TRAVEL',
  amount: 0,
  expenseDate: new Date().toISOString().slice(0, 10),
})

const formRules = {
  expenseCode: [{ required: true, message: '单号必填', trigger: 'blur' }],
  employeeId: [{ required: true, message: '员工 ID 必填', trigger: 'blur' }],
  amount: [{ required: true, message: '金额必填', trigger: 'blur' }],
}

function openCreate() {
  Object.assign(form, {
    expenseCode: '',
    employeeId: query.employeeId ?? 0,
    initiationId: query.initiationId,
    expenseType: 'TRAVEL',
    amount: 0,
    expenseDate: new Date().toISOString().slice(0, 10),
    description: '',
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  await createExpense(form as ExpenseCreateDTO)
  ElMessage.success('已创建（触发预算校验）')
  dialogVisible.value = false
  fetchList()
}

async function handleStatus(row: ExpenseVO, target: string) {
  const targetText = (statusMap as any)[target]?.label || target
  try {
    await ElMessageBox.confirm(`确认将状态变更为「${targetText}」吗？`, '提示', { type: 'warning' })
    await changeExpenseStatus({ id: row.id, targetStatus: target, approverId: 1, approverName: '系统' })
    ElMessage.success('状态已更新')
    fetchList()
  } catch { /* 取消 */ }
}

async function handleDelete(row: ExpenseVO) {
  try {
    await ElMessageBox.confirm(`确认删除报销单「${row.expenseCode}」吗？`, '提示', { type: 'warning' })
    await deleteExpense(row.id)
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
      <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="单号/说明" clearable /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.expenseType" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目 ID"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.EXECUTION_EXPENSE_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        新增报销
      </el-button>
    </template>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="expenseCode" title="单号" width="160" />
        <vxe-column field="employeeName" title="员工" width="100" />
        <vxe-column field="initiationName" title="项目" width="160" show-overflow />
        <vxe-column field="expenseType" title="类型" width="100">
          <template #default="{ row }">{{ typeMap[(row.expenseType as any)]?.label || row.expenseType || '-' }}</template>
        </vxe-column>
        <vxe-column field="amount" title="金额" width="120" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="expenseDate" title="发生日期" width="110" />
        <vxe-column field="approverName" title="审批人" width="100" />
        <vxe-column field="status" title="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column field="description" title="说明" min-width="200" show-overflow />
        <vxe-column title="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" v-permission="[PC.EXECUTION_EXPENSE_STATUS]" link type="warning" size="small" @click="handleStatus(row, 'SUBMITTED')">提交</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.EXECUTION_EXPENSE_STATUS]" link type="success" size="small" @click="handleStatus(row, 'APPROVED')">通过</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.EXECUTION_EXPENSE_STATUS]" link type="danger" size="small" @click="handleStatus(row, 'REJECTED')">驳回</el-button>
            <el-button v-if="row.status === 'APPROVED'" v-permission="[PC.EXECUTION_EXPENSE_STATUS]" link type="success" size="small" @click="handleStatus(row, 'PAID')">支付</el-button>
            <el-button v-permission="[PC.EXECUTION_EXPENSE_STATUS]" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" title="新增费用" width="520px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="单号" prop="expenseCode"><el-input v-model="form.expenseCode" /></el-form-item>
        <el-form-item label="员工 ID" prop="employeeId"><el-input-number v-model="form.employeeId" :min="1" :controls="false" style="width: 100%" /></el-form-item>
        <el-form-item label="项目 ID"><el-input-number v-model="form.initiationId" :min="0" :controls="false" style="width: 100%" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.expenseType" style="width: 100%">
            <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="发生日期">
          <el-date-picker v-model="form.expenseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon>
          提示: 关联项目时将触发【预算强管控】校验。
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
