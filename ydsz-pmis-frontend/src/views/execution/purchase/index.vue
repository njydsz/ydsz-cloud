<!--
  @file 采购管理
  @description 采购单管理页面：支持采购单分页查询、新建、状态流转(DRAFT→SUBMITTED→APPROVED→RECEIVED→PAID/REJECTED/CANCELLED)，提交时触发项目预算强管控校验，对应路由 /execution/purchase
  @module views/execution/purchase
-->
<script setup lang="ts">
/**
 * 采购管理
 *
 * 状态: DRAFT -> SUBMITTED -> APPROVED -> RECEIVED -> PAID / REJECTED / CANCELLED
 * 涉及预算强管控，提交时校验项目预算。
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pagePurchases,
  createPurchase,
  changePurchaseStatus,
  deletePurchase,
} from '@/api/execution/purchase'
import type { PurchaseVO, PurchaseCreateDTO } from '@/api/execution/purchase/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

// 列表查询状态
const loading = ref(false)
const list = ref<PurchaseVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  initiationId: undefined as number | undefined,
})

// 状态字典：采购单全生命周期状态映射到标签文案与色值
const statusMap = computed(() => ({
  DRAFT: { label: t('execution.purchase.status.DRAFT'), type: 'info' as const },
  SUBMITTED: { label: t('execution.purchase.status.SUBMITTED'), type: 'warning' as const },
  APPROVED: { label: t('execution.purchase.status.APPROVED'), type: 'success' as const },
  REJECTED: { label: t('execution.purchase.status.REJECTED'), type: 'danger' as const },
  RECEIVED: { label: t('execution.purchase.status.RECEIVED'), type: 'primary' as const },
  PAID: { label: t('execution.purchase.status.PAID'), type: 'success' as const },
  CANCELLED: { label: t('execution.purchase.status.CANCELLED'), type: 'info' as const },
}))

/** 拉取采购单分页数据 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await pagePurchases(query.page, query.size, {
      keyword: query.keyword,
      status: query.status,
      initiationId: query.initiationId,
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 重置查询条件并刷新列表 */
function handleReset() {
  query.keyword = ''
  query.status = ''
  query.initiationId = undefined
  query.page = 1
  fetchList()
}

/** 提交按钮 loading 状态，防止重复提交 */
const submitting = ref(false)
// 弹窗 - 新建采购单
const dialogVisible = ref(false)
const formRef = ref<any>()
const form = reactive<Partial<PurchaseCreateDTO>>({
  purchaseCode: '',
  initiationId: 0,
  vendor: '',
  itemName: '',
  quantity: 1,
  unitPrice: 0,
  amount: 0,
  purchaseDate: new Date().toISOString().slice(0, 10),
  applicantId: 0,
})

const formRules = computed(() => ({
  purchaseCode: [{ required: true, message: t('execution.purchase.rules.purchaseCodeRequired'), trigger: 'blur' }],
  initiationId: [{ required: true, message: t('execution.purchase.rules.initiationIdRequired'), trigger: 'blur' }],
  itemName: [{ required: true, message: t('execution.purchase.rules.itemNameRequired'), trigger: 'blur' }],
  applicantId: [{ required: true, message: t('execution.purchase.rules.applicantIdRequired'), trigger: 'blur' }],
}))

function openCreate() {
  Object.assign(form, {
    purchaseCode: '',
    initiationId: 0,
    vendor: '',
    itemName: '',
    quantity: 1,
    unitPrice: 0,
    amount: 0,
    purchaseDate: new Date().toISOString().slice(0, 10),
    applicantId: 0,
    description: '',
  })
  dialogVisible.value = true
}

/** 提交新建表单：校验通过后自动计算金额并触发预算强管控校验 */
async function submitForm() {
  try {
    submitting.value = true
    await formRef.value?.validate()
    // 自动计算金额 = 数量 × 单价
    if (form.quantity && form.unitPrice) {
      form.amount = Number(form.quantity) * Number(form.unitPrice)
    }
    await createPurchase(form as PurchaseCreateDTO)
    ElMessage.success(t('execution.purchase.messages.created'))
    dialogVisible.value = false
    fetchList()
  } catch {
    // 拦截器已弹错，保持弹窗打开
  } finally {
    submitting.value = false
  }
}

/** 删除采购单（二次确认） */
async function handleDelete(row: PurchaseVO) {
  try {
    await ElMessageBox.confirm(t('execution.purchase.messages.confirmDelete', { code: row.purchaseCode }), t('common.tip'), { type: 'warning' })
    await deletePurchase(row.id)
    ElMessage.success(t('execution.purchase.messages.deleted'))
    fetchList()
  } catch { /* 取消 */ }
}

/** 状态流转：根据当前状态推进到下一节点（提交/审批/收货/付款等） */
async function handleStatus(row: PurchaseVO, target: string) {
  const targetText = (statusMap.value as any)[target]?.label || target
  try {
    await ElMessageBox.confirm(t('execution.purchase.messages.confirmStatusChange', { target: targetText }), t('common.tip'), { type: 'warning' })
    await changePurchaseStatus({ id: row.id, targetStatus: target, approverId: 1, approverName: t('execution.purchase.systemApprover') })
    ElMessage.success(t('execution.purchase.messages.statusUpdated'))
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
    <!-- 查询条件区：关键字 / 状态 / 项目 ID -->
    <template #search>
      <el-form-item :label="$t('execution.purchase.search.keyword')"><el-input v-model="query.keyword" :placeholder="$t('execution.purchase.search.keywordPlaceholder')" clearable /></el-form-item>
      <el-form-item :label="$t('execution.purchase.search.status')">
        <el-select v-model="query.status" :placeholder="$t('common.all')" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('execution.purchase.search.initiationId')"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
    </template>

    <!-- 工具栏：新增采购按钮（受权限控制） -->
    <template #toolbar>
      <el-button v-permission="[PC.EXECUTION_PURCHASE_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        {{ $t('execution.purchase.buttons.create') }}
      </el-button>
    </template>

    <!-- 数据表格：采购单明细 + 状态流转操作列 -->
    <template #table="scope">
      <vxe-table :data="list" :loading="loading" border stripe :height="scope.tableProps.height" :scroll-y="scope.tableProps.scrollY">
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="purchaseCode" :title="$t('execution.purchase.columns.purchaseCode')" width="160" />
        <vxe-column field="initiationName" :title="$t('execution.purchase.columns.initiationName')" width="160" show-overflow />
        <vxe-column field="vendor" :title="$t('execution.purchase.columns.vendor')" width="140" show-overflow />
        <vxe-column field="itemName" :title="$t('execution.purchase.columns.itemName')" min-width="200" show-overflow />
        <vxe-column field="quantity" :title="$t('execution.purchase.columns.quantity')" width="80" align="right" />
        <vxe-column field="unitPrice" :title="$t('execution.purchase.columns.unitPrice')" width="110" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="amount" :title="$t('execution.purchase.columns.amount')" width="120" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="applicantName" :title="$t('execution.purchase.columns.applicantName')" width="100" />
        <vxe-column field="purchaseDate" :title="$t('execution.purchase.columns.purchaseDate')" width="110" />
        <vxe-column field="status" :title="$t('execution.purchase.columns.status')" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column :title="$t('execution.purchase.columns.action')" width="320" fixed="right">
          <!-- 操作按钮按状态推进流转：提交/通过/驳回/收货/付款/删除 -->
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" v-permission="[PC.EXECUTION_PURCHASE_STATUS]" link type="warning" size="small" @click="handleStatus(row, 'SUBMITTED')">{{ $t('execution.purchase.buttons.submit') }}</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.EXECUTION_PURCHASE_STATUS]" link type="success" size="small" @click="handleStatus(row, 'APPROVED')">{{ $t('execution.purchase.buttons.approve') }}</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.EXECUTION_PURCHASE_STATUS]" link type="danger" size="small" @click="handleStatus(row, 'REJECTED')">{{ $t('execution.purchase.buttons.reject') }}</el-button>
            <el-button v-if="row.status === 'APPROVED'" v-permission="[PC.EXECUTION_PURCHASE_STATUS]" link type="primary" size="small" @click="handleStatus(row, 'RECEIVED')">{{ $t('execution.purchase.buttons.receive') }}</el-button>
            <el-button v-if="row.status === 'RECEIVED'" v-permission="[PC.EXECUTION_PURCHASE_STATUS]" link type="success" size="small" @click="handleStatus(row, 'PAID')">{{ $t('execution.purchase.buttons.pay') }}</el-button>
            <el-button v-permission="[PC.EXECUTION_PURCHASE_DELETE]" link type="danger" size="small" @click="handleDelete(row)">{{ $t('execution.purchase.buttons.delete') }}</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <!-- 新增采购单弹窗：表单字段 + 预算强管控提示 -->
    <el-dialog v-model="dialogVisible" :title="$t('execution.purchase.dialog.createTitle')" width="640px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item :label="$t('execution.purchase.dialog.purchaseCode')" prop="purchaseCode"><el-input v-model="form.purchaseCode" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('execution.purchase.dialog.initiationId')" prop="initiationId"><el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item :label="$t('execution.purchase.dialog.vendor')"><el-input v-model="form.vendor" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('execution.purchase.dialog.applicantId')" prop="applicantId"><el-input-number v-model="form.applicantId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item :label="$t('execution.purchase.dialog.itemName')" prop="itemName"><el-input v-model="form.itemName" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item :label="$t('execution.purchase.dialog.quantity')"><el-input-number v-model="form.quantity" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="$t('execution.purchase.dialog.unitPrice')"><el-input-number v-model="form.unitPrice" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="$t('execution.purchase.dialog.amount')"><el-input-number :model-value="Number(form.quantity || 0) * Number(form.unitPrice || 0)" :min="0" disabled style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item :label="$t('execution.purchase.dialog.purchaseDate')">
          <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('execution.purchase.dialog.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon>
          {{ $t('execution.purchase.dialog.budgetTip') }}
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{ $t('common.ok') }}</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
