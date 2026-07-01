<script setup lang="ts">
/**
 * 经营驾驶舱（批次18 增强）
 *
 * 顶部预警 banner + 核心 KPI 概览 + 维度下钻(事业部/项目类型/客户) + ECharts 可视化 +
 * KPI 月度趋势 + 60 秒自动刷新。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
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
import { PC } from '@/constants/permissionCodes'

defineOptions({ name: 'Cockpit' })

const query = ref({ period: new Date().toISOString().slice(0, 7) })
const overview = ref<any>(null)
const drillData = ref<any[]>([])
const drillDimension = ref<'dept' | 'projectType' | 'customer'>('dept')
const alert = ref<{ redCount: number; yellowCount: number; totalCount: number; topEvent: any | null } | null>(null)
const trend = ref<{ periods: string[]; contractAmountSeries: number[]; confirmedRevenueSeries: number[]; totalCostSeries: number[]; grossProfitSeries: number[]; grossMarginPctSeries: number[] } | null>(null)
const lastUpdated = ref('')

// 自动刷新
let pollTimer: number | null = null
const autoRefresh = ref(true)
const REFRESH_INTERVAL = 60_000

// ========== ECharts: 下钻分析 ==========
const chartRef = ref<HTMLDivElement | null>(null)
const { setOption: setDrillOption } = useECharts(chartRef)

// ========== ECharts: 健康度饼图 ==========
const healthRef = ref<HTMLDivElement | null>(null)
const { setOption: setHealthOption } = useECharts(healthRef)

// ========== ECharts: KPI 趋势 ==========
const trendRef = ref<HTMLDivElement | null>(null)
const { setOption: setTrendOption } = useECharts(trendRef)

async function loadOverview() {
  try {
    const { data } = await getCockpitOverview(query.value.period)
    overview.value = data ?? {}
  } catch {
    /* 静默 */
  }
}

async function loadHealth() {
  try {
    const { data } = await getEvmHealthDistribution(query.value.period)
    const d = data as any
    setHealthOption({
      title: { text: 'EVM 健康度分布', left: 'center' },
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
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
  } catch {
    /* 静默 */
  }
}

async function loadDrill() {
  try {
    let res: any
    if (drillDimension.value === 'dept') res = await drillByDept(query.value.period)
    else if (drillDimension.value === 'projectType') res = await drillByProjectType(query.value.period)
    else res = await drillByCustomer(query.value.period)
    drillData.value = (res?.data as any[]) || []
    renderDrillChart()
  } catch {
    /* 静默 */
  }
}

function renderDrillChart() {
  const rows = drillData.value
  setDrillOption({
    title: { text: '下钻分析', left: 'center' },
    tooltip: { trigger: 'axis' },
    legend: { data: ['收入', '成本', '毛利'], top: 30 },
    grid: { top: 80, left: 60, right: 40, bottom: 40 },
    xAxis: {
      type: 'category',
      data: rows.map((d: any) => d.name || d.dimension || '-'),
    },
    yAxis: { type: 'value' },
    series: [
      { name: '收入', type: 'bar', data: rows.map((d: any) => Number(d.revenue || 0)), itemStyle: { color: '#409eff' } },
      { name: '成本', type: 'bar', data: rows.map((d: any) => Number(d.cost || 0)), itemStyle: { color: '#909399' } },
      { name: '毛利', type: 'bar', data: rows.map((d: any) => Number(d.grossProfit || 0)), itemStyle: { color: '#67c23a' } },
    ],
  })
}

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
  } catch {
    /* 静默 */
  }
}

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
  } catch {
    /* 静默 */
  }
}

function renderTrendChart() {
  const t = trend.value
  if (!t) return
  setTrendOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['合同', '收入', '成本'], top: 0 },
    grid: { left: 50, right: 50, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: t.periods },
    yAxis: [
      { type: 'value', name: '金额' },
      { type: 'value', name: '毛利率(%)', position: 'right', min: 0, max: 100 },
    ],
    series: [
      { name: '合同', type: 'bar', data: t.contractAmountSeries, itemStyle: { color: '#409eff' } },
      { name: '收入', type: 'bar', data: t.confirmedRevenueSeries, itemStyle: { color: '#67c23a' } },
      { name: '成本', type: 'bar', data: t.totalCostSeries, itemStyle: { color: '#909399' } },
      {
        name: '毛利率',
        type: 'line',
        yAxisIndex: 1,
        data: t.grossMarginPctSeries,
        smooth: true,
        lineStyle: { type: 'dashed' },
        itemStyle: { color: '#f56c6c' },
      },
    ],
  })
}

async function refresh() {
  try {
    await Promise.all([loadOverview(), loadHealth(), loadDrill(), loadAlert(), loadTrend()])
    lastUpdated.value = new Date().toLocaleTimeString('zh-CN')
  } catch (e: any) {
    ElMessage.error('刷新失败：' + (e?.message || ''))
  }
}

function startPolling() {
  stopPolling()
  if (!autoRefresh.value) return
  pollTimer = window.setInterval(() => refresh(), REFRESH_INTERVAL)
}

function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

function toggleAutoRefresh() {
  autoRefresh.value = !autoRefresh.value
  if (autoRefresh.value) startPolling()
  else stopPolling()
}

// ========== 计算属性 ==========
const alertTone = computed<'success' | 'warning' | 'info' | 'error'>(() => {
  if (!alert.value) return 'info'
  if (alert.value.redCount > 0) return 'error'
  if (alert.value.yellowCount > 0) return 'warning'
  return 'success'
})

const alertMessage = computed(() => {
  if (!alert.value || alert.value.totalCount === 0) return '当前无触发预警，系统状态良好。'
  const a = alert.value
  const parts: string[] = []
  if (a.redCount > 0) parts.push(`红色 ${a.redCount} 项`)
  if (a.yellowCount > 0) parts.push(`黄色 ${a.yellowCount} 项`)
  return `存在 ${parts.join('，')} 预警事件` + (a.topEvent ? `：${a.topEvent.title}` : '')
})

function fmtMoney(v: any) {
  if (v === null || v === undefined) return '-'
  return `¥${Number(v).toLocaleString()}`
}

function fmtPct(v: any) {
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
          <div class="page-title">经营驾驶舱</div>
          <div class="page-subtitle">KPI 总览 · 维度下钻 · 实时预警</div>
        </div>
        <div class="header-right">
          <el-tag v-if="lastUpdated" type="info" effect="plain" size="small">
            最后更新：{{ lastUpdated }}
          </el-tag>
          <el-switch
            v-model="autoRefresh"
            inline-prompt
            active-text="自动"
            inactive-text="手动"
            @change="toggleAutoRefresh"
          />
          <el-button :icon="'Refresh'" @click="refresh">立即刷新</el-button>
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
        <el-form-item label="期间 (YYYY-MM)">
          <el-input v-model="query.period" placeholder="如 2026-07" style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button v-permission="[PC.COCKPIT_OVERVIEW_VIEW]" type="primary" :icon="'Refresh'" @click="refresh">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-title">合同总额</div>
          <div class="kpi-value money">{{ fmtMoney(overview?.totalContractAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-title">已确认收入</div>
          <div class="kpi-value money">{{ fmtMoney(overview?.confirmedRevenue) }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-title">累计成本</div>
          <div class="kpi-value">{{ fmtMoney(overview?.totalCost) }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="kpi-card highlight">
          <div class="kpi-title">累计毛利</div>
          <div class="kpi-value money">{{ fmtMoney(overview?.grossProfit) }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="kpi-card highlight">
          <div class="kpi-title">平均毛利率</div>
          <div class="kpi-value">{{ fmtPct(overview?.grossMargin) }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-title">在执行项目</div>
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
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="chart-card">
          <div ref="healthRef" class="chart-area" />
        </el-card>
      </el-col>
    </el-row>

    <!-- KPI 趋势 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="24">
        <el-card shadow="never" header="KPI 月度趋势（最近 12 月）">
          <div ref="trendRef" class="chart-area" style="height: 320px" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 关键提示 + 快捷入口 -->
    <el-card shadow="never" class="extra-card">
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
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
        <el-col :xs="24" :md="12">
          <h4>快捷入口</h4>
          <el-space wrap>
            <el-button :icon="'TrendCharts'" @click="$router.push('/report/executive')">高管看板</el-button>
            <el-button :icon="'WarningFilled'" @click="$router.push('/execution/risk')">风险预警</el-button>
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
    .kpi-title { font-size: 12px; color: var(--el-text-color-secondary); }
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
