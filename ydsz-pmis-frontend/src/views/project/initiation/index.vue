<!--
  @file 项目立项管理
  @description 立项的查询、新增、阶段流转、预算管理与门径评审；阶段机 DRAFT/UNDER_REVIEW/APPROVED/REJECTED/EXECUTING/CLOSED，门径 CD1_KICKOFF → CD2_DESIGN → CD3_BUILD → CD4_UAT → CD5_GO_LIVE；对接自研工作流审批流与 @/api/project/initiation
  @module views/project/initiation
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
} from '@/api/project/initiation'
import type { InitiationVO, InitiationCreateDTO } from '@/api/project/initiation/types'
import { PC } from '@/constants/permissionCodes'

const loading = ref(false)
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

const stageMap = {
  DRAFT: { label: '草稿', type: 'info' as const },
  UNDER_REVIEW: { label: '审批中', type: 'warning' as const },
  APPROVED: { label: '已审批', type: 'success' as const },
  REJECTED: { label: '已驳回', type: 'danger' as const },
  EXECUTING: { label: '执行中', type: 'primary' as const },
  CLOSED: { label: '已结项', type: 'info' as const },
}

const levelMap = {
  A: { label: 'A 级', type: 'danger' as const },
  B: { label: 'B 级', type: 'warning' as const },
  C: { label: 'C 级', type: 'info' as const },
  D: { label: 'D 级', type: 'info' as const },
}

const gateMap = {
  CD1_KICKOFF: { label: 'CD1 启动', type: 'info' as const },
  CD2_DESIGN: { label: 'CD2 设计', type: 'primary' as const },
  CD3_BUILD: { label: 'CD3 构建', type: 'primary' as const },
  CD4_UAT: { label: 'CD4 UAT', type: 'warning' as const },
  CD5_GO_LIVE: { label: 'CD5 上线', type: 'success' as const },
}

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
const formRef = ref<any>()
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
  projectCode: [{ required: true, message: '项目编码必填', trigger: 'blur' }],
  projectName: [{ required: true, message: '项目名称必填', trigger: 'blur' }],
  customerId: [{ required: true, message: '客户 ID 必填', trigger: 'blur' }],
  projectType: [{ required: true, message: '项目类型必填', trigger: 'change' }],
}

/** 打开新建立项弹窗，重置表单为初始值 */
function openCreate() {
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
  await formRef.value?.validate()
  await createInitiation(form as InitiationCreateDTO)
  ElMessage.success('创建成功')
  dialogVisible.value = false
  fetchList()
}

/**
 * 删除立项（二次确认）
 * @param row 选中的立项行数据
 */
async function handleDelete(row: InitiationVO) {
  try {
    await ElMessageBox.confirm(`确认删除立项「${row.projectName}」吗？`, '提示', { type: 'warning' })
    await deleteInitiation(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 取消 */ }
}

/**
 * 变更立项阶段（二次确认），阶段机见文件头
 * @param row 选中的立项行数据
 * @param target 目标阶段编码
 */
async function handleStage(row: InitiationVO, target: string) {
  const targetText = (stageMap as any)[target]?.label || target
  try {
    await ElMessageBox.confirm(`确认将阶段变更为「${targetText}」吗？`, '提示', { type: 'warning' })
    await changeInitiationStage({ id: row.id, targetStage: target })
    ElMessage.success('阶段已更新')
    fetchList()
  } catch { /* 取消 */ }
}

async function handleStartProcess(row: InitiationVO) {
  try {
    const { data } = await startInitiationProcess(row.id, 1)
    ElMessage.success(`审批流已启动: ${data}`)
    fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '启动失败')
  }
}

// 预算弹窗
const budgetDialogVisible = ref(false)
const budgetInitiationId = ref<number | null>(null)
const budgetList = ref<any[]>([])
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
  await addBudgetItem({
    initiationId: budgetInitiationId.value,
    category: budgetForm.category,
    itemName: budgetForm.itemName,
    amount: budgetForm.amount,
    remark: budgetForm.remark,
  })
  ElMessage.success('已添加')
  const { data } = await listBudget(budgetInitiationId.value)
  budgetList.value = data || []
  budgetForm.itemName = ''
  budgetForm.amount = 0
  budgetForm.remark = ''
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
  await reviewGate({
    initiationId: gateInitiationId.value,
    gateCode: gateForm.gateCode,
    reviewResult: gateForm.reviewResult,
    comment: gateForm.comment,
  })
  ElMessage.success('评审已提交')
  gateDialogVisible.value = false
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
      <el-form-item label="阶段">
        <el-select v-model="query.stage" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="(v, k) in stageMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目分级">
        <el-select v-model="query.projectLevel" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="(v, k) in levelMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.PROJECT_INITIATION_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        新建立项
      </el-button>
    </template>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="projectCode" title="项目编码" width="160" />
        <vxe-column field="projectName" title="项目名称" min-width="200" show-overflow />
        <vxe-column field="customerName" title="客户" width="160" show-overflow />
        <vxe-column field="pmName" title="项目经理" width="100" />
        <vxe-column field="projectLevel" title="分级" width="80" align="center">
          <template #default="{ row }">
            <StatusTag :value="row.projectLevel" :map="levelMap" />
          </template>
        </vxe-column>
        <vxe-column field="budgetAmount" title="预算" width="120" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="currentGate" title="当前门径" width="120">
          <template #default="{ row }">
            <StatusTag v-if="row.currentGate" :value="row.currentGate" :map="gateMap" />
            <span v-else>-</span>
          </template>
        </vxe-column>
        <vxe-column field="stage" title="阶段" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.stage" :map="stageMap" />
          </template>
        </vxe-column>
        <vxe-column field="plannedStartDate" title="计划开始" width="110" />
        <vxe-column field="plannedEndDate" title="计划结束" width="110" />
        <vxe-column title="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="[PC.PROJECT_INITIATION_BUDGET]" link type="primary" size="small" @click="openBudget(row)">
              预算
            </el-button>
            <el-button v-permission="[PC.PROJECT_INITIATION_GATE]" link type="primary" size="small" @click="openGate(row)">
              门径评审
            </el-button>
            <el-button v-if="row.stage === 'DRAFT'" v-permission="[PC.PROJECT_INITIATION_START_PROCESS]" link type="success" size="small" @click="handleStartProcess(row)">
              启动审批
            </el-button>
            <el-button v-if="row.stage === 'DRAFT'" v-permission="[PC.PROJECT_INITIATION_GATE]" link type="warning" size="small" @click="handleStage(row, 'UNDER_REVIEW')">
              提交评审
            </el-button>
            <el-button v-if="row.stage === 'UNDER_REVIEW'" v-permission="[PC.PROJECT_INITIATION_GATE]" link type="success" size="small" @click="handleStage(row, 'APPROVED')">
              审批通过
            </el-button>
            <el-button v-if="row.stage === 'UNDER_REVIEW'" v-permission="[PC.PROJECT_INITIATION_GATE]" link type="danger" size="small" @click="handleStage(row, 'REJECTED')">
              驳回
            </el-button>
            <el-button v-if="row.stage === 'APPROVED'" v-permission="[PC.PROJECT_INITIATION_GATE]" link type="primary" size="small" @click="handleStage(row, 'EXECUTING')">
              启动执行
            </el-button>
            <el-button v-permission="[PC.PROJECT_INITIATION_DELETE]" link type="danger" size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <!-- 立项表单 -->
    <el-dialog v-model="dialogVisible" title="新建立项" width="720px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目编码" prop="projectCode">
              <el-input v-model="form.projectCode" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目名称" prop="projectName">
              <el-input v-model="form.projectName" />
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
            <el-form-item label="项目类型" prop="projectType">
              <el-select v-model="form.projectType" style="width: 100%">
                <el-option label="内部研发" value="INTERNAL" />
                <el-option label="客户定制" value="CUSTOM" />
                <el-option label="产品交付" value="PRODUCT" />
                <el-option label="运维服务" value="SERVICE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目分级">
              <el-select v-model="form.projectLevel" style="width: 100%">
                <el-option v-for="(v, k) in levelMap" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目经理 ID">
              <el-input-number v-model="form.pmId" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发起人 ID">
              <el-input-number v-model="form.sponsorId" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="预计金额">
              <el-input-number v-model="form.estimatedAmount" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预算金额">
              <el-input-number v-model="form.budgetAmount" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="计划开始">
              <el-date-picker v-model="form.plannedStartDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划结束">
              <el-date-picker v-model="form.plannedEndDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="项目描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="商业论证">
          <el-input v-model="form.businessCase" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="风险评估">
          <el-input v-model="form.riskAssessment" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 预算弹窗 -->
    <el-dialog v-model="budgetDialogVisible" title="预算明细" width="800px">
      <el-form :model="budgetForm" label-width="100px" inline>
        <el-form-item label="分类">
          <el-select v-model="budgetForm.category" style="width: 140px">
            <el-option label="人工" value="LABOR" />
            <el-option label="采购" value="PURCHASE" />
            <el-option label="费用" value="EXPENSE" />
            <el-option label="外协" value="OUTSOURCE" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="budgetForm.itemName" placeholder="如: 后端开发" />
        </el-form-item>
        <el-form-item label="金额">
          <el-input-number v-model="budgetForm.amount" :min="0" :controls="false" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="budgetForm.remark" style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Plus'" @click="submitBudget">添加</el-button>
        </el-form-item>
      </el-form>
      <vxe-table :data="budgetList" border>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="category" title="分类" width="100" />
        <vxe-column field="itemName" title="名称" min-width="180" />
        <vxe-column field="amount" title="金额" width="140" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="remark" title="备注" min-width="180" />
      </vxe-table>
    </el-dialog>

    <!-- 门径评审弹窗 -->
    <el-dialog v-model="gateDialogVisible" title="门径评审" width="520px">
      <el-form :model="gateForm" label-width="100px">
        <el-form-item label="门径">
          <el-select v-model="gateForm.gateCode" style="width: 100%">
            <el-option v-for="(v, k) in gateMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="评审结果">
          <el-radio-group v-model="gateForm.reviewResult">
            <el-radio value="PASS">通过</el-radio>
            <el-radio value="CONDITIONAL">有条件通过</el-radio>
            <el-radio value="FAIL">不通过</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="评审意见">
          <el-input v-model="gateForm.comment" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="gateDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitGate">提交</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
