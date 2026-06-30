<script setup lang="ts">
/**
 * 经营驾驶舱
 *
 * 核心 KPI 概览 + 维度下钻(事业部/项目类型/客户) + ECharts 可视化。
 */
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import {
  getCockpitOverview,
  getEvmHealthDistribution,
  getBenchCostSummary,
  getUtilizationSummary,
  drillByDept,
  drillByProjectType,
  drillByCustomer,
} from '@/api/execution/cockpit'
import { PC } from '@/constants/permissionCodes'

const query = reactive({ period: new Date().toISOString().slice(0, 7) })
const overview = ref<any>(null)
const drillData = ref<any[]>([])
const drillDimension = ref<'dept' | 'projectType' | 'customer'>('dept')

const chartRef = ref<HTMLDivElement | null>(null)
const healthRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let healthChart: echarts.ECharts | null = null

async function loadOverview() {
  try {
    const { data } = await getCockpitOverview(query.period)
    overview.value = data ?? {}
  } catch { /* 静默 */ }
}

async function loadHealth() {
  try {
    const { data } = await getEvmHealthDistribution(query.period)
    const d = data as any
    if (healthChart) {
      healthChart.setOption({
        title: { text: 'EVM 健康度分布', left: 'center' },
        tooltip: { trigger: 'item' },
        series: [
          {
            name: '健康度',
            type: 'pie',
            radius: ['40%', '70%'],
            data: [
              { name: '正常', value: d?.NORMAL ?? 0, itemStyle: { color: '#67c23a' } },
              { name: '黄色', value: d?.YELLOW ?? 0, itemStyle: { color: '#e6a23c' } },
              { name: '红色', value: d?.RED ?? 0, itemStyle: { color: '#f56c6c' } },
            ],
          },
        ],
      })
    }
  } catch { /* 静默 */ }
}

async function loadDrill() {
  try {
    let res: any
    if (drillDimension.value === 'dept') res = await drillByDept(query.period)
    else if (drillDimension.value === 'projectType') res = await drillByProjectType(query.period)
    else res = await drillByCustomer(query.period)
    drillData.value = res?.data || []
    renderDrillChart()
  } catch { /* 静默 */ }
}

function renderDrillChart() {
  if (!chart) return
  chart.setOption({
    title: { text: '下钻分析', left: 'center' },
    tooltip: { trigger: 'axis' },
    legend: { data: ['收入', '成本', '毛利'], top: 30 },
    grid: { top: 80, left: 60, right: 40, bottom: 40 },
    xAxis: {
      type: 'category',
      data: drillData.map((d: any) => d.name || d.dimension || '-'),
    },
    yAxis: { type: 'value' },
    series: [
      { name: '收入', type: 'bar', data: drillData.map((d: any) => Number(d.revenue || 0)), itemStyle: { color: '#409eff' } },
      { name: '成本', type: 'bar', data: drillData.map((d: any) => Number(d.cost || 0)), itemStyle: { color: '#909399' } },
      { name: '毛利', type: 'bar', data: drillData.map((d: any) => Number(d.grossProfit || 0)), itemStyle: { color: '#67c23a' } },
    ],
  })
}

async function refresh() {
  await loadOverview()
  await loadHealth()
  await loadDrill()
}

onMounted(() => {
  if (chartRef.value) {
    chart = echarts.init(chartRef.value)
  }
  if (healthRef.value) {
    healthChart = echarts.init(healthRef.value)
  }
  refresh()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  healthChart?.dispose()
})

function handleResize() {
  chart?.resize()
  healthChart?.resize()
}

function fmtMoney(v: any) {
  if (v == null) return '-'
  return `¥${Number(v).toLocaleString()}`
}
</script>

<template>
  <div class="cockpit-page">
    <el-card shadow="never" class="query-card">
      <el-form inline>
        <el-form-item label="期间 (YYYY-MM)">
          <el-input v-model="query.period" placeholder="如 2026-07" style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Refresh'" @click="refresh" v-permission="[PC.COCKPIT_OVERVIEW_VIEW]">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="16" class="kpi-row">
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-title">总收入</div>
          <div class="kpi-value money">{{ fmtMoney((overview as any)?.totalRevenue) }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-title">总成本</div>
          <div class="kpi-value">{{ fmtMoney((overview as any)?.totalCost) }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="kpi-card highlight">
          <div class="kpi-title">总毛利</div>
          <div class="kpi-value money">{{ fmtMoney((overview as any)?.totalGrossProfit) }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-title">活跃项目数</div>
          <div class="kpi-value">{{ (overview as any)?.activeProjectCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-title">回款率</div>
          <div class="kpi-value">{{ (((overview as any)?.collectionRate || 0) * 100).toFixed(1) }}%</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-title">平均毛利率</div>
          <div class="kpi-value">{{ (((overview as any)?.avgMargin || 0) * 100).toFixed(1) }}%</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-row">
      <el-col :span="16">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="chart-header">
              <span>下钻分析</span>
              <el-radio-group v-model="drillDimension" size="small" @change="loadDrill">
                <el-radio-button value="dept">事业部</el-radio-button>
                <el-radio-button value="projectType">项目类型</el-radio-button>
                <el-radio-button value="customer">客户</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="chartRef" class="chart-area" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="chart-card">
          <div ref="healthRef" class="chart-area" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="extra-card">
      <el-row :gutter="16">
        <el-col :span="12">
          <h4>关键提示</h4>
          <ul class="hint-list">
            <li v-for="(h, i) in (overview as any)?.hints || []" :key="i">
              <el-tag :type="(h.level as any) === 'RED' ? 'danger' : (h.level as any) === 'YELLOW' ? 'warning' : 'info'" size="small">
                {{ h.level }}
              </el-tag>
              {{ h.message }}
            </li>
            <li v-if="!((overview as any)?.hints || []).length" class="empty">暂无提示</li>
          </ul>
        </el-col>
        <el-col :span="12">
          <h4>快捷入口</h4>
          <el-space wrap>
            <el-button :icon="'TrendCharts'" @click="$router.push('/execution/risk')">风险预警</el-button>
            <el-button :icon="'Document'" @click="$router.push('/report')">利润报表</el-button>
            <el-button :icon="'Coin'" @click="$router.push('/execution/invoice')">发票管理</el-button>
            <el-button :icon="'Money'" @click="$router.push('/execution/payment')">回款管理</el-button>
          </el-space>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.cockpit-page {
  .query-card { margin-bottom: 16px; }
  .kpi-row { margin-bottom: 16px; }
  .kpi-card {
    text-align: center;
    .kpi-title { font-size: 12px; color: var(--el-text-color-secondary); }
    .kpi-value { font-size: 22px; font-weight: 600; margin-top: 8px;
      &.money { color: var(--el-color-primary); }
    }
    &.highlight .kpi-value { color: var(--el-color-success); }
  }
  .chart-row { margin-bottom: 16px; }
  .chart-card {
    .chart-header { display: flex; justify-content: space-between; align-items: center; }
    .chart-area { width: 100%; height: 360px; }
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
