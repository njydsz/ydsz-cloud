<!--
  @file 经营驾驶舱
  @description 经营驾驶舱页面，聚合 KPI 总览、EVM 健康度分布、维度下钻（事业部/项目类型/客户）、KPI 月度趋势与实时预警，60 秒自动刷新，对接 @/api/execution/cockpit 模块。
  @module views/cockpit
-->
<script setup lang="ts">
/**
 * 经营驾驶舱（批次18 增强）
 *
 * 顶部预警 banner + 核心 KPI 概览 + 维度下钻(事业部/项目类型/客户) + ECharts 可视化 +
 * KPI 月度趋势 + 60 秒自动刷新。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useECharts } from '@/composables/useECharts'
import {
  getCockpitOverview,
  getEvmHealthDistribution,
  getAlertSummary,
  getKpiTrend,
  drillByDept,
  drillByProjectType,
  drillByCustomer,
} from '@/api/execution/cockpit'
import { isHandledError } from '@/utils/error'
import { PC } from '@/constants/permissionCodes'

defineOptions({ name: 'Cockpit' })

const { t, locale } = useI18n()

/** 驾驶舱 KPI 总览数据 */
interface CockpitOverview {
  totalContractAmount: number
  totalRevenue: number
  totalCost: number
  grossProfit: number
  grossMarginPct: number
  activeProjectCount: number
  overdueProjectCount: number
  benchIdleCost: number
  benchIdleCount: number
  utilizationPct: number
  /** 关键提示项 */
  hints?: Array<{ level: string; message: string }>
}

/** 预警事件 */
interface AlertEvent {
  projectName: string
  alertType: string
  severity: 'RED' | 'YELLOW'
  message: string
  createdAt: string
}

/** 查询条件（期间，YYYY-MM） */
const query = ref({ period: new Date().toISOString().slice(0, 7) })
/** KPI 总览数据 */
const overview = ref<CockpitOverview | null>(null)
/** 下钻分析原始数据 */
const drillData = ref<Record<string, unknown>[]>([])
/** 当前下钻维度：dept-事业部 / projectType-项目类型 / customer-客户 */
const drillDimension = ref<'dept' | 'projectType' | 'customer'>('dept')
/** 预警汇总数据 */
const alert = ref<{ redCount: number; yellowCount: number; totalCount: number; topEvent: AlertEvent | null } | null>(null)
/** KPI 月度趋势数据（最近 12 月） */
const trend = ref<{ periods: string[]; contractAmountSeries: number[]; confirmedRevenueSeries: number[]; totalCostSeries: number[]; grossProfitSeries: number[]; grossMarginPctSeries: number[] } | null>(null)
/** 最后一次刷新时间 */
const lastUpdated = ref('')
/** H15.2 修复：整体加载状态，控制 KPI/图表骨架与按钮 loading */
const loading = ref(false)

// 自动刷新
let pollTimer: number | null = null
/** 是否启用自动刷新 */
const autoRefresh = ref(true)
/** 自动刷新间隔（毫秒） */
const REFRESH_INTERVAL = 60_000

// ========== ECharts: 下钻分析 ==========
/** 下钻分析图表容器 ref */
const chartRef = ref<HTMLDivElement | null>(null)
const { setOption: setDrillOption } = useECharts(chartRef)

// ========== ECharts: 健康度饼图 ==========
/** EVM 健康度饼图容器 ref */
const healthRef = ref<HTMLDivElement | null>(null)
const { setOption: setHealthOption } = useECharts(healthRef)

// ========== ECharts: KPI 趋势 ==========
/** KPI 趋势图表容器 ref */
const trendRef = ref<HTMLDivElement | null>(null)
const { setOption: setTrendOption } = useECharts(trendRef)

/** 加载 KPI 总览数据，失败静默处理 */
async function loadOverview() {
  try {
    const { data } = await getCockpitOverview(query.value.period)
    overview.value = data ?? {}
  } catch (e) {
    if (!isHandledError(e)) {
      ElMessage.error(t('cockpit.messages.overviewLoadFailed'))
    }
  }
}

/** 加载 EVM 健康度分布并渲染饼图，失败静默处理 */
async function loadHealth() {
  try {
    const { data } = await getEvmHealthDistribution(query.value.period)
    const d = data
    setHealthOption({
      title: { text: t('cockpit.health.title'), left: 'center' },
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [
        {
          name: t('cockpit.health.seriesName'),
          type: 'pie',
          radius: ['40%', '70%'],
          data: [
            { name: t('cockpit.health.normal'), value: d?.NORMAL ?? 0, itemStyle: { color: '#67c23a' } },
            { name: t('cockpit.health.yellow'), value: d?.YELLOW ?? 0, itemStyle: { color: '#e6a23c' } },
            { name: t('cockpit.health.red'), value: d?.RED ?? 0, itemStyle: { color: '#f56c6c' } },
          ],
        },
      ],
    })
  } catch (e) {
    if (!isHandledError(e)) {
      ElMessage.error(t('cockpit.messages.healthLoadFailed'))
    }
  }
}

/** 按当前下钻维度拉取数据并渲染图表，失败静默处理 */
async function loadDrill() {
  try {
    let res: { data?: Array<Record<string, unknown>> } | undefined
    if (drillDimension.value === 'dept') res = await drillByDept(query.value.period)
    else if (drillDimension.value === 'projectType') res = await drillByProjectType(query.value.period)
    else res = await drillByCustomer(query.value.period)
    drillData.value = res?.data || []
    renderDrillChart()
  } catch (e) {
    if (!isHandledError(e)) {
      ElMessage.error(t('cockpit.messages.drillLoadFailed'))
    }
  }
}

/** 渲染下钻分析柱状图（收入/成本/毛利对比） */
function renderDrillChart() {
  const rows = drillData.value
  setDrillOption({
    title: { text: t('cockpit.drill.title'), left: 'center' },
    tooltip: { trigger: 'axis' },
    legend: { data: [t('cockpit.drill.legend.revenue'), t('cockpit.drill.legend.cost'), t('cockpit.drill.legend.grossProfit')], top: 30 },
    grid: { top: 80, left: 60, right: 40, bottom: 40 },
    xAxis: {
      type: 'category',
      data: rows.map((d) => String(d.name || d.dimension || '-')),
    },
    yAxis: { type: 'value' },
    series: [
      { name: t('cockpit.drill.legend.revenue'), type: 'bar', data: rows.map((d) => Number(d.revenue || 0)), itemStyle: { color: '#409eff' } },
      { name: t('cockpit.drill.legend.cost'), type: 'bar', data: rows.map((d) => Number(d.cost || 0)), itemStyle: { color: '#909399' } },
      { name: t('cockpit.drill.legend.grossProfit'), type: 'bar', data: rows.map((d) => Number(d.grossProfit || 0)), itemStyle: { color: '#67c23a' } },
    ],
  })
}

/** 加载预警汇总数据，失败静默处理 */
async function loadAlert() {
  try {
    const { data } = await getAlertSummary(query.value.period)
    if (!data) {
      alert.value = null
      return
    }
    alert.value = {
      redCount: data.redCount ?? 0,
      yellowCount: data.yellowCount ?? 0,
      totalCount: data.totalCount ?? 0,
      topEvent: data.topEvent ?? null,
    }
  } catch (e) {
    if (!isHandledError(e)) {
      ElMessage.error(t('cockpit.messages.alertLoadFailed'))
    }
  }
}

/** 加载最近 12 月 KPI 趋势数据并渲染图表，失败静默处理 */
async function loadTrend() {
  try {
    const { data } = await getKpiTrend(12)
    if (!data) {
      trend.value = null
      return
    }
    trend.value = {
      periods: data.periods || [],
      contractAmountSeries: data.contractAmountSeries || [],
      confirmedRevenueSeries: data.confirmedRevenueSeries || [],
      totalCostSeries: data.totalCostSeries || [],
      grossProfitSeries: data.grossProfitSeries || [],
      grossMarginPctSeries: data.grossMarginPctSeries || [],
    }
    renderTrendChart()
  } catch (e) {
    if (!isHandledError(e)) {
      ElMessage.error(t('cockpit.messages.trendLoadFailed'))
    }
  }
}

/** 渲染 KPI 月度趋势图（合同/收入/成本柱图 + 毛利率折线） */
function renderTrendChart() {
  const td = trend.value
  if (!td) return
  setTrendOption({
    tooltip: { trigger: 'axis' },
    legend: { data: [t('cockpit.trend.legend.contract'), t('cockpit.trend.legend.revenue'), t('cockpit.trend.legend.cost')], top: 0 },
    grid: { left: 50, right: 50, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: td.periods },
    yAxis: [
      { type: 'value', name: t('cockpit.trend.yAxisAmount') },
      { type: 'value', name: t('cockpit.trend.yAxisGrossMarginPct'), position: 'right', min: 0, max: 100 },
    ],
    series: [
      { name: t('cockpit.trend.legend.contract'), type: 'bar', data: td.contractAmountSeries, itemStyle: { color: '#409eff' } },
      { name: t('cockpit.trend.legend.revenue'), type: 'bar', data: td.confirmedRevenueSeries, itemStyle: { color: '#67c23a' } },
      { name: t('cockpit.trend.legend.cost'), type: 'bar', data: td.totalCostSeries, itemStyle: { color: '#909399' } },
      {
        name: t('cockpit.trend.legend.grossMargin'),
        type: 'line',
        yAxisIndex: 1,
        data: td.grossMarginPctSeries,
        smooth: true,
        lineStyle: { type: 'dashed' },
        itemStyle: { color: '#f56c6c' },
      },
    ],
  })
}

/**
 * 并发刷新所有驾驶舱数据并更新最后刷新时间
 *
 * H15.2 修复：
 *  - 用 Promise.allSettled 替换 Promise.all，任一接口失败不影响其他接口的独立错误提示，
 *    避免单个 load 函数未捕获 reject 时中断整体刷新；
 *  - 新增 loading 状态，KPI/图表区域展示骨架反馈，refresh 按钮置 loading。
 */
async function refresh() {
  loading.value = true
  try {
    await Promise.allSettled([loadOverview(), loadHealth(), loadDrill(), loadAlert(), loadTrend()])
    lastUpdated.value = new Date().toLocaleTimeString(locale.value)
  } finally {
    loading.value = false
  }
}

/** 启动 60s 轮询定时器 */
function startPolling() {
  stopPolling()
  if (!autoRefresh.value) return
  pollTimer = window.setInterval(() => refresh(), REFRESH_INTERVAL)
}

/** 停止轮询定时器 */
function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

/** 切换自动刷新开关 */
function toggleAutoRefresh() {
  autoRefresh.value = !autoRefresh.value
  if (autoRefresh.value) startPolling()
  else stopPolling()
}

// ========== 计算属性 ==========
/** 预警 banner 颜色基调：error-红色预警 / warning-黄色预警 / success-无预警 / info-未加载 */
const alertTone = computed<'success' | 'warning' | 'info' | 'error'>(() => {
  if (!alert.value) return 'info'
  if (alert.value.redCount > 0) return 'error'
  if (alert.value.yellowCount > 0) return 'warning'
  return 'success'
})

/** 预警 banner 文案，包含红/黄数量与 TOP 事件标题 */
const alertMessage = computed(() => {
  if (!alert.value || alert.value.totalCount === 0) return t('cockpit.alert.noAlert')
  const a = alert.value
  const parts: string[] = []
  if (a.redCount > 0) parts.push(t('cockpit.alert.redCount', { count: a.redCount }))
  if (a.yellowCount > 0) parts.push(t('cockpit.alert.yellowCount', { count: a.yellowCount }))
  const base = t('cockpit.alert.hasAlert', { parts: parts.join(t('cockpit.alert.separator')) })
  return a.topEvent ? base + t('cockpit.alert.suffix', { title: a.topEvent.title }) : base
})

/**
 * 格式化金额为人民币千分位字符串
 * @param v 金额数值
 * @returns 带人民币符号的格式化字符串，空值返回 '-'
 */
function fmtMoney(v: unknown) {
  if (v === null || v === undefined) return '-'
  return `¥${Number(v).toLocaleString()}`
}

/**
 * 格式化比例为百分比字符串（保留 1 位小数）
 * @param v 比例值（0-1）
 * @returns 百分比字符串
 */
function fmtPct(v: unknown) {
  if (v === null || v === undefined) return '0.0%'
  return `${(Number(v) * 100).toFixed(1)}%`
}

onMounted(() => {
  refresh()
  startPolling()
})
onBeforeUnmount(() => stopPolling())
</script>

<template>
  <div class="cockpit-page">
    <!-- 顶部：自动刷新 + 最后更新时间 + 预警 banner -->
    <el-card shadow="never" class="header-card">
      <div class="header-row">
        <div>
          <div class="page-title">{{ t('cockpit.title') }}</div>
          <div class="page-subtitle">{{ t('cockpit.subtitle') }}</div>
        </div>
        <div class="header-right">
          <el-tag v-if="lastUpdated" type="info" effect="plain" size="small">
            {{ t('cockpit.lastUpdated', { time: lastUpdated }) }}
          </el-tag>
          <el-switch
            v-model="autoRefresh"
            inline-prompt
            :active-text="t('cockpit.autoRefresh.auto')"
            :inactive-text="t('cockpit.autoRefresh.manual')"
            @change="toggleAutoRefresh"
          />
          <el-button :icon="'Refresh'" :loading="loading" @click="refresh">{{ t('cockpit.autoRefresh.refreshNow') }}</el-button>
        </div>
      </div>
    </el-card>

    <!-- 预警 banner -->
    <el-alert
      v-if="alert"
      class="alert-banner"
      :type="alertTone"
      :title="alertMessage"
      :closable="false"
      show-icon
    >
      <template #default>
        <div class="alert-content">
          <span>{{ alertMessage }}</span>
          <el-space v-if="alert.topEvent" :size="6" class="alert-extra">
            <el-tag :type="alert.topEvent.severity === 'RED' ? 'danger' : 'warning'" size="small" effect="dark">
              {{ alert.topEvent.severity }}
            </el-tag>
            <span class="alert-title">{{ alert.topEvent.title }}</span>
            <span class="alert-desc">{{ alert.topEvent.description }}</span>
          </el-space>
        </div>
      </template>
    </el-alert>

    <!-- 查询条件 -->
    <el-card shadow="never" class="query-card">
      <el-form inline>
        <el-form-item :label="t('cockpit.query.periodLabel')">
          <el-input v-model="query.period" :placeholder="t('cockpit.query.periodPlaceholder')" style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button v-permission="[PC.COCKPIT_OVERVIEW_VIEW]" type="primary" :icon="'Refresh'" :loading="loading" @click="refresh">{{ t('cockpit.query.refresh') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- KPI 卡片 -->
    <el-row :gutter="16" v-loading="loading" class="kpi-row">
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-title">{{ t('cockpit.kpi.totalContractAmount') }}</div>
          <div class="kpi-value money">{{ fmtMoney(overview?.totalContractAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-title">{{ t('cockpit.kpi.confirmedRevenue') }}</div>
          <div class="kpi-value money">{{ fmtMoney(overview?.confirmedRevenue) }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-title">{{ t('cockpit.kpi.totalCost') }}</div>
          <div class="kpi-value">{{ fmtMoney(overview?.totalCost) }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="kpi-card highlight">
          <div class="kpi-title">{{ t('cockpit.kpi.grossProfit') }}</div>
          <div class="kpi-value money">{{ fmtMoney(overview?.grossProfit) }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="kpi-card highlight">
          <div class="kpi-title">{{ t('cockpit.kpi.grossMargin') }}</div>
          <div class="kpi-value">{{ fmtPct(overview?.grossMargin) }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-title">{{ t('cockpit.kpi.activeProjects') }}</div>
          <div class="kpi-value">{{ overview?.activeProjects ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 下钻分析 + EVM 健康度 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :md="16">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="chart-header">
              <span>{{ t('cockpit.drill.title') }}</span>
              <el-radio-group v-model="drillDimension" size="small" @change="loadDrill">
                <el-radio-button value="dept">{{ t('cockpit.drill.dimension.dept') }}</el-radio-button>
                <el-radio-button value="projectType">{{ t('cockpit.drill.dimension.projectType') }}</el-radio-button>
                <el-radio-button value="customer">{{ t('cockpit.drill.dimension.customer') }}</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div v-loading="loading" ref="chartRef" class="chart-area" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="chart-card">
          <div v-loading="loading" ref="healthRef" class="chart-area" />
        </el-card>
      </el-col>
    </el-row>

    <!-- KPI 趋势 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="24">
        <el-card shadow="never" :header="t('cockpit.trend.title')">
          <div v-loading="loading" ref="trendRef" class="chart-area" style="height: 320px" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 关键提示 + 快捷入口 -->
    <el-card shadow="never" class="extra-card">
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <h4>{{ t('cockpit.hints.title') }}</h4>
          <ul class="hint-list">
            <li v-for="(h, i) in overview?.hints || []" :key="i">
              <el-tag :type="h.level === 'RED' ? 'danger' : h.level === 'YELLOW' ? 'warning' : 'info'" size="small">
                {{ h.level }}
              </el-tag>
              {{ h.message }}
            </li>
            <li v-if="!(overview?.hints || []).length" class="empty">{{ t('cockpit.hints.empty') }}</li>
          </ul>
        </el-col>
        <el-col :xs="24" :md="12">
          <h4>{{ t('cockpit.shortcuts.title') }}</h4>
          <el-space wrap>
            <el-button :icon="'TrendCharts'" @click="$router.push('/report/executive')">{{ t('cockpit.shortcuts.executive') }}</el-button>
            <el-button :icon="'WarningFilled'" @click="$router.push('/execution/risk')">{{ t('cockpit.shortcuts.risk') }}</el-button>
            <el-button :icon="'Document'" @click="$router.push('/report')">{{ t('cockpit.shortcuts.profitReport') }}</el-button>
            <el-button :icon="'Coin'" @click="$router.push('/execution/invoice')">{{ t('cockpit.shortcuts.invoice') }}</el-button>
            <el-button :icon="'Money'" @click="$router.push('/execution/payment')">{{ t('cockpit.shortcuts.payment') }}</el-button>
          </el-space>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.cockpit-page {
  padding: 16px;
  .header-card { margin-bottom: 12px; }
  .header-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    .page-title { font-size: 18px; font-weight: 600; }
    .page-subtitle { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 4px; }
    .header-right { display: flex; gap: 12px; align-items: center; }
  }
  .alert-banner { margin-bottom: 12px; }
  .alert-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
    .alert-extra { font-size: 12px; }
    .alert-title { font-weight: 600; }
    .alert-desc { color: var(--el-text-color-secondary); }
  }
  .query-card { margin-bottom: 16px; }
  .kpi-row { margin-bottom: 16px; }
  .kpi-card {
    text-align: center;
    /* H18.3 修复：KPI 标题改用 regular 色（#606266），满足 WCAG AA 对比度 4.5:1，原 secondary(#909399) 仅 3.5:1 */
    .kpi-title { font-size: 12px; color: var(--el-text-color-regular); }
    .kpi-value { font-size: 22px; font-weight: 600; margin-top: 8px;
      &.money { color: var(--el-color-primary); }
    }
    &.highlight .kpi-value { color: var(--el-color-success); }
  }
  .chart-row { margin-bottom: 16px; }
  .chart-card {
    .chart-header { display: flex; justify-content: space-between; align-items: center; }
    .chart-area { width: 100%; height: 320px; }
  }
  .extra-card {
    .hint-list { list-style: none; padding: 0; margin: 0;
      li { padding: 8px 0; border-bottom: 1px solid var(--el-border-color-lighter); display: flex; align-items: center; gap: 8px;
        &.empty { color: var(--el-text-color-placeholder); border: none; }
      }
    }
  }
}
</style>
