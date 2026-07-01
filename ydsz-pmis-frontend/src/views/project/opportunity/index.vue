<script setup lang="ts">
/**
 * 商机管理页面
 *
 * 提供商机的查询、新增、编辑、状态流转、赢率评估、转立项等操作。
 * 状态机: FOLLOWING -> QUOTED -> NEGOTIATING -> WON -> CONVERTED / LOST
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageOpportunities,
  createOpportunity,
  updateOpportunity,
  changeOpportunityStatus,
  deleteOpportunity,
  evaluateWinRate,
  convertToInitiation,
} from '@/api/project/opportunity'
import type { OpportunityVO, OpportunityCreateDTO, OpportunityUpdateDTO } from '@/api/project/opportunity/types'
import { PC } from '@/constants/permissionCodes'

const loading = ref(false)
const list = ref<OpportunityVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  level: '',
  ownerId: undefined as number | undefined,
})

const statusMap = {
  FOLLOWING: { label: '跟进中', type: 'info' as const },
  QUOTED: { label: '已报价', type: 'warning' as const },
  NEGOTIATING: { label: '商务谈判', type: 'primary' as const },
  WON: { label: '已赢单', type: 'success' as const },
  CONVERTED: { label: '已转立项', type: 'success' as const },
  LOST: { label: '已输单', type: 'danger' as const },
  INVALID: { label: '无效', type: 'info' as const },
}

const levelMap = {
  A: { label: 'A 级', type: 'danger' as const },
  B: { label: 'B 级', type: 'warning' as const },
  C: { label: 'C 级', type: 'info' as const },
  D: { label: 'D 级', type: 'info' as const },
}

async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageOpportunities(query.page, query.size, {
      keyword: query.keyword,
      status: query.status,
      level: query.level,
      ownerId: query.ownerId,
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
  query.level = ''
  query.ownerId = undefined
  query.page = 1
  fetchList()
}

// 表单弹窗
const dialogVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formRef = ref<any>()
const form = reactive<Partial<OpportunityVO> & { id?: number }>({
  opportunityCode: '',
  opportunityName: '',
  customerId: 0,
  customerName: '',
  ownerId: 0,
  ownerName: '',
  level: 'C',
  estimatedAmount: undefined,
})

const formRules = {
  opportunityCode: [{ required: true, message: '商机编码必填', trigger: 'blur' }],
  opportunityName: [{ required: true, message: '商机名称必填', trigger: 'blur' }],
  customerId: [{ required: true, message: '客户 ID 必填', trigger: 'blur' }],
  ownerId: [{ required: true, message: '负责人 ID 必填', trigger: 'blur' }],
}

/** 打开新增商机弹窗，重置表单为初始值 */
function openCreate() {
  formMode.value = 'create'
  Object.assign(form, {
    id: undefined,
    opportunityCode: '',
    opportunityName: '',
    customerId: 0,
    customerName: '',
    ownerId: 0,
    ownerName: '',
    level: 'C',
    estimatedAmount: undefined,
    status: 'FOLLOWING',
    source: '',
    industry: '',
  })
  dialogVisible.value = true
}

async function openEdit(row: OpportunityVO) {
  formMode.value = 'edit'
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  if (formMode.value === 'create') {
    const dto: OpportunityCreateDTO = {
      opportunityCode: form.opportunityCode!,
      opportunityName: form.opportunityName!,
      customerId: form.customerId!,
      customerName: form.customerName,
      ownerId: form.ownerId!,
      ownerName: form.ownerName,
      level: form.level,
      estimatedAmount: form.estimatedAmount,
      source: form.source,
      industry: form.industry,
    }
    await createOpportunity(dto)
    ElMessage.success('创建成功')
  } else if (form.id) {
    const dto: OpportunityUpdateDTO = {
      id: form.id,
      opportunityName: form.opportunityName,
      level: form.level,
      industry: form.industry,
      estimatedAmount: form.estimatedAmount,
      winRate: form.winRate,
      expectedSignDate: form.expectedSignDate,
      expectedStartDate: form.expectedStartDate,
      expectedEndDate: form.expectedEndDate,
      competitor: form.competitor,
      remark: form.remark,
      tags: form.tags,
    }
    await updateOpportunity(dto)
    ElMessage.success('更新成功')
  }
  dialogVisible.value = false
  fetchList()
}

async function handleDelete(row: OpportunityVO) {
  try {
    await ElMessageBox.confirm(`确认删除商机「${row.opportunityName}」吗？`, '提示', { type: 'warning' })
    await deleteOpportunity(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 取消 */ }
}

/**
 * 变更商机状态（二次确认），状态机见文件头
 * @param row 选中的商机行数据
 * @param target 目标状态编码
 */
async function handleChangeStatus(row: OpportunityVO, target: string) {
  const targetText = (statusMap as any)[target]?.label || target
  try {
    await ElMessageBox.confirm(`确认将状态变更为「${targetText}」吗？`, '提示', { type: 'warning' })
    await changeOpportunityStatus({ id: row.id, targetStatus: target })
    ElMessage.success('状态已更新')
    fetchList()
  } catch { /* 取消 */ }
}

async function handleEvaluate(row: OpportunityVO) {
  try {
    const { data } = await evaluateWinRate(row.id, (row as any).customerCredit, false)
    ElMessage.success(`赢率评估结果: ${(data * 100).toFixed(1)}%`)
    fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '评估失败')
  }
}

async function handleConvert(row: OpportunityVO) {
  try {
    await ElMessageBox.confirm(
      `确认将商机「${row.opportunityName}」转立项吗？将自动创建预立项草稿。`,
      '商机转立项',
      { type: 'info' },
    )
    const { data } = await convertToInitiation(row.id)
    ElMessage.success(`立项草稿已创建 (ID: ${data})`)
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
      <el-form-item label="分级">
        <el-select v-model="query.level" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="(v, k) in levelMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.PROJECT_OPPORTUNITY_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        新增商机
      </el-button>
    </template>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="opportunityCode" title="编码" width="160" />
        <vxe-column field="opportunityName" title="商机名称" min-width="200" show-overflow />
        <vxe-column field="customerName" title="客户" width="160" show-overflow />
        <vxe-column field="ownerName" title="负责人" width="100" />
        <vxe-column field="level" title="分级" width="80" align="center">
          <template #default="{ row }">
            <StatusTag :value="row.level" :map="levelMap" fallback-type="info" />
          </template>
        </vxe-column>
        <vxe-column field="estimatedAmount" title="预计金额" width="120" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
        <vxe-column field="winRate" title="赢率" width="80" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `${(Number(cellValue) * 100).toFixed(0)}%` : '-'" />
        <vxe-column field="expectedSignDate" title="预计签约" width="110" />
        <vxe-column field="status" title="状态" width="110">
          <template #default="{ row }">
            <StatusTag :value="row.status" :map="statusMap" />
          </template>
        </vxe-column>
        <vxe-column title="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="[PC.PROJECT_OPPORTUNITY_UPDATE]" link type="primary" size="small" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-if="row.status === 'WON'" v-permission="[PC.PROJECT_OPPORTUNITY_CONVERT]" link type="success" size="small" @click="handleConvert(row)">
              转立项
            </el-button>
            <el-button v-permission="[PC.PROJECT_OPPORTUNITY_EVALUATE]" link type="primary" size="small" @click="handleEvaluate(row)">
              评估赢率
            </el-button>
            <el-button v-if="row.status === 'FOLLOWING'" v-permission="[PC.PROJECT_OPPORTUNITY_UPDATE]" link type="warning" size="small" @click="handleChangeStatus(row, 'QUOTED')">
              转报价
            </el-button>
            <el-button v-if="row.status === 'QUOTED'" v-permission="[PC.PROJECT_OPPORTUNITY_UPDATE]" link type="warning" size="small" @click="handleChangeStatus(row, 'NEGOTIATING')">
              转谈判
            </el-button>
            <el-button v-if="row.status === 'NEGOTIATING'" v-permission="[PC.PROJECT_OPPORTUNITY_UPDATE]" link type="success" size="small" @click="handleChangeStatus(row, 'WON')">
              赢单
            </el-button>
            <el-button v-if="['FOLLOWING', 'QUOTED', 'NEGOTIATING'].includes(row.status || '')" v-permission="[PC.PROJECT_OPPORTUNITY_UPDATE]" link type="danger" size="small" @click="handleChangeStatus(row, 'LOST')">
              输单
            </el-button>
            <el-button v-permission="[PC.PROJECT_OPPORTUNITY_DELETE]" link type="danger" size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <template #footer>
      <el-dialog v-model="dialogVisible" :title="formMode === 'create' ? '新增商机' : '编辑商机'" width="720px">
        <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="商机编码" prop="opportunityCode">
                <el-input v-model="form.opportunityCode" :disabled="formMode === 'edit'" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="商机名称" prop="opportunityName">
                <el-input v-model="form.opportunityName" />
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
              <el-form-item label="负责人 ID" prop="ownerId">
                <el-input-number v-model="form.ownerId" :min="1" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="负责人名称">
                <el-input v-model="form.ownerName" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="分级">
                <el-select v-model="form.level" style="width: 100%">
                  <el-option v-for="(v, k) in levelMap" :key="k" :label="v.label" :value="k" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="预计金额">
                <el-input-number v-model="form.estimatedAmount" :min="0" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="商机来源">
                <el-input v-model="form.source" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="行业">
                <el-input v-model="form.industry" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </template>
      </el-dialog>
    </template>
  </PageLayout>
</template>
