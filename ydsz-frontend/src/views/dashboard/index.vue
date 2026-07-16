﻿<!--
  @file 首页仪表盘
  @description 系统首页仪表盘，基于 Cockpit 总览 API 与 ECharts 可视化展示活跃项目、本月合同/收入/毛利、EVM 健康度分布、近 6 月趋势与预警 TOP 5，对接 @/api/report/cockpit 与 @/api/alert 模块。
  @module views/dashboard
-->
<script setup lang="ts">
/**
 * 仪表盘
 *
 * 接入 Cockpit 总览 API + ECharts 可视化 (基于 useECharts composable)
 * KPI: 活跃项目数、本月合同额、已确认收入、本月毛利、EVM 健康度、利用率均值
 * 图表: 项目健康度饼图、收入趋势折线图、EVM 状态分布柱图、预警 TOP 5
 *
 * 批次 21 / P2 - 迁移原始 echarts.init() 到 useECharts composable
 */
import { ref, onMounted, computed, nextTick, watch } from 'vue'
import type { EChartsOption } from '@/utils/echarts'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { formatDate } from '@/utils/format'
import { getCockpitOverview, getKpiTrend } from '@/api/report/cockpit'
import type { KpiTrendVO } from '@/api/report/cockpit'
import { getCockpitAlertTopN } from '@/api/alert'
import { useECharts } from '@/composables/useECharts'
import { chartColors } from '@/utils/chart-theme'
import { isHandledError } from '@/utils/error'
import SkeletonCard from '@/components/common/SkeletonCard.vue'
import SkeletonTable from '@/components/common/SkeletonTable.vue'
import { useDashboardLayout, type WidgetConfig } from '@/composables/useDashboardLayout'
import { onKeyActivate } from '@/composables/useKeyboardA11y'

const { t } = useI18n()
const router = useRouter()

// ===== Dashboard 拖拽布局 =====
const dashboardWidgets: WidgetConfig[] = [
  { id: 'health', title: t('dashboard.charts.healthTitle'), defaultSpan: 8, defaultVisible: true, disableHide: true },
  { id: 'trend', title: t('dashboard.charts.trendTitle'), defaultSpan: 10, defaultVisible: true },
  { id: 'keyMetrics', title: t('dashboard.keyMetrics.title'), defaultSpan: 6, defaultVisible: true },
  { id: 'evm', title: t('dashboard.charts.evmTitle'), defaultSpan: 10, defaultVisible: true },
  { id: 'alertTopN', title: t('dashboard.charts.alertTitle'), defaultSpan: 14, defaultVisible: true },
]
const {
  widgets: layoutWidgets,
  isCustomizing: isDashboardCustomizing,
  toggleVisible: toggleWidgetVisible,
  resetLayout: resetDashboardLayout,
  toggleCustomizing: toggleDashboardCustomizing,
} = useDashboardLayout('main', dashboardWidgets)

// ===== 数据状态 =====
/** Cockpit KPI 数据结构 */
interface CockpitKpi {
  activeProjectCount: number
  totalRevenue: number
  recognizedRevenue: number
  totalGrossProfit: number
  grossMargin: number
  evmRedCount: number
  evmYellowCount: number
  evmGreenCount: number
  avgUtilization: number
  benchIdleCost: number
  normalProjects: number
  yellowProjects: number
  redProjects: number
}

/** KPI 区域加载状态 */
const kpiLoading = ref(false)
/** 趋势图区域加载状态 */
const trendLoading = ref(false)
/** 预警区域加载状态 */
const alertLoading = ref(false)
/** KPI 区域加载失败 */
const kpiError = ref(false)
/** 趋势图区域加载失败 */
const trendError = ref(false)
/** 预警区域加载失败 */
const alertError = ref(false)
/** 整体 loading（仅用于刷新按钮状态） */
const loading = computed(() => kpiLoading.value || trendLoading.value || alertLoading.value)
/** KPI 数据 */
const kpi = ref<CockpitKpi | null>(null)
/** 查询期间（YYYY-MM） */
const period = ref(new Date().toISOString().slice(0, 7))
/** 近 6 月收入/毛利趋势数据（来自后端 kpi-trend 接口） */
const trendData = ref<KpiTrendVO | null>(null)

// ===== 图表容器 ref =====
/** 项目健康度饼图容器 */
const healthRef = ref<HTMLDivElement | null>(null)
/** 收入趋势折线图容器 */
const trendRef = ref<HTMLDivElement | null>(null)
/** EVM 状态柱图容器 */
const evmRef = ref<HTMLDivElement | null>(null)
/** 预警 TOP 5 图表容器 */
const alertTopNRef = ref<HTMLDivElement | null>(null)

// ===== useECharts 实例化 =====
const { setOption: setHealthOption } = useECharts(healthRef)
const { setOption: setTrendOption } = useECharts(trendRef)
const { setOption: setEvmOption } = useECharts(evmRef)
const { setOption: setAlertTopNOption } = useECharts(alertTopNRef)

// ===== 格式化辅助 =====
const userStore = useUserStore()
/** 根据当前时间返回问候语 */
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 12) return t('dashboard.welcome.greetingMorning')
  if (h < 18) return t('dashboard.welcome.greetingAfternoon')
  return t('dashboard.welcome.greetingEvening')
})
/** 元转万元（保留 1 位小数） */
const yuanToWan = (v: number | undefined) => {
  const n = Number(v ?? 0)
  return (n / 10000).toFixed(1)
}
/** 比例转百分比字符串（保留 1 位小数） */
const fmtPercent = (v: number | undefined) => {
  if (v === undefined || v === null) return '0.0%'
  return `${(Number(v) * 100).toFixed(1)}%`
}

// ===== KPI 卡片交互（批次 30-3） =====
/** sparkline 宽度 */
const SPARKLINE_W = 100
/** sparkline 高度 */
const SPARKLINE_H = 28

/**
 * 将数值数组转换为 SVG path d 属性字符串
 * - 自动归一化到 [0, SPARKLINE_H] 区间
 * - 空数组或单点返回空字符串
 * @param data 数值序列
 * @returns SVG path d 属性字符串
 */
function buildSparklinePath(data: number[] | undefined): string {
  if (!data || data.length === 0) return ''
  const arr = data.filter((v) => typeof v === 'number' && !isNaN(v))
  if (arr.length === 0) return ''
  const min = Math.min(...arr)
  const max = Math.max(...arr)
  const range = max - min || 1
  const stepX = arr.length > 1 ? SPARKLINE_W / (arr.length - 1) : 0
  return arr
    .map((v, i) => {
      const x = i * stepX
      const y = SPARKLINE_H - ((v - min) / range) * SPARKLINE_H
      return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
}

/**
 * 点击 KPI 卡片跳转到下钻路由
 * @param metric KPI 卡片数据
 */
function handleMetricClick(metric: DashboardMetric): void {
  if (!metric.drillRoute) return
  router.push(metric.drillRoute).catch(() => { /* 路由跳转失败已被全局拦截 */ })
}

// ===== KPI 列表 (动态计算) =====
/**
 * KPI 卡片数据结构
 *
 * 批次 30-3 增强：
 *  - mtdGrowth: 环比增长率（小数 0.15 = +15%），来自 KpiTrendVO
 *  - sparkline: 迷你趋势数据序列，用于在卡片底部渲染 SVG sparkline
 *  - drillRoute: 点击卡片跳转的路由路径
 */
interface DashboardMetric {
  title: string
  value: string
  unit: string
  color: string
  icon: string
  /** 可选副标题（如毛利率） */
  sub?: string
  /** 环比增长率（0.15 表示 +15%），undefined 表示无数据 */
  mtdGrowth?: number
  /** 迷你趋势数据（最近 6 期），用于渲染 sparkline */
  sparkline?: number[]
  /** 点击卡片跳转的路由 */
  drillRoute?: string
}
/** 顶部 4 个 KPI 卡片数据 */
const metrics = computed<DashboardMetric[]>(() => [
  {
    title: t('dashboard.metrics.activeProjects'),
    value: String(kpi.value?.activeProjectCount ?? 0),
    unit: t('dashboard.unit.count'),
    color: chartColors.primary,
    icon: 'Document',
    sparkline: trendData.value?.activeProjectsSeries,
    drillRoute: '/execution/wbs-task',
  },
  {
    title: t('dashboard.metrics.monthlyContractAmount'),
    value: yuanToWan(kpi.value?.totalRevenue),
    unit: t('dashboard.unit.tenThousand'),
    color: chartColors.success,
    icon: 'Money',
    mtdGrowth: trendData.value?.contractMtdGrowth,
    sparkline: trendData.value?.contractAmountSeries,
    drillRoute: '/cockpit',
  },
  {
    title: t('dashboard.metrics.recognizedRevenue'),
    value: yuanToWan(kpi.value?.recognizedRevenue),
    unit: t('dashboard.unit.tenThousand'),
    color: chartColors.purple,
    icon: 'TrendCharts',
    mtdGrowth: trendData.value?.revenueMtdGrowth,
    sparkline: trendData.value?.confirmedRevenueSeries,
    drillRoute: '/finance/profit',
  },
  {
    title: t('dashboard.metrics.monthlyGrossProfit'),
    value: yuanToWan(kpi.value?.totalGrossProfit),
    unit: t('dashboard.unit.tenThousand'),
    color: chartColors.orange,
    sub: t('dashboard.metrics.grossMargin', { rate: fmtPercent(kpi.value?.grossMargin) }),
    icon: 'DataAnalysis',
    mtdGrowth: trendData.value?.profitMtdGrowth,
    sparkline: trendData.value?.grossProfitSeries,
    drillRoute: '/finance/profit',
  },
])

// ===== 图表 option 工厂 =====
/** 项目健康度饼图 option */
const healthOption = computed<EChartsOption>(() => ({
  title: { text: t('dashboard.charts.healthTitle'), left: 'center', textStyle: { fontSize: 14 } },
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: 0, left: 'center' },
  series: [
    {
      name: t('dashboard.charts.health'),
      type: 'pie',
      radius: ['38%', '70%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 4, borderColor: chartColors.borderColor, borderWidth: 2 },
      label: { show: true, formatter: '{b}\n{c}' },
      data: [
        { name: t('dashboard.charts.normal'), value: kpi.value?.normalProjects ?? 0, itemStyle: { color: chartColors.success } },
        { name: t('dashboard.charts.yellow'), value: kpi.value?.yellowProjects ?? 0, itemStyle: { color: chartColors.warning } },
        { name: t('dashboard.charts.red'), value: kpi.value?.redProjects ?? 0, itemStyle: { color: chartColors.danger } },
      ],
    },
  ],
}))

/** 近 6 月收入/毛利趋势折线图 option */
const trendOption = computed<EChartsOption>(() => {
  // 优先使用后端返回的周期标签，回退到基于 period 推断的近 6 月
  const months: string[] = []
  if (trendData.value?.periods?.length) {
    months.push(...trendData.value.periods)
  } else {
    const baseDate = new Date(period.value + '-01')
    for (let i = 5; i >= 0; i--) {
      const d = new Date(baseDate.getFullYear(), baseDate.getMonth() - i, 1)
      months.push(t('dashboard.charts.monthSuffix', { n: d.getMonth() + 1 }))
    }
  }
  // 收入/毛利序列来自后端 kpi-trend 接口（已确认收入 / 毛利）
  const revenueSeries = trendData.value?.confirmedRevenueSeries ?? []
  const profitSeries = trendData.value?.grossProfitSeries ?? []
  return {
    title: { text: t('dashboard.charts.trendTitle'), left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    legend: { data: [t('dashboard.charts.revenue'), t('dashboard.charts.profit')], top: 30 },
    grid: { top: 80, left: 50, right: 30, bottom: 30 },
    xAxis: { type: 'category', data: months, boundaryGap: false },
    yAxis: { type: 'value', name: t('dashboard.charts.unitTenThousandYuan') },
    series: [
      {
        name: t('dashboard.charts.revenue'),
        type: 'line',
        smooth: true,
        symbolSize: 8,
        data: revenueSeries,
        itemStyle: { color: chartColors.primary },
        areaStyle: { opacity: 0.15 },
      },
      {
        name: t('dashboard.charts.profit'),
        type: 'line',
        smooth: true,
        symbolSize: 8,
        data: profitSeries,
        itemStyle: { color: chartColors.success },
        areaStyle: { opacity: 0.15 },
      },
    ],
  }
})

/** EVM 健康度柱图 option */
const evmOption = computed<EChartsOption>(() => ({
  title: { text: t('dashboard.charts.evmTitle'), left: 'center', textStyle: { fontSize: 14 } },
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { top: 50, left: 40, right: 20, bottom: 30 },
  xAxis: { type: 'category', data: [t('dashboard.charts.normal'), t('dashboard.charts.yellowAlert'), t('dashboard.charts.redAlert')] },
  yAxis: { type: 'value', name: t('dashboard.charts.projectCount') },
  series: [
    {
      type: 'bar',
      barWidth: '50%',
      data: [
        { value: kpi.value?.evmGreenCount ?? 0, itemStyle: { color: chartColors.success } },
        { value: kpi.value?.evmYellowCount ?? 0, itemStyle: { color: chartColors.warning } },
        { value: kpi.value?.evmRedCount ?? 0, itemStyle: { color: chartColors.danger } },
      ],
      label: { show: true, position: 'top' },
    },
  ],
}))

/** 预警 TOP 5 项目条目结构 */
interface AlertTopNItem {
  projectCode: string
  projectName: string
  alertLevel: 'RED' | 'YELLOW' | 'NORMAL'
  alertCount: number
}
/** 预警 TOP 5 项目列表 */
const alertTopN = ref<AlertTopNItem[]>([])
/** 预警 TOP 5 横向柱图 option */
const alertTopNOption = computed<EChartsOption>(() => ({
  title: { text: t('dashboard.charts.alertTopNTitle'), left: 'center', textStyle: { fontSize: 14 } },
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { top: 50, left: 100, right: 30, bottom: 30 },
  xAxis: { type: 'value', name: t('dashboard.charts.alertCount') },
  yAxis: {
    type: 'category',
    data: alertTopN.value.map((a) => a.projectName || a.projectCode),
  },
  series: [
    {
      type: 'bar',
      barWidth: '60%',
      data: alertTopN.value.map((a) => ({
        value: a.alertCount,
        itemStyle: { color: a.alertLevel === 'RED' ? chartColors.danger : chartColors.warning },
      })),
      label: { show: true, position: 'right' },
    },
  ],
}))

// ===== 数据加载 =====
/** 拉取 Cockpit 总览 KPI 数据（静默请求，不触发全局 loading） */
async function loadOverview() {
  kpiLoading.value = true
  kpiError.value = false
  try {
    const { data } = await getCockpitOverview(period.value)
    kpi.value = data as CockpitKpi
  } catch (e) {
    kpi.value = null
    kpiError.value = true
    if (!isHandledError(e)) {
      ElMessage.error(t('dashboard.messages.loadFailed'))
    }
  } finally {
    kpiLoading.value = false
  }
}

/** 拉取预警 TOP 5 项目列表（静默请求，不触发全局 loading） */
async function loadAlertTopN() {
  alertLoading.value = true
  alertError.value = false
  try {
    const { data } = await getCockpitAlertTopN(period.value, 5)
    alertTopN.value = (data as AlertTopNItem[]) || []
  } catch (e) {
    alertTopN.value = []
    alertError.value = true
    if (!isHandledError(e)) {
      ElMessage.error(t('dashboard.messages.alertLoadFailed'))
    }
  } finally {
    alertLoading.value = false
  }
}

/** 拉取近 6 月收入/毛利趋势数据并更新图表（静默请求，不触发全局 loading） */
async function loadTrendData() {
  trendLoading.value = true
  trendError.value = false
  try {
    const { data } = await getKpiTrend(6)
    trendData.value = data ?? null
  } catch (e) {
    trendData.value = null
    trendError.value = true
    if (!isHandledError(e)) {
      ElMessage.error(t('dashboard.messages.trendLoadFailed'))
    }
  } finally {
    trendLoading.value = false
  }
}

/** 并发刷新所有数据并重绘所有图表（allSettled 确保单个失败不阻塞其他区域） */
async function refreshAll() {
  await Promise.allSettled([loadOverview(), loadAlertTopN(), loadTrendData()])
  await nextTick()
  // useECharts 自动绑定实例, 只需 setOption
  setHealthOption(healthOption.value)
  setTrendOption(trendOption.value)
  setEvmOption(evmOption.value)
  setAlertTopNOption(alertTopNOption.value)
}

// ===== 响应式: kpi 变化时重绘 =====
watch([healthOption, trendOption, evmOption, alertTopNOption], () => {
  setHealthOption(healthOption.value)
  setTrendOption(trendOption.value)
  setEvmOption(evmOption.value)
  setAlertTopNOption(alertTopNOption.value)
})

// ===== 周期切换 =====
/** el-date-picker 禁选未来月份 */
function disabledFutureDate(date: Date): boolean {
  const now = new Date()
  return date.getFullYear() > now.getFullYear() || (date.getFullYear() === now.getFullYear() && date.getMonth() > now.getMonth())
}

onMounted(async () => {
  if (!userStore.userInfo) {
    userStore.fetchUserInfo().catch(() => { /* 已被全局拦截 */ })
  }
  await refreshAll()
})
</script>

<template>
  <div class="dashboard">
    <!-- 欢迎卡片 -->
    <el-card class="welcome-card" shadow="never">
      <div class="welcome-content">
        <div>
          <h2>{{ greeting }},{{ userStore.realName || userStore.username }}!</h2>
          <p>{{ t('dashboard.welcome.text', { time: formatDate(new Date(), 'YYYY-MM-DD HH:mm') }) }}</p>
        </div>
        <el-icon class="welcome-icon" :size="60"><Sunny /></el-icon>
      </div>
    </el-card>

    <!-- 周期切换 + KPI -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-date-picker
          v-model="period"
          type="month"
          :placeholder="t('dashboard.period.placeholder')"
          :format="'YYYY-MM'"
          :value-format="'YYYY-MM'"
          :clearable="false"
          :disabled-date="disabledFutureDate"
          style="width: 180px"
          @change="refreshAll"
        />
        <el-button :loading="loading" @click="refreshAll">{{ t('common.refresh') }}</el-button>
      </div>
      <div class="toolbar-right">
        <el-popover trigger="click" placement="bottom-end" :width="240">
          <template #reference>
            <el-button :icon="'Setting'" circle :aria-label="t('common.dashboardCustomize')" />
          </template>
          <div class="dashboard-layout-settings">
            <div class="dashboard-layout-header">
              <span>{{ t('common.dashboardCustomize') }}</span>
              <el-button link type="primary" size="small" @click="resetDashboardLayout">
                {{ t('common.reset') }}
              </el-button>
            </div>
            <div class="dashboard-layout-list">
              <div
                v-for="w in layoutWidgets"
                :key="w.id"
                class="dashboard-layout-item"
              >
                <el-checkbox
                  :model-value="w.visible"
                  :disabled="w.disableHide"
                  @change="toggleWidgetVisible(w.id)"
                >
                  {{ w.title }}
                </el-checkbox>
              </div>
            </div>
            <div class="dashboard-layout-footer">
              <el-button size="small" @click="toggleDashboardCustomizing">
                {{ isDashboardCustomizing ? t('common.dashboardExitCustomize') : t('common.dashboardEnterCustomize') }}
              </el-button>
            </div>
          </div>
        </el-popover>
      </div>
    </div>

    <el-row :gutter="16" class="metric-row">
      <el-col v-for="m in metrics" :key="m.title" :xs="24" :sm="12" :md="8" :lg="6" :xl="6">
        <el-card
          class="metric-card"
          :class="{ 'metric-card--clickable': !!m.drillRoute }"
          shadow="hover"
          @click="handleMetricClick(m)"
        >
          <SkeletonCard v-if="kpiLoading" :rows="3" />
          <div v-else class="metric-content">
            <div class="metric-icon" :style="{ background: m.color }">
              <el-icon :size="24"><component :is="m.icon" /></el-icon>
            </div>
            <div class="metric-info">
              <p class="metric-title">
                {{ m.title }}
                <!-- 环比箭头（批次 30-3） -->
                <span
                  v-if="m.mtdGrowth !== undefined"
                  class="metric-mtd"
                  :class="m.mtdGrowth >= 0 ? 'is-up' : 'is-down'"
                  :title="t('dashboard.metrics.mtdTooltip')"
                >
                  <el-icon :size="12">
                    <CaretTop v-if="m.mtdGrowth >= 0" />
                    <CaretBottom v-else />
                  </el-icon>
                  {{ m.mtdGrowth >= 0 ? '+' : '' }}{{ (m.mtdGrowth * 100).toFixed(1) }}%
                </span>
              </p>
              <p class="metric-value">
                <span class="value">{{ m.value }}</span>
                <span class="unit">{{ m.unit }}</span>
              </p>
              <p v-if="m.sub" class="metric-sub">{{ m.sub }}</p>
            </div>
          </div>
          <!-- 迷你 sparkline（批次 30-3） -->
          <div v-if="!kpiLoading && buildSparklinePath(m.sparkline)" class="metric-sparkline">
            <svg :width="SPARKLINE_W" :height="SPARKLINE_H" :viewBox="`0 0 ${SPARKLINE_W} ${SPARKLINE_H}`" preserveAspectRatio="none">
              <path :d="buildSparklinePath(m.sparkline)" :stroke="m.color" fill="none" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round" />
            </svg>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第一行图表: 健康度 + 趋势 -->
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="never">
          <div v-if="kpiLoading" class="chart-skeleton"><SkeletonTable :rows="6" /></div>
          <div
            v-else-if="kpiError"
            class="chart-error"
            role="button"
            tabindex="0"
            aria-label="加载失败，点击重试"
            @click="loadOverview"
            @keydown="onKeyActivate(loadOverview)"
          >
            <el-icon :size="32"><WarningFilled /></el-icon>
            <p>{{ t('dashboard.messages.loadFailed') }}</p>
            <el-button text type="primary" @click="loadOverview">{{ t('common.retry') }}</el-button>
          </div>
          <div v-else ref="healthRef" class="chart-area" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="10">
        <el-card shadow="never">
          <div v-if="trendLoading" class="chart-skeleton"><SkeletonTable :rows="6" /></div>
          <div
            v-else-if="trendError"
            class="chart-error"
            role="button"
            tabindex="0"
            aria-label="趋势加载失败，点击重试"
            @click="loadTrendData"
            @keydown="onKeyActivate(loadTrendData)"
          >
            <el-icon :size="32"><WarningFilled /></el-icon>
            <p>{{ t('dashboard.messages.trendLoadFailed') }}</p>
            <el-button text type="primary" @click="loadTrendData">{{ t('common.retry') }}</el-button>
          </div>
          <div v-else ref="trendRef" class="chart-area" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ t('dashboard.keyMetrics.title') }}</span>
            </div>
          </template>
          <el-scrollbar height="280px">
            <div class="kpi-mini">
              <span class="kpi-mini-label">{{ t('dashboard.keyMetrics.grossMargin') }}</span>
              <span class="kpi-mini-value">{{ fmtPercent(kpi?.grossMargin) }}</span>
            </div>
            <div class="kpi-mini">
              <span class="kpi-mini-label">{{ t('dashboard.keyMetrics.avgUtilization') }}</span>
              <span class="kpi-mini-value">{{ fmtPercent(kpi?.avgUtilization) }}</span>
            </div>
            <div class="kpi-mini">
              <span class="kpi-mini-label">{{ t('dashboard.keyMetrics.benchIdleCost') }}</span>
              <span class="kpi-mini-value">{{ yuanToWan(kpi?.benchIdleCost) }} {{ t('dashboard.unit.tenThousand') }}</span>
            </div>
            <div class="kpi-mini">
              <span class="kpi-mini-label">{{ t('dashboard.keyMetrics.evmRedAlert') }}</span>
              <span class="kpi-mini-value danger">{{ kpi?.evmRedCount ?? 0 }}</span>
            </div>
            <div class="kpi-mini">
              <span class="kpi-mini-label">{{ t('dashboard.keyMetrics.evmYellowAlert') }}</span>
              <span class="kpi-mini-value warn">{{ kpi?.evmYellowCount ?? 0 }}</span>
            </div>
          </el-scrollbar>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二行图表: EVM 柱图 + 预警 TOP 5 -->
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :md="10">
        <el-card shadow="never">
          <div v-if="kpiLoading" class="chart-skeleton"><SkeletonTable :rows="6" /></div>
          <div
            v-else-if="kpiError"
            class="chart-error"
            role="button"
            tabindex="0"
            aria-label="加载失败，点击重试"
            @click="loadOverview"
            @keydown="onKeyActivate(loadOverview)"
          >
            <el-icon :size="32"><WarningFilled /></el-icon>
            <p>{{ t('dashboard.messages.loadFailed') }}</p>
            <el-button text type="primary" @click="loadOverview">{{ t('common.retry') }}</el-button>
          </div>
          <div v-else ref="evmRef" class="chart-area" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="14">
        <el-card shadow="never">
          <div v-if="alertLoading" class="chart-skeleton"><SkeletonTable :rows="6" /></div>
          <div
            v-else-if="alertError"
            class="chart-error"
            role="button"
            tabindex="0"
            aria-label="预警加载失败，点击重试"
            @click="loadAlertTopN"
            @keydown="onKeyActivate(loadAlertTopN)"
          >
            <el-icon :size="32"><WarningFilled /></el-icon>
            <p>{{ t('dashboard.messages.alertLoadFailed') }}</p>
            <el-button text type="primary" @click="loadAlertTopN">{{ t('common.retry') }}</el-button>
          </div>
          <div v-else ref="alertTopNRef" class="chart-area" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style lang="scss" scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.welcome-card {
  // 渐变背景使用图表色板变量，暗黑模式下自动切换为提亮版本
  background: $gradient-welcome;
  color: $bg-white;
  border: none;

  :deep(.el-card__body) {
    padding: $spacing-lg;
  }

  .welcome-content {
    display: flex;
    align-items: center;
    justify-content: space-between;

    h2 {
      font-size: 24px;
      margin-bottom: $spacing-sm;
    }

    p {
      opacity: 0.9;
    }
  }

  .welcome-icon {
    opacity: 0.6;
  }
}

.toolbar {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: -8px;
}

.metric-row {
  margin-bottom: 0;
}

.metric-card {
  .metric-content {
    display: flex;
    align-items: center;
    gap: $spacing-md;
  }

  .metric-icon {
    width: 56px;
    height: 56px;
    border-radius: $border-radius-lg;
    display: flex;
    align-items: center;
    justify-content: center;
    color: $bg-white;
    flex-shrink: 0;
  }

  .metric-info {
    flex: 1;
    min-width: 0;
  }

  .metric-title {
    font-size: $font-size-sm;
    color: $text-secondary;
    margin-bottom: $spacing-xs;
  }

  .metric-value {
    display: flex;
    align-items: baseline;
    margin-bottom: $spacing-xs;

    .value {
      font-size: 26px;
      font-weight: 600;
      color: $text-primary;
    }

    .unit {
      margin-left: $spacing-xs;
      font-size: $font-size-sm;
      color: $text-secondary;
    }
  }

  .metric-sub {
    font-size: $font-size-xs;
    color: $success-color;
  }

  /* 批次 30-3：可点击卡片样式 */
  &--clickable {
    cursor: pointer;
    transition: transform 0.15s, box-shadow 0.15s;

    &:hover {
      transform: translateY(-2px);
    }
  }

  /* 批次 30-3：环比箭头 */
  .metric-mtd {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    margin-left: $spacing-xs;
    font-size: $font-size-xs;
    font-weight: 600;
    vertical-align: middle;

    &.is-up {
      color: $success-color;
    }

    &.is-down {
      color: $danger-color;
    }
  }

  /* 批次 30-3：迷你 sparkline */
  .metric-sparkline {
    margin-top: $spacing-sm;
    width: 100%;
    height: 28px;
    overflow: hidden;

    svg {
      display: block;
      width: 100%;
      height: 100%;
    }
  }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chart-area {
  width: 100%;
  // 最小宽度：保证图表在窄列下仍有可读的渲染区域；1366px 下最小图表列约 455px，不会触发横向溢出
  min-width: 280px;
  height: 320px;
}

.chart-skeleton {
  width: 100%;
  height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-md;
}

.chart-error {
  width: 100%;
  height: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  color: $text-secondary;
  cursor: pointer;

  .el-icon {
    color: $warning-color;
  }

  p {
    font-size: $font-size-sm;
  }
}

.kpi-mini {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid $border-extra-light;

  &-label {
    color: $text-regular;
    font-size: $font-size-sm;
  }

  &-value {
    font-weight: 600;
    color: $text-primary;

    &.danger {
      color: $danger-color;
    }

    &.warn {
      color: $warning-color;
    }
  }
}

// 移动端适配
@media (max-width: $breakpoint-sm) {
  .welcome-card {
    :deep(.el-card__body) {
      padding: $spacing-md;
    }

    .welcome-content {
      flex-direction: column;
      text-align: center;
      gap: $spacing-md;

      h2 {
        font-size: 20px;
      }
    }

    .welcome-icon {
      order: -1;
    }
  }

  .toolbar {
    flex-direction: column;
    align-items: stretch;

    .el-select,
    .el-button {
      width: 100%;
    }
  }

  .metric-card {
    .metric-content {
      gap: $spacing-sm;
    }

    .metric-icon {
      width: 48px;
      height: 48px;
    }

    .metric-value .value {
      font-size: 22px;
    }
  }

  .chart-area {
    height: 280px;
  }
}
</style>
