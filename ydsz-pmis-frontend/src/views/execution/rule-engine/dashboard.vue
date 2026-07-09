<!--
  @file 规则引擎监控大盘（P1-6）
  @description 规则引擎监控大盘页面：聚合规则数量、触发率、P99 耗时、错误率趋势等核心指标，
               支持时间范围切换（24h/7d/30d）、30 秒自动刷新，对应路由 /execution/rule-engine/dashboard。
  @module views/execution/rule-engine
-->
<script setup lang="ts">
/**
 * 规则引擎监控大盘（P1-6）
 *
 * 功能区域：
 *  1. 顶部工具栏：时间范围切换（24h/7d/30d）+ 手动刷新 + 自动刷新开关
 *  2. 指标卡片区：规则总数、今日触发率、今日错误率、P99 耗时、当前 QPS（5 张卡片）
 *  3. 趋势图区：触发次数趋势（评估/触发/错误 三线）+ 错误率 & P99 耗时趋势（双 Y 轴）
 *  4. 分布饼图区：规则状态分布 + 规则类别分布
 *  5. Top 规则表格区：最活跃规则 Top 10（按触发次数）+ 最慢规则 Top 10（按平均耗时）
 *  6. 实时指标条：注册规则数 / 最近评估规则数 / 活跃规则数 / Trace 队列积压
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Refresh, DataLine, Monitor, TrendCharts, Warning, Timer, Odometer } from '@element-plus/icons-vue'
import { useECharts } from '@/composables/useECharts'
import {
  getDashboardOverview,
  getDashboardTrends,
  getDashboardDistribution,
  getDashboardTopRules,
  getDashboardRealtime,
} from '@/api/rule-engine'
import type {
  DashboardOverview,
  DashboardTrend,
  DashboardDistribution,
  DashboardTopRule,
  DashboardRealtime,
  DashboardTimeRange,
  DashboardTopType,
} from '@/api/rule-engine'
import { logger } from '@/utils/logger'

defineOptions({ name: 'RuleEngineDashboard' })

const router = useRouter()

// ==================== 状态 ====================

/** 时间范围 */
const timeRange = ref<DashboardTimeRange>('24h')
/** 概览指标 */
const overview = ref<DashboardOverview | null>(null)
/** 趋势指标 */
const trend = ref<DashboardTrend | null>(null)
/** 分布指标 */
const distribution = ref<DashboardDistribution | null>(null)
/** 最活跃规则 Top 10 */
const topTriggered = ref<DashboardTopRule[]>([])
/** 最慢规则 Top 10 */
const topSlowest = ref<DashboardTopRule[]>([])
/** 实时指标 */
const realtime = ref<DashboardRealtime | null>(null)
/** 整体加载状态 */
const loading = ref(false)
/** 趋势图加载状态 */
const trendLoading = ref(false)
/** 最后一次刷新时间 */
const lastUpdated = ref('')
/** 是否启用自动刷新 */
const autoRefresh = ref(true)
/** 自动刷新间隔（毫秒） */
const REFRESH_INTERVAL = 30_000
let pollTimer: number | null = null

// ==================== ECharts 实例 ====================

/** 触发次数趋势图容器 */
const triggerTrendRef = ref<HTMLDivElement | null>(null)
const { setOption: setTriggerTrendOption } = useECharts(triggerTrendRef)

/** 错误率 & P99 耗时趋势图容器 */
const errorTrendRef = ref<HTMLDivElement | null>(null)
const { setOption: setErrorTrendOption } = useECharts(errorTrendRef)

/** 规则状态分布饼图容器 */
const statusPieRef = ref<HTMLDivElement | null>(null)
const { setOption: setStatusPieOption } = useECharts(statusPieRef)

/** 规则类别分布饼图容器 */
const categoryPieRef = ref<HTMLDivElement | null>(null)
const { setOption: setCategoryPieOption } = useECharts(categoryPieRef)

// ==================== 计算属性 ====================

/** 触发率百分比 */
const triggerRatePct = computed(() => {
  const v = overview.value?.todayTriggerRate ?? 0
  return (v * 100).toFixed(2) + '%'
})

/** 错误率百分比 */
const errorRatePct = computed(() => {
  const v = overview.value?.todayErrorRate ?? 0
  return (v * 100).toFixed(2) + '%'
})

/** P99 耗时（毫秒） */
const p99Ms = computed(() => Math.round(overview.value?.p99ElapsedMs ?? 0))

/** 当前 QPS */
const currentQps = computed(() => (realtime.value?.currentQps ?? 0).toFixed(2))

/** 时间范围选项 */
const timeRangeOptions: Array<{ label: string; value: DashboardTimeRange }> = [
  { label: '近 24 小时', value: '24h' },
  { label: '近 7 天', value: '7d' },
  { label: '近 30 天', value: '30d' },
]

// ==================== 数据加载 ====================

/** 加载概览指标 */
async function loadOverview() {
  try {
    const { data } = await getDashboardOverview()
    overview.value = data ?? null
  } catch (e) {
    logger.error('[RuleDashboard] 加载概览指标失败', e)
    ElMessage.error('加载概览指标失败')
  }
}

/** 加载趋势指标并渲染图表 */
async function loadTrend() {
  trendLoading.value = true
  try {
    const { data } = await getDashboardTrends(timeRange.value)
    trend.value = data ?? null
    renderTriggerTrend()
    renderErrorTrend()
  } catch (e) {
    logger.error('[RuleDashboard] 加载趋势指标失败', e)
    ElMessage.error('加载趋势指标失败')
  } finally {
    trendLoading.value = false
  }
}

/** 加载分布指标并渲染饼图 */
async function loadDistribution() {
  try {
    const { data } = await getDashboardDistribution()
    distribution.value = data ?? null
    renderStatusPie()
    renderCategoryPie()
  } catch (e) {
    logger.error('[RuleDashboard] 加载分布指标失败', e)
    ElMessage.error('加载分布指标失败')
  }
}

/** 加载 Top 规则列表 */
async function loadTopRules() {
  try {
    const [triggered, slowest] = await Promise.all([
      getDashboardTopRules('triggered' as DashboardTopType, 10),
      getDashboardTopRules('slowest' as DashboardTopType, 10),
    ])
    topTriggered.value = triggered.data ?? []
    topSlowest.value = slowest.data ?? []
  } catch (e) {
    logger.error('[RuleDashboard] 加载 Top 规则失败', e)
    ElMessage.error('加载 Top 规则失败')
  }
}

/** 加载实时指标 */
async function loadRealtime() {
  try {
    const { data } = await getDashboardRealtime()
    realtime.value = data ?? null
  } catch (e) {
    logger.error('[RuleDashboard] 加载实时指标失败', e)
  }
}

/** 加载全部数据 */
async function loadAll() {
  loading.value = true
  await Promise.all([
    loadOverview(),
    loadTrend(),
    loadDistribution(),
    loadTopRules(),
    loadRealtime(),
  ])
  loading.value = false
  lastUpdated.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

/** 手动刷新 */
async function handleRefresh() {
  await loadAll()
  ElMessage.success('刷新成功')
}

/** 时间范围切换 */
function handleTimeRangeChange() {
  loadTrend()
}

// ==================== 图表渲染 ====================

/** 渲染触发次数趋势图（评估/触发/错误 三线） */
function renderTriggerTrend() {
  const t = trend.value
  if (!t || t.timeLabels.length === 0) return
  setTriggerTrendOption({
    title: { text: '触发次数趋势', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    legend: { data: ['评估次数', '触发次数', '错误次数'], bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '12%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: t.timeLabels },
    yAxis: { type: 'value', name: '次数' },
    series: [
      {
        name: '评估次数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: t.evaluationSeries,
        itemStyle: { color: '#409EFF' },
      },
      {
        name: '触发次数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: t.triggeredSeries,
        itemStyle: { color: '#67C23A' },
      },
      {
        name: '错误次数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: t.errorSeries,
        itemStyle: { color: '#F56C6C' },
      },
    ],
  })
}

/** 渲染错误率 & P99 耗时趋势图（双 Y 轴） */
function renderErrorTrend() {
  const t = trend.value
  if (!t || t.timeLabels.length === 0) return
  setErrorTrendOption({
    title: { text: '错误率 & P99 耗时趋势', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    legend: { data: ['错误率', 'P99 耗时(ms)'], bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '12%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: t.timeLabels },
    yAxis: [
      {
        type: 'value',
        name: '错误率',
        axisLabel: { formatter: (v: number) => (v * 100).toFixed(1) + '%' },
      },
      {
        type: 'value',
        name: '耗时(ms)',
        axisLabel: { formatter: (v: number) => v + 'ms' },
      },
    ],
    series: [
      {
        name: '错误率',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: t.errorRateSeries,
        itemStyle: { color: '#F56C6C' },
      },
      {
        name: 'P99 耗时(ms)',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        yAxisIndex: 1,
        data: t.p99ElapsedSeries,
        itemStyle: { color: '#E6A23C' },
      },
    ],
  })
}

/** 状态中文映射 */
const statusLabels: Record<string, string> = {
  DRAFT: '草稿',
  REVIEW: '审核中',
  PUBLISHED: '已发布',
  DISABLED: '已停用',
  ARCHIVED: '已归档',
}

/** 渲染规则状态分布饼图 */
function renderStatusPie() {
  const d = distribution.value
  if (!d) return
  const items = (d.statusPie ?? []).map((it) => ({ name: statusLabels[it.name] || it.name, value: it.value }))
  setStatusPieOption({
    title: { text: '规则状态分布', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: false, position: 'center' },
        emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
        labelLine: { show: false },
        data: items,
      },
    ],
  })
}

/** 渲染规则类别分布饼图 */
function renderCategoryPie() {
  const d = distribution.value
  if (!d) return
  const items = (d.categoryPie ?? []).map((it) => ({ name: it.name || '未分类', value: it.value }))
  setCategoryPieOption({
    title: { text: '规则类别分布', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: false, position: 'center' },
        emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
        labelLine: { show: false },
        data: items,
      },
    ],
  })
}

// ==================== 表格工具方法 ====================

/** 格式化百分比（0~1 → xx.xx%） */
function pct(v?: number): string {
  if (v === undefined || v === null) return '-'
  return (v * 100).toFixed(2) + '%'
}

/** 格式化耗时（毫秒） */
function ms(v?: number): string {
  if (v === undefined || v === null) return '-'
  return Math.round(v) + ' ms'
}

/** 严重度标签类型 */
function severityType(severity?: string): 'danger' | 'warning' | 'info' {
  if (severity === 'RED') return 'danger'
  if (severity === 'YELLOW') return 'warning'
  return 'info'
}

/** 严重度中文标签 */
function severityLabel(severity?: string): string {
  if (severity === 'RED') return '红色'
  if (severity === 'YELLOW') return '黄色'
  if (severity === 'NORMAL') return '通知'
  return severity || '-'
}

// ==================== 自动刷新 ====================

/** 启动自动刷新 */
function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(() => {
    loadAll()
  }, REFRESH_INTERVAL)
}

/** 停止自动刷新 */
function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

/** 切换自动刷新（v-model 已更新 autoRefresh.value，直接读取即可） */
function toggleAutoRefresh() {
  if (autoRefresh.value) {
    startPolling()
  } else {
    stopPolling()
  }
}

/** 返回规则引擎管理页 */
function goBack() {
  router.push('/execution/rule-engine')
}

// ==================== 生命周期 ====================

onMounted(async () => {
  await loadAll()
  if (autoRefresh.value) {
    startPolling()
  }
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<template>
  <div class="rule-dashboard">
    <!-- 顶部工具栏 -->
    <div class="dashboard-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" plain @click="goBack">返回</el-button>
        <h2 class="dashboard-title">
          <el-icon><DataLine /></el-icon>
          规则引擎监控大盘
        </h2>
      </div>
      <div class="header-right">
        <el-radio-group v-model="timeRange" size="default" @change="handleTimeRangeChange">
          <el-radio-button
            v-for="opt in timeRangeOptions"
            :key="opt.value"
            :value="opt.value"
            :aria-label="opt.label"
          >
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>
        <el-switch
          v-model="autoRefresh"
          active-text="自动刷新"
          inactive-text=""
          inline-prompt
          @change="toggleAutoRefresh"
        />
        <el-button :icon="Refresh" :loading="loading" type="primary" plain aria-label="手动刷新" @click="handleRefresh">
          刷新
        </el-button>
      </div>
    </div>

    <!-- 指标卡片 -->
    <el-row :gutter="16" class="metric-row" v-loading="loading">
      <el-col :xs="24" :sm="12" :md="8" :lg="4" :xl="4">
        <el-card shadow="hover" class="metric-card metric-card--blue">
          <div class="metric-icon"><el-icon><Odometer /></el-icon></div>
          <div class="metric-body">
            <div class="metric-label">规则总数 / 启用</div>
            <div class="metric-value">
              {{ overview?.totalRules ?? 0 }}
              <span class="metric-sub">/ {{ overview?.enabledRules ?? 0 }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8" :lg="4" :xl="4">
        <el-card shadow="hover" class="metric-card metric-card--green">
          <div class="metric-icon"><el-icon><TrendCharts /></el-icon></div>
          <div class="metric-body">
            <div class="metric-label">今日触发率</div>
            <div class="metric-value">{{ triggerRatePct }}</div>
            <div class="metric-foot">触发 {{ overview?.todayTriggered ?? 0 }} / 评估 {{ overview?.todayEvaluations ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8" :lg="4" :xl="4">
        <el-card shadow="hover" class="metric-card metric-card--red">
          <div class="metric-icon"><el-icon><Warning /></el-icon></div>
          <div class="metric-body">
            <div class="metric-label">今日错误率</div>
            <div class="metric-value">{{ errorRatePct }}</div>
            <div class="metric-foot">错误 {{ overview?.todayErrors ?? 0 }} 次</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8" :lg="4" :xl="4">
        <el-card shadow="hover" class="metric-card metric-card--orange">
          <div class="metric-icon"><el-icon><Timer /></el-icon></div>
          <div class="metric-body">
            <div class="metric-label">P99 耗时</div>
            <div class="metric-value">{{ p99Ms }}<span class="metric-unit"> ms</span></div>
            <div class="metric-foot">P50 {{ Math.round(overview?.p50ElapsedMs ?? 0) }} / 平均 {{ Math.round(overview?.avgElapsedMs ?? 0) }} ms</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8" :lg="4" :xl="4">
        <el-card shadow="hover" class="metric-card metric-card--purple">
          <div class="metric-icon"><el-icon><Monitor /></el-icon></div>
          <div class="metric-body">
            <div class="metric-label">当前 QPS</div>
            <div class="metric-value">{{ currentQps }}</div>
            <div class="metric-foot">活跃规则 {{ realtime?.activeRules ?? 0 }} / 注册 {{ realtime?.registeredRules ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势图区 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" v-loading="trendLoading">
          <div ref="triggerTrendRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" v-loading="trendLoading">
          <div ref="errorTrendRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 分布饼图区 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" v-loading="loading">
          <div ref="statusPieRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" v-loading="loading">
          <div ref="categoryPieRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Top 规则表格区 -->
    <el-row :gutter="16" class="table-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>最活跃规则 Top 10（按触发次数）</span>
            </div>
          </template>
          <el-table :data="topTriggered" size="small" stripe border style="width: 100%">
            <el-table-column type="index" label="#" width="48" align="center" />
            <el-table-column prop="ruleName" label="规则名称" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <span>{{ row.ruleName }}</span>
                <div class="rule-code">{{ row.ruleCode }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="category" label="类别" width="100" align="center" />
            <el-table-column prop="evaluations" label="评估次数" width="90" align="right" />
            <el-table-column prop="triggered" label="触发次数" width="90" align="right" />
            <el-table-column label="触发率" width="90" align="right">
              <template #default="{ row }">{{ pct(row.triggerRate) }}</template>
            </el-table-column>
            <el-table-column label="错误率" width="90" align="right">
              <template #default="{ row }">
                <el-tag :type="row.errorRate > 0.05 ? 'danger' : 'success'" size="small">
                  {{ pct(row.errorRate) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="严重度" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="severityType(row.defaultSeverity)" size="small">
                  {{ severityLabel(row.defaultSeverity) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>最慢规则 Top 10（按平均耗时）</span>
            </div>
          </template>
          <el-table :data="topSlowest" size="small" stripe border style="width: 100%">
            <el-table-column type="index" label="#" width="48" align="center" />
            <el-table-column prop="ruleName" label="规则名称" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <span>{{ row.ruleName }}</span>
                <div class="rule-code">{{ row.ruleCode }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="category" label="类别" width="100" align="center" />
            <el-table-column label="平均耗时" width="100" align="right">
              <template #default="{ row }">
                <el-tag :type="row.avgElapsedMs > 1000 ? 'danger' : row.avgElapsedMs > 300 ? 'warning' : 'success'" size="small">
                  {{ ms(row.avgElapsedMs) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="P99 耗时" width="100" align="right">
              <template #default="{ row }">{{ ms(row.p99ElapsedMs) }}</template>
            </el-table-column>
            <el-table-column prop="evaluations" label="评估次数" width="90" align="right" />
            <el-table-column prop="errors" label="错误次数" width="90" align="right" />
            <el-table-column prop="owner" label="责任人" width="100" align="center" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 实时指标条 -->
    <el-card shadow="hover" class="realtime-card">
      <div class="realtime-bar">
        <div class="realtime-item">
          <span class="realtime-label">注册规则数</span>
          <span class="realtime-value">{{ realtime?.registeredRules ?? 0 }}</span>
        </div>
        <div class="realtime-item">
          <span class="realtime-label">最近评估规则数</span>
          <span class="realtime-value">{{ realtime?.lastEvaluatedRules ?? 0 }}</span>
        </div>
        <div class="realtime-item">
          <span class="realtime-label">活跃规则数</span>
          <span class="realtime-value">{{ realtime?.activeRules ?? 0 }}</span>
        </div>
        <div class="realtime-item">
          <span class="realtime-label">最近 1 分钟评估</span>
          <span class="realtime-value">{{ realtime?.recentEvaluations ?? 0 }}</span>
        </div>
        <div class="realtime-item">
          <span class="realtime-label">最近 1 分钟触发</span>
          <span class="realtime-value">{{ realtime?.recentTriggered ?? 0 }}</span>
        </div>
        <div class="realtime-item">
          <span class="realtime-label">最近 1 分钟错误</span>
          <span class="realtime-value">{{ realtime?.recentErrors ?? 0 }}</span>
        </div>
        <div class="realtime-item">
          <span class="realtime-label">Trace 队列积压</span>
          <span class="realtime-value" :class="{ 'realtime-warn': (realtime?.traceQueueSize ?? 0) > 1000 }">
            {{ realtime?.traceQueueSize ?? 0 }}
          </span>
        </div>
        <div class="realtime-item">
          <span class="realtime-label">服务器时间</span>
          <span class="realtime-value">
            {{ realtime ? new Date(realtime.timestamp).toLocaleTimeString('zh-CN', { hour12: false }) : '-' }}
          </span>
        </div>
      </div>
    </el-card>

    <!-- 底部状态栏 -->
    <div class="dashboard-footer">
      <span>最后刷新：{{ lastUpdated || '-' }}</span>
      <span v-if="autoRefresh">· 自动刷新中（{{ REFRESH_INTERVAL / 1000 }}s）</span>
    </div>
  </div>
</template>

<style scoped>
.rule-dashboard {
  padding: 16px;
  background-color: #f5f7fa;
  min-height: 100%;
}

/* 顶部工具栏 */
.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
  background-color: #fff;
  padding: 12px 16px;
  border-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dashboard-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.dashboard-title .el-icon {
  color: #409eff;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

/* 指标卡片 */
.metric-row {
  margin-bottom: 16px;
}

.metric-card {
  margin-bottom: 12px;
  position: relative;
  overflow: hidden;
  transition: transform 0.2s;
}

.metric-card:hover {
  transform: translateY(-2px);
}

.metric-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  padding: 16px;
  gap: 12px;
}

.metric-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #fff;
  flex-shrink: 0;
}

.metric-card--blue .metric-icon {
  background: linear-gradient(135deg, #409eff, #66b1ff);
}
.metric-card--green .metric-icon {
  background: linear-gradient(135deg, #67c23a, #85ce61);
}
.metric-card--red .metric-icon {
  background: linear-gradient(135deg, #f56c6c, #f78989);
}
.metric-card--orange .metric-icon {
  background: linear-gradient(135deg, #e6a23c, #ebb563);
}
.metric-card--purple .metric-icon {
  background: linear-gradient(135deg, #9b59b6, #b477cd);
}

.metric-body {
  flex: 1;
  min-width: 0;
}

.metric-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.metric-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.metric-sub {
  font-size: 14px;
  color: #909399;
  font-weight: 400;
}

.metric-unit {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
}

.metric-foot {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 4px;
}

/* 图表区 */
.chart-row {
  margin-bottom: 16px;
}

.chart-container {
  width: 100%;
  height: 320px;
}

/* 表格区 */
.table-row {
  margin-bottom: 16px;
}

.card-header {
  font-weight: 600;
  color: #303133;
}

.rule-code {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

/* 实时指标条 */
.realtime-card {
  margin-bottom: 16px;
}

.realtime-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  align-items: center;
}

.realtime-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.realtime-label {
  font-size: 12px;
  color: #909399;
}

.realtime-value {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.realtime-warn {
  color: #f56c6c;
}

/* 底部状态栏 */
.dashboard-footer {
  text-align: center;
  font-size: 12px;
  color: #909399;
  padding: 8px 0;
}

/* 响应式 */
@media (max-width: 768px) {
  .dashboard-header {
    flex-direction: column;
    align-items: stretch;
  }
  .header-left,
  .header-right {
    width: 100%;
    justify-content: flex-start;
  }
  .chart-container {
    height: 260px;
  }
  .realtime-bar {
    gap: 16px;
  }
  .realtime-value {
    font-size: 16px;
  }
}
</style>
