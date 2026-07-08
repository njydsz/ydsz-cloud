<!--
  @fileoverview Agent 评测框架可视化页面
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { listEvaluators, runEvaluation } from '@/api/agent/evaluation'
import type { EvaluationReport, EvaluationResult, EvaluatorType } from '@/api/agent/evaluation/types'
import { useECharts } from '@/composables/useECharts'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

const AGENT_TYPE_OPTIONS = computed(() => [
  { value: 'RISK_WARNING', label: t('agent.debug.agentType.RISK_WARNING') },
  { value: 'RESOURCE_RECOMMEND', label: t('agent.debug.agentType.RESOURCE_RECOMMEND') },
  { value: 'PROFIT_FORECAST', label: t('agent.debug.agentType.PROFIT_FORECAST') },
  { value: 'WIN_RATE_PREDICT', label: t('agent.debug.agentType.WIN_RATE_PREDICT') },
  { value: 'TIMESHEET_ANOMALY', label: t('agent.debug.agentType.TIMESHEET_ANOMALY') },
  { value: 'APPROVER_RECOMMEND', label: t('agent.debug.agentType.APPROVER_RECOMMEND') },
  { value: 'COMMENT_DRAFT', label: t('agent.debug.agentType.COMMENT_DRAFT') },
  { value: 'FLOW_GENERATOR', label: t('agent.debug.agentType.FLOW_GENERATOR') },
])

const evaluators = ref<EvaluatorType[]>([])
const form = reactive({
  agentType: 'RISK_WARNING',
  parallelism: 1,
  cases: [
    { id: 'case-001', userInput: '', expectedOutput: '', evaluator: 'KEYWORD_CONTAINS', passThreshold: 0.6, tag: '' },
  ] as Array<{ id: string; userInput: string; expectedOutput: string; evaluator: string; passThreshold: number; tag: string }>,
})
const running = ref(false)
const report = ref<EvaluationReport | null>(null)

const chartRef = ref<HTMLDivElement | null>(null)
const { setOption } = useECharts(chartRef)

async function loadEvaluators() {
  try {
    const { data } = await listEvaluators()
    evaluators.value = (data as EvaluatorType[]) || []
  } catch {
    evaluators.value = [
      { code: 'EXACT_MATCH', desc: '精确匹配' },
      { code: 'KEYWORD_CONTAINS', desc: '关键词包含' },
      { code: 'COSINE_SIMILARITY', desc: '余弦相似度' },
      { code: 'LLM_AS_JUDGE', desc: 'LLM 评审' },
      { code: 'CUSTOM', desc: '自定义' },
    ]
  }
}

function addCase() {
  form.cases.push({
    id: `case-${String(form.cases.length + 1).padStart(3, '0')}`,
    userInput: '',
    expectedOutput: '',
    evaluator: 'KEYWORD_CONTAINS',
    passThreshold: 0.6,
    tag: '',
  })
}

function removeCase(idx: number) {
  form.cases.splice(idx, 1)
}

async function handleRun() {
  if (!form.agentType) {
    ElMessage.warning(t('agent.debug.messages.agentRequired'))
    return
  }
  if (form.cases.length === 0) {
    ElMessage.warning(t('agent.eval.messages.casesRequired'))
    return
  }
  running.value = true
  report.value = null
  try {
    const { data } = await runEvaluation({
      agentType: form.agentType,
      parallelism: form.parallelism,
      cases: form.cases,
    })
    report.value = data as EvaluationReport
    ElMessage.success(t('agent.eval.messages.runSuccess'))
    renderChart()
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.eval.messages.runFailed'))
  } finally {
    running.value = false
  }
}

function renderChart() {
  if (!report.value) return
  const results = report.value.results
  setOption({
    title: { text: t('agent.eval.chart.title'), left: 'center', top: 0 },
    tooltip: { trigger: 'axis' },
    legend: { data: [t('agent.eval.chart.score'), t('agent.eval.chart.elapsed')], top: 28 },
    grid: { top: 70, left: 60, right: 40, bottom: 60 },
    xAxis: { type: 'category', data: results.map((r) => r.caseId), axisLabel: { rotate: 30 } },
    yAxis: [
      { type: 'value', name: t('agent.eval.chart.score'), max: 1 },
      { type: 'value', name: t('agent.eval.chart.elapsedMs') },
    ],
    series: [
      {
        name: t('agent.eval.chart.score'),
        type: 'bar',
        data: results.map((r) => Number(r.score.toFixed(3))),
        itemStyle: {
          color: (params: any) => results[params.dataIndex].passed ? '#67C23A' : '#F56C6C',
        },
      },
      {
        name: t('agent.eval.chart.elapsed'),
        type: 'line',
        yAxisIndex: 1,
        data: results.map((r) => r.elapsedMs),
        itemStyle: { color: '#E6A23C' },
      },
    ],
  })
}

function scoreColor(score: number): string {
  if (score >= 0.8) return '#67C23A'
  if (score >= 0.6) return '#E6A23C'
  return '#F56C6C'
}

onMounted(() => {
  loadEvaluators()
})
</script>

<template>
  <div class="eval-page">
    <el-row :gutter="16">
      <!-- 左侧：配置 -->
      <el-col :xs="24" :md="10">
        <el-card shadow="never" :header="t('agent.eval.config')">
          <el-form label-width="80px" size="small">
            <el-form-item :label="t('agent.eval.form.agentType')">
              <el-select v-model="form.agentType" style="width: 100%">
                <el-option v-for="a in AGENT_TYPE_OPTIONS" :key="a.value" :value="a.value" :label="a.label" />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('agent.eval.form.parallelism')">
              <el-input-number v-model="form.parallelism" :min="1" :max="10" />
            </el-form-item>

            <el-divider content-position="left">{{ t('agent.eval.casesTitle') }}</el-divider>

            <div v-for="(c, idx) in form.cases" :key="idx" class="case-item">
              <div class="case-header">
                <el-tag size="small">{{ c.id }}</el-tag>
                <el-button :icon="'Delete'" link type="danger" size="small" @click="removeCase(idx)" />
              </div>
              <el-input v-model="c.userInput" :placeholder="t('agent.eval.form.userInput')" :rows="2" type="textarea" style="margin-bottom: 4px" />
              <el-input v-model="c.expectedOutput" :placeholder="t('agent.eval.form.expectedOutput')" :rows="1" type="textarea" style="margin-bottom: 4px" />
              <div class="case-meta">
                <el-select v-model="c.evaluator" size="small" style="width: 140px">
                  <el-option v-for="e in evaluators" :key="e.code" :value="e.code" :label="e.desc" />
                </el-select>
                <span style="font-size: 12px">{{ t('agent.eval.form.threshold') }}:</span>
                <el-input-number v-model="c.passThreshold" :min="0" :max="1" :step="0.1" size="small" style="width: 100px" />
              </div>
            </div>

            <el-button :icon="'Plus'" link type="primary" size="small" @click="addCase">
              {{ t('agent.eval.buttons.addCase') }}
            </el-button>

            <el-form-item style="margin-top: 12px">
              <el-button v-permission="[PC.AGENT_RUN]" type="primary" :icon="'VideoPlay'" :loading="running"
                style="width: 100%" @click="handleRun">
                {{ t('agent.eval.buttons.run') }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右侧：报告 -->
      <el-col :xs="24" :md="14">
        <el-card v-if="report" shadow="never" :header="t('agent.eval.report')">
          <!-- KPI -->
          <el-row :gutter="12" class="kpi-row">
            <el-col :span="6">
              <el-card shadow="hover" class="kpi-card">
                <div class="kpi-title">{{ t('agent.eval.kpi.total') }}</div>
                <div class="kpi-value">{{ report.totalCases }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="kpi-card success">
                <div class="kpi-title">{{ t('agent.eval.kpi.passed') }}</div>
                <div class="kpi-value">{{ report.passedCases }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="kpi-card danger">
                <div class="kpi-title">{{ t('agent.eval.kpi.failed') }}</div>
                <div class="kpi-value">{{ report.failedCases }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="kpi-card">
                <div class="kpi-title">{{ t('agent.eval.kpi.passRate') }}</div>
                <div class="kpi-value">{{ (report.passRate * 100).toFixed(1) }}%</div>
              </el-card>
            </el-col>
          </el-row>

          <el-alert :title="report.summary" type="info" :closable="false" style="margin-top: 12px" />

          <!-- 图表 -->
          <div ref="chartRef" class="chart-area" style="margin-top: 12px" />

          <!-- 明细表 -->
          <el-table :data="report.results" stripe size="small" style="margin-top: 12px">
            <el-table-column prop="caseId" label="ID" width="100" />
            <el-table-column prop="userInput" :label="t('agent.eval.table.userInput')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="expectedOutput" :label="t('agent.eval.table.expected')" min-width="100" show-overflow-tooltip />
            <el-table-column prop="actualOutput" :label="t('agent.eval.table.actual')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="score" :label="t('agent.eval.table.score')" width="80">
              <template #default="{ row }">
                <span :style="{ color: scoreColor(row.score), fontWeight: 600 }">
                  {{ row.score.toFixed(3) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="passed" :label="t('agent.eval.table.passed')" width="70">
              <template #default="{ row }">
                <el-tag :type="row.passed ? 'success' : 'danger'" size="small">
                  {{ row.passed ? '✓' : '✗' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="elapsedMs" :label="t('agent.eval.table.elapsed')" width="80">
              <template #default="{ row }">{{ row.elapsedMs }}ms</template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-empty v-else :description="t('agent.eval.empty')" :image-size="100" style="margin-top: 60px" />
      </el-col>
    </el-row>
  </div>
</template>

<style lang="scss" scoped>
.eval-page {
  .case-item {
    background: var(--el-fill-color-light);
    border-radius: 6px;
    padding: 8px;
    margin-bottom: 8px;
    .case-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 4px;
    }
    .case-meta {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }
  .kpi-row { margin-bottom: 8px; }
  .kpi-card {
    text-align: center;
    .kpi-title { font-size: 12px; color: var(--el-text-color-secondary); }
    .kpi-value { font-size: 22px; font-weight: 600; margin-top: 8px; }
    &.success .kpi-value { color: var(--el-color-success); }
    &.danger .kpi-value { color: var(--el-color-danger); }
  }
  .chart-area { width: 100%; height: 300px; }
}
</style>
