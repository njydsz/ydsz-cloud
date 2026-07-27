<!--
  @fileoverview 规则引擎监控大盘组件 (Vue 3)
  @description 实时展示规则引擎运行指标：
  - 执行次数/触发率/异常率趋势图
  - TOP 规则排行（触发/耗时/异常）
  - 规则健康度评分
  - 实时执行流
  @module components/rule-engine/RuleMonitorDashboard
  @author ydsz-team
  @since 2.0.0
-->
<script setup lang="ts">
/**
 * RuleMonitorDashboard - 规则引擎监控大盘
 */
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from '@/utils/echarts'
import * as ruleApi from '@/api/rule-engine'
import { logger } from '@/utils/logger'

// ===== 状态 =====
const loading = ref(false)
const overview = ref({
  totalEvaluations: 0,
  totalTriggered: 0,
  totalErrors: 0,
  avgElapsedMs: 0,
  activeRuleCount: 0,
  triggerRate: 0,
  errorRate: 0
})
const topRules = ref<any[]>([])
const trendData = ref<{ time: string; evals: number; triggered: number; errors: number }[]>([])

// 图表引用
const trendChartRef = ref<HTMLElement>()
const healthChartRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null
let healthChart: echarts.ECharts | null = null

// 自动刷新
const autoRefresh = ref(false)
let refreshTimer: ReturnType<typeof setInterval> | null = null

// ===== 计算属性 =====
/** 触发率对应的颜色（>50% 绿，>20% 橙，其他灰） */
const triggerRateColor = computed(() => {
  const rate = overview.value.triggerRate
  if (rate > 50) return '#67c23a'
  if (rate > 20) return '#e6a23c'
  return '#909399'
})

/** 异常率对应的颜色（<1% 绿，<5% 橙，>=5% 红） */
const errorRateColor = computed(() => {
  const rate = overview.value.errorRate
  if (rate < 1) return '#67c23a'
  if (rate < 5) return '#e6a23c'
  return '#f56c6c'
})

// ===== 方法 =====
/** 加载监控大盘数据（概览 + TOP 排行 + 趋势） */
async function loadData() {
  loading.value = true
  try {
    const [ovRes, topRes, trendRes] = await Promise.all([
      ruleApi.getDashboardOverview(),
      ruleApi.getDashboardTopRules(),
      ruleApi.getDashboardTrend()
    ])
    overview.value = {
      ...overview.value,
      ...ovRes.data,
      triggerRate: ovRes.data.totalEvaluations > 0
        ? Math.round((ovRes.data.totalTriggered / ovRes.data.totalEvaluations) * 1000) / 10
        : 0,
      errorRate: ovRes.data.totalEvaluations > 0
        ? Math.round((ovRes.data.totalErrors / ovRes.data.totalEvaluations) * 1000) / 10
        : 0
    }
    topRules.value = topRes.data || []
    trendData.value = trendRes.data || []
    renderCharts()
  } catch (err) {
    logger.error('加载监控数据失败', err)
  } finally {
    loading.value = false
  }
}

/** 渲染所有图表 */
function renderCharts() {
  renderTrendChart()
  renderHealthChart()
}

/** 渲染执行趋势折线图 */
function renderTrendChart() {
  if (!trendChartRef.value) return
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }

  const times = trendData.value.map(d => d.time)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['执行', '触发', '异常'], bottom: 0 },
    grid: { left: 50, right: 20, top: 20, bottom: 40 },
    xAxis: { type: 'category', data: times, axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value', name: '次数' },
    series: [
      {
        name: '执行',
        type: 'line',
        smooth: true,
        data: trendData.value.map(d => d.evals),
        itemStyle: { color: '#409eff' },
        areaStyle: { opacity: 0.1 }
      },
      {
        name: '触发',
        type: 'line',
        smooth: true,
        data: trendData.value.map(d => d.triggered),
        itemStyle: { color: '#67c23a' }
      },
      {
        name: '异常',
        type: 'line',
        smooth: true,
        data: trendData.value.map(d => d.errors),
        itemStyle: { color: '#f56c6c' }
      }
    ]
  })
}

/** 渲染规则健康度 TOP 10 横向柱状图 */
function renderHealthChart() {
  if (!healthChartRef.value) return
  if (!healthChart) {
    healthChart = echarts.init(healthChartRef.value)
  }

  const data = topRules.value.slice(0, 10).map(r => ({
    name: r.ruleCode,
    value: r.healthScore || 0
  }))

  healthChart.setOption({
    tooltip: { trigger: 'item' },
    grid: { left: 100, right: 20, top: 10, bottom: 20 },
    xAxis: { type: 'value', max: 100, name: '健康度' },
    yAxis: { type: 'category', data: data.map(d => d.name), inverse: true },
    series: [{
      type: 'bar',
      data: data.map(d => ({
        value: d.value,
        itemStyle: {
          color: d.value > 80 ? '#67c23a' : d.value > 50 ? '#e6a23c' : '#f56c6c'
        }
      })),
      label: { show: true, position: 'right', formatter: '{c}' },
      barWidth: 16
    }]
  })
}

/** 切换自动刷新（开启时每 5s 轮询一次） */
function toggleAutoRefresh() {
  if (autoRefresh.value) {
    refreshTimer = setInterval(loadData, 5000)
  } else {
    if (refreshTimer) {
      clearInterval(refreshTimer)
      refreshTimer = null
    }
  }
}

/** 窗口尺寸变化时自适应图表 */
function handleResize() {
  trendChart?.resize()
  healthChart?.resize()
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  healthChart?.dispose()
})
</script>

<template>
  <div class="monitor-dashboard" v-loading="loading">
    <!-- 指标概览卡片 -->
    <el-row :gutter="16" class="overview-row">
      <el-col :span="4">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value">{{ overview.totalEvaluations.toLocaleString() }}</div>
          <div class="metric-label">总执行次数</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value" :style="{ color: triggerRateColor }">{{ overview.triggerRate }}%</div>
          <div class="metric-label">触发率</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value" :style="{ color: errorRateColor }">{{ overview.errorRate }}%</div>
          <div class="metric-label">异常率</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value">{{ overview.avgElapsedMs }}ms</div>
          <div class="metric-label">平均耗时</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value">{{ overview.activeRuleCount }}</div>
          <div class="metric-label">活跃规则数</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value">{{ overview.totalTriggered.toLocaleString() }}</div>
          <div class="metric-label">总触发次数</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>执行趋势</span>
              <div>
                <el-switch v-model="autoRefresh" @change="toggleAutoRefresh" active-text="自动刷新" />
                <el-button link @click="loadData">刷新</el-button>
              </div>
            </div>
          </template>
          <div ref="trendChartRef" class="chart-box" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <span>规则健康度 TOP 10</span>
          </template>
          <div ref="healthChartRef" class="chart-box" />
        </el-card>
      </el-col>
    </el-row>

    <!-- TOP 规则表格 -->
    <el-card shadow="hover" class="top-rules-card">
      <template #header>
        <span>规则排行</span>
      </template>
      <el-table :data="topRules" stripe size="small">
        <el-table-column prop="ruleCode" label="规则编码" width="150" />
        <el-table-column prop="ruleName" label="规则名称" min-width="200" />
        <el-table-column prop="evalCount" label="执行次数" width="100" sortable />
        <el-table-column prop="triggerCount" label="触发次数" width="100" sortable />
        <el-table-column label="触发率" width="100" sortable :sort-method="(a: any, b: any) => a.triggerRate - b.triggerRate">
          <template #default="{ row }">
            <el-progress :percentage="row.triggerRate" :stroke-width="6" :show-text="false"
              :color="row.triggerRate > 50 ? '#67c23a' : '#e6a23c'" />
            <span class="rate-text">{{ row.triggerRate }}%</span>
          </template>
        </el-table-column>
        <el-table-column prop="avgElapsedMs" label="平均耗时" width="100" sortable>
          <template #default="{ row }">
            {{ row.avgElapsedMs }}ms
          </template>
        </el-table-column>
        <el-table-column prop="errorCount" label="异常次数" width="100" sortable />
        <el-table-column label="健康度" width="120" sortable :sort-method="(a: any, b: any) => a.healthScore - b.healthScore">
          <template #default="{ row }">
            <el-tag :type="row.healthScore > 80 ? 'success' : row.healthScore > 50 ? 'warning' : 'danger'" size="small">
              {{ row.healthScore || '—' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.monitor-dashboard {
  padding: 16px;
}

.overview-row {
  margin-bottom: 16px;
}

.metric-card {
  text-align: center;
}

.metric-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  line-height: 1.5;
}

.metric-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.chart-row {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-box {
  width: 100%;
  height: 300px;
}

.rate-text {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-left: 4px;
}

.top-rules-card {
  margin-top: 8px;
}
</style>
