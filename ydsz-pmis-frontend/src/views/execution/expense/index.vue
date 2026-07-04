<!--
  @file 费用报销管理
  @description 项目执行过程中的费用报销管理页面，支持费用单的创建、提交、审批、支付、驳回、取消等全流程操作；
               状态流转: DRAFT → SUBMITTED → APPROVED → PAID / REJECTED / CANCELLED；
               关联项目时由后端触发【预算强管控】校验，超预算将被拒绝。
  @module views/execution/expense
-->
<script setup lang="ts">
/**
 * 费用报销管理
 *
 * 状态: DRAFT -> SUBMITTED -> APPROVED -> PAID / REJECTED / CANCELLED
 * 涉及预算强管控。
 */
import { ref, reactive, computed, onMounted } from 'vue'
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
import { useFormDraft } from '@/composables/useFormDraft'

/** 列表加载状态 */
const loading = ref(false)
/** 费用报销记录列表 */
const list = ref<ExpenseVO[]>([])
/** 记录总数（分页用） */
const total = ref(0)
/** 查询条件：关键字 + 状态 + 类型 + 员工 ID + 项目 ID */
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  expenseType: '',
  employeeId: undefined as number | undefined,
  initiationId: undefined as number | undefined,
})

/** 报销状态 → 标签/样式映射 */
const statusMap = {
  DRAFT: { label: '草稿', type: 'info' as const },
  SUBMITTED: { label: '已提交', type: 'warning' as const },
  APPROVED: { label: '已通过', type: 'success' as const },
  REJECTED: { label: '已驳回', type: 'danger' as const },
  PAID: { label: '已支付', type: 'success' as const },
  CANCELLED: { label: '已取消', type: 'info' as const },
}

/** 费用类型 → 中文标签映射 */
const typeMap = {
  TRAVEL: { label: '差旅' },
  OFFICE: { label: '办公' },
  EQUIPMENT: { label: '设备' },
  TRAINING: { label: '培训' },
  MEAL: { label: '餐饮' },
  OTHER: { label: '其他' },
}

/** 分页查询费用报销列表 */
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

/** 重置查询条件并回到首页刷新 */
function handleReset() {
  query.keyword = ''
  query.status = ''
  query.expenseType = ''
  query.employeeId = undefined
  query.initiationId = undefined
  query.page = 1
  fetchList()
}

/** 提交按钮 loading 状态，防止重复提交 */
const submitting = ref(false)
/** 新增报销弹窗可见性 */
const dialogVisible = ref(false)
/** 表单引用（用于校验） */
const formRef = ref<any>()
/** 新增报销表单数据 */
const form = reactive<Partial<ExpenseCreateDTO>>({
  expenseCode: '',
  employeeId: 0,
  initiationId: undefined,
  expenseType: 'TRAVEL',
  amount: 0,
  expenseDate: new Date().toISOString().slice(0, 10),
})

/** 表单校验规则 */
const formRules = {
  expenseCode: [{ required: true, message: '单号必填', trigger: 'blur' }],
  employeeId: [{ required: true, message: '员工 ID 必填', trigger: 'blur' }],
  amount: [{ required: true, message: '金额必填', trigger: 'blur' }],
}

// ===== 表单草稿 =====
const { hasDraft, lastSavedAt, restore, clear: clearDraft } = useFormDraft(form, {
  key: 'expense-create',
  debounce: 3000,
})

const draftTimeText = computed(() => {
  if (!lastSavedAt.value) return ''
  return lastSavedAt.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
})

/** 打开新增弹窗并重置表单为默认值；若检测到草稿则提示恢复 */
function openCreate() {
  if (hasDraft.value) {
    ElMessageBox.confirm('检测到未提交的草稿，是否恢复？', '提示', { type: 'info' })
      .then(() => {
        restore()
        ElMessage.success('草稿已恢复')
        dialogVisible.value = true
      })
      .catch(() => {
        clearDraft()
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
      })
    return
  }
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

/** 提交新建报销单，校验通过后创建（触发预算校验）并刷新列表 */
async function submitForm() {
  try {
    submitting.value = true
    await formRef.value?.validate()
    await createExpense(form as ExpenseCreateDTO)
    clearDraft()
    ElMessage.success('已创建（触发预算校验）')
    dialogVisible.value = false
    fetchList()
  } catch {
    // 拦截器已弹错，保持弹窗打开
  } finally {
    submitting.value = false
  }
}

/**
 * 变更报销单状态（提交/通过/驳回/支付），需二次确认
 * @param row 报销单记录
 * @param target 目标状态
 */
async function handleStatus(row: ExpenseVO, target: string) {
  const targetText = (statusMap as any)[target]?.label || target
  try {
    await ElMessageBox.confirm(`确认将状态变更为「${targetText}」吗？`, '提示', { type: 'warning' })
    await changeExpenseStatus({ id: row.id, targetStatus: target, approverId: 1, approverName: '系统' })
    ElMessage.success('状态已更新')
    fetchList()
  } catch { /* 取消 */ }
}

/**
 * 删除指定报销单，需二次确认
 * @param row 报销单记录
 */
async function handleDelete(row: ExpenseVO) {
  try {
    await ElMessageBox.confirm(`确认删除报销单「${row.expenseCode}」吗？`, '提示', { type: 'warning' })
    await deleteExpense(row.id)
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
          <template #default="{ row }">{{ typeMap[row.expenseType as keyof typeof typeMap]?.label || row.expenseType || '-' }}</template>
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
        <span v-if="draftTimeText" style="color: #909399; font-size: 12px; margin-right: auto;">草稿已保存 {{ draftTimeText }}</span>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
