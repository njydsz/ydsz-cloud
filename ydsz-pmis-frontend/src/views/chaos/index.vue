<!--
  @file 混沌工程控制台
  @description 混沌实验仪表盘，提供实验注册/启用/禁用/Dry-Run/注销、注入历史查询、按 outcome 与 target 的实时统计可视化；列表与历史每 5s 轮询刷新，对接 @/api/chaos 模块。
  @module views/chaos

  功能:
    - 实验列表 (启用/禁用/编辑/删除/注册)
    - 实时统计 (按 outcome 类型分布饼图 + 按 target 柱状图)
    - 注入历史 (最近 100 条, 实时刷新)
    - Dry-Run 按钮 (主动触发一次, 验证容错)

  实时性策略:
    - 列表/历史每 5s 自动刷新 (useIntervalFn)
    - 实时统计基于 history 数据, 跟随刷新节流
    - 暂不接入 WebSocket (后端未实现推送), 5s 轮询已可满足"实时统计"需求
-->
<template>
  <div class="chaos-dashboard">
    <header class="chaos-dashboard__header">
      <h2>{{ t('chaos.title') }}</h2>
      <p class="chaos-dashboard__desc">
        {{ t('chaos.desc') }}
      </p>
    </header>

    <!-- KPI 概览 -->
    <section class="chaos-dashboard__kpis">
      <div class="kpi-card">
        <div class="kpi-card__label">{{ t('chaos.kpi.registered') }}</div>
        <div class="kpi-card__value">{{ experiments.length }}</div>
      </div>
      <div class="kpi-card kpi-card--success">
        <div class="kpi-card__label">{{ t('chaos.kpi.enabled') }}</div>
        <div class="kpi-card__value">{{ enabledCount }}</div>
      </div>
      <div class="kpi-card kpi-card--warning">
        <div class="kpi-card__label">{{ t('chaos.kpi.injected') }}</div>
        <div class="kpi-card__value">{{ injectedCount }}</div>
      </div>
      <div class="kpi-card kpi-card--info">
        <div class="kpi-card__label">{{ t('chaos.kpi.flag') }}</div>
        <div class="kpi-card__value kpi-card__value--small">
          {{ canaryDeployFlag ? 'ON' : 'OFF' }}
        </div>
      </div>
    </section>

    <!-- 图表区 -->
    <section class="chaos-dashboard__charts">
      <div class="chart-box">
        <h4>{{ t('chaos.charts.outcomeTitle') }}</h4>
        <div ref="outcomeChartRef" class="chart-canvas" data-test="chart-outcome" />
      </div>
      <div class="chart-box">
        <h4>{{ t('chaos.charts.targetTitle') }}</h4>
        <div ref="targetChartRef" class="chart-canvas" data-test="chart-target" />
      </div>
    </section>

    <!-- 实验列表 + 操作 -->
    <section class="chaos-dashboard__experiments">
      <div class="section-header">
        <h3>{{ t('chaos.experiments.title') }}</h3>
        <el-button
          type="primary"
          :loading="registering"
          @click="onRegister"
          data-test="btn-register"
        >
          {{ t('chaos.experiments.buttons.register') }}
        </el-button>
      </div>

      <el-table
        :data="experiments"
        border
        stripe
        data-test="exp-table"
        :loading="loading"
      >
        <el-table-column prop="target" :label="t('chaos.experiments.columns.target')" min-width="220" />
        <el-table-column prop="type" :label="t('chaos.experiments.columns.type')" width="160">
          <template #default="{ row }">
            <el-tag :type="typeTagType(row.type)">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('chaos.experiments.columns.params')" min-width="200">
          <template #default="{ row }">
            <span v-if="row.type === 'LATENCY'">{{ row.latencyMs ?? 0 }} ms</span>
            <span v-else-if="row.type === 'ERROR_RATE'">{{ ((row.errorRate ?? 0) * 100).toFixed(0) }}%</span>
            <span v-else-if="row.type === 'EXCEPTION'">{{ row.exceptionClass || 'RuntimeException' }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="t('chaos.experiments.columns.description')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="t('chaos.experiments.columns.enabled')" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              :loading="toggleLoading[row.target]"
              @change="(v) => onToggle(row.target, v as boolean)"
              data-test="switch-enabled"
            />
          </template>
        </el-table-column>
        <el-table-column :label="t('chaos.experiments.columns.action')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="warning"
              :loading="dryRunning === row.target"
              @click="onDryRun(row.target)"
              data-test="btn-dry-run"
            >
              {{ t('chaos.experiments.buttons.dryRun') }}
            </el-button>
            <el-button
              size="small"
              type="danger"
              :loading="deleting === row.target"
              @click="onUnregister(row.target)"
            >
              {{ t('chaos.experiments.buttons.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 历史时间线 -->
    <section class="chaos-dashboard__history">
      <div class="section-header">
        <h3>{{ t('chaos.history.title', { count: history.length }) }}</h3>
        <div>
          <el-button @click="refresh" :loading="loading">{{ t('chaos.history.buttons.refresh') }}</el-button>
          <el-button type="danger" plain @click="onClearHistory" data-test="btn-clear-history">
            {{ t('chaos.history.buttons.clear') }}
          </el-button>
        </div>
      </div>
      <el-table :data="history" border max-height="420" data-test="history-table">
        <el-table-column :label="t('chaos.history.columns.time')" width="200">
          <template #default="{ row }">
            {{ formatTime(row.timestamp) }}
          </template>
        </el-table-column>
        <el-table-column prop="target" :label="t('chaos.history.columns.target')" min-width="220" />
        <el-table-column :label="t('chaos.history.columns.outcome')" width="180">
          <template #default="{ row }">
            <el-tag :type="outcomeTagType(row.outcome)">{{ row.outcome }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detail" :label="t('chaos.history.columns.detail')" min-width="300" show-overflow-tooltip />
      </el-table>
    </section>

    <!-- 注册弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="t('chaos.dialog.registerTitle')"
      width="560px"
      data-test="dialog-register"
    >
      <el-form :model="form" label-width="100px">
        <el-form-item :label="t('chaos.dialog.form.target')" required>
          <el-input
            v-model="form.target"
            :placeholder="t('chaos.dialog.form.targetPlaceholder')"
            data-test="input-target"
          />
        </el-form-item>
        <el-form-item :label="t('chaos.dialog.form.type')" required>
          <el-select v-model="form.type" data-test="select-type">
            <el-option :label="t('chaos.dialog.typeOptions.LATENCY')" value="LATENCY" />
            <el-option :label="t('chaos.dialog.typeOptions.EXCEPTION')" value="EXCEPTION" />
            <el-option :label="t('chaos.dialog.typeOptions.ERROR_RATE')" value="ERROR_RATE" />
            <el-option :label="t('chaos.dialog.typeOptions.NETWORK_PARTITION')" value="NETWORK_PARTITION" />
            <el-option :label="t('chaos.dialog.typeOptions.RESOURCE_EXHAUSTION')" value="RESOURCE_EXHAUSTION" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.type === 'LATENCY'" :label="t('chaos.dialog.form.latency')">
          <el-input-number v-model="form.latencyMs" :min="0" :max="60_000" />
        </el-form-item>
        <el-form-item v-if="form.type === 'EXCEPTION'" :label="t('chaos.dialog.form.exceptionClass')">
          <el-input
            v-model="form.exceptionClass"
            placeholder="java.lang.RuntimeException"
          />
        </el-form-item>
        <el-form-item v-if="form.type === 'ERROR_RATE'" :label="t('chaos.dialog.form.errorRate')">
          <el-input-number
            v-model="form.errorRate"
            :min="0"
            :max="1"
            :step="0.1"
          />
        </el-form-item>
        <el-form-item :label="t('chaos.dialog.form.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('chaos.dialog.form.enabled')">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :loading="registering"
          @click="onSubmitRegister"
          data-test="btn-submit-register"
        >
          {{ t('common.ok') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * chaos-dashboard 主页面
 *
 * 数据来源:
 *   - experiments: GET /chaos/experiments
 *   - history:     GET /chaos/history
 *   - toggle:      PUT /chaos/experiments/{target}/enabled?enabled=...
 *   - register:    POST /chaos/experiments
 *   - dryRun:      POST /chaos/dry-run?target=...
 */
import { computed, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useECharts } from '@/composables/useECharts'
import { useIntervalFn } from '@vueuse/core'
import {
  listExperiments,
  toggleExperiment,
  unregisterExperiment,
  registerExperiment,
  dryRun,
  history as fetchHistory,
  clearHistory as apiClearHistory,
} from '@/api/chaos'
import type { ChaosExperiment, ChaosEvent, ChaosOutcome, ChaosExperimentType, ChaosDryRunResult } from '@/api/chaos/types'

const { t } = useI18n()

/** 实验列表数据 */
const experiments = ref<ChaosExperiment[]>([])
/** 注入历史数据（最近 100 条） */
const history = ref<ChaosEvent[]>([])
/** 列表加载状态 */
const loading = ref(false)
/** 注册提交中状态 */
const registering = ref(false)
/** 当前 Dry-Run 中的 target，用于按钮 loading */
const dryRunning = ref<string | null>(null)
/** 当前注销中的 target，用于按钮 loading */
const deleting = ref<string | null>(null)
/** 各 target 启停切换 loading 状态 */
const toggleLoading = reactive<Record<string, boolean>>({})
/** 注册实验弹窗显隐 */
const dialogVisible = ref(false)

const canaryDeployFlag = ref(false)  // 仅展示, 真实后端通过 /feature-flags 拉取

/** 注册实验表单初始默认值 */
const initialForm: ChaosExperiment = {
  target: '',
  type: 'LATENCY',
  latencyMs: 500,
  errorRate: 0.3,
  exceptionClass: 'java.lang.RuntimeException',
  description: '',
  enabled: false,
  createdBy: 'admin',
}
/** 注册实验表单数据 */
const form = reactive<ChaosExperiment>({ ...initialForm })

// ===== 派生指标 =====
/** 启用中的实验数量 */
const enabledCount = computed(() => experiments.value.filter((e) => e.enabled).length)
/** 近 100 条历史中已成功注入的次数 */
const injectedCount = computed(
  () => history.value.filter((h) => h.outcome === 'INJECTED').length,
)

// ===== ECharts =====
/** Outcome 饼图容器 ref */
const outcomeChartRef = ref<HTMLDivElement | null>(null)
/** Target 柱状图容器 ref */
const targetChartRef = ref<HTMLDivElement | null>(null)
const outcomeChart = useECharts(outcomeChartRef)
const targetChart = useECharts(targetChartRef)

/** 渲染 Outcome 饼图与 Target Top10 柱状图 */
function renderCharts() {
  // Outcome 饼图
  const outcomeGroups: Record<ChaosOutcome, number> = {
    INJECTED: 0,
    NOT_TRIGGERED: 0,
    BLOCKED_BY_FLAG: 0,
    SKIPPED_PROBABILITY: 0,
  }
  for (const ev of history.value) outcomeGroups[ev.outcome]++
  outcomeChart.setOption(
    {
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [
        {
          type: 'pie',
          radius: ['40%', '70%'],
          data: (Object.keys(outcomeGroups) as ChaosOutcome[]).map((k) => ({
            name: k,
            value: outcomeGroups[k],
          })),
        },
      ],
    },
    true,
  )

  // Target 柱状图 (Top 10)
  const targetCount: Record<string, number> = {}
  for (const ev of history.value) {
    if (ev.outcome === 'INJECTED') {
      targetCount[ev.target] = (targetCount[ev.target] || 0) + 1
    }
  }
  const top = Object.entries(targetCount)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 10)
  targetChart.setOption(
    {
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 16, top: 16, bottom: 40 },
      xAxis: {
        type: 'category',
        data: top.map(([t]) => t.length > 18 ? t.slice(0, 18) + '…' : t),
        axisLabel: { rotate: 30 },
      },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: top.map(([, c]) => c) }],
    },
    true,
  )
}

watch(history, () => renderCharts(), { deep: true })

// ===== 数据加载 =====
/** 并发拉取实验列表与注入历史，失败时全局提示 */
async function refresh() {
  loading.value = true
  try {
    const [exps, hist] = await Promise.all([listExperiments(), fetchHistory()])
    experiments.value = (exps as unknown as { data: ChaosExperiment[] }).data ?? (exps as unknown as ChaosExperiment[])
    history.value = (hist as unknown as { data: ChaosEvent[] }).data ?? (hist as unknown as ChaosEvent[])
  } catch (e) {
    ElMessage.error(t('chaos.messages.loadFailed', { message: e instanceof Error ? e.message : String(e) }))
  } finally {
    loading.value = false
  }
}

// 自动刷新 (5s) - 准实时统计
const { pause: pauseAutoRefresh, resume: resumeAutoRefresh } = useIntervalFn(refresh, 5_000, {
  immediate: true,
  immediateCallback: true,
})

onMounted(() => {
  // 首屏数据已在 useIntervalFn immediate 触发
})
onBeforeUnmount(() => pauseAutoRefresh())

// ===== 操作 =====
/**
 * 切换实验启用状态
 * @param target 实验目标标识
 * @param enabled 是否启用
 */
async function onToggle(target: string, enabled: boolean) {
  toggleLoading[target] = true
  try {
    await toggleExperiment(target, enabled)
    ElMessage.success(enabled ? t('chaos.messages.toggleOn', { target }) : t('chaos.messages.toggleOff', { target }))
    await refresh()
  } catch (e) {
    ElMessage.error(t('chaos.messages.opFailed', { message: e instanceof Error ? e.message : String(e) }))
  } finally {
    toggleLoading[target] = false
  }
}

/**
 * 注销指定实验，需二次确认
 * @param target 实验目标标识
 */
async function onUnregister(target: string) {
  await ElMessageBox.confirm(t('chaos.messages.confirmUnregister', { target }), t('chaos.messages.unregisterTitle'), { type: 'warning' })
  deleting.value = target
  try {
    await unregisterExperiment(target)
    ElMessage.success(t('chaos.messages.unregistered'))
    await refresh()
  } catch (e) {
    ElMessage.error(t('chaos.messages.unregisterFailed', { message: e instanceof Error ? e.message : String(e) }))
  } finally {
    deleting.value = null
  }
}

/**
 * 触发一次 Dry-Run 注入以验证容错能力
 * @param target 实验目标标识
 */
async function onDryRun(target: string) {
  dryRunning.value = target
  try {
    const resp = (await dryRun(target)) as unknown as { data?: ChaosDryRunResult } & Partial<ChaosDryRunResult>
    const r: ChaosDryRunResult = resp.data ?? (resp as ChaosDryRunResult)
    if (r.outcome === 'INJECTED') {
      ElMessage.warning(t('chaos.messages.dryRunInjected', { error: r.error }))
    } else {
      ElMessage.info(t('chaos.messages.dryRunOutcome', { outcome: r.outcome }))
    }
    await refresh()
  } catch (e) {
    ElMessage.error(t('chaos.messages.dryRunFailed', { message: e instanceof Error ? e.message : String(e) }))
  } finally {
    dryRunning.value = null
  }
}

/** 打开注册实验弹窗，重置表单为默认值 */
function onRegister() {
  Object.assign(form, initialForm)
  dialogVisible.value = true
}

/** 提交注册实验表单，校验 target 必填后调用注册接口 */
async function onSubmitRegister() {
  if (!form.target.trim()) {
    ElMessage.warning(t('chaos.messages.targetRequired'))
    return
  }
  registering.value = true
  try {
    await registerExperiment({ ...form })
    ElMessage.success(t('chaos.messages.registered'))
    dialogVisible.value = false
    await refresh()
  } catch (e) {
    ElMessage.error(t('chaos.messages.registerFailed', { message: e instanceof Error ? e.message : String(e) }))
  } finally {
    registering.value = false
  }
}

/** 清空所有注入历史，需二次确认且不可恢复 */
async function onClearHistory() {
  await ElMessageBox.confirm(t('chaos.messages.confirmClear'), t('chaos.messages.clearTitle'), { type: 'warning' })
  try {
    await apiClearHistory()
    ElMessage.success(t('chaos.messages.cleared'))
    await refresh()
  } catch (e) {
    ElMessage.error(t('chaos.messages.clearFailed', { message: e instanceof Error ? e.message : String(e) }))
  }
}

// ===== 辅助 =====
/**
 * 将时间戳格式化为中文本地时间字符串（24 小时制）
 * @param ts 毫秒时间戳
 * @returns 格式化后的时间字符串
 */
function formatTime(ts: number) {
  return new Date(ts).toLocaleString('zh-CN', { hour12: false })
}

/**
 * 根据实验类型返回 el-tag type
 * @param t 实验类型
 * @returns el-tag type
 */
function typeTagType(t: ChaosExperimentType): 'success' | 'warning' | 'info' | 'danger' {
  switch (t) {
    case 'LATENCY':
      return 'warning'
    case 'EXCEPTION':
      return 'danger'
    case 'ERROR_RATE':
      return 'info'
    case 'NETWORK_PARTITION':
      return 'danger'
    default:
      return 'success'
  }
}

/**
 * 根据 Outcome 返回 el-tag type
 * @param o 注入结果
 * @returns el-tag type
 */
function outcomeTagType(o: ChaosOutcome): 'success' | 'warning' | 'info' | 'danger' {
  switch (o) {
    case 'INJECTED':
      return 'danger'
    case 'BLOCKED_BY_FLAG':
      return 'warning'
    case 'SKIPPED_PROBABILITY':
      return 'info'
    default:
      return 'success'
  }
}

defineExpose({ refresh, pauseAutoRefresh, resumeAutoRefresh })
</script>

<style scoped>
.chaos-dashboard {
  padding: 16px 24px;
  max-width: 1600px;
  margin: 0 auto;
}
.chaos-dashboard__header h2 {
  margin: 0;
  font-size: 20px;
}
.chaos-dashboard__desc {
  color: #666;
  font-size: 13px;
  margin: 4px 0 16px;
}
.chaos-dashboard__desc code {
  background: #f0f0f0;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
}
.chaos-dashboard__kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.kpi-card {
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  border-left: 3px solid #409eff;
}
.kpi-card--success { border-left-color: #67c23a; }
.kpi-card--warning { border-left-color: #e6a23c; }
.kpi-card--info    { border-left-color: #909399; }
.kpi-card__label { color: #888; font-size: 13px; }
.kpi-card__value { font-size: 24px; font-weight: 600; margin-top: 4px; }
.kpi-card__value--small { font-size: 16px; }

.chaos-dashboard__charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}
.chart-box {
  background: #fff;
  border-radius: 8px;
  padding: 12px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.chart-box h4 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #333;
}
.chart-canvas {
  width: 100%;
  height: 280px;
}

.chaos-dashboard__experiments,
.chaos-dashboard__history {
  background: #fff;
  border-radius: 8px;
  padding: 12px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  margin-bottom: 16px;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.section-header h3 {
  margin: 0;
  font-size: 16px;
}
</style>
