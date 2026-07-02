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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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

const levelMap = {
  VERY_DISSATISFIED: { label: '极不满意', type: 'danger' as const },
  DISSATISFIED: { label: '不满意', type: 'danger' as const },
  NEUTRAL: { label: '一般', type: 'info' as const },
  SATISFIED: { label: '满意', type: 'success' as const },
  VERY_SATISFIED: { label: '非常满意', type: 'success' as const },
}

const followUpMap = {
  PENDING: { label: '待跟进', type: 'warning' as const },
  FOLLOW_UP: { label: '跟进中', type: 'primary' as const },
  CLOSED: { label: '已关闭', type: 'success' as const },
}

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
const formRef = ref<any>()
/** 评价表单数据 */
const form = reactive<Partial<SatisfactionCreateDTO>>({
  overallScore: 5,
  ticketId: undefined,
  initiationId: undefined,
  evaluatorId: undefined,
})

const formRules = {
  overallScore: [{ required: true, message: '总体评分必填', trigger: 'change' }],
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
  await submitSatisfaction(form as SatisfactionCreateDTO)
  ElMessage.success('已提交评价')
  dialogVisible.value = false
  fetchList()
  fetchStats()
}

/** 标记不满意评价为跟进中，需输入跟进说明 */
async function handleFollowUp(row: SatisfactionVO) {
  const level = inferLevel(row.overallScore)
  if (!['DISSATISFIED', 'VERY_DISSATISFIED'].includes(level)) {
    ElMessage.info('仅不满意评价需要跟进')
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('请输入跟进说明', '标记跟进', {
      inputValidator: (v) => !!v || '说明必填',
    })
    await markFollowUp(row.id, value)
    ElMessage.success('已标记跟进')
    fetchList()
  } catch { /* 取消 */ }
}

/** 关闭跟进，需输入关闭说明 */
async function handleCloseFollowUp(row: SatisfactionVO) {
  try {
    const { value } = await ElMessageBox.prompt('请输入关闭说明', '关闭跟进', {
      inputValidator: (v) => !!v || '说明必填',
    })
    await closeFollowUp(row.id)
    ElMessage.success(`已关闭：${value}`)
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
      <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="编号/评价" clearable /></el-form-item>
      <el-form-item label="等级">
        <el-select v-model="query.level" placeholder="全部" clearable style="width: 130px">
          <el-option v-for="(v, k) in levelMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="跟进">
        <el-select v-model="query.followUpStatus" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="(v, k) in followUpMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目 ID"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
    </template>

    <!-- 工具栏 -->
    <template #toolbar>
      <el-button v-permission="[PC.AFTERSALES_SATISFACTION_SUBMIT]" type="primary" :icon="'Plus'" @click="openCreate">
        提交评价
      </el-button>
    </template>

    <!-- 统计卡 -->
    <el-row :gutter="12" class="mb-3">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="text-sm text-gray-500">评价总数</div>
          <div class="text-2xl font-bold mt-1">{{ overall.count ?? total }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="text-sm text-gray-500">综合均分</div>
          <div class="text-2xl font-bold mt-1">
            {{ overall.avgScore != null ? Number(overall.avgScore).toFixed(2) : '—' }}
            <span class="text-sm text-gray-400">/5</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="text-sm text-gray-500">不满意条数</div>
          <div class="text-2xl font-bold text-red-500 mt-1">{{ overall.unsatisfiedCount ?? '—' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="text-sm text-gray-500">等级分布</div>
          <div v-for="(row, idx) in levelDist" :key="idx" class="text-xs mt-1">
            {{ (row as any).level || (row as any).name }}：<b>{{ (row as any).count }}</b>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 评价表格 -->
    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="satisfactionCode" title="评价编号" width="180" />
        <vxe-column field="ticketCode" title="工单" width="180" show-overflow />
        <vxe-column field="initiationName" title="项目" min-width="160" show-overflow />
        <vxe-column field="evaluatorName" title="评价人" width="100" />
        <vxe-column field="overallScore" title="总分" width="80" align="center" />
        <vxe-column label="等级" width="110">
          <template #default="{ row }">
            <StatusTag :value="inferLevel(row.overallScore)" :map="levelMap" />
          </template>
        </vxe-column>
        <vxe-column field="responseScore" title="响应" width="70" align="center" />
        <vxe-column field="professionalScore" title="专业" width="70" align="center" />
        <vxe-column field="attitudeScore" title="态度" width="70" align="center" />
        <vxe-column field="resultScore" title="结果" width="70" align="center" />
        <vxe-column field="speedScore" title="速度" width="70" align="center" />
        <vxe-column field="comment" title="评价说明" min-width="200" show-overflow />
        <vxe-column field="followUpStatus" title="跟进状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.followUpStatus" :map="followUpMap" /></template>
        </vxe-column>
        <vxe-column field="followUpAt" title="跟进时间" width="170" />
        <vxe-column title="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.followUpStatus === 'PENDING'"
              v-permission="[PC.AFTERSALES_SATISFACTION_FOLLOWUP]"
              link
              type="warning"
              size="small"
              @click="handleFollowUp(row)"
            >标记跟进</el-button>
            <el-button
              v-if="row.followUpStatus === 'FOLLOW_UP'"
              v-permission="[PC.AFTERSALES_SATISFACTION_FOLLOWUP]"
              link
              type="success"
              size="small"
              @click="handleCloseFollowUp(row)"
            >关闭跟进</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <!-- 提交评价弹窗 -->
    <el-dialog v-model="dialogVisible" title="提交满意度评价" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="关联工单 ID">
          <el-input-number v-model="form.ticketId" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="项目 ID">
          <el-input-number v-model="form.initiationId" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="评价人 ID">
          <el-input-number v-model="form.evaluatorId" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="总体评分" prop="overallScore">
          <el-rate v-model="form.overallScore" :max="5" />
        </el-form-item>
        <el-form-item label="响应速度">
          <el-rate v-model="form.responseScore" :max="5" />
        </el-form-item>
        <el-form-item label="专业度">
          <el-rate v-model="form.professionalScore" :max="5" />
        </el-form-item>
        <el-form-item label="服务态度">
          <el-rate v-model="form.attitudeScore" :max="5" />
        </el-form-item>
        <el-form-item label="结果质量">
          <el-rate v-model="form.resultScore" :max="5" />
        </el-form-item>
        <el-form-item label="响应速度">
          <el-rate v-model="form.speedScore" :max="5" />
        </el-form-item>
        <el-form-item label="评价说明">
          <el-input v-model="form.comment" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">提交</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
