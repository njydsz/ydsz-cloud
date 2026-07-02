<script setup lang="ts">
/**
 * @file 实时监控仪表盘
 * @module views/workflow/monitor
 * @description 管理员视角：流程运行监控仪表盘，含统计卡片、趋势图、瓶颈分析、
 *   审批效率排名、异常流程列表、流程类型分布。每 30 秒自动轮询概览数据。
 */
import { ref, reactive, onMounted, onUnmounted, watch, nextTick } from 'vue'
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
    }
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
      data: ['新增实例', '完成实例'],
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
        name: '新增实例',
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
        name: '完成实例',
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
    const res = await nodeDurationStats({})
    if (res.data?.code === 0) {
      const all = res.data.data || []
      bottleneckData.value = all
        .sort((a, b) => (b.avgDurationMs || 0) - (a.avgDurationMs || 0))
        .slice(0, 10)
      renderBottleneckChart()
    }
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
      formatter: (params: any) => {
        const p = Array.isArray(params) ? params[0] : params
        return `${p.name}<br/>平均耗时: ${formatDuration(p.value)}<br/>实例数: ${top[p.dataIndex]?.instanceCount || 0}`
      },
    },
    grid: { left: 120, right: 80, top: 10, bottom: 20 },
    xAxis: {
      type: 'value',
      name: '耗时',
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
          formatter: (p: any) => formatDuration(p.value),
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
    const res = await getApproverEfficiency({ topN: 10 })
    if (res.data?.code === 0) {
      approverData.value = res.data.data || []
      renderApproverChart()
    }
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
      formatter: (params: any) => {
        const p = Array.isArray(params) ? params[0] : params
        const idx = data.length - 1 - (p.dataIndex || 0)
        const item = data[idx]
        return `${item?.userName || p.name}<br/>平均处理时长: ${formatDuration(p.value)}<br/>完成数: ${item?.completedCount || 0}`
      },
    },
    grid: { left: 120, right: 80, top: 10, bottom: 20 },
    xAxis: {
      type: 'value',
      name: '平均时长',
      axisLabel: {
        formatter: (val: number) => formatDuration(val),
      },
    },
    yAxis: {
      type: 'category',
      data: data.map((s) => s.userName || `用户${s.userId}`).reverse(),
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
          formatter: (p: any) => formatDuration(p.value),
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
      anomalyList.value = res.data.data?.records || []
      anomalyTotal.value = res.data.data?.total || 0
    }
  } finally {
    anomalyLoading.value = false
  }
}

const anomalyTypeMap: Record<string, { label: string; color: string }> = {
  TIMEOUT: { label: '超时', color: '#f5222d' },
  STUCK: { label: '卡单', color: '#fa8c16' },
  CIRCULAR_APPROVAL: { label: '循环审批', color: '#722ed1' },
  REPEATED_REJECT: { label: '重复驳回', color: '#eb2f96' },
}

const warnLevelMap: Record<string, { label: string; type: string }> = {
  RED: { label: '严重', type: 'danger' },
  YELLOW: { label: '警告', type: 'warning' },
  ORANGE: { label: '注意', type: '' },
}

function goInstance(row: AnomalyInstanceDTO) {
  router.push({ path: '/workflow/instance', query: { id: String(row.id) } })
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
    const res = await getFlowTypeDistribution({})
    if (res.data?.code === 0) {
      distributionData.value = res.data.data || []
      renderDistributionChart()
    }
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
  if (!ms || ms <= 0) return '0 秒'
  const s = Math.floor(ms / 1000)
  if (s < 60) return `${s} 秒`
  const m = Math.floor(s / 60)
  if (m < 60) return `${m} 分 ${s % 60} 秒`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 时 ${m % 60} 分`
  return `${Math.floor(h / 24)} 天 ${h % 24} 时`
}

// ===========================================
// 自动刷新
// ===========================================
let pollTimer: ReturnType<typeof setInterval> | null = null

function startPolling() {
  stopPolling()
  pollTimer = setInterval(() => {
    loadOverview()
  }, 30_000)
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
const statCards = [
  {
    key: 'runningCount' as const,
    title: '运行中实例',
    icon: 'el-icon-loading',
    color: '#1890ff',
    bgColor: 'rgba(24,144,255,0.08)',
  },
  {
    key: 'todayNewCount' as const,
    title: '今日新增',
    icon: 'el-icon-plus',
    color: '#52c41a',
    bgColor: 'rgba(82,196,26,0.08)',
  },
  {
    key: 'pendingTaskCount' as const,
    title: '待办任务',
    icon: 'el-icon-s-order',
    color: '#fa8c16',
    bgColor: 'rgba(250,140,22,0.08)',
  },
  {
    key: 'overdueTaskCount' as const,
    title: '超时任务',
    icon: 'el-icon-warning',
    color: '#f5222d',
    bgColor: 'rgba(245,34,45,0.08)',
  },
  {
    key: 'todayCompletedCount' as const,
    title: '今日完成',
    icon: 'el-icon-circle-check',
    color: '#13c2c2',
    bgColor: 'rgba(19,194,194,0.08)',
  },
]
</script>

<template>
  <div class="monitor-dashboard">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>实时监控仪表盘</h2>
      <p class="page-header__sub">
        流程运行状态实时监控，数据每 30 秒自动刷新
        <el-tag size="small" type="success" effect="plain" style="margin-left: 8px">自动刷新中</el-tag>
      </p>
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
            <span>流程运行趋势</span>
            <el-radio-group v-model="trendDays" size="small">
              <el-radio-button :value="7">近 7 天</el-radio-button>
              <el-radio-button :value="30">近 30 天</el-radio-button>
            </el-radio-group>
          </div>
        </template>
        <div ref="trendChartRef" class="chart-box" v-loading="trendLoading" />
      </el-card>

      <el-card shadow="never" class="chart-card">
        <template #header>
          <span>流程类型分布</span>
        </template>
        <div ref="distributionChartRef" class="chart-box" v-loading="distributionLoading" />
      </el-card>
    </div>

    <!-- 第二行：节点耗时瓶颈 + 审批人效率 -->
    <div class="chart-row">
      <el-card shadow="never" class="chart-card">
        <template #header>
          <span>节点耗时瓶颈 TOP 10</span>
        </template>
        <div ref="bottleneckChartRef" class="chart-box" v-loading="bottleneckLoading" />
      </el-card>

      <el-card shadow="never" class="chart-card">
        <template #header>
          <span>审批效率排名 TOP 10</span>
        </template>
        <div ref="approverChartRef" class="chart-box" v-loading="approverLoading" />
      </el-card>
    </div>

    <!-- 异常流程列表 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <span>异常流程列表</span>
          <el-button size="small" text @click="loadAnomaly">刷新</el-button>
        </div>
      </template>
      <div class="filter-bar">
        <el-select
          v-model="anomalyQuery.anomalyType"
          placeholder="异常类型"
          clearable
          style="width: 140px"
          @change="anomalyQuery.pageNum = 1; loadAnomaly()"
        >
          <el-option label="超时" value="TIMEOUT" />
          <el-option label="卡单" value="STUCK" />
          <el-option label="循环审批" value="CIRCULAR_APPROVAL" />
          <el-option label="重复驳回" value="REPEATED_REJECT" />
        </el-select>
        <el-select
          v-model="anomalyQuery.warnLevel"
          placeholder="预警级别"
          clearable
          style="width: 120px"
          @change="anomalyQuery.pageNum = 1; loadAnomaly()"
        >
          <el-option label="严重" value="RED" />
          <el-option label="警告" value="YELLOW" />
          <el-option label="注意" value="ORANGE" />
        </el-select>
        <el-button type="primary" @click="anomalyQuery.pageNum = 1; loadAnomaly()">查询</el-button>
      </div>
      <el-table :data="anomalyList" v-loading="anomalyLoading" stripe>
        <el-table-column prop="id" label="实例 ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="flowName" label="流程" width="140" show-overflow-tooltip />
        <el-table-column prop="initiatorName" label="发起人" width="100" />
        <el-table-column label="异常类型" width="120">
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
        <el-table-column label="预警级别" width="100">
          <template #default="{ row }">
            <el-tag
              :type="(warnLevelMap[row.warnLevel]?.type as any) || 'info'"
              size="small"
            >
              {{ warnLevelMap[row.warnLevel]?.label || row.warnLevel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentNodeName" label="当前节点" width="120" />
        <el-table-column label="超期天数" width="100">
          <template #default="{ row }">
            <span v-if="row.overdueDays != null" style="color: #f5222d; font-weight: 600">
              {{ row.overdueDays }} 天
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="发起时间" width="160">
          <template #default="{ row }">
            {{ row.startTime ? dayjs(row.startTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="goInstance(row)">查看详情</el-button>
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
    font-size: 28px;
    font-weight: 700;
    line-height: 1.2;
    font-variant-numeric: tabular-nums;
  }
}

/* ========== 图表行 ========== */
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.chart-card {
  .chart-box {
    width: 100%;
    height: 340px;
  }
}

/* ========== 通用区域 ========== */
.section {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

/* ========== 响应式 ========== */
@media (max-width: 1400px) {
  .stat-cards {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 900px) {
  .stat-cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .chart-row {
    grid-template-columns: 1fr;
  }
}
</style>