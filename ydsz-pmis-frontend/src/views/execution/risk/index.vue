<!--
  @file 风险管理
  @description 项目风险管理页面：支持风险分页查询、新建(概率×影响自动评级)、状态流转(OPEN→MITIGATING→CLOSED/ACCEPTED)，对应路由 /execution/risk
  @module views/execution/risk
-->
<script setup lang="ts">
/**
 * 风险管理
 *
 * 风险矩阵: 概率(1-3) × 影响(1-3) = 评分(1-9)
 * 等级: 1-2 LOW / 3-5 MEDIUM / 6-9 HIGH
 * 状态: OPEN -> MITIGATING -> CLOSED / ACCEPTED
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageRisks,
  createRisk,
  changeRiskStatus,
} from '@/api/execution/risk'
import type { RiskVO, RiskCreateDTO } from '@/api/execution/risk/types'
import { PC } from '@/constants/permissionCodes'

// 列表查询状态
const loading = ref(false)
const list = ref<RiskVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  level: '',
  initiationId: undefined as number | undefined,
})

// 状态字典：风险处理状态映射到标签文案与色值
const statusMap = {
  OPEN: { label: '待处理', type: 'danger' as const },
  MITIGATING: { label: '缓解中', type: 'warning' as const },
  CLOSED: { label: '已关闭', type: 'info' as const },
  ACCEPTED: { label: '已接受', type: 'success' as const },
}

// 等级字典：风险等级(LOW/MEDIUM/HIGH)映射到标签文案与色值
const levelMap = {
  LOW: { label: '低', type: 'success' as const },
  MEDIUM: { label: '中', type: 'warning' as const },
  HIGH: { label: '高', type: 'danger' as const },
}

// 分类字典：风险分类(技术/商务/资源/外部/其他)
const categoryMap = {
  TECHNICAL: { label: '技术' },
  COMMERCE: { label: '商务' },
  RESOURCE: { label: '资源' },
  EXTERNAL: { label: '外部' },
  OTHER: { label: '其他' },
}

/** 拉取风险分页数据 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageRisks(query.page, query.size, {
      keyword: query.keyword,
      status: query.status,
      level: query.level,
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
  query.level = ''
  query.initiationId = undefined
  query.page = 1
  fetchList()
}

// 弹窗 - 新建风险
const dialogVisible = ref(false)
const formRef = ref<any>()
const form = reactive<Partial<RiskCreateDTO>>({
  initiationId: 0,
  riskName: '',
  category: 'TECHNICAL',
  probability: 1,
  impact: 1,
  ownerId: 0,
  mitigation: '',
})

const formRules = {
  initiationId: [{ required: true, message: '项目 ID 必填', trigger: 'blur' }],
  riskName: [{ required: true, message: '风险名称必填', trigger: 'blur' }],
  probability: [{ required: true, message: '概率必填', trigger: 'blur' }],
  impact: [{ required: true, message: '影响必填', trigger: 'blur' }],
}

/** 打开新建弹窗：重置表单为默认值 */
function openCreate() {
  Object.assign(form, {
    initiationId: 0,
    riskName: '',
    category: 'TECHNICAL',
    probability: 1,
    impact: 1,
    ownerId: 0,
    description: '',
    mitigation: '',
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  await createRisk(form as RiskCreateDTO)
  ElMessage.success('已创建')
  dialogVisible.value = false
  fetchList()
}

async function handleStatus(row: RiskVO, target: string) {
  const targetText = (statusMap as any)[target]?.label || target
  try {
    await ElMessageBox.confirm(`确认将状态变更为「${targetText}」吗？`, '提示', { type: 'warning' })
    await changeRiskStatus({ id: row.id, targetStatus: target })
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
    <template #search>
      <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="名称" clearable /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="等级">
        <el-select v-model="query.level" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="(v, k) in levelMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目 ID"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.EXECUTION_RISK_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        新增风险
      </el-button>
    </template>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="riskCode" title="编号" width="120" />
        <vxe-column field="riskName" title="风险名称" min-width="200" show-overflow />
        <vxe-column field="initiationName" title="项目" width="160" show-overflow />
        <vxe-column field="category" title="分类" width="100">
          <template #default="{ row }">{{ categoryMap[row.category as keyof typeof categoryMap]?.label || row.category || '-' }}</template>
        </vxe-column>
        <vxe-column field="probability" title="概率" width="80" align="center" />
        <vxe-column field="impact" title="影响" width="80" align="center" />
        <vxe-column field="riskScore" title="评分" width="80" align="center" />
        <vxe-column field="level" title="等级" width="80" align="center">
          <template #default="{ row }"><StatusTag :value="row.level" :map="levelMap" /></template>
        </vxe-column>
        <vxe-column field="ownerName" title="负责人" width="100" />
        <vxe-column field="mitigation" title="应对措施" min-width="200" show-overflow />
        <vxe-column field="status" title="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column title="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'OPEN'" v-permission="[PC.EXECUTION_RISK_STATUS]" link type="warning" size="small" @click="handleStatus(row, 'MITIGATING')">启动缓解</el-button>
            <el-button v-if="row.status === 'MITIGATING'" v-permission="[PC.EXECUTION_RISK_STATUS]" link type="info" size="small" @click="handleStatus(row, 'CLOSED')">关闭</el-button>
            <el-button v-if="row.status === 'OPEN'" v-permission="[PC.EXECUTION_RISK_STATUS]" link type="success" size="small" @click="handleStatus(row, 'ACCEPTED')">接受</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" title="新增风险" width="640px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="项目 ID" prop="initiationId"><el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="风险编号"><el-input v-model="form.riskCode" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="风险名称" prop="riskName"><el-input v-model="form.riskName" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="分类"><el-select v-model="form.category" style="width: 100%"><el-option v-for="(v, k) in categoryMap" :key="k" :label="v.label" :value="k" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="概率(1-3)" prop="probability"><el-input-number v-model="form.probability" :min="1" :max="3" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="影响(1-3)" prop="impact"><el-input-number v-model="form.impact" :min="1" :max="3" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="负责人 ID">
          <el-input-number v-model="form.ownerId" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="应对措施">
          <el-input v-model="form.mitigation" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
