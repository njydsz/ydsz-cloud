<script setup lang="ts">
/**
 * @file 实时监控仪表盘
 * @module views/workflow/monitor
 * @description 管理员视角：流程运行监控仪表盘，含统计卡片、趋势图、瓶颈分析、
 *   审批效率排名、异常流程列表、流程类型分布。每 30 秒自动轮询概览数据。
 */
import { ref, reactive, onMounted, onUnmounted, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import {
  getMonitorOverview,
  getAnomalyInstances,
  getInstanceTrend,
  nodeDurationStats,
  getApproverEfficiency,
  getFlowTypeDistribution,
} from '@/api/workflow'
import type {
  MonitorOverviewDTO,
  AnomalyInstanceDTO,
  InstanceTrendItemDTO,
  FlowNodeDurationStatDTO,
  ApproverEfficiencyDTO,
  FlowTypeDistributionDTO,
} from '@/api/workflow/types'
import * as echarts from 'echarts/core'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { logger } from '@/utils/logger'

/** ECharts tooltip formatter 的 axisPointer 模式参数类型（外部未导出，手动声明） */
interface AxisTooltipParam {
  name?: string
  value?: number
  dataIndex?: number
}

echarts.use([
  LineChart,
  BarChart,
  PieChart,
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  CanvasRenderer,
])

const router = useRouter()
const { t } = useI18n()

// ===========================================
// 统计概览
// ===========================================
const overview = ref<MonitorOverviewDTO>({
  runningCount: 0,
  todayNewCount: 0,
  pendingTaskCount: 0,
  overdueTaskCount: 0,
  todayCompletedCount: 0,
})
const overviewLoading = ref(false)

// ===========================================
// P2-4: 全局时间范围选择器（驱动趋势/瓶颈/审批人/分布图）
// ===========================================
const dateRange = ref<[string, string] | null>(null)
const dateShortcuts = computed(() => [
  {
    text: t('workflow.monitor.rangeToday'),
    value: () => {
      const today = dayjs().format('YYYY-MM-DD 00:00:00')
      return [today, dayjs().format('YYYY-MM-DD 23:59:59')]
    },
  },
  {
    text: t('workflow.monitor.range7Days'),
    value: () => {
      return [
        dayjs().subtract(6, 'day').format('YYYY-MM-DD 00:00:00'),
        dayjs().format('YYYY-MM-DD 23:59:59'),
      ]
    },
  },
  {
    text: t('workflow.monitor.range30Days'),
    value: () => {
      return [
        dayjs().subtract(29, 'day').format('YYYY-MM-DD 00:00:00'),
        dayjs().format('YYYY-MM-DD 23:59:59'),
      ]
    },
  },
])

/** 返回当前时间范围的 {startTime, endTime}，未选择则返回空对象 */
function currentTimeRange() {
  if (!dateRange.value || dateRange.value.length !== 2) return {}
  return {
    startTime: dateRange.value[0],
    endTime: dateRange.value[1],
  }
}

/** 时间范围变化时重载除概览外的所有图表 */
watch(dateRange, () => {
  loadTrend()
  loadBottleneck()
  loadApproverEfficiency()
  loadDistribution()
  anomalyQuery.pageNum = 1
  loadAnomaly()
})

// 数字动画显示值
const displayStats = reactive({
  runningCount: 0,
  todayNewCount: 0,
  pendingTaskCount: 0,
  overdueTaskCount: 0,
  todayCompletedCount: 0,
})

/** 数字缓动动画 */
function animateNumber(key: keyof typeof displayStats, target: number) {
  const start = displayStats[key]
  const diff = target - start
  if (diff === 0) return
  const duration = 800
  const startTime = performance.now()
  function step(now: number) {
    const elapsed = now - startTime
    const progress = Math.min(elapsed / duration, 1)
    // easeOutCubic
    const eased = 1 - Math.pow(1 - progress, 3)
    displayStats[key] = Math.round(start + diff * eased)
    if (progress < 1) {
      requestAnimationFrame(step)
    }
  }
  requestAnimationFrame(step)
}

async function loadOverview() {
  overviewLoading.value = true
  try {
    const res = await getMonitorOverview()
    if (res.data?.code === 0 && res.data.data) {
      const data = res.data.data
      overview.value = data
      animateNumber('runningCount', data.runningCount)
      animateNumber('todayNewCount', data.todayNewCount)
      animateNumber('pendingTaskCount', data.pendingTaskCount)
      animateNumber('overdueTaskCount', data.overdueTaskCount)
      animateNumber('todayCompletedCount', data.todayCompletedCount)
      // 轮询成功，重置失败计数与状态
      pollFailCount = 0
      if (pollStatus.value !== 'running') {
        pollStatus.value = 'running'
      }
    }
  } catch (e) {
    // 拦截器已弹错误提示，此处仅记录失败计数用于轮询退避
    recordPollFailure()
    logger.warn('[WorkflowMonitor]', 'loadOverview failed:', (e as Error).message)
  } finally {
    overviewLoading.value = false
  }
}

// ===========================================
// 流程实例趋势
// ===========================================
const trendDays = ref(7)
const trendData = ref<InstanceTrendItemDTO[]>([])
const trendLoading = ref(false)
const trendChartRef = ref<HTMLElement | null>(null)
let trendChart: echarts.ECharts | null = null

async function loadTrend() {
  trendLoading.value = true
  try {
    const res = await getInstanceTrend({ days: trendDays.value })
    if (res.data?.code === 0) {
      trendData.value = res.data.data || []
      renderTrendChart()
    }
  } catch (e) {
    logger.warn('[WorkflowMonitor]', 'loadTrend failed:', (e as Error).message)
  } finally {
    trendLoading.value = false
  }
}

function renderTrendChart() {
  if (!trendChartRef.value) return
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }
  const data = trendData.value
  trendChart.setOption({
    tooltip: {
      trigger: 'axis',
    },
    legend: {
      data: [t('workflow.monitor.charts.newSeries'), t('workflow.monitor.charts.doneSeries')],
      bottom: 0,
    },
    grid: {
      left: 50,
      right: 20,
      top: 20,
      bottom: 40,
    },
    xAxis: {
      type: 'category',
      data: data.map((d) => d.date),
      axisLabel: {
        rotate: data.length > 10 ? 30 : 0,
      },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
    },
    series: [
      {
        name: t('workflow.monitor.charts.newSeries'),
        type: 'line',
        data: data.map((d) => d.newCount),
        smooth: true,
        lineStyle: { color: '#1890ff', width: 2 },
        itemStyle: { color: '#1890ff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(24,144,255,0.25)' },
            { offset: 1, color: 'rgba(24,144,255,0.02)' },
          ]),
        },
      },
      {
        name: t('workflow.monitor.charts.doneSeries'),
        type: 'line',
        data: data.map((d) => d.completedCount),
        smooth: true,
        lineStyle: { color: '#52c41a', width: 2 },
        itemStyle: { color: '#52c41a' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(82,196,26,0.25)' },
            { offset: 1, color: 'rgba(82,196,26,0.02)' },
          ]),
        },
      },
    ],
  })
}

watch(trendDays, () => {
  loadTrend()
})

// ===========================================
// 节点耗时瓶颈排名
// ===========================================
const bottleneckData = ref<FlowNodeDurationStatDTO[]>([])
const bottleneckLoading = ref(false)
const bottleneckChartRef = ref<HTMLElement | null>(null)
let bottleneckChart: echarts.ECharts | null = null

async function loadBottleneck() {
  bottleneckLoading.value = true
  try {
    const res = await nodeDurationStats(currentTimeRange())
    if (res.data?.code === 0) {
      const all = res.data.data || []
      bottleneckData.value = all
        .sort((a, b) => (b.avgDurationMs || 0) - (a.avgDurationMs || 0))
        .slice(0, 10)
      renderBottleneckChart()
    }
  } catch (e) {
    logger.warn('[WorkflowMonitor]', 'loadBottleneck failed:', (e as Error).message)
  } finally {
    bottleneckLoading.value = false
  }
}

function renderBottleneckChart() {
  if (!bottleneckChartRef.value) return
  if (!bottleneckChart) {
    bottleneckChart = echarts.init(bottleneckChartRef.value)
  }
  const top = bottleneckData.value
  bottleneckChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: AxisTooltipParam | AxisTooltipParam[]) => {
        const p = Array.isArray(params) ? params[0] : params
        return t('workflow.monitor.charts.bottleneckTooltip', {
          name: p?.name ?? '',
          duration: formatDuration(p?.value),
          count: top[p?.dataIndex ?? -1]?.instanceCount || 0,
        })
      },
    },
    grid: { left: 120, right: 80, top: 10, bottom: 20 },
    xAxis: {
      type: 'value',
      name: t('workflow.monitor.charts.costSeries'),
      axisLabel: {
        formatter: (val: number) => formatDuration(val),
      },
    },
    yAxis: {
      type: 'category',
      data: top.map((s) => s.nodeName || s.nodeCode).reverse(),
      axisLabel: {
        width: 100,
        overflow: 'truncate',
      },
    },
    series: [
      {
        type: 'bar',
        data: top
          .map((s) => ({
            value: Math.round(s.avgDurationMs || 0),
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                { offset: 0, color: '#ff7a45' },
                { offset: 1, color: '#ff4d4f' },
              ]),
              borderRadius: [0, 4, 4, 0],
            },
          }))
          .reverse(),
        barMaxWidth: 28,
        label: {
          show: true,
          position: 'right',
          formatter: (p: AxisTooltipParam) => formatDuration(p?.value),
          fontSize: 11,
        },
      },
    ],
  })
}

// ===========================================
// 审批人效率排名
// ===========================================
const approverData = ref<ApproverEfficiencyDTO[]>([])
const approverLoading = ref(false)
const approverChartRef = ref<HTMLElement | null>(null)
let approverChart: echarts.ECharts | null = null

async function loadApproverEfficiency() {
  approverLoading.value = true
  try {
    const res = await getApproverEfficiency({ topN: 10, ...currentTimeRange() })
    if (res.data?.code === 0) {
      approverData.value = res.data.data || []
      renderApproverChart()
    }
  } catch (e) {
    logger.warn('[WorkflowMonitor]', 'loadApproverEfficiency failed:', (e as Error).message)
  } finally {
    approverLoading.value = false
  }
}

function renderApproverChart() {
  if (!approverChartRef.value) return
  if (!approverChart) {
    approverChart = echarts.init(approverChartRef.value)
  }
  const data = approverData.value
  approverChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: AxisTooltipParam | AxisTooltipParam[]) => {
        const p = Array.isArray(params) ? params[0] : params
        const idx = data.length - 1 - (p?.dataIndex || 0)
        const item = data[idx]
        return t('workflow.monitor.charts.approverTooltip', {
          name: item?.userName || p?.name || '',
          duration: formatDuration(p?.value),
          count: item?.completedCount || 0,
        })
      },
    },
    grid: { left: 120, right: 80, top: 10, bottom: 20 },
    xAxis: {
      type: 'value',
      name: t('workflow.monitor.charts.avgDurationSeries'),
      axisLabel: {
        formatter: (val: number) => formatDuration(val),
      },
    },
    yAxis: {
      type: 'category',
      data: data.map((s) => s.userName || t('workflow.monitor.charts.anonymousUser', { n: s.userId })).reverse(),
      axisLabel: {
        width: 100,
        overflow: 'truncate',
      },
    },
    series: [
      {
        type: 'bar',
        data: data
          .map((s) => ({
            value: Math.round(s.avgDurationMs || 0),
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                { offset: 0, color: '#36cfc9' },
                { offset: 1, color: '#13c2c2' },
              ]),
              borderRadius: [0, 4, 4, 0],
            },
          }))
          .reverse(),
        barMaxWidth: 28,
        label: {
          show: true,
          position: 'right',
          formatter: (p: AxisTooltipParam) => formatDuration(p?.value),
          fontSize: 11,
        },
      },
    ],
  })
}

// ===========================================
// 异常流程列表
// ===========================================
const anomalyQuery = reactive({
  anomalyType: '',
  warnLevel: '',
  pageNum: 1,
  pageSize: 10,
})
const anomalyList = ref<AnomalyInstanceDTO[]>([])
const anomalyTotal = ref(0)
const anomalyLoading = ref(false)

async function loadAnomaly() {
  anomalyLoading.value = true
  try {
    const res = await getAnomalyInstances(anomalyQuery)
    if (res.data?.code === 0) {
      anomalyList.value = res.data.data?.list || []
      anomalyTotal.value = res.data.data?.total || 0
    }
  } catch (e) {
    logger.warn('[WorkflowMonitor]', 'loadAnomaly failed:', (e as Error).message)
  } finally {
    anomalyLoading.value = false
  }
}

const anomalyTypeMap = computed<Record<string, { label: string; color: string }>>(() => ({
  TIMEOUT: { label: t('workflow.monitor.anomaly.type.TIMEOUT'), color: '#f5222d' },
  STUCK: { label: t('workflow.monitor.anomaly.type.STUCK'), color: '#fa8c16' },
  CIRCULAR_APPROVAL: { label: t('workflow.monitor.anomaly.type.CIRCULAR_APPROVAL'), color: '#722ed1' },
  REPEATED_REJECT: { label: t('workflow.monitor.anomaly.type.REPEATED_REJECT'), color: '#eb2f96' },
}))

const warnLevelMap = computed<Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info' }>>(() => ({
  RED: { label: t('workflow.monitor.anomaly.warnLevel.RED'), type: 'danger' as const },
  YELLOW: { label: t('workflow.monitor.anomaly.warnLevel.YELLOW'), type: 'warning' as const },
  ORANGE: { label: t('workflow.monitor.anomaly.warnLevel.ORANGE'), type: 'info' as const },
}))

function goInstance(row: AnomalyInstanceDTO) {
  router.push({ path: '/workflow/instance', query: { id: String(row.id) } })
}

/**
 * P2-4: 导出异常列表为 CSV
 *
 * <p>前端纯客户端导出，不新增后端接口；适合异常列表数据量小的场景。
 * 字段：实例ID / 标题 / 流程 / 发起人 / 异常类型 / 预警级别 / 当前节点 / 超期天数 / 发起时间
 */
function exportAnomalyCsv() {
  if (!anomalyList.value.length) {
    ElMessage.warning(t('workflow.monitor.csv.empty'))
    return
  }
  const headers = [
    t('workflow.monitor.anomaly.colId'),
    t('workflow.monitor.anomaly.colTitle'),
    t('workflow.monitor.anomaly.colFlow'),
    t('workflow.monitor.anomaly.colInitiator'),
    t('workflow.monitor.anomaly.colAnomalyType'),
    t('workflow.monitor.anomaly.colWarnLevel'),
    t('workflow.monitor.anomaly.colCurrentNode'),
    t('workflow.monitor.anomaly.colOverdueDays'),
    t('workflow.monitor.anomaly.colStartTime'),
  ]
  const rows = anomalyList.value.map((r) => [
    r.id ?? '',
    r.title ?? '',
    r.flowName ?? '',
    r.initiatorName ?? '',
    anomalyTypeMap.value[r.anomalyType]?.label ?? r.anomalyType ?? '',
    warnLevelMap.value[r.warnLevel]?.label ?? r.warnLevel ?? '',
    r.currentNodeName ?? '',
    r.overdueDays ?? '',
    r.startTime ? dayjs(r.startTime).format('YYYY-MM-DD HH:mm:ss') : '',
  ])
  // CSV 转义：含逗号/换行/引号的字段用双引号包裹，内部双引号变两个
  const escapeCell = (v: unknown) => {
    const s = String(v ?? '')
    if (/[",\n]/.test(s)) {
      return '"' + s.replace(/"/g, '""') + '"'
    }
    return s
  }
  const csvContent = [
    headers.map(escapeCell).join(','),
    ...rows.map((r) => r.map(escapeCell).join(',')),
  ].join('\n')
  // 加 BOM 头避免 Excel 中文乱码
  const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = t('workflow.monitor.csv.fileName', { time: dayjs().format('YYYYMMDD_HHmmss') })
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  ElMessage.success(t('workflow.monitor.csv.exportSuccess', { n: anomalyList.value.length }))
}

// ===========================================
// 流程类型分布
// ===========================================
const distributionData = ref<FlowTypeDistributionDTO[]>([])
const distributionLoading = ref(false)
const distributionChartRef = ref<HTMLElement | null>(null)
let distributionChart: echarts.ECharts | null = null

async function loadDistribution() {
  distributionLoading.value = true
  try {
    const res = await getFlowTypeDistribution(currentTimeRange())
    if (res.data?.code === 0) {
      distributionData.value = res.data.data || []
      renderDistributionChart()
    }
  } catch (e) {
    logger.warn('[WorkflowMonitor]', 'loadDistribution failed:', (e as Error).message)
  } finally {
    distributionLoading.value = false
  }
}

function renderDistributionChart() {
  if (!distributionChartRef.value) return
  if (!distributionChart) {
    distributionChart = echarts.init(distributionChartRef.value)
  }
  const data = distributionData.value
  distributionChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      textStyle: { fontSize: 12 },
    },
    series: [
      {
        type: 'pie',
        radius: ['45%', '75%'],
        center: ['40%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 4,
          borderColor: '#fff',
          borderWidth: 2,
        },
        label: {
          show: false,
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 14,
            fontWeight: 'bold',
          },
        },
        data: data.map((d) => ({
          name: d.flowName || d.flowCode,
          value: d.count,
        })),
      },
    ],
    color: ['#1890ff', '#52c41a', '#fa8c16', '#f5222d', '#722ed1', '#13c2c2', '#eb2f96', '#faad14', '#2f54eb', '#a0d911'],
  })
}

// ===========================================
// 工具函数
// ===========================================
function formatDuration(ms?: number): string {
  if (!ms || ms <= 0) return t('workflow.monitor.duration.zero')
  const s = Math.floor(ms / 1000)
  if (s < 60) return t('workflow.monitor.duration.second', { n: s })
  const m = Math.floor(s / 60)
  if (m < 60) return t('workflow.monitor.duration.minute', { n: m, s: s % 60 })
  const h = Math.floor(m / 60)
  if (h < 24) return t('workflow.monitor.duration.hour', { n: h, m: m % 60 })
  return t('workflow.monitor.duration.day', { n: Math.floor(h / 24), h: h % 24 })
}

// ===========================================
// 自动刷新（含失败退避）
// ===========================================
// 轮询策略：默认 30s 间隔；连续失败时指数退避（最多 5min）；
// 连续失败 5 次后停止轮询，避免后端不可用时持续打请求。
let pollTimer: ReturnType<typeof setInterval> | null = null
let pollFailCount = 0
const POLL_MAX_FAIL = 5
const POLL_BASE_INTERVAL = 30_000
const POLL_MAX_INTERVAL = 5 * 60_000
/** 轮询状态：running=正常 / backing-off=退避中 / stopped=已停止 */
const pollStatus = ref<'running' | 'backing-off' | 'stopped'>('running')

/** 记录轮询失败，达到阈值则停止轮询 */
function recordPollFailure() {
  pollFailCount++
  if (pollFailCount >= POLL_MAX_FAIL) {
    logger.warn(
      '[WorkflowMonitor]',
      `轮询连续失败 ${pollFailCount} 次，停止自动刷新。请手动刷新页面恢复。`,
    )
    ElMessage.warning(t('workflow.monitor.messages.pollStopped'))
    stopPolling()
    pollStatus.value = 'stopped'
  } else {
    // 指数退避：30s → 60s → 120s → 240s → 300s（上限）
    const nextInterval = Math.min(
      POLL_BASE_INTERVAL * Math.pow(2, pollFailCount),
      POLL_MAX_INTERVAL,
    )
    logger.warn(
      '[WorkflowMonitor]',
      `轮询失败 ${pollFailCount} 次，下次间隔 ${nextInterval / 1000}s`,
    )
    pollStatus.value = 'backing-off'
    stopPolling()
    pollTimer = setInterval(() => {
      loadOverview()
    }, nextInterval)
  }
}

function startPolling() {
  stopPolling()
  pollFailCount = 0
  pollStatus.value = 'running'
  pollTimer = setInterval(() => {
    loadOverview()
  }, POLL_BASE_INTERVAL)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

// ===========================================
// 窗口 resize
// ===========================================
function resizeCharts() {
  trendChart?.resize()
  bottleneckChart?.resize()
  approverChart?.resize()
  distributionChart?.resize()
}

// ===========================================
// 生命周期
// ===========================================
onMounted(() => {
  loadOverview()
  loadTrend()
  loadBottleneck()
  loadAnomaly()
  loadApproverEfficiency()
  loadDistribution()
  startPolling()
  window.addEventListener('resize', resizeCharts)
})

onUnmounted(() => {
  stopPolling()
  window.removeEventListener('resize', resizeCharts)
  trendChart?.dispose()
  bottleneckChart?.dispose()
  approverChart?.dispose()
  distributionChart?.dispose()
})

// ===========================================
// 统计卡片配置
// ===========================================
const statCards = computed(() => [
  {
    key: 'runningCount' as const,
    title: t('workflow.monitor.stat.running'),
    icon: 'el-icon-loading',
    color: '#1890ff',
    bgColor: 'rgba(24,144,255,0.08)',
  },
  {
    key: 'todayNewCount' as const,
    title: t('workflow.monitor.stat.todayNew'),
    icon: 'el-icon-plus',
    color: '#52c41a',
    bgColor: 'rgba(82,196,26,0.08)',
  },
  {
    key: 'pendingTaskCount' as const,
    title: t('workflow.monitor.stat.pending'),
    icon: 'el-icon-s-order',
    color: '#fa8c16',
    bgColor: 'rgba(250,140,22,0.08)',
  },
  {
    key: 'overdueTaskCount' as const,
    title: t('workflow.monitor.stat.overdue'),
    icon: 'el-icon-warning',
    color: '#f5222d',
    bgColor: 'rgba(245,34,45,0.08)',
  },
  {
    key: 'todayCompletedCount' as const,
    title: t('workflow.monitor.stat.todayCompleted'),
    icon: 'el-icon-circle-check',
    color: '#13c2c2',
    bgColor: 'rgba(19,194,194,0.08)',
  },
])
</script>

<template>
  <div class="monitor-dashboard">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="page-header__left">
        <h2>{{ t('workflow.monitor.title') }}</h2>
        <p class="page-header__sub">
          {{ t('workflow.monitor.subtitle') }}
          <el-tag
            v-if="pollStatus === 'running'"
            size="small"
            type="success"
            effect="plain"
            style="margin-left: 8px"
          >{{ t('workflow.monitor.pollRunning') }}</el-tag>
          <el-tag
            v-else-if="pollStatus === 'backing-off'"
            size="small"
            type="warning"
            effect="plain"
            style="margin-left: 8px"
          >{{ t('workflow.monitor.pollBackingOff') }}</el-tag>
          <el-tag
            v-else
            size="small"
            type="danger"
            effect="plain"
            style="margin-left: 8px"
          >{{ t('workflow.monitor.pollStopped') }}</el-tag>
        </p>
      </div>
      <div class="page-header__right">
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          :range-separator="t('workflow.monitor.rangeSeparator')"
          :start-placeholder="t('workflow.monitor.startPlaceholder')"
          :end-placeholder="t('workflow.monitor.endPlaceholder')"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          :shortcuts="dateShortcuts"
          size="default"
          clearable
          style="width: 360px"
        />
      </div>
    </div>

    <!-- 统计卡片行 -->
    <div class="stat-cards">
      <div
        v-for="card in statCards"
        :key="card.key"
        class="stat-card"
        :style="{ borderTopColor: card.color }"
      >
        <div class="stat-card__icon" :style="{ backgroundColor: card.bgColor, color: card.color }">
          <i :class="card.icon" />
        </div>
        <div class="stat-card__info">
          <div class="stat-card__title">{{ card.title }}</div>
          <div class="stat-card__value" :style="{ color: card.color }">
            {{ displayStats[card.key] }}
          </div>
        </div>
      </div>
    </div>

    <!-- 第一行：趋势图 + 流程类型分布 -->
    <div class="chart-row">
      <el-card shadow="never" class="chart-card">
        <template #header>
          <div class="card-header">
            <span>{{ t('workflow.monitor.charts.trendTitle') }}</span>
            <el-radio-group v-model="trendDays" size="small">
              <el-radio-button :value="7">{{ t('workflow.monitor.range7Days') }}</el-radio-button>
              <el-radio-button :value="30">{{ t('workflow.monitor.range30Days') }}</el-radio-button>
            </el-radio-group>
          </div>
        </template>
        <div ref="trendChartRef" class="chart-box" v-loading="trendLoading" />
      </el-card>

      <el-card shadow="never" class="chart-card">
        <template #header>
          <span>{{ t('workflow.monitor.charts.distributionTitle') }}</span>
        </template>
        <div ref="distributionChartRef" class="chart-box" v-loading="distributionLoading" />
      </el-card>
    </div>

    <!-- 第二行：节点耗时瓶颈 + 审批人效率 -->
    <div class="chart-row">
      <el-card shadow="never" class="chart-card">
        <template #header>
          <span>{{ t('workflow.monitor.charts.bottleneckTitle') }}</span>
        </template>
        <div ref="bottleneckChartRef" class="chart-box" v-loading="bottleneckLoading" />
      </el-card>

      <el-card shadow="never" class="chart-card">
        <template #header>
          <span>{{ t('workflow.monitor.charts.approverTitle') }}</span>
        </template>
        <div ref="approverChartRef" class="chart-box" v-loading="approverLoading" />
      </el-card>
    </div>

    <!-- 异常流程列表 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <span>{{ t('workflow.monitor.anomaly.title') }}</span>
          <div>
            <el-button size="small" text @click="loadAnomaly">{{ t('workflow.monitor.buttons.refresh') }}</el-button>
            <el-button size="small" type="primary" plain @click="exportAnomalyCsv" :disabled="!anomalyList.length">
              {{ t('workflow.monitor.buttons.exportCsv') }}
            </el-button>
          </div>
        </div>
      </template>
      <div class="filter-bar">
        <el-select
          v-model="anomalyQuery.anomalyType"
          :placeholder="t('workflow.monitor.anomaly.anomalyTypePlaceholder')"
          clearable
          style="width: 140px"
          @change="anomalyQuery.pageNum = 1; loadAnomaly()"
        >
          <el-option :label="t('workflow.monitor.anomaly.type.TIMEOUT')" value="TIMEOUT" />
          <el-option :label="t('workflow.monitor.anomaly.type.STUCK')" value="STUCK" />
          <el-option :label="t('workflow.monitor.anomaly.type.CIRCULAR_APPROVAL')" value="CIRCULAR_APPROVAL" />
          <el-option :label="t('workflow.monitor.anomaly.type.REPEATED_REJECT')" value="REPEATED_REJECT" />
        </el-select>
        <el-select
          v-model="anomalyQuery.warnLevel"
          :placeholder="t('workflow.monitor.anomaly.warnLevelPlaceholder')"
          clearable
          style="width: 120px"
          @change="anomalyQuery.pageNum = 1; loadAnomaly()"
        >
          <el-option :label="t('workflow.monitor.anomaly.warnLevel.RED')" value="RED" />
          <el-option :label="t('workflow.monitor.anomaly.warnLevel.YELLOW')" value="YELLOW" />
          <el-option :label="t('workflow.monitor.anomaly.warnLevel.ORANGE')" value="ORANGE" />
        </el-select>
        <el-button type="primary" @click="anomalyQuery.pageNum = 1; loadAnomaly()">{{ t('workflow.monitor.buttons.query') }}</el-button>
      </div>
      <el-table :data="anomalyList" v-loading="anomalyLoading" stripe>
        <el-table-column prop="id" :label="t('workflow.monitor.anomaly.colId')" width="80" />
        <el-table-column prop="title" :label="t('workflow.monitor.anomaly.colTitle')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="flowName" :label="t('workflow.monitor.anomaly.colFlow')" width="140" show-overflow-tooltip />
        <el-table-column prop="initiatorName" :label="t('workflow.monitor.anomaly.colInitiator')" width="100" />
        <el-table-column :label="t('workflow.monitor.anomaly.colAnomalyType')" width="120">
          <template #default="{ row }">
            <el-tag
              :color="anomalyTypeMap[row.anomalyType]?.color"
              size="small"
              effect="dark"
              style="border: none"
            >
              {{ anomalyTypeMap[row.anomalyType]?.label || row.anomalyType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('workflow.monitor.anomaly.colWarnLevel')" width="100">
          <template #default="{ row }">
            <el-tag
              :type="warnLevelMap[row.warnLevel]?.type || 'info'"
              size="small"
            >
              {{ warnLevelMap[row.warnLevel]?.label || row.warnLevel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentNodeName" :label="t('workflow.monitor.anomaly.colCurrentNode')" width="120" />
        <el-table-column :label="t('workflow.monitor.anomaly.colOverdueDays')" width="100">
          <template #default="{ row }">
            <span v-if="row.overdueDays != null" style="color: #f5222d; font-weight: 600">
              {{ row.overdueDays }} {{ t('workflow.monitor.anomaly.dayUnit') }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('workflow.monitor.anomaly.colStartTime')" width="160">
          <template #default="{ row }">
            {{ row.startTime ? dayjs(row.startTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('workflow.monitor.anomaly.colAction')" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="goInstance(row as AnomalyInstanceDTO)">{{ t('workflow.monitor.buttons.viewDetail') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="anomalyQuery.pageNum"
        v-model:page-size="anomalyQuery.pageSize"
        :total="anomalyTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="loadAnomaly"
        @size-change="loadAnomaly"
      />
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.monitor-dashboard {
  padding: 16px;
}

.page-header {
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;

  &__left {
    flex: 1;
    min-width: 0;
  }

  &__right {
    flex-shrink: 0;
  }

  h2 {
    margin: 0;
    font-size: 20px;
    color: #1e293b;
  }

  &__sub {
    margin: 4px 0 0;
    color: #64748b;
    font-size: 13px;
    display: flex;
    align-items: center;
  }
}

/* ========== 统计卡片 ========== */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px 16px;
  display: flex;
  align-items: center;
  gap: 16px;
  border-top: 3px solid;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  transition: box-shadow 0.2s, transform 0.2s;

  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    transform: translateY(-2px);
  }

  &__icon {
    width: 48px;
    height: 48px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
    flex-shrink: 0;

    i {
      font-size: 22px;
    }
  }

  &__info {
    flex: 1;
    min-width: 0;
  }

  &__title {
    font-size: 13px;
    color: #64748b;
    margin-bottom: 4px;
  }

  &__value {
    font-size: 24px;
    font-weight: 600;
    line-height: 1;
  }
}

/* ========== 图表区域 ========== */
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 20px;
}

.chart-card {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: 600;
  }
}

.chart-box {
  height: 320px;
}

/* ========== 异常列表 ========== */
.section {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: 600;
  }
}

.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
