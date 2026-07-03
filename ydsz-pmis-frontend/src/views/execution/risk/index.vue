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
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()

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
const statusMap = computed(() => ({
  OPEN: { label: t('execution.risk.status.OPEN'), type: 'danger' as const },
  MITIGATING: { label: t('execution.risk.status.MITIGATING'), type: 'warning' as const },
  CLOSED: { label: t('execution.risk.status.CLOSED'), type: 'info' as const },
  ACCEPTED: { label: t('execution.risk.status.ACCEPTED'), type: 'success' as const },
}))

// 等级字典：风险等级(LOW/MEDIUM/HIGH)映射到标签文案与色值
const levelMap = computed(() => ({
  LOW: { label: t('execution.risk.level.LOW'), type: 'success' as const },
  MEDIUM: { label: t('execution.risk.level.MEDIUM'), type: 'warning' as const },
  HIGH: { label: t('execution.risk.level.HIGH'), type: 'danger' as const },
}))

// 分类字典：风险分类(技术/商务/资源/外部/其他)
const categoryMap = computed(() => ({
  TECHNICAL: { label: t('execution.risk.category.TECHNICAL') },
  COMMERCE: { label: t('execution.risk.category.COMMERCE') },
  RESOURCE: { label: t('execution.risk.category.RESOURCE') },
  EXTERNAL: { label: t('execution.risk.category.EXTERNAL') },
  OTHER: { label: t('execution.risk.category.OTHER') },
}))

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

const formRules = computed(() => ({
  initiationId: [{ required: true, message: t('execution.risk.rules.initiationIdRequired'), trigger: 'blur' }],
  riskName: [{ required: true, message: t('execution.risk.rules.riskNameRequired'), trigger: 'blur' }],
  probability: [{ required: true, message: t('execution.risk.rules.probabilityRequired'), trigger: 'blur' }],
  impact: [{ required: true, message: t('execution.risk.rules.impactRequired'), trigger: 'blur' }],
}))

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

/** 提交新建表单：校验通过后调用创建接口，RiskScoreEvaluator 自动评级 */
async function submitForm() {
  await formRef.value?.validate()
  await createRisk(form as RiskCreateDTO)
  ElMessage.success(t('execution.risk.messages.createSuccess'))
  dialogVisible.value = false
  fetchList()
}

/** 状态流转：根据目标状态推进风险处理流程（启动缓解/关闭/接受） */
async function handleStatus(row: RiskVO, target: string) {
  const targetText = (statusMap.value as any)[target]?.label || target
  try {
    await ElMessageBox.confirm(t('execution.risk.messages.confirmStatusChange', { target: targetText }), t('common.tip'), { type: 'warning' })
    await changeRiskStatus({ id: row.id, targetStatus: target })
    ElMessage.success(t('execution.risk.messages.statusUpdated'))
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
      <el-form-item :label="t('execution.risk.search.keyword')"><el-input v-model="query.keyword" :placeholder="t('execution.risk.search.keywordPlaceholder')" clearable /></el-form-item>
      <el-form-item :label="t('execution.risk.search.status')">
        <el-select v-model="query.status" :placeholder="t('common.all')" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('execution.risk.search.level')">
        <el-select v-model="query.level" :placeholder="t('common.all')" clearable style="width: 120px">
          <el-option v-for="(v, k) in levelMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('execution.risk.search.initiationId')"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.EXECUTION_RISK_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        {{ t('execution.risk.buttons.create') }}
      </el-button>
    </template>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="riskCode" :title="t('execution.risk.columns.riskCode')" width="120" />
        <vxe-column field="riskName" :title="t('execution.risk.columns.riskName')" min-width="200" show-overflow />
        <vxe-column field="initiationName" :title="t('execution.risk.columns.initiationName')" width="160" show-overflow />
        <vxe-column field="category" :title="t('execution.risk.columns.category')" width="100">
          <template #default="{ row }">{{ categoryMap[row.category as keyof typeof categoryMap]?.label || row.category || '-' }}</template>
        </vxe-column>
        <vxe-column field="probability" :title="t('execution.risk.columns.probability')" width="80" align="center" />
        <vxe-column field="impact" :title="t('execution.risk.columns.impact')" width="80" align="center" />
        <vxe-column field="riskScore" :title="t('execution.risk.columns.riskScore')" width="80" align="center" />
        <vxe-column field="level" :title="t('execution.risk.columns.level')" width="80" align="center">
          <template #default="{ row }"><StatusTag :value="row.level" :map="levelMap" /></template>
        </vxe-column>
        <vxe-column field="ownerName" :title="t('execution.risk.columns.ownerName')" width="100" />
        <vxe-column field="mitigation" :title="t('execution.risk.columns.mitigation')" min-width="200" show-overflow />
        <vxe-column field="status" :title="t('execution.risk.columns.status')" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column :title="t('execution.risk.columns.action')" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'OPEN'" v-permission="[PC.EXECUTION_RISK_STATUS]" link type="warning" size="small" @click="handleStatus(row, 'MITIGATING')">{{ t('execution.risk.buttons.startMitigating') }}</el-button>
            <el-button v-if="row.status === 'MITIGATING'" v-permission="[PC.EXECUTION_RISK_STATUS]" link type="info" size="small" @click="handleStatus(row, 'CLOSED')">{{ t('execution.risk.buttons.close') }}</el-button>
            <el-button v-if="row.status === 'OPEN'" v-permission="[PC.EXECUTION_RISK_STATUS]" link type="success" size="small" @click="handleStatus(row, 'ACCEPTED')">{{ t('execution.risk.buttons.accept') }}</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" :title="t('execution.risk.dialog.createTitle')" width="640px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item :label="t('execution.risk.form.initiationId')" prop="initiationId"><el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('execution.risk.form.riskCode')"><el-input v-model="form.riskCode" /></el-form-item></el-col>
        </el-row>
        <el-form-item :label="t('execution.risk.form.riskName')" prop="riskName"><el-input v-model="form.riskName" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item :label="t('execution.risk.form.category')"><el-select v-model="form.category" style="width: 100%"><el-option v-for="(v, k) in categoryMap" :key="k" :label="v.label" :value="k" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="t('execution.risk.form.probability')" prop="probability"><el-input-number v-model="form.probability" :min="1" :max="3" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="t('execution.risk.form.impact')" prop="impact"><el-input-number v-model="form.impact" :min="1" :max="3" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item :label="t('execution.risk.form.ownerId')">
          <el-input-number v-model="form.ownerId" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('execution.risk.form.mitigation')">
          <el-input v-model="form.mitigation" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item :label="t('execution.risk.form.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitForm">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
