<!--
  @file 项目立项管理
  @description 立项的查询、新增、阶段流转、预算管理与门径评审；阶段机 DRAFT/UNDER_REVIEW/APPROVED/REJECTED/EXECUTING/CLOSED，门径 CD1_KICKOFF → CD2_DESIGN → CD3_BUILD → CD4_UAT → CD5_GO_LIVE；对接自研工作流审批流与 @/api/initiation
  @module views/initiation
-->
<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageInitiations,
  createInitiation,
  changeInitiationStage,
  deleteInitiation,
  addBudgetItem,
  listBudget,
  reviewGate,
  startInitiationProcess,
} from '@/api/initiation'
import type { InitiationVO, InitiationCreateDTO, BudgetItemVO } from '@/api/initiation/types'
import { PC } from '@/constants/permissionCodes'
import { useFormDraft } from '@/composables/useFormDraft'
import { useFormGuard } from '@/composables/useFormGuard'
import { useUserStore } from '@/store/modules/user'

const { t } = useI18n()

const loading = ref(false)
/** H17.1 修复：3 个提交按钮共享 loading 状态，防止重复提交 */
const submitting = ref(false)
const submittingBudget = ref(false)
const submittingGate = ref(false)
const list = ref<InitiationVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  stage: '',
  projectLevel: '',
  pmId: undefined as number | undefined,
})

const stageMap = computed(() => ({
  DRAFT: { label: t('project.initiation.stage.DRAFT'), type: 'info' as const },
  UNDER_REVIEW: { label: t('project.initiation.stage.UNDER_REVIEW'), type: 'warning' as const },
  APPROVED: { label: t('project.initiation.stage.APPROVED'), type: 'success' as const },
  REJECTED: { label: t('project.initiation.stage.REJECTED'), type: 'danger' as const },
  EXECUTING: { label: t('project.initiation.stage.EXECUTING'), type: 'primary' as const },
  CLOSED: { label: t('project.initiation.stage.CLOSED'), type: 'info' as const },
}))

const levelMap = computed(() => ({
  A: { label: t('project.initiation.level.A'), type: 'danger' as const },
  B: { label: t('project.initiation.level.B'), type: 'warning' as const },
  C: { label: t('project.initiation.level.C'), type: 'info' as const },
  D: { label: t('project.initiation.level.D'), type: 'info' as const },
}))

const gateMap = computed(() => ({
  CD1_KICKOFF: { label: t('project.initiation.gate.CD1_KICKOFF'), type: 'info' as const },
  CD2_DESIGN: { label: t('project.initiation.gate.CD2_DESIGN'), type: 'primary' as const },
  CD3_BUILD: { label: t('project.initiation.gate.CD3_BUILD'), type: 'primary' as const },
  CD4_UAT: { label: t('project.initiation.gate.CD4_UAT'), type: 'warning' as const },
  CD5_GO_LIVE: { label: t('project.initiation.gate.CD5_GO_LIVE'), type: 'success' as const },
}))

/** 拉取立项分页列表 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageInitiations(query.page, query.size, {
      keyword: query.keyword,
      stage: query.stage,
      projectLevel: query.projectLevel,
      pmId: query.pmId,
    })
    list.value = data.list
    total.value = data.total
  } catch {
    // H16.1 修复：查询失败时清空陈旧数据
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 重置查询条件并重新加载列表 */
function handleReset() {
  query.keyword = ''
  query.stage = ''
  query.projectLevel = ''
  query.pmId = undefined
  query.page = 1
  fetchList()
}

// 立项弹窗
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<Partial<InitiationCreateDTO>>({
  projectCode: '',
  projectName: '',
  customerId: 0,
  customerName: '',
  projectType: 'INTERNAL',
  projectLevel: 'C',
  pmId: undefined,
  estimatedAmount: undefined,
  budgetAmount: undefined,
  plannedStartDate: '',
  plannedEndDate: '',
})

const formRules = {
  projectCode: [{ required: true, message: t('project.initiation.rules.projectCodeRequired'), trigger: 'blur' }],
  projectName: [{ required: true, message: t('project.initiation.rules.projectNameRequired'), trigger: 'blur' }],
  customerId: [{ required: true, message: t('project.initiation.rules.customerIdRequired'), trigger: 'blur' }],
  projectType: [{ required: true, message: t('project.initiation.rules.projectTypeRequired'), trigger: 'change' }],
}

// ===== 表单草稿 =====
const userStore = useUserStore()
const { hasDraft, lastSavedAt, restore, clear: clearDraft } = useFormDraft(form, {
  key: 'initiation-create',
  debounce: 3000,
  userId: userStore.userInfo?.id,
})

// ===== 表单防误关闭守卫 =====
const { setDirty } = useFormGuard({ message: '立项表单内容未保存，确定离开？' })
// 表单字段修改时启用守卫（仅弹窗打开期间）
watch(form, () => {
  if (dialogVisible.value) setDirty(true)
}, { deep: true })
// 弹窗打开后清除 dirty（覆盖 openCreate 重置 form 触发的 watch）
watch(dialogVisible, (val) => {
  if (val) nextTick(() => setDirty(false))
})

const draftTimeText = computed(() => {
  if (!lastSavedAt.value) return ''
  return lastSavedAt.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
})

/** 打开新建立项弹窗，重置表单为初始值；若检测到草稿则提示恢复 */
function openCreate() {
  if (hasDraft.value) {
    ElMessageBox.confirm(t('project.initiation.messages.confirmRestoreDraft'), t('common.tip'), { type: 'info' })
      .then(() => {
        restore()
        ElMessage.success(t('project.initiation.messages.draftRestored'))
        dialogVisible.value = true
      })
      .catch(() => {
        clearDraft()
        Object.assign(form, {
          projectCode: '',
          projectName: '',
          customerId: 0,
          customerName: '',
          projectType: 'INTERNAL',
          projectLevel: 'C',
          pmId: undefined,
          estimatedAmount: undefined,
          budgetAmount: undefined,
          plannedStartDate: '',
          plannedEndDate: '',
          description: '',
          businessCase: '',
          riskAssessment: '',
        })
        dialogVisible.value = true
      })
    return
  }
  Object.assign(form, {
    projectCode: '',
    projectName: '',
    customerId: 0,
    customerName: '',
    projectType: 'INTERNAL',
    projectLevel: 'C',
    pmId: undefined,
    estimatedAmount: undefined,
    budgetAmount: undefined,
    plannedStartDate: '',
    plannedEndDate: '',
    description: '',
    businessCase: '',
    riskAssessment: '',
  })
  dialogVisible.value = true
}

/** 提交立项表单：校验通过后创建并刷新列表 */
async function submitForm() {
  // H16.2 修复：try/catch 包裹避免 success 误报；H17.1 修复：submitting 防重复
  try {
    submitting.value = true
    await formRef.value?.validate()
    await createInitiation(form as InitiationCreateDTO)
    clearDraft()
    // 表单已保存，解除防误关闭守卫
    setDirty(false)
    ElMessage.success(t('project.initiation.messages.createSuccess'))
    dialogVisible.value = false
    fetchList()
  } catch {
    // 校验或创建失败：拦截器已弹错，保持弹窗打开
  } finally {
    submitting.value = false
  }
}

/**
 * 删除立项（二次确认）
 * @param row 选中的立项行数据
 */
async function handleDelete(row: InitiationVO) {
  try {
    await ElMessageBox.confirm(t('project.initiation.messages.confirmDelete', { name: row.projectName }), t('common.tip'), { type: 'warning' })
    await deleteInitiation(row.id)
    ElMessage.success(t('project.initiation.messages.deleteSuccess'))
    fetchList()
  } catch { /* 取消 */ }
}

/**
 * 变更立项阶段（二次确认），阶段机见文件头
 * @param row 选中的立项行数据
 * @param target 目标阶段编码
 */
async function handleStage(row: InitiationVO, target: string) {
  const targetText = (stageMap.value as Record<string, { label: string }>)[target]?.label || target
  try {
    await ElMessageBox.confirm(t('project.initiation.messages.confirmStageChange', { target: targetText }), t('common.tip'), { type: 'warning' })
    await changeInitiationStage({ id: row.id, targetStage: target })
    ElMessage.success(t('project.initiation.messages.stageUpdated'))
    fetchList()
  } catch { /* 取消 */ }
}

async function handleStartProcess(row: InitiationVO) {
  try {
    const { data } = await startInitiationProcess(row.id, 1)
    ElMessage.success(t('project.initiation.messages.processStarted', { data }))
    fetchList()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || t('project.initiation.messages.startFailed'))
  }
}

// 预算弹窗
const budgetDialogVisible = ref(false)
const budgetInitiationId = ref<number | null>(null)
const budgetList = ref<BudgetItemVO[]>([])
const budgetForm = reactive({ category: 'LABOR', itemName: '', amount: 0, remark: '' })

/**
 * 打开预算明细弹窗，加载当前立项的预算列表
 * @param row 选中的立项行数据
 */
async function openBudget(row: InitiationVO) {
  budgetInitiationId.value = row.id
  budgetForm.category = 'LABOR'
  budgetForm.itemName = ''
  budgetForm.amount = 0
  budgetForm.remark = ''
  try {
    const { data } = await listBudget(row.id)
    budgetList.value = data || []
  } catch {
    budgetList.value = []
  }
  budgetDialogVisible.value = true
}

/** 提交预算明细：追加一条预算项并刷新预算列表 */
async function submitBudget() {
  if (!budgetInitiationId.value) return
  // H16.2 修复：try/catch 包裹；H17.1 修复：submittingBudget 防重复
  try {
    submittingBudget.value = true
    await addBudgetItem({
      initiationId: budgetInitiationId.value,
      category: budgetForm.category,
      itemName: budgetForm.itemName,
      amount: budgetForm.amount,
      remark: budgetForm.remark,
    })
    ElMessage.success(t('project.initiation.messages.budgetAdded'))
    const { data } = await listBudget(budgetInitiationId.value)
    budgetList.value = data || []
    budgetForm.itemName = ''
    budgetForm.amount = 0
    budgetForm.remark = ''
  } catch {
    // 失败：拦截器已弹错
  } finally {
    submittingBudget.value = false
  }
}

// 门径评审弹窗
const gateDialogVisible = ref(false)
const gateInitiationId = ref<number | null>(null)
const gateForm = reactive({ gateCode: 'CD2_DESIGN', reviewResult: 'PASS', comment: '' })

function openGate(row: InitiationVO) {
  gateInitiationId.value = row.id
  gateForm.gateCode = 'CD2_DESIGN'
  gateForm.reviewResult = 'PASS'
  gateForm.comment = ''
  gateDialogVisible.value = true
}

/** 提交门径评审结果：PASS / CONDITIONAL / FAIL */
async function submitGate() {
  if (!gateInitiationId.value) return
  // H16.2 修复：try/catch 包裹；H17.1 修复：submittingGate 防重复
  try {
    submittingGate.value = true
    await reviewGate({
      initiationId: gateInitiationId.value,
      gateCode: gateForm.gateCode,
      reviewResult: gateForm.reviewResult,
      comment: gateForm.comment,
    })
    ElMessage.success(t('project.initiation.messages.gateSubmitted'))
    gateDialogVisible.value = false
  } catch {
    // 失败：拦截器已弹错
  } finally {
    submittingGate.value = false
  }
}

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
      <el-form-item :label="t('project.initiation.search.keyword')">
        <el-input v-model="query.keyword" :placeholder="t('project.initiation.search.keywordPlaceholder')" clearable aria-label="搜索关键字" @clear="query.page = 1; fetchList()" @keyup.enter="query.page = 1; fetchList()" />
      </el-form-item>
      <el-form-item :label="t('project.initiation.search.stage')">
        <el-select v-model="query.stage" :placeholder="t('common.all')" clearable style="width: 140px">
          <el-option v-for="(v, k) in stageMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('project.initiation.search.projectLevel')">
        <el-select v-model="query.projectLevel" :placeholder="t('common.all')" clearable style="width: 120px">
          <el-option v-for="(v, k) in levelMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.PROJECT_INITIATION_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        {{ t('project.initiation.buttons.create') }}
      </el-button>
    </template>

    <template #table="scope">
      <vxe-table :data="list" :loading="loading" border stripe :height="scope.tableProps.height" :scroll-y="scope.tableProps.scrollY">
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="projectCode" :title="t('project.initiation.columns.projectCode')" width="160" />
        <vxe-column field="projectName" :title="t('project.initiation.columns.projectName')" min-width="200" show-overflow />
        <vxe-column field="customerName" :title="t('project.initiation.columns.customerName')" width="160" show-overflow />
        <vxe-column field="pmName" :title="t('project.initiation.columns.pmName')" width="100" />
        <vxe-column field="projectLevel" :title="t('project.initiation.columns.projectLevel')" width="80" align="center">
          <template #default="{ row }">
            <StatusTag :value="row.projectLevel" :map="levelMap" />
          </template>
        </vxe-column>
        <vxe-column field="budgetAmount" :title="t('project.initiation.columns.budgetAmount')" width="120" align="right" :formatter="({ cellValue }) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="currentGate" :title="t('project.initiation.columns.currentGate')" width="120">
          <template #default="{ row }">
            <StatusTag v-if="row.currentGate" :value="row.currentGate" :map="gateMap" />
            <span v-else>-</span>
          </template>
        </vxe-column>
        <vxe-column field="stage" :title="t('project.initiation.columns.stage')" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.stage" :map="stageMap" />
          </template>
        </vxe-column>
        <vxe-column field="plannedStartDate" :title="t('project.initiation.columns.plannedStartDate')" width="110" />
        <vxe-column field="plannedEndDate" :title="t('project.initiation.columns.plannedEndDate')" width="110" />
        <vxe-column :title="t('project.initiation.columns.action')" width="320" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="[PC.PROJECT_INITIATION_BUDGET]" link type="primary" size="small" @click="openBudget(row)">
              {{ t('project.initiation.buttons.budget') }}
            </el-button>
            <el-button v-permission="[PC.PROJECT_INITIATION_GATE]" link type="primary" size="small" @click="openGate(row)">
              {{ t('project.initiation.buttons.gateReview') }}
            </el-button>
            <el-button v-if="row.stage === 'DRAFT'" v-permission="[PC.PROJECT_INITIATION_START_PROCESS]" link type="success" size="small" @click="handleStartProcess(row)">
              {{ t('project.initiation.buttons.startApproval') }}
            </el-button>
            <el-button v-if="row.stage === 'DRAFT'" v-permission="[PC.PROJECT_INITIATION_GATE]" link type="warning" size="small" @click="handleStage(row, 'UNDER_REVIEW')">
              {{ t('project.initiation.buttons.submitReview') }}
            </el-button>
            <el-button v-if="row.stage === 'UNDER_REVIEW'" v-permission="[PC.PROJECT_INITIATION_GATE]" link type="success" size="small" @click="handleStage(row, 'APPROVED')">
              {{ t('project.initiation.buttons.approve') }}
            </el-button>
            <el-button v-if="row.stage === 'UNDER_REVIEW'" v-permission="[PC.PROJECT_INITIATION_GATE]" link type="danger" size="small" @click="handleStage(row, 'REJECTED')">
              {{ t('project.initiation.buttons.reject') }}
            </el-button>
            <el-button v-if="row.stage === 'APPROVED'" v-permission="[PC.PROJECT_INITIATION_GATE]" link type="primary" size="small" @click="handleStage(row, 'EXECUTING')">
              {{ t('project.initiation.buttons.startExecution') }}
            </el-button>
            <el-button v-permission="[PC.PROJECT_INITIATION_DELETE]" link type="danger" size="small" @click="handleDelete(row)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <!-- 立项表单 -->
    <el-dialog v-model="dialogVisible" :title="t('project.initiation.dialog.createTitle')" width="720px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('project.initiation.form.projectCode')" prop="projectCode">
              <el-input v-model="form.projectCode" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('project.initiation.form.projectName')" prop="projectName">
              <el-input v-model="form.projectName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('project.initiation.form.customerId')" prop="customerId">
              <el-input-number v-model="form.customerId" :min="1" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('project.initiation.form.customerName')">
              <el-input v-model="form.customerName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('project.initiation.form.projectType')" prop="projectType">
              <el-select v-model="form.projectType" style="width: 100%">
                <el-option :label="t('project.initiation.projectType.INTERNAL')" value="INTERNAL" />
                <el-option :label="t('project.initiation.projectType.CUSTOM')" value="CUSTOM" />
                <el-option :label="t('project.initiation.projectType.PRODUCT')" value="PRODUCT" />
                <el-option :label="t('project.initiation.projectType.SERVICE')" value="SERVICE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('project.initiation.form.projectLevel')">
              <el-select v-model="form.projectLevel" style="width: 100%">
                <el-option v-for="(v, k) in levelMap" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('project.initiation.form.pmId')">
              <el-input-number v-model="form.pmId" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('project.initiation.form.sponsorId')">
              <el-input-number v-model="form.sponsorId" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('project.initiation.form.estimatedAmount')">
              <el-input-number v-model="form.estimatedAmount" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('project.initiation.form.budgetAmount')">
              <el-input-number v-model="form.budgetAmount" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('project.initiation.form.plannedStartDate')">
              <el-date-picker v-model="form.plannedStartDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('project.initiation.form.plannedEndDate')">
              <el-date-picker v-model="form.plannedEndDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('project.initiation.form.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('project.initiation.form.businessCase')">
          <el-input v-model="form.businessCase" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('project.initiation.form.riskAssessment')">
          <el-input v-model="form.riskAssessment" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span v-if="draftTimeText" style="color: #909399; font-size: 12px; margin-right: auto;">草稿已保存 {{ draftTimeText }}</span>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 预算弹窗 -->
    <el-dialog v-model="budgetDialogVisible" :title="t('project.initiation.dialog.budgetTitle')" width="720px">
      <el-form :model="budgetForm" label-width="80px" inline>
        <el-form-item :label="t('project.initiation.budget.category')">
          <el-select v-model="budgetForm.category" style="width: 140px">
            <el-option :label="t('project.initiation.budgetCategory.LABOR')" value="LABOR" />
            <el-option :label="t('project.initiation.budgetCategory.PURCHASE')" value="PURCHASE" />
            <el-option :label="t('project.initiation.budgetCategory.EXPENSE')" value="EXPENSE" />
            <el-option :label="t('project.initiation.budgetCategory.OUTSOURCE')" value="OUTSOURCE" />
            <el-option :label="t('project.initiation.budgetCategory.OTHER')" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('project.initiation.budget.itemName')">
          <el-input v-model="budgetForm.itemName" :placeholder="t('project.initiation.budget.itemNamePlaceholder')" style="width: 200px" />
        </el-form-item>
        <el-form-item :label="t('project.initiation.budget.amount')">
          <el-input-number v-model="budgetForm.amount" :min="0" :controls="false" style="width: 160px" />
        </el-form-item>
        <el-form-item :label="t('project.initiation.budget.remark')">
          <el-input v-model="budgetForm.remark" style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submittingBudget" @click="submitBudget">{{ t('project.initiation.budget.add') }}</el-button>
        </el-form-item>
      </el-form>
      <vxe-table :data="budgetList" border stripe max-height="300">
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="category" :title="t('project.initiation.budget.category')" width="100" />
        <vxe-column field="itemName" :title="t('project.initiation.budget.itemName')" min-width="160" show-overflow />
        <vxe-column field="amount" :title="t('project.initiation.budget.amount')" width="120" align="right" :formatter="({ cellValue }) => `¥${Number(cellValue).toLocaleString()}`" />
        <vxe-column field="remark" :title="t('project.initiation.budget.remark')" min-width="120" show-overflow />
      </vxe-table>
      <template #footer>
        <el-button @click="budgetDialogVisible = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>

    <!-- 门径评审弹窗 -->
    <el-dialog v-model="gateDialogVisible" :title="t('project.initiation.dialog.gateTitle')" width="520px">
      <el-form :model="gateForm" label-width="100px">
        <el-form-item :label="t('project.initiation.gateForm.gate')">
          <el-select v-model="gateForm.gateCode" style="width: 100%">
            <el-option v-for="(v, k) in gateMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('project.initiation.gateForm.reviewResult')">
          <el-radio-group v-model="gateForm.reviewResult">
            <el-radio value="PASS">{{ t('project.initiation.reviewResult.PASS') }}</el-radio>
            <el-radio value="CONDITIONAL">{{ t('project.initiation.reviewResult.CONDITIONAL') }}</el-radio>
            <el-radio value="FAIL">{{ t('project.initiation.reviewResult.FAIL') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('project.initiation.gateForm.comment')">
          <el-input v-model="gateForm.comment" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="gateDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submittingGate" @click="submitGate">{{ t('common.submit') }}</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
