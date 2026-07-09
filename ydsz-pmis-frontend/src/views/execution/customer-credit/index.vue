<!--
  @file 客户信用管理
  @description 客户信用评分与等级管理页面，支持列表查询、按客户查询最新信用、人工触发信用评估；
               评分模型: 30 基础分（新客户） + 合同数 + 合作年限 + 付款习惯 - 逾期惩罚；
               等级划分: A(90-100) / B(75-89) / C(60-74) / D(0-59)。
  @module views/execution/customer-credit
-->
<script setup lang="ts">
/**
 * 客户信用管理
 *
 * 评分模型: 30 基础分(新客户) + 合同数 + 合作年限 + 付款习惯 - 逾期惩罚
 * 等级: A(90-100) / B(75-89) / C(60-74) / D(0-59)
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useI18n } from 'vue-i18n'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageCustomerCredits,
  assessCustomerCredit,
  getCreditByCustomer,
} from '@/api/finance/credit'
import type { CustomerCreditVO, CreditAssessmentDTO } from '@/api/finance/credit/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

/** 列表加载状态 */
const loading = ref(false)
/** 客户信用记录列表 */
const list = ref<CustomerCreditVO[]>([])
/** 记录总数（分页用） */
const total = ref(0)
/** 查询条件：关键字 + 等级 + 客户 ID */
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  level: '',
  customerId: undefined as number | undefined,
})

/** 信用等级 → 标签/样式映射（A/B/C/D） */
const levelMap = computed(() => ({
  A: { label: t('finance.credit.level.A'), type: 'success' as const },
  B: { label: t('finance.credit.level.B'), type: 'primary' as const },
  C: { label: t('finance.credit.level.C'), type: 'warning' as const },
  D: { label: t('finance.credit.level.D'), type: 'danger' as const },
}))

/** 付款习惯 → 中文标签映射 */
const habitMap = computed(() => ({
  ONTIME: { label: t('finance.credit.habit.ONTIME') },
  OFTEN_LATE: { label: t('finance.credit.habit.OFTEN_LATE') },
  SEVERE_LATE: { label: t('finance.credit.habit.SEVERE_LATE') },
}))

/** 分页查询客户信用列表 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageCustomerCredits(query.page, query.size, {
      keyword: query.keyword,
      level: query.level,
      customerId: query.customerId,
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
  query.level = ''
  query.customerId = undefined
  query.page = 1
  fetchList()
}

/**
 * 按客户查询最新信用评级并以消息提示
 * @param row 当前客户信用记录
 */
async function handleQuery(row: CustomerCreditVO) {
  try {
    const { data } = await getCreditByCustomer(row.customerId)
    ElMessage.success(t('finance.credit.messages.queryResult', { level: data?.level, score: data?.score }))
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || t('finance.credit.messages.queryFailed'))
  }
}

/** 提交按钮 loading 状态，防止重复提交 */
const submitting = ref(false)
/** 信用评估弹窗可见性 */
const dialogVisible = ref(false)
/** 表单引用（用于校验） */
const formRef = ref<FormInstance>()
/** 信用评估入参表单 */
const form = reactive<Partial<CreditAssessmentDTO>>({
  customerId: 0,
  customerName: '',
  contractCount: 0,
  totalContractAmount: 0,
  overdueCount: 0,
  overdueAmount: 0,
  cooperationYears: 0,
  paymentHabit: 'ONTIME',
})

/** 表单校验规则 */
const formRules = computed(() => ({
  customerId: [{ required: true, message: t('finance.credit.rules.customerIdRequired'), trigger: 'blur' }],
}))

/** 打开信用评估弹窗并重置表单为默认值 */
function openAssess() {
  Object.assign(form, {
    customerId: 0,
    customerName: '',
    contractCount: 0,
    totalContractAmount: 0,
    overdueCount: 0,
    overdueAmount: 0,
    cooperationYears: 0,
    paymentHabit: 'ONTIME',
  })
  dialogVisible.value = true
}

/** 提交信用评估，成功后展示评级与分数并刷新列表 */
async function submitAssess() {
  try {
    submitting.value = true
    await formRef.value?.validate()
    const { data } = await assessCustomerCredit(form as CreditAssessmentDTO)
    ElMessage.success(t('finance.credit.messages.assessResult', { level: data.level, score: data.score }))
    dialogVisible.value = false
    fetchList()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || t('finance.credit.messages.assessFailed'))
  } finally {
    submitting.value = false
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
    @query="query.page = 1; fetchList()"
    @reset="handleReset"
    @page-change="fetchList"
    @refresh="fetchList"
  >
    <!-- 搜索栏 -->
    <template #search>
      <el-form-item :label="t('finance.credit.search.keyword')"><el-input v-model="query.keyword" :placeholder="t('finance.credit.search.keywordPlaceholder')" clearable @keyup.enter="query.page = 1; fetchList()" /></el-form-item>
      <el-form-item :label="t('finance.credit.search.level')">
        <el-select v-model="query.level" :placeholder="t('common.all')" clearable style="width: 120px">
          <el-option v-for="(v, k) in levelMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
    </template>

    <!-- 工具栏 -->
    <template #toolbar>
      <el-button v-permission="[PC.FINANCE_CREDIT_ASSESS]" type="primary" :icon="'Plus'" @click="openAssess">
        {{ t('finance.credit.buttons.assess') }}
      </el-button>
    </template>

    <!-- 客户信用列表表格 -->
    <template #table="scope">
      <vxe-table :data="list" :loading="loading" border stripe :height="scope.tableProps.height" :scroll-y="scope.tableProps.scrollY">
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="customerId" :title="t('finance.credit.columns.customerId')" width="100" align="center" />
        <vxe-column field="customerName" :title="t('finance.credit.columns.customerName')" min-width="200" show-overflow />
        <vxe-column field="score" :title="t('finance.credit.columns.score')" width="100" align="center">
          <template #default="{ row }">
            <span :style="{ color: Number(row.score) >= 90 ? '#67c23a' : Number(row.score) >= 60 ? '#e6a23c' : '#f56c6c', fontWeight: 600 }">
              {{ row.score }}
            </span>
          </template>
        </vxe-column>
        <vxe-column field="level" :title="t('finance.credit.columns.level')" width="100" align="center">
          <template #default="{ row }"><StatusTag :value="row.level" :map="levelMap" /></template>
        </vxe-column>
        <vxe-column field="contractCount" :title="t('finance.credit.columns.contractCount')" width="100" align="center" />
        <vxe-column field="totalContractAmount" :title="t('finance.credit.columns.totalContractAmount')" width="140" align="right" :formatter="({ cellValue }) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="overdueCount" :title="t('finance.credit.columns.overdueCount')" width="100" align="center" />
        <vxe-column field="overdueAmount" :title="t('finance.credit.columns.overdueAmount')" width="130" align="right" :formatter="({ cellValue }) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="lastAssessDate" :title="t('finance.credit.columns.lastAssessDate')" width="120" />
        <vxe-column :title="t('finance.credit.columns.action')" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleQuery(row)">{{ t('finance.credit.buttons.query') }}</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <!-- 信用评估弹窗 -->
    <el-dialog v-model="dialogVisible" :title="t('finance.credit.dialog.assessTitle')" width="600px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item :label="t('finance.credit.form.customerId')" prop="customerId"><el-input-number v-model="form.customerId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('finance.credit.form.customerName')"><el-input v-model="form.customerName" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item :label="t('finance.credit.form.contractCount')"><el-input-number v-model="form.contractCount" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('finance.credit.form.cooperationYears')"><el-input-number v-model="form.cooperationYears" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item :label="t('finance.credit.form.totalContractAmount')"><el-input-number v-model="form.totalContractAmount" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('finance.credit.form.paymentHabit')"><el-select v-model="form.paymentHabit" style="width: 100%"><el-option v-for="(v, k) in habitMap" :key="k" :label="v.label" :value="k" /></el-select></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item :label="t('finance.credit.form.overdueCount')"><el-input-number v-model="form.overdueCount" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('finance.credit.form.overdueAmount')"><el-input-number v-model="form.overdueAmount" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-alert type="info" :closable="false" show-icon>
          {{ t('finance.credit.assessTip') }}
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAssess">{{ t('finance.credit.buttons.submit') }}</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
