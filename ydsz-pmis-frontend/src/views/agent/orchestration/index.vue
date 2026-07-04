<script setup lang="ts">
/**
 * 多智能体编排可视化页面
 *
 * 4 种编排模式 (SEQUENTIAL/PARALLEL/VOTING/CASCADE) 切换：
 *  - SEQUENTIAL：按声明顺序串行执行
 *  - PARALLEL：  并发执行，取 score 最高为 finalResult
 *  - VOTING：    加权融合 score/confidence，按严重度取最高告警等级
 *  - CASCADE：   逐级判定达标即停（置信度阈值默认 0.85）
 *
 * 流程图采用内嵌 SVG 自绘（避免引入额外图依赖），结果指标用 ECharts 柱状对比。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { coordinate, listAgents } from '@/api/agent/orchestration'
import type {
  AgentResultPayload,
  AgentTypeInfo,
  OrchestrationModeCode,
  OrchestrationResult,
} from '@/api/agent/orchestration/types'
import { useECharts } from '@/composables/useECharts'
import { PC } from '@/constants/permissionCodes'
import OrchestrationPipeline from './components/OrchestrationPipeline.vue'

const { t } = useI18n()

// ============= 静态元数据 =============
const MODE_OPTIONS = computed<Array<{ code: OrchestrationModeCode; label: string; desc: string; color: string }>>(() => [
  { code: 'SEQUENTIAL', label: t('agent.orchestration.mode.SEQUENTIAL'), desc: t('agent.orchestration.modeDesc.SEQUENTIAL'), color: '#409EFF' },
  { code: 'PARALLEL',   label: t('agent.orchestration.mode.PARALLEL'), desc: t('agent.orchestration.modeDesc.PARALLEL'), color: '#67C23A' },
  { code: 'VOTING',     label: t('agent.orchestration.mode.VOTING'), desc: t('agent.orchestration.modeDesc.VOTING'), color: '#E6A23C' },
  { code: 'CASCADE',    label: t('agent.orchestration.mode.CASCADE'), desc: t('agent.orchestration.modeDesc.CASCADE'), color: '#F56C6C' },
])

const BIZ_TYPE_OPTIONS = computed(() => [
  { value: 'PROJECT',     label: t('agent.orchestration.bizType.PROJECT') },
  { value: 'OPPORTUNITY', label: t('agent.orchestration.bizType.OPPORTUNITY') },
  { value: 'TIMESHEET',   label: t('agent.orchestration.bizType.TIMESHEET') },
  { value: 'STAFF',       label: t('agent.orchestration.bizType.STAFF') },
])

// ============= 表单状态 =============
const form = reactive({
  bizType: 'PROJECT' as 'PROJECT' | 'OPPORTUNITY' | 'TIMESHEET' | 'STAFF',
  bizId: 1001 as number | undefined,
  bizRef: 'PRJ-001',
  mode: 'SEQUENTIAL' as OrchestrationModeCode,
  agentTypes: ['RISK_WARNING', 'PROFIT_FORECAST'] as string[],
  confidenceThreshold: 0.85,
  // 事实上下文 - key/value
  facts: [
    { key: 'cpi', value: '0.95' },
    { key: 'spi', value: '0.92' },
  ] as Array<{ key: string; value: string }>,
  // 投票权重 - key/value
  weights: [
    { agentType: 'RISK_WARNING',   weight: 0.5 },
    { agentType: 'PROFIT_FORECAST', weight: 0.5 },
  ] as Array<{ agentType: string; weight: number }>,
})

const agentOptions = ref<AgentTypeInfo[]>([])

async function loadAgentOptions() {
  try {
    const { data } = await listAgents()
    agentOptions.value = (data as AgentTypeInfo[]) ?? []
  } catch {
    // 后端如未提供，回退静态元数据
    agentOptions.value = [
      { code: 'RISK_WARNING',      desc: t('agent.orchestration.agentDesc.RISK_WARNING') },
      { code: 'RESOURCE_RECOMMEND', desc: t('agent.orchestration.agentDesc.RESOURCE_RECOMMEND') },
      { code: 'PROFIT_FORECAST',    desc: t('agent.orchestration.agentDesc.PROFIT_FORECAST') },
      { code: 'WIN_RATE_PREDICT',   desc: t('agent.orchestration.agentDesc.WIN_RATE_PREDICT') },
      { code: 'TIMESHEET_ANOMALY',  desc: t('agent.orchestration.agentDesc.TIMESHEET_ANOMALY') },
    ]
  }
}

// ============= 执行 =============
const submitting = ref(false)
const result = ref<OrchestrationResult | null>(null)

async function runOrchestration() {
  if (form.agentTypes.length === 0) {
    ElMessage.warning(t('agent.orchestration.messages.agentRequired'))
    return
  }
  if (form.mode === 'VOTING' && form.weights.length === 0) {
    ElMessage.warning(t('agent.orchestration.messages.weightsRequired'))
    return
  }
  submitting.value = true
  result.value = null
  try {
    const facts: Record<string, unknown> = {}
    for (const f of form.facts) {
      if (f.key && f.value !== '' && f.value !== undefined) {
        const num = Number(f.value)
        facts[f.key] = Number.isFinite(num) && f.value !== '' ? num : f.value
      }
    }
    const weights: Record<string, number> = {}
    for (const w of form.weights) {
      if (w.agentType) weights[w.agentType] = Number(w.weight)
    }
    const payload = {
      bizType: form.bizType,
      bizId: form.bizId,
      bizRef: form.bizRef,
      callerId: 1,
      callerName: 'demo-user',
      source: 'MANUAL',
      mode: form.mode,
      agentTypes: form.agentTypes,
      facts,
      weights: form.mode === 'VOTING' ? weights : undefined,
      confidenceThreshold: form.mode === 'CASCADE' ? form.confidenceThreshold : undefined,
    }
    const { data } = await coordinate(payload)
    result.value = (data as OrchestrationResult) ?? null
    if (result.value) {
      ElMessage.success(t('agent.orchestration.messages.success', { count: result.value.executedAgents.length }))
      await nextTick()
      renderScoreChart()
    }
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.orchestration.messages.failed'))
  } finally {
    submitting.value = false
  }
}

// ============= 可视化 =============
const chartRef = ref<HTMLDivElement | null>(null)
const { setOption } = useECharts(chartRef)

function renderScoreChart() {
  if (!result.value) return
  const agents = result.value.executedAgents
  const rows = agents.map((t) => {
    const ar = result.value!.agentResults[t] || ({} as AgentResultPayload)
    return {
      agent: t,
      score: Number(ar.score ?? 0),
      confidence: Number(((ar.confidence ?? 0) * 100).toFixed(1)),
      level: ar.alertLevel || 'NORMAL',
    }
  })
  setOption({
    title: { text: t('agent.orchestration.chart.title'), left: 'center', top: 0 },
    tooltip: { trigger: 'axis' },
    legend: { data: [t('agent.orchestration.chart.score'), t('agent.orchestration.chart.confidence')], top: 28 },
    grid: { top: 70, left: 60, right: 40, bottom: 60 },
    xAxis: { type: 'category', data: rows.map((r) => r.agent), axisLabel: { rotate: 20 } },
    yAxis: [
      { type: 'value', name: t('agent.orchestration.chart.yAxisScore'), max: 100 },
      { type: 'value', name: t('agent.orchestration.chart.yAxisConfidence'), max: 100 },
    ],
    series: [
      { name: t('agent.orchestration.chart.score'), type: 'bar', data: rows.map((r) => r.score), itemStyle: { color: '#409EFF' } },
      { name: t('agent.orchestration.chart.confidence'), type: 'line', yAxisIndex: 1, data: rows.map((r) => r.confidence), itemStyle: { color: '#E6A23C' } },
    ],
  })
}

// ============= 工具方法 =============
/**
 * 告警等级映射为 el-tag 类型
 * @param level 告警等级 RED / YELLOW / NORMAL / 其他
 * @returns el-tag 类型 danger / warning / success / info
 */
function levelTagType(level?: string): 'success' | 'info' | 'warning' | 'danger' {
  switch (level) {
    case 'RED':  return 'danger'
    case 'YELLOW': return 'warning'
    case 'NORMAL': return 'success'
    default: return 'info'
  }
}

/** 追加一条事实上下文 */
function addFact() { form.facts.push({ key: '', value: '' }) }
/** 删除指定下标的事实上下文 */
function removeFact(i: number) { form.facts.splice(i, 1) }
/** 追加一条投票权重 */
function addWeight() { form.weights.push({ agentType: '', weight: 0.5 }) }
/** 删除指定下标的投票权重 */
function removeWeight(i: number) { form.weights.splice(i, 1) }

const currentMode = computed(() => MODE_OPTIONS.value.find((m) => m.code === form.mode)!)

onMounted(() => {
  loadAgentOptions()
})
</script>

<template>
  <div class="orchestration-page">
    <!-- 顶部：模式切换 -->
    <el-card shadow="never" class="mode-card">
      <el-radio-group v-model="form.mode" size="large">
        <el-radio-button
          v-for="m in MODE_OPTIONS"
          :key="m.code"
          :value="m.code"
        >
          <span :style="{ color: form.mode === m.code ? m.color : undefined }">{{ m.label }}</span>
        </el-radio-button>
      </el-radio-group>
      <div class="mode-desc">
        <el-tag :color="currentMode.color" effect="dark" size="small" style="color:#fff">{{ currentMode.label }}</el-tag>
        {{ currentMode.desc }}
      </div>
    </el-card>

    <el-row :gutter="16">
      <!-- 左侧：编排配置 -->
      <el-col :xs="24" :md="10">
        <el-card shadow="never" :header="t('agent.orchestration.sections.config')">
          <el-form label-width="92px" size="default">
            <el-form-item :label="t('agent.orchestration.form.bizType')">
              <el-select v-model="form.bizType" style="width: 100%">
                <el-option v-for="b in BIZ_TYPE_OPTIONS" :key="b.value" :value="b.value" :label="b.label" />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('agent.orchestration.form.bizId')">
              <el-input-number v-model="form.bizId" :min="0" style="width: 100%" />
            </el-form-item>
            <el-form-item :label="t('agent.orchestration.form.bizRef')">
              <el-input v-model="form.bizRef" :placeholder="t('agent.orchestration.form.bizRefPlaceholder')" />
            </el-form-item>
            <el-form-item :label="t('agent.orchestration.form.agentTypes')">
              <el-select
v-model="form.agentTypes" multiple collapse-tags collapse-tags-tooltip
                         :placeholder="t('agent.orchestration.form.agentTypesPlaceholder')" style="width: 100%">
                <el-option v-for="a in agentOptions" :key="a.code" :value="a.code" :label="`${a.code}（${a.desc}）`" />
              </el-select>
            </el-form-item>

            <el-form-item v-if="form.mode === 'CASCADE'" :label="t('agent.orchestration.form.confidenceThreshold')">
              <el-slider v-model="form.confidenceThreshold" :min="0" :max="1" :step="0.05" show-input />
            </el-form-item>

            <el-divider content-position="left">{{ t('agent.orchestration.factsDivider') }}</el-divider>
            <div v-for="(f, i) in form.facts" :key="i" class="kv-row">
              <el-input v-model="f.key" placeholder="key" style="width: 35%" />
              <el-input v-model="f.value" placeholder="value" style="width: 55%; margin-left: 8px" />
              <el-button :icon="'Delete'" link style="margin-left: 4px" @click="removeFact(i)" />
            </div>
            <el-button :icon="'Plus'" link type="primary" size="small" @click="addFact">{{ t('agent.orchestration.buttons.addFact') }}</el-button>

            <template v-if="form.mode === 'VOTING'">
              <el-divider content-position="left">{{ t('agent.orchestration.weightsDivider') }}</el-divider>
              <div v-for="(w, i) in form.weights" :key="i" class="kv-row">
                <el-select v-model="w.agentType" placeholder="Agent" style="width: 40%">
                  <el-option v-for="a in agentOptions" :key="a.code" :value="a.code" :label="a.code" />
                </el-select>
                <el-input-number v-model="w.weight" :min="0" :max="1" :step="0.05" style="width: 50%; margin-left: 8px" />
                <el-button :icon="'Delete'" link style="margin-left: 4px" @click="removeWeight(i)" />
              </div>
              <el-button :icon="'Plus'" link type="primary" size="small" @click="addWeight">{{ t('agent.orchestration.buttons.addWeight') }}</el-button>
            </template>

            <el-form-item style="margin-top: 16px">
              <el-button
                v-permission="[PC.AGENT_ORCHESTRATION_RUN]"
                type="primary"
                :icon="'VideoPlay'"
                :loading="submitting"
                :disabled="form.agentTypes.length === 0"
                @click="runOrchestration"
              >
                {{ t('agent.orchestration.buttons.run') }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右侧：流程图可视化 -->
      <el-col :xs="24" :md="14">
        <el-card shadow="never" :header="t('agent.orchestration.sections.pipeline')">
          <OrchestrationPipeline
            :mode="form.mode"
            :agent-types="form.agentTypes"
            :result="result"
          />
        </el-card>
      </el-col>
    </el-row>

    <!-- 下方：执行结果 -->
    <el-card v-if="result" shadow="never" class="result-card" :header="t('agent.orchestration.sections.result')">
      <el-row :gutter="16">
        <el-col :xs="24" :md="8">
          <el-card shadow="hover" class="kpi-card">
            <div class="kpi-title">{{ t('agent.orchestration.kpi.mode') }}</div>
            <el-tag :type="form.mode === 'CASCADE' ? 'danger' : 'primary'" effect="dark" size="large">
              {{ currentMode.label }}
            </el-tag>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="8">
          <el-card shadow="hover" class="kpi-card">
            <div class="kpi-title">{{ t('agent.orchestration.kpi.agents') }}</div>
            <div class="kpi-value">{{ result.agentCount }} / {{ result.executedAgents.length }}</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="8">
          <el-card shadow="hover" class="kpi-card">
            <div class="kpi-title">{{ t('agent.orchestration.kpi.cost') }}</div>
            <div class="kpi-value">{{ result.totalCostMs }} ms</div>
          </el-card>
        </el-col>
      </el-row>

      <el-alert v-if="result.note" :title="result.note" type="info" :closable="false" style="margin-top: 12px" />

      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :xs="24" :md="14">
          <el-card shadow="never" :header="t('agent.orchestration.sections.metrics')">
            <div ref="chartRef" class="chart-area" />
          </el-card>
        </el-col>
        <el-col :xs="24" :md="10">
          <el-card shadow="never" :header="t('agent.orchestration.sections.finalResult')">
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item :label="t('agent.orchestration.resultFields.agentType')">
                {{ result.finalResult?.agentType || '-' }}
              </el-descriptions-item>
              <el-descriptions-item :label="t('agent.orchestration.resultFields.alertLevel')">
                <el-tag :type="levelTagType(result.finalResult?.alertLevel)" effect="dark">
                  {{ result.finalResult?.alertLevel || 'NORMAL' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item :label="t('agent.orchestration.resultFields.score')">
                {{ Number(result.finalResult?.score ?? 0).toFixed(2) }}
              </el-descriptions-item>
              <el-descriptions-item :label="t('agent.orchestration.resultFields.confidence')">
                {{ Number(result.finalResult?.confidence ?? 0).toFixed(4) }}
              </el-descriptions-item>
              <el-descriptions-item :label="t('agent.orchestration.resultFields.suggestion')">
                {{ result.finalResult?.suggestion || '-' }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="never" :header="t('agent.orchestration.sections.trace')" style="margin-top: 16px">
        <el-table :data="result.trace" stripe size="small">
          <el-table-column type="index" width="56" label="#" />
          <el-table-column prop="agentType" :label="t('agent.orchestration.traceCols.agent')" width="200" />
          <el-table-column prop="mode" :label="t('agent.orchestration.traceCols.mode')" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.mode" size="small">{{ row.mode }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="score" :label="t('agent.orchestration.traceCols.score')" width="100">
            <template #default="{ row }">
              {{ row.score == null ? '-' : Number(row.score).toFixed(2) }}
            </template>
          </el-table-column>
          <el-table-column prop="confidence" :label="t('agent.orchestration.traceCols.confidence')" width="100">
            <template #default="{ row }">
              {{ row.confidence == null ? '-' : Number(row.confidence).toFixed(4) }}
            </template>
          </el-table-column>
          <el-table-column prop="note" :label="t('agent.orchestration.traceCols.note')" />
          <el-table-column :label="t('agent.orchestration.traceCols.time')" width="160">
            <template #default="{ row }">{{ new Date(row.ts).toLocaleString() }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.orchestration-page {
  .mode-card {
    margin-bottom: 16px;
    .mode-desc {
      margin-top: 12px;
      font-size: 12px;
      color: var(--el-text-color-secondary);
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }
  .kv-row { display: flex; align-items: center; margin-bottom: 8px; }
  .result-card { margin-top: 16px; }
  .kpi-card {
    text-align: center;
    .kpi-title { font-size: 12px; color: var(--el-text-color-secondary); }
    .kpi-value { font-size: 22px; font-weight: 600; margin-top: 8px; color: var(--el-color-primary); }
  }
  .chart-area { width: 100%; height: 320px; }
}
</style>
