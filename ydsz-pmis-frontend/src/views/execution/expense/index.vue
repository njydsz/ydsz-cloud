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
import { useI18n } from 'vue-i18n'
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
import { useUserStore } from '@/store/modules/user'

const { t } = useI18n()

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
  DRAFT: { label: t('execution.expense.status.DRAFT'), type: 'info' as const },
  SUBMITTED: { label: t('execution.expense.status.SUBMITTED'), type: 'warning' as const },
  APPROVED: { label: t('execution.expense.status.APPROVED'), type: 'success' as const },
  REJECTED: { label: t('execution.expense.status.REJECTED'), type: 'danger' as const },
  PAID: { label: t('execution.expense.status.PAID'), type: 'success' as const },
  CANCELLED: { label: t('execution.expense.status.CANCELLED'), type: 'info' as const },
}

/** 费用类型 → 中文标签映射 */
const typeMap = {
  TRAVEL: { label: t('execution.expense.type.TRAVEL') },
  OFFICE: { label: t('execution.expense.type.OFFICE') },
  EQUIPMENT: { label: t('execution.expense.type.EQUIPMENT') },
  TRAINING: { label: t('execution.expense.type.TRAINING') },
  MEAL: { label: t('execution.expense.type.MEAL') },
  OTHER: { label: t('execution.expense.type.OTHER') },
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
const formRef = ref<FormInstance>()
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
  expenseCode: [{ required: true, message: t('execution.expense.rules.expenseCodeRequired'), trigger: 'blur' }],
  employeeId: [{ required: true, message: t('execution.expense.rules.employeeIdRequired'), trigger: 'blur' }],
  amount: [{ required: true, message: t('execution.expense.rules.amountRequired'), trigger: 'blur' }],
}

// ===== 表单草稿 =====
const userStore = useUserStore()
const { hasDraft, lastSavedAt, restore, clear: clearDraft } = useFormDraft(form, {
  key: 'expense-create',
  debounce: 3000,
  userId: userStore.userInfo?.id,
})

const draftTimeText = computed(() => {
  if (!lastSavedAt.value) return ''
  return lastSavedAt.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
})

/** 打开新增弹窗并重置表单为默认值；若检测到草稿则提示恢复 */
function openCreate() {
  if (hasDraft.value) {
    ElMessageBox.confirm(t('execution.expense.messages.draftDetected'), t('common.tip'), { type: 'info' })
      .then(() => {
        restore()
        ElMessage.success(t('execution.expense.messages.draftRestored'))
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
    ElMessage.success(t('execution.expense.messages.created'))
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
    await ElMessageBox.confirm(t('execution.expense.messages.confirmStatusChange', { target: targetText }), t('common.tip'), { type: 'warning' })
    await changeExpenseStatus({ id: row.id, targetStatus: target, approverId: 1, approverName: t('execution.expense.systemApprover') })
    ElMessage.success(t('execution.expense.messages.statusUpdated'))
    fetchList()
  } catch { /* 取消 */ }
}

/**
 * 删除指定报销单，需二次确认
 * @param row 报销单记录
 */
async function handleDelete(row: ExpenseVO) {
  try {
    await ElMessageBox.confirm(t('execution.expense.messages.confirmDelete', { code: row.expenseCode }), t('common.tip'), { type: 'warning' })
    await deleteExpense(row.id)
    ElMessage.success(t('execution.expense.messages.deleted'))
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
      <el-form-item :label="$t('execution.expense.search.keyword')"><el-input v-model="query.keyword" :placeholder="$t('execution.expense.search.keywordPlaceholder')" clearable @keyup.enter="query.page = 1; fetchList()" /></el-form-item>
      <el-form-item :label="$t('execution.expense.search.status')">
        <el-select v-model="query.status" :placeholder="$t('common.all')" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('execution.expense.search.type')">
        <el-select v-model="query.expenseType" :placeholder="$t('common.all')" clearable style="width: 120px">
          <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('execution.expense.search.initiationId')"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.EXECUTION_EXPENSE_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        {{ $t('execution.expense.buttons.create') }}
      </el-button>
    </template>

    <template #table="scope">
      <vxe-table :data="list" :loading="loading" border stripe :height="scope.tableProps.height" :scroll-y="scope.tableProps.scrollY">
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="expenseCode" :title="$t('execution.expense.columns.expenseCode')" width="160" />
        <vxe-column field="employeeName" :title="$t('execution.expense.columns.employeeName')" width="100" />
        <vxe-column field="initiationName" :title="$t('execution.expense.columns.initiationName')" width="160" show-overflow />
        <vxe-column field="expenseType" :title="$t('execution.expense.columns.expenseType')" width="100">
          <template #default="{ row }">{{ typeMap[row.expenseType as keyof typeof typeMap]?.label || row.expenseType || '-' }}</template>
        </vxe-column>
        <vxe-column field="amount" :title="$t('execution.expense.columns.amount')" width="120" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="expenseDate" :title="$t('execution.expense.columns.expenseDate')" width="110" />
        <vxe-column field="approverName" :title="$t('execution.expense.columns.approverName')" width="100" />
        <vxe-column field="status" :title="$t('execution.expense.columns.status')" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column field="description" :title="$t('execution.expense.columns.description')" min-width="200" show-overflow />
        <vxe-column :title="$t('execution.expense.columns.action')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" v-permission="[PC.EXECUTION_EXPENSE_STATUS]" link type="warning" size="small" @click="handleStatus(row, 'SUBMITTED')">{{ $t('execution.expense.buttons.submit') }}</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.EXECUTION_EXPENSE_STATUS]" link type="success" size="small" @click="handleStatus(row, 'APPROVED')">{{ $t('execution.expense.buttons.approve') }}</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.EXECUTION_EXPENSE_STATUS]" link type="danger" size="small" @click="handleStatus(row, 'REJECTED')">{{ $t('execution.expense.buttons.reject') }}</el-button>
            <el-button v-if="row.status === 'APPROVED'" v-permission="[PC.EXECUTION_EXPENSE_STATUS]" link type="success" size="small" @click="handleStatus(row, 'PAID')">{{ $t('execution.expense.buttons.pay') }}</el-button>
            <el-button v-permission="[PC.EXECUTION_EXPENSE_STATUS]" link type="danger" size="small" @click="handleDelete(row)">{{ $t('execution.expense.buttons.delete') }}</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" :title="$t('execution.expense.dialog.createTitle')" width="520px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="$t('execution.expense.dialog.expenseCode')" prop="expenseCode"><el-input v-model="form.expenseCode" /></el-form-item>
        <el-form-item :label="$t('execution.expense.dialog.employeeId')" prop="employeeId"><el-input-number v-model="form.employeeId" :min="1" :controls="false" style="width: 100%" /></el-form-item>
        <el-form-item :label="$t('execution.expense.dialog.initiationId')"><el-input-number v-model="form.initiationId" :min="0" :controls="false" style="width: 100%" /></el-form-item>
        <el-form-item :label="$t('execution.expense.dialog.type')">
          <el-select v-model="form.expenseType" style="width: 100%">
            <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('execution.expense.dialog.amount')" prop="amount">
          <el-input-number v-model="form.amount" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('execution.expense.dialog.expenseDate')">
          <el-date-picker v-model="form.expenseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('execution.expense.dialog.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon>
          {{ $t('execution.expense.dialog.budgetTip') }}
        </el-alert>
      </el-form>
      <template #footer>
        <span v-if="draftTimeText" style="color: #909399; font-size: 12px; margin-right: auto;">{{ $t('execution.expense.messages.draftSaved', { time: draftTimeText }) }}</span>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{ $t('common.ok') }}</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
