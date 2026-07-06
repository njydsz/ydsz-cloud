<!--
  @file 服务满意度评价
  @description 售后服务满意度评价管理页面，支持多维度打分、不满意评价跟进闭环及综合统计概览。
  @module views/aftersales/satisfaction
-->
<script setup lang="ts">
/**
 * 服务满意度评价 (P7)
 *
 * 评价: 1-5 星，分项打分（响应/专业/态度/结果/速度）
 * 跟进: PENDING / FOLLOW_UP / CLOSED
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageSatisfactions,
  submitSatisfaction,
  markFollowUp,
  closeFollowUp,
  overallSatisfaction,
  levelDistributionSatisfaction,
} from '@/api/execution/aftersales/satisfaction'
import type { SatisfactionVO, SatisfactionCreateDTO } from '@/api/execution/aftersales/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

/** 列表加载状态 */
const loading = ref(false)
/** 评价列表数据 */
const list = ref<SatisfactionVO[]>([])
/** 列表总条数（用于分页） */
const total = ref(0)
/** 综合评价统计概览 */
const overall = ref<Record<string, unknown>>({})
/** 等级分布统计 */
const levelDist = ref<Array<Record<string, unknown>>>([])
/** 列表查询条件 */
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  level: '',
  followUpStatus: '',
  initiationId: undefined as number | undefined,
})

const levelMap = computed(() => ({
  VERY_DISSATISFIED: { label: t('aftersales.satisfaction.level.VERY_DISSATISFIED'), type: 'danger' as const },
  DISSATISFIED: { label: t('aftersales.satisfaction.level.DISSATISFIED'), type: 'danger' as const },
  NEUTRAL: { label: t('aftersales.satisfaction.level.NEUTRAL'), type: 'info' as const },
  SATISFIED: { label: t('aftersales.satisfaction.level.SATISFIED'), type: 'success' as const },
  VERY_SATISFIED: { label: t('aftersales.satisfaction.level.VERY_SATISFIED'), type: 'success' as const },
}))

const followUpMap = computed(() => ({
  PENDING: { label: t('aftersales.satisfaction.followUp.PENDING'), type: 'warning' as const },
  FOLLOW_UP: { label: t('aftersales.satisfaction.followUp.FOLLOW_UP'), type: 'primary' as const },
  CLOSED: { label: t('aftersales.satisfaction.followUp.CLOSED'), type: 'success' as const },
}))

/** 根据总分推断满意度等级 */
function inferLevel(score?: number) {
  if (score === null || score === undefined) return ''
  if (score <= 1) return 'VERY_DISSATISFIED'
  if (score <= 2) return 'DISSATISFIED'
  if (score <= 3) return 'NEUTRAL'
  if (score <= 4) return 'SATISFIED'
  return 'VERY_SATISFIED'
}

/** 拉取满意度评价分页列表 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageSatisfactions({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      level: query.level || undefined,
      followUpStatus: query.followUpStatus || undefined,
      initiationId: query.initiationId,
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 拉取综合评价统计与等级分布 */
async function fetchStats() {
  try {
    overall.value = await overallSatisfaction().then((r) => r.data as Record<string, unknown>)
  } catch {
    overall.value = {}
  }
  try {
    levelDist.value = await levelDistributionSatisfaction().then((r) => r.data as Array<Record<string, unknown>>)
  } catch {
    levelDist.value = []
  }
}

/** 重置查询条件并重新加载列表 */
function handleReset() {
  query.keyword = ''
  query.level = ''
  query.followUpStatus = ''
  query.initiationId = undefined
  query.page = 1
  fetchList()
}

/** 评价弹窗显隐 */
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
/** 提交中状态（防重复提交） */
const submitting = ref(false)
/** 评价表单数据 */
const form = reactive<Partial<SatisfactionCreateDTO>>({
  overallScore: 5,
  ticketId: undefined,
  initiationId: undefined,
  evaluatorId: undefined,
})

const formRules = {
  overallScore: [{ required: true, message: t('aftersales.satisfaction.rules.overallScoreRequired'), trigger: 'change' }],
}

/** 打开评价弹窗，重置表单各维度评分默认值 */
function openCreate() {
  Object.assign(form, {
    ticketId: undefined,
    initiationId: undefined,
    evaluatorId: undefined,
    overallScore: 5,
    responseScore: 5,
    professionalScore: 5,
    attitudeScore: 5,
    resultScore: 5,
    speedScore: 5,
    comment: '',
  })
  dialogVisible.value = true
}

/** 提交评价表单，校验通过后调用提交接口并刷新统计 */
async function submitForm() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    await submitSatisfaction(form as SatisfactionCreateDTO)
    ElMessage.success(t('aftersales.satisfaction.messages.submitted'))
    dialogVisible.value = false
    fetchList()
    fetchStats()
  } finally {
    submitting.value = false
  }
}

/** 标记不满意评价为跟进中，需输入跟进说明 */
async function handleFollowUp(row: SatisfactionVO) {
  const level = inferLevel(row.overallScore)
  if (!['DISSATISFIED', 'VERY_DISSATISFIED'].includes(level)) {
    ElMessage.info(t('aftersales.satisfaction.messages.onlyDissatisfiedNeedFollowUp'))
    return
  }
  try {
    const { value } = await ElMessageBox.prompt(t('aftersales.satisfaction.messages.followUpPrompt'), t('aftersales.satisfaction.messages.followUpTitle'), {
      inputValidator: (v) => !!v || t('aftersales.satisfaction.messages.noteRequired'),
    })
    await markFollowUp(row.id, value)
    ElMessage.success(t('aftersales.satisfaction.messages.followUpMarked'))
    fetchList()
  } catch { /* 取消 */ }
}

/** 关闭跟进，需输入关闭说明 */
async function handleCloseFollowUp(row: SatisfactionVO) {
  try {
    const { value } = await ElMessageBox.prompt(t('aftersales.satisfaction.messages.closePrompt'), t('aftersales.satisfaction.messages.closeTitle'), {
      inputValidator: (v) => !!v || t('aftersales.satisfaction.messages.noteRequired'),
    })
    await closeFollowUp(row.id)
    ElMessage.success(t('aftersales.satisfaction.messages.closed', { value }))
    fetchList()
  } catch { /* 取消 */ }
}

onMounted(() => {
  fetchList()
  fetchStats()
})
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
    @refresh="() => { fetchList(); fetchStats(); }"
  >
    <!-- 搜索栏 -->
    <template #search>
      <el-form-item :label="t('aftersales.satisfaction.search.keyword')"><el-input v-model="query.keyword" :placeholder="t('aftersales.satisfaction.search.keywordPlaceholder')" clearable @keyup.enter="query.page = 1; fetchList()" /></el-form-item>
      <el-form-item :label="t('aftersales.satisfaction.search.level')">
        <el-select v-model="query.level" :placeholder="t('common.all')" clearable style="width: 130px">
          <el-option v-for="(v, k) in levelMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('aftersales.satisfaction.search.followUp')">
        <el-select v-model="query.followUpStatus" :placeholder="t('common.all')" clearable style="width: 120px">
          <el-option v-for="(v, k) in followUpMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('aftersales.satisfaction.search.initiationId')"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
    </template>

    <!-- 工具栏 -->
    <template #toolbar>
      <el-button v-permission="[PC.AFTERSALES_SATISFACTION_SUBMIT]" type="primary" :icon="'Plus'" @click="openCreate">
        {{ t('aftersales.satisfaction.buttons.submit') }}
      </el-button>
    </template>

    <!-- 统计卡 -->
    <el-row :gutter="12" class="mb-3">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="text-sm text-gray-500">{{ t('aftersales.satisfaction.stats.count') }}</div>
          <div class="text-2xl font-bold mt-1">{{ overall.count ?? total }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="text-sm text-gray-500">{{ t('aftersales.satisfaction.stats.avgScore') }}</div>
          <div class="text-2xl font-bold mt-1">
            {{ overall.avgScore != null ? Number(overall.avgScore).toFixed(2) : '—' }}
            <span class="text-sm text-gray-400">/5</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="text-sm text-gray-500">{{ t('aftersales.satisfaction.stats.unsatisfiedCount') }}</div>
          <div class="text-2xl font-bold text-red-500 mt-1">{{ overall.unsatisfiedCount ?? '—' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="text-sm text-gray-500">{{ t('aftersales.satisfaction.stats.levelDist') }}</div>
          <div v-for="(row, idx) in levelDist" :key="idx" class="text-xs mt-1">
            {{ (row as any).level || (row as any).name }}：<b>{{ (row as any).count }}</b>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 评价表格 -->
    <template #table="scope">
      <vxe-table :data="list" :loading="loading" border stripe :height="scope.tableProps.height" :scroll-y="scope.tableProps.scrollY">
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="satisfactionCode" :title="t('aftersales.satisfaction.columns.satisfactionCode')" width="180" />
        <vxe-column field="ticketCode" :title="t('aftersales.satisfaction.columns.ticketCode')" width="180" show-overflow />
        <vxe-column field="initiationName" :title="t('aftersales.satisfaction.columns.initiationName')" min-width="160" show-overflow />
        <vxe-column field="evaluatorName" :title="t('aftersales.satisfaction.columns.evaluatorName')" width="100" />
        <vxe-column field="overallScore" :title="t('aftersales.satisfaction.columns.overallScore')" width="80" align="center" />
        <vxe-column :label="t('aftersales.satisfaction.columns.level')" width="110">
          <template #default="{ row }">
            <StatusTag :value="inferLevel(row.overallScore)" :map="levelMap" />
          </template>
        </vxe-column>
        <vxe-column field="responseScore" :title="t('aftersales.satisfaction.columns.responseScore')" width="70" align="center" />
        <vxe-column field="professionalScore" :title="t('aftersales.satisfaction.columns.professionalScore')" width="70" align="center" />
        <vxe-column field="attitudeScore" :title="t('aftersales.satisfaction.columns.attitudeScore')" width="70" align="center" />
        <vxe-column field="resultScore" :title="t('aftersales.satisfaction.columns.resultScore')" width="70" align="center" />
        <vxe-column field="speedScore" :title="t('aftersales.satisfaction.columns.speedScore')" width="70" align="center" />
        <vxe-column field="comment" :title="t('aftersales.satisfaction.columns.comment')" min-width="200" show-overflow />
        <vxe-column field="followUpStatus" :title="t('aftersales.satisfaction.columns.followUpStatus')" width="110">
          <template #default="{ row }"><StatusTag :value="row.followUpStatus" :map="followUpMap" /></template>
        </vxe-column>
        <vxe-column field="followUpAt" :title="t('aftersales.satisfaction.columns.followUpAt')" width="170" />
        <vxe-column :title="t('aftersales.satisfaction.columns.action')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.followUpStatus === 'PENDING'"
              v-permission="[PC.AFTERSALES_SATISFACTION_FOLLOWUP]"
              link
              type="warning"
              size="small"
              @click="handleFollowUp(row)"
            >{{ t('aftersales.satisfaction.buttons.markFollowUp') }}</el-button>
            <el-button
              v-if="row.followUpStatus === 'FOLLOW_UP'"
              v-permission="[PC.AFTERSALES_SATISFACTION_FOLLOWUP]"
              link
              type="success"
              size="small"
              @click="handleCloseFollowUp(row)"
            >{{ t('aftersales.satisfaction.buttons.closeFollowUp') }}</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <!-- 提交评价弹窗 -->
    <el-dialog v-model="dialogVisible" :title="t('aftersales.satisfaction.dialog.createTitle')" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="t('aftersales.satisfaction.form.ticketId')">
          <el-input-number v-model="form.ticketId" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('aftersales.satisfaction.form.initiationId')">
          <el-input-number v-model="form.initiationId" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('aftersales.satisfaction.form.evaluatorId')">
          <el-input-number v-model="form.evaluatorId" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('aftersales.satisfaction.form.overallScore')" prop="overallScore">
          <el-rate v-model="form.overallScore" :max="5" />
        </el-form-item>
        <el-form-item :label="t('aftersales.satisfaction.form.responseScore')">
          <el-rate v-model="form.responseScore" :max="5" />
        </el-form-item>
        <el-form-item :label="t('aftersales.satisfaction.form.professionalScore')">
          <el-rate v-model="form.professionalScore" :max="5" />
        </el-form-item>
        <el-form-item :label="t('aftersales.satisfaction.form.attitudeScore')">
          <el-rate v-model="form.attitudeScore" :max="5" />
        </el-form-item>
        <el-form-item :label="t('aftersales.satisfaction.form.resultScore')">
          <el-rate v-model="form.resultScore" :max="5" />
        </el-form-item>
        <el-form-item :label="t('aftersales.satisfaction.form.speedScore')">
          <el-rate v-model="form.speedScore" :max="5" />
        </el-form-item>
        <el-form-item :label="t('aftersales.satisfaction.form.comment')">
          <el-input v-model="form.comment" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submitForm">{{ t('common.submit') }}</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
