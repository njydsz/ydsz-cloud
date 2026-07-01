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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
const statusMap = {
  DRAFT: { label: '草稿', type: 'info' as const },
  SUBMITTED: { label: '已提交', type: 'warning' as const },
  APPROVED: { label: '已审批', type: 'success' as const },
  REJECTED: { label: '已驳回', type: 'danger' as const },
  RECEIVED: { label: '已收货', type: 'primary' as const },
  PAID: { label: '已付款', type: 'success' as const },
  CANCELLED: { label: '已取消', type: 'info' as const },
}

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

const formRules = {
  purchaseCode: [{ required: true, message: '采购单号必填', trigger: 'blur' }],
  initiationId: [{ required: true, message: '项目 ID 必填', trigger: 'blur' }],
  itemName: [{ required: true, message: '物品名称必填', trigger: 'blur' }],
  applicantId: [{ required: true, message: '申请人 ID 必填', trigger: 'blur' }],
}

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
  await formRef.value?.validate()
  // 自动计算金额 = 数量 × 单价
  if (form.quantity && form.unitPrice) {
    form.amount = Number(form.quantity) * Number(form.unitPrice)
  }
  await createPurchase(form as PurchaseCreateDTO)
  ElMessage.success('已创建（触发预算校验）')
  dialogVisible.value = false
  fetchList()
}

/** 删除采购单（二次确认） */
async function handleDelete(row: PurchaseVO) {
  try {
    await ElMessageBox.confirm(`确认删除采购单「${row.purchaseCode}」吗？`, '提示', { type: 'warning' })
    await deletePurchase(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 取消 */ }
}

/** 状态流转：根据当前状态推进到下一节点（提交/审批/收货/付款等） */
async function handleStatus(row: PurchaseVO, target: string) {
  const targetText = (statusMap as any)[target]?.label || target
  try {
    await ElMessageBox.confirm(`确认将状态变更为「${targetText}」吗？`, '提示', { type: 'warning' })
    await changePurchaseStatus({ id: row.id, targetStatus: target, approverId: 1, approverName: '系统' })
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
    <!-- 查询条件区：关键字 / 状态 / 项目 ID -->
    <template #search>
      <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="单号/物品" clearable /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目 ID"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
    </template>

    <!-- 工具栏：新增采购按钮（受权限控制） -->
    <template #toolbar>
      <el-button v-permission="[PC.EXECUTION_PURCHASE_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        新增采购
      </el-button>
    </template>

    <!-- 数据表格：采购单明细 + 状态流转操作列 -->
    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="purchaseCode" title="采购单号" width="160" />
        <vxe-column field="initiationName" title="项目" width="160" show-overflow />
        <vxe-column field="vendor" title="供应商" width="140" show-overflow />
        <vxe-column field="itemName" title="物品" min-width="200" show-overflow />
        <vxe-column field="quantity" title="数量" width="80" align="right" />
        <vxe-column field="unitPrice" title="单价" width="110" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="amount" title="金额" width="120" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="applicantName" title="申请人" width="100" />
        <vxe-column field="purchaseDate" title="采购日期" width="110" />
        <vxe-column field="status" title="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column title="操作" width="320" fixed="right">
          <!-- 操作按钮按状态推进流转：提交/通过/驳回/收货/付款/删除 -->
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" v-permission="[PC.EXECUTION_PURCHASE_STATUS]" link type="warning" size="small" @click="handleStatus(row, 'SUBMITTED')">提交</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.EXECUTION_PURCHASE_STATUS]" link type="success" size="small" @click="handleStatus(row, 'APPROVED')">通过</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.EXECUTION_PURCHASE_STATUS]" link type="danger" size="small" @click="handleStatus(row, 'REJECTED')">驳回</el-button>
            <el-button v-if="row.status === 'APPROVED'" v-permission="[PC.EXECUTION_PURCHASE_STATUS]" link type="primary" size="small" @click="handleStatus(row, 'RECEIVED')">收货</el-button>
            <el-button v-if="row.status === 'RECEIVED'" v-permission="[PC.EXECUTION_PURCHASE_STATUS]" link type="success" size="small" @click="handleStatus(row, 'PAID')">付款</el-button>
            <el-button v-permission="[PC.EXECUTION_PURCHASE_DELETE]" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <!-- 新增采购单弹窗：表单字段 + 预算强管控提示 -->
    <el-dialog v-model="dialogVisible" title="新增采购单" width="640px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="采购单号" prop="purchaseCode"><el-input v-model="form.purchaseCode" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="项目 ID" prop="initiationId"><el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="供应商"><el-input v-model="form.vendor" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="申请人 ID" prop="applicantId"><el-input-number v-model="form.applicantId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="物品名称" prop="itemName"><el-input v-model="form.itemName" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="数量"><el-input-number v-model="form.quantity" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="单价"><el-input-number v-model="form.unitPrice" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="金额"><el-input-number :model-value="Number(form.quantity || 0) * Number(form.unitPrice || 0)" :min="0" disabled style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="采购日期">
          <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon>
          提示: 提交时将触发【预算强管控】校验，确保采购金额不超出项目预算。
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
