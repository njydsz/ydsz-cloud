<!--
  @file 项目变更管理
  @description 项目变更管理页面，支持变更创建、状态机流转（DRAFT→SUBMITTED→UNDER_REVIEW→APPROVED/REJECTED→EXECUTING→EXECUTED）、影响等级评估与详情查看，对接 @/api/initiation/change 模块。
  @module views/change
-->
<script setup lang="ts">
/**
 * 项目变更管理（批次 19 补全 + 批次 21 / P2 useTable 重构）
 *
 * 状态机: DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED
 *         APPROVED → EXECUTING → EXECUTED
 *         DRAFT/SUBMITTED/UNDER_REVIEW/APPROVED/EXECUTING → CANCELLED
 * 终态:   EXECUTED / REJECTED / CANCELLED
 * 影响等级: LOW / MEDIUM / HIGH（后端 ChangeImpactEvaluator 多因子评估）
 * 重大变更: GM + CFO 双审批
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import {
  pageProjectChanges,
  getProjectChange,
  createProjectChange,
  changeProjectChangeStatus,
  deleteProjectChange,
  getAllowedTransitions,
} from '@/api/initiation/change'
import type {
  ProjectChangeVO,
  ProjectChangeCreateDTO,
} from '@/api/initiation/change/types'
import { PC } from '@/constants/permissionCodes'
import { useTable } from '@/composables/useTable'
import type { PageResult } from '@/utils/request'

const { t } = useI18n()

// ===== 列表查询 (useTable composable) =====
const {
  loading,
  list,
  total,
  error,
  query,
  fetchData: fetchList,
  handleQuery,
  resetQuery,
  handlePageChange,
} = useTable<{
  page: number
  size: number
  keyword: string
  status: string
  changeType: string
  initiationId: number | undefined
}>(async (q) => {
  const resp = await pageProjectChanges(q.page, q.size, {
    keyword: q.keyword || undefined,
    changeType: q.changeType || undefined,
    status: q.status || undefined,
    initiationId: q.initiationId,
  })
  const data = resp.data ?? (resp as unknown as PageResult)
  return { list: data.list || [], total: data.total || 0, page: data.page, size: data.size, pages: data.pages }
}, { defaultSize: 10 })

// 状态映射（与后端 ChangeStatus 枚举对齐，响应语言切换）
const statusMap = computed<Record<string, { label: string; type: 'info' | 'warning' | 'success' | 'danger' | 'primary' }>>(() => ({
  DRAFT:        { label: t('common.status.draft'),     type: 'info' },
  SUBMITTED:    { label: t('common.status.submitted'), type: 'warning' },
  UNDER_REVIEW: { label: t('change.status.underReview'), type: 'warning' },
  APPROVED:     { label: t('common.status.approved'), type: 'success' },
  REJECTED:     { label: t('common.status.rejected'), type: 'danger' },
  EXECUTING:    { label: t('common.status.running'),  type: 'primary' },
  EXECUTED:     { label: t('common.status.executed'), type: 'success' },
  CANCELLED:    { label: t('common.status.canceled'), type: 'info' },
}))

// 变更类型（与后端 ChangeType 枚举对齐：SCOPE/COST/CONTRACT/STAFF/SCHEDULE）
const typeMap = computed<Record<string, { label: string; color: string }>>(() => ({
  SCOPE:    { label: t('change.type.scope'),    color: '#409EFF' },
  COST:     { label: t('change.type.cost'),     color: '#F56C6C' },
  CONTRACT: { label: t('change.type.contract'), color: '#E6A23C' },
  STAFF:    { label: t('change.type.staff'),    color: '#67C23A' },
  SCHEDULE: { label: t('change.type.schedule'), color: '#909399' },
}))

// 风险等级（与后端 RiskLevel 枚举对齐）
const riskMap = computed<Record<string, { label: string; color: string }>>(() => ({
  LOW:    { label: t('change.risk.low'),    color: '#67C23A' },
  MEDIUM: { label: t('change.risk.medium'), color: '#E6A23C' },
  HIGH:   { label: t('change.risk.high'),   color: '#F56C6C' },
}))

// 状态机迁移规则 (前端兜底; 服务端 allowed-transitions 优先)
const transitions: Record<string, string[]> = {
  DRAFT:        ['SUBMITTED', 'CANCELLED'],
  SUBMITTED:    ['UNDER_REVIEW', 'CANCELLED'],
  UNDER_REVIEW: ['APPROVED', 'REJECTED'],
  APPROVED:     ['EXECUTING', 'CANCELLED'],
  EXECUTING:    ['EXECUTED', 'CANCELLED'],
  EXECUTED:     [],
  REJECTED:     [],
  CANCELLED:    [],
}

// 优先从后端拉取 allowed-transitions; 失败时用前端兜底
const backendAllowedMap = reactive<Record<number, string[]>>({})

/**
 * 加载指定变更的后端允许状态迁移列表，缓存避免重复请求
 * @param id 变更 ID
 */
async function loadAllowedTransitions(id: number) {
  if (backendAllowedMap[id]) return
  try {
    const { data } = await getAllowedTransitions(id)
    backendAllowedMap[id] = data || []
  } catch {
    backendAllowedMap[id] = []
  }
}

/**
 * 获取当前行允许迁移的目标状态列表，后端优先、前端兜底
 * @param row 当前行变更数据
 * @returns 允许的目标状态码数组
 */
function allowedTargets(row: ProjectChangeVO): string[] {
  return backendAllowedMap[row.id]?.length
    ? backendAllowedMap[row.id]
    : transitions[row.status] || []
}

/** 是否处于空态: 非加载中且列表无数据 */
const isEmpty = computed(() => !loading.value && list.value.length === 0)

/** 选中的项目变更行 (用于批量操作) */
const selectedRows = ref<ProjectChangeVO[]>([])

/** 表格勾选行变更回调，同步本地选中列表 */
function onSelectionChange({ records }: { records: ProjectChangeVO[] }) {
  selectedRows.value = records
}

/** 重置查询条件并重新加载列表 */
function handleReset() {
  resetQuery()
}

// ========== 新增/编辑 ==========
/** 新增变更弹窗显隐 */
const dialogVisible = ref(false)
/** 提交中状态（防重复提交） */
const submitting = ref(false)
/** 新增变更表单引用 */
const formRef = ref<FormInstance>()
/** 新增变更表单数据 */
const form = reactive<Partial<ProjectChangeCreateDTO>>({
  changeCode: '',
  initiationId: undefined,
  changeType: 'SCOPE',
  changeTitle: '',
  changeReason: '',
  changeDesc: '',
  budgetImpact: undefined,
  contractImpact: undefined,
  scheduleImpactDays: undefined,
  profitImpact: undefined,
  affectedWbsCount: undefined,
  affectedStaffCount: undefined,
  contractId: undefined,
  applicantId: 1,
  applicantName: '',
  remark: '',
})

const formRules = computed(() => ({
  changeCode: [{ required: true, message: t('common.rule.required', { field: t('change.field.changeCode') }), trigger: 'blur' }],
  initiationId: [{ required: true, message: t('common.rule.required', { field: t('change.field.initiationId') }), trigger: 'blur' }],
  changeTitle: [{ required: true, message: t('common.rule.required', { field: t('change.field.changeTitle') }), trigger: 'blur' }],
}))

/** 打开新增变更弹窗，自动生成变更编号并重置表单为默认值 */
function openCreate() {
  Object.assign(form, {
    changeCode: `CHG-${Date.now().toString().slice(-8)}`,
    initiationId: undefined,
    changeType: 'SCOPE',
    changeTitle: '',
    changeReason: '',
    changeDesc: '',
    budgetImpact: undefined,
    contractImpact: undefined,
    scheduleImpactDays: undefined,
    profitImpact: undefined,
    affectedWbsCount: undefined,
    affectedStaffCount: undefined,
    contractId: undefined,
    applicantId: 1,
    applicantName: '',
    remark: '',
  })
  dialogVisible.value = true
}

/** 提交新增变更表单，校验通过后调用创建接口并刷新列表 */
async function submitForm() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    await createProjectChange(form as ProjectChangeCreateDTO)
    ElMessage.success(t('change.message.submitted'))
    dialogVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

// ========== 状态迁移 ==========
/**
 * 变更状态迁移，重大变更需 GM+CFO 双审批（前端提示）
 * @param row 当前行变更数据
 * @param target 目标状态码
 */
async function handleStatus(row: ProjectChangeVO, target: string) {
  const targetLabel = statusMap.value[target]?.label || target
  let extraHint = ''
  if (row.majorFlag === 1 && (target === 'APPROVED' || target === 'UNDER_REVIEW')) {
    extraHint = '\n' + t('change.message.majorChangeHint', { roles: row.approverRoles || 'GM/CFO' })
  }
  try {
    await ElMessageBox.confirm(
      t('change.message.confirmStatusChange', { code: row.changeCode, target: targetLabel, hint: extraHint }),
      t('change.dialog.statusTransition'),
      { type: 'warning' },
    )
    await changeProjectChangeStatus({ id: row.id, targetStatus: target })
    ElMessage.success(t('common.message.statusUpdated'))
    fetchList()
  } catch { /* 取消 */ }
}

// ========== 删除 ==========
/**
 * 删除项目变更，仅 DRAFT/REJECTED/CANCELLED 状态可删除
 * @param row 当前行变更数据
 */
async function handleDelete(row: ProjectChangeVO) {
  try {
    await ElMessageBox.confirm(
      t('change.message.confirmDelete', { code: row.changeCode }),
      t('common.dialog.deleteTitle'),
      { type: 'warning' },
    )
    await deleteProjectChange(row.id)
    ElMessage.success(t('common.message.deleted'))
    fetchList()
  } catch { /* 取消 */ }
}

// ========== 详情抽屉 ==========
/** 详情抽屉显隐 */
const detailVisible = ref(false)
/** 详情加载状态 */
const detailLoading = ref(false)
/** 当前查看的变更详情 */
const detail = ref<ProjectChangeVO | null>(null)

/**
 * 打开详情抽屉，并行加载后端 allowed-transitions 与详情数据
 * @param row 当前行变更数据
 */
async function openDetail(row: ProjectChangeVO) {
  detail.value = row
  detailVisible.value = true
  detailLoading.value = true
  // 并行加载 allowed-transitions
  loadAllowedTransitions(row.id)
  try {
    const { data } = await getProjectChange(row.id)
    detail.value = data
  } finally {
    detailLoading.value = false
  }
}

/**
 * 格式化金额为千分位两位小数
 * @param n 金额数值
 * @returns 格式化后的字符串，空值返回 '-'
 */
function fmtAmount(n?: number) {
  if (n === undefined || n === null) return '-'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/**
 * 格式化影响金额，正数前加 '+' 号
 * @param n 影响金额数值
 * @returns 带符号的格式化字符串
 */
function fmtImpact(n?: number) {
  if (n === undefined || n === null) return '-'
  const prefix = n > 0 ? '+' : ''
  return prefix + fmtAmount(n)
}

/**
 * 格式化百分比为保留两位小数
 * @param n 比例值（0-1）
 * @returns 百分比字符串
 */
function fmtPct(n?: number) {
  if (n === undefined || n === null) return '-'
  return (n * 100).toFixed(2) + '%'
}

// 影响等级预估（前端即时提示, 后端评估为准）
const estimatedRisk = computed(() => {
  let score = 0
  if (form.budgetImpact && Math.abs(form.budgetImpact) > 100000) score += 2
  else if (form.budgetImpact && Math.abs(form.budgetImpact) > 10000) score += 1
  if (form.scheduleImpactDays && Math.abs(form.scheduleImpactDays) > 14) score += 2
  else if (form.scheduleImpactDays && Math.abs(form.scheduleImpactDays) > 3) score += 1
  if (form.affectedWbsCount && form.affectedWbsCount > 5) score += 1
  if (form.affectedStaffCount && form.affectedStaffCount > 3) score += 1
  if (score >= 4) return { level: 'HIGH', label: t('change.risk.highMajor'), color: '#F56C6C' }
  if (score >= 2) return { level: 'MEDIUM', label: t('change.risk.medium'), color: '#E6A23C' }
  return { level: 'LOW', label: t('change.risk.low'), color: '#67C23A' }
})

onMounted(() => {
  fetchList()
})
</script>

<template>
  <PageLayout
    v-model:query="query"
    :list="list"
    :total="total"
    :loading="loading"
    :error="error"
    search-collapsible
    :search-collapse-count="3"
    empty-preset="list"
    :empty-action-text="t('change.button.create')"
    @query="handleQuery"
    @reset="handleReset"
    @page-change="handlePageChange"
    @refresh="fetchList"
    @retry="fetchList"
    @empty-action="openCreate"
  >
    <template #search>
      <el-form-item :label="t('change.field.keyword')">
        <el-input v-model="query.keyword" :placeholder="t('change.placeholder.keyword')" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item :label="t('common.column.status')">
        <el-select v-model="query.status" :placeholder="t('common.all')" clearable style="width: 130px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('common.column.type')">
        <el-select v-model="query.changeType" :placeholder="t('common.all')" clearable style="width: 140px">
          <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('change.field.initiationId')">
        <el-input v-model.number="query.initiationId" :placeholder="t('change.placeholder.initiationId')" clearable style="width: 140px" />
      </el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.PROJECT_CHANGE_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        {{ t('change.button.create') }}
      </el-button>
    </template>

    <template #table="scope">
      <EmptyState
        v-if="isEmpty"
        preset="search"
        :title="query.keyword || query.status || query.changeType || query.initiationId ? t('change.empty.noMatch') : t('change.empty.noData')"
        :description="query.keyword || query.status || query.changeType || query.initiationId ? t('change.empty.adjustFilter') : t('change.empty.createHint')"
        :action-text="t('change.button.resetFilter')"
        @action="resetQuery"
      />
      <vxe-table v-else :data="list" :loading="loading" border stripe :height="scope.tableProps.height" :scroll-y="scope.tableProps.scrollY" @checkbox-change="onSelectionChange" @checkbox-all="onSelectionChange">
        <vxe-column type="checkbox" width="50" />
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="changeCode" :title="t('change.field.changeCode')" width="170" fixed="left" />
        <vxe-column field="changeTitle" :title="t('change.field.changeTitle')" min-width="200" show-overflow />
        <vxe-column field="changeType" :title="t('common.column.type')" width="110">
          <template #default="{ row }">
            <el-tag v-if="typeMap[row.changeType]" :color="typeMap[row.changeType].color" effect="light" size="small" style="color: #fff; border: none">
              {{ typeMap[row.changeType].label }}
            </el-tag>
            <span v-else>{{ row.changeType || '-' }}</span>
          </template>
        </vxe-column>
        <vxe-column field="status" :title="t('common.column.status')" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.status" :map="statusMap" />
          </template>
        </vxe-column>
        <vxe-column field="riskLevelAfter" :title="t('change.field.impactLevel')" width="100">
          <template #default="{ row }">
            <el-tag v-if="riskMap[row.riskLevelAfter]" :type="row.riskLevelAfter === 'HIGH' ? 'danger' : row.riskLevelAfter === 'MEDIUM' ? 'warning' : 'success'" size="small">
              {{ riskMap[row.riskLevelAfter].label }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </vxe-column>
        <vxe-column :title="t('change.field.major')" width="60" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.majorFlag === 1" type="danger" size="small">{{ t('change.field.major') }}</el-tag>
            <span v-else>-</span>
          </template>
        </vxe-column>
        <vxe-column field="budgetImpact" :title="t('change.field.budgetImpact')" width="120" align="right">
          <template #default="{ row }">
            <span :style="{ color: (row.budgetImpact || 0) > 0 ? '#F56C6C' : (row.budgetImpact || 0) < 0 ? '#67C23A' : undefined }">
              {{ fmtImpact(row.budgetImpact) }}
            </span>
          </template>
        </vxe-column>
        <vxe-column field="scheduleImpactDays" :title="t('change.field.scheduleImpact')" width="110" align="right">
          <template #default="{ row }">
            <span :style="{ color: (row.scheduleImpactDays || 0) > 0 ? '#E6A23C' : (row.scheduleImpactDays || 0) < 0 ? '#67C23A' : undefined }">
              {{ fmtImpact(row.scheduleImpactDays) }}
            </span>
          </template>
        </vxe-column>
        <vxe-column field="profitImpactPct" :title="t('change.field.profitImpactPct')" width="100" align="right">
          <template #default="{ row }">
            <span :style="{ color: (row.profitImpactPct || 0) < 0 ? '#F56C6C' : '#67C23A' }">
              {{ fmtPct(row.profitImpactPct) }}
            </span>
          </template>
        </vxe-column>
        <vxe-column field="applicantName" :title="t('common.column.applicant')" width="100" />
        <vxe-column field="createdAt" :title="t('common.column.createdAt')" width="170" />
        <vxe-column :title="t('common.column.action')" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">{{ t('common.button.viewDetail') }}</el-button>
            <el-button
              v-for="target in allowedTargets(row)"
              :key="target"
              link
              size="small"
              :type="target === 'APPROVED' ? 'success' : target === 'REJECTED' ? 'danger' : 'primary'"
              :disabled="row.majorFlag === 1 && target === 'APPROVED' && row.status === 'UNDER_REVIEW'"
              @click="handleStatus(row, target)"
            >
              {{ statusMap[target]?.label || target }}
            </el-button>
            <el-button
              v-if="['DRAFT', 'REJECTED', 'CANCELLED'].includes(row.status)"
              v-permission="[PC.PROJECT_CHANGE_STATUS]"
              link
              type="danger"
              size="small"
              @click="handleDelete(row)"
            >
              {{ t('common.delete') }}
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <!-- 新增变更弹窗 -->
    <el-dialog v-model="dialogVisible" :title="t('change.dialog.create')" width="900px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('change.field.changeCode')" prop="changeCode">
              <el-input v-model="form.changeCode" :placeholder="t('change.placeholder.changeCode')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('change.field.initiationId')" prop="initiationId">
              <el-input v-model.number="form.initiationId" :placeholder="t('change.placeholder.initiationId')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('change.field.changeType')">
              <el-select v-model="form.changeType" style="width: 100%">
                <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('change.field.estimatedLevel')">
              <el-tag :color="estimatedRisk.color" effect="dark" size="default" style="color: #fff; border: none">
                {{ estimatedRisk.label }}
              </el-tag>
              <span style="margin-left: 8px; color: #909399; font-size: 12px">
                {{ t('change.field.estimatedHint') }}
              </span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('change.field.changeTitle')" prop="changeTitle">
          <el-input v-model="form.changeTitle" :placeholder="t('change.placeholder.changeTitle')" />
        </el-form-item>
        <el-form-item :label="t('change.field.changeReason')">
          <el-input v-model="form.changeReason" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('change.field.changeDesc')">
          <el-input v-model="form.changeDesc" type="textarea" :rows="3" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item :label="t('change.field.budgetImpact')">
              <el-input v-model.number="form.budgetImpact" type="number" :placeholder="t('change.unit.yuan')" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="t('change.field.contractImpact')">
              <el-input v-model.number="form.contractImpact" type="number" :placeholder="t('change.unit.yuan')" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="t('change.field.scheduleImpact')">
              <el-input v-model.number="form.scheduleImpactDays" type="number" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="t('change.field.profitImpact')">
              <el-input v-model.number="form.profitImpact" type="number" :placeholder="t('change.unit.yuan')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item :label="t('change.field.affectedWbs')">
              <el-input v-model.number="form.affectedWbsCount" type="number" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('change.field.affectedStaff')">
              <el-input v-model.number="form.affectedStaffCount" type="number" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('change.field.contractId')">
              <el-input v-model.number="form.contractId" type="number" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('common.column.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{ t('common.submit') }}</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="t('change.dialog.detail')" size="60%">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="t('change.field.changeCode')">{{ detail.changeCode }}</el-descriptions-item>
            <el-descriptions-item :label="t('common.column.status')">
              <StatusTag :value="detail.status" :map="statusMap" />
            </el-descriptions-item>
            <el-descriptions-item :label="t('common.column.type')">
              <el-tag v-if="typeMap[detail.changeType]" :color="typeMap[detail.changeType].color" effect="light" size="small" style="color: #fff; border: none">
                {{ typeMap[detail.changeType].label }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="t('change.field.impactLevel')">
              <el-tag v-if="riskMap[detail.riskLevelAfter as string]" :type="detail.riskLevelAfter === 'HIGH' ? 'danger' : detail.riskLevelAfter === 'MEDIUM' ? 'warning' : 'success'" size="small">
                {{ riskMap[detail.riskLevelAfter as string].label }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="t('change.field.majorChange')">
              <el-tag v-if="detail.majorFlag === 1" type="danger" size="small">{{ t('common.yes') }}</el-tag>
              <span v-else>{{ t('common.no') }}</span>
            </el-descriptions-item>
            <el-descriptions-item :label="t('change.field.initiationId')">{{ detail.initiationId }}</el-descriptions-item>
            <el-descriptions-item :label="t('common.column.title')" :span="2">{{ detail.changeTitle }}</el-descriptions-item>
            <el-descriptions-item :label="t('change.field.changeReason')" :span="2">{{ detail.changeReason || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('change.field.changeDesc')" :span="2">{{ detail.changeDesc || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('change.field.budgetImpact')">{{ fmtImpact(detail.budgetImpact) }}</el-descriptions-item>
            <el-descriptions-item :label="t('change.field.contractImpact')">{{ fmtImpact(detail.contractImpact) }}</el-descriptions-item>
            <el-descriptions-item :label="t('change.field.scheduleImpact')">{{ fmtImpact(detail.scheduleImpactDays) }} {{ t('change.unit.days') }}</el-descriptions-item>
            <el-descriptions-item :label="t('change.field.profitImpact')">{{ fmtPct(detail.profitImpactPct) }}</el-descriptions-item>
            <el-descriptions-item :label="t('common.column.applicant')">{{ detail.applicantName }}</el-descriptions-item>
            <el-descriptions-item :label="t('common.column.createdAt')">{{ detail.createdAt }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.approverRoles" :label="t('change.field.approverRoles')" :span="2">
              <el-tag type="warning" size="small">{{ detail.approverRoles }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.remark" :label="t('common.column.remark')" :span="2">{{ detail.remark }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
    </el-drawer>
  </PageLayout>
</template>
