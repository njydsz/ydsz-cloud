<script setup lang="ts">
/**
 * 报表中心 (P4-2 高级报表可视化)
 *
 * 提供项目利润、成本归集、回款台账、生命周期台账、EVM 挣值、
 * 双费率对比、风险看板、利用率、Bench 成本等核心报表的 ECharts 可视化。
 */
import { ref, reactive, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import {
  getProjectProfitReport,
  getCostDetailReport,
  getPaymentLedger,
  getLifecycleReport,
  getProfitSummary,
  getEvmReport,
  getDualRateComparison,
  getRiskDashboard,
  getUtilizationReport,
  getBenchCostReport,
} from '@/api/execution/report'

type TabKey =
  | 'profit'
  | 'cost'
  | 'payment'
  | 'lifecycle'
  | 'summary'
  | 'evm'
  | 'dualRate'
  | 'risk'
  | 'utilization'
  | 'bench'

const tab = ref<TabKey>('profit')
const loading = ref(false)
const reportData = ref<any>(null)
const summaryData = ref<any[]>([])
const listData = ref<any[]>([])

const query = reactive({ initiationId: undefined as number | undefined, period: '' })

// ECharts 实例容器
const chartRefs = reactive<Record<string, HTMLDivElement | null>>({})
const charts: Record<string, echarts.ECharts | null> = {}

function setRef(key: string) {
  return (el: any) => {
    chartRefs[key] = el as HTMLDivElement | null
  }
}

async function load(target: TabKey) {
  if (
    target !== 'summary' &&
    target !== 'risk' &&
    target !== 'utilization' &&
    target !== 'bench' &&
    !query.initiationId
  ) {
    ElMessage.warning('请填写项目 ID')
    return
  }
  loading.value = true
  try {
    let res: any
    listData.value = []
    reportData.value = null
    summaryData.value = []
    switch (target) {
      case 'profit':
        res = await getProjectProfitReport(query.initiationId!, query.period)
        reportData.value = res?.data ?? null
        break
      case 'cost':
        res = await getCostDetailReport(query.initiationId!, query.period)
        reportData.value = res?.data ?? null
        break
      case 'payment':
        res = await getPaymentLedger(query.initiationId!)
        reportData.value = res?.data ?? null
        break
      case 'lifecycle':
        res = await getLifecycleReport(query.initiationId!)
        reportData.value = res?.data ?? null
        break
      case 'summary':
        res = await getProfitSummary()
        summaryData.value = (res?.data as any[]) || []
        renderSummaryChart()
        return
      case 'evm':
        res = await getEvmReport(query.initiationId!, query.period)
        reportData.value = res?.data ?? null
        break
      case 'dualRate':
        res = await getDualRateComparison(query.initiationId!)
        reportData.value = res?.data ?? null
        break
      case 'risk':
        res = await getRiskDashboard(query.period)
        reportData.value = res?.data ?? null
        break
      case 'utilization':
        res = await getUtilizationReport(query.period)
        listData.value = (res?.data as any[]) || []
        renderUtilizationChart()
        return
      case 'bench':
        res = await getBenchCostReport(query.period)
        reportData.value = res?.data ?? null
        break
    }
    await nextTick()
    renderChartForTab(target)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
    reportData.value = null
  } finally {
    loading.value = false
  }
}

function onTabChange(v: any) {
  tab.value = v as TabKey
  if (
    v === 'summary' ||
    v === 'risk' ||
    v === 'utilization' ||
    v === 'bench' ||
    (query.initiationId && v)
  ) {
    load(v)
  } else {
    reportData.value = null
    listData.value = []
    summaryData.value = []
  }
}

function fmtMoney(v: any) {
  if (v === null || v === undefined) return '-'
  return `¥${Number(v).toLocaleString()}`
}
function fmtPct(v: any) {
  if (v === null || v === undefined) return '-'
  return `${(Number(v) * 100).toFixed(2)}%`
}
function toNumber(v: any, def = 0) {
  const n = Number(v)
  return Number.isFinite(n) ? n : def
}

// ============== 渲染分发 ==============

async function renderChartForTab(key: TabKey) {
  await nextTick()
  if (key === 'profit') renderProfitCharts()
  else if (key === 'cost') renderCostCharts()
  else if (key === 'evm') renderEvmChart()
  else if (key === 'dualRate') renderDualRateChart()
  else if (key === 'risk') renderRiskChart()
  else if (key === 'bench') renderBenchChart()
}

function ensureChart(key: string) {
  const el = chartRefs[key]
  if (!el) return null
  if (!charts[key]) charts[key] = echarts.init(el)
  return charts[key]
}

function renderProfitCharts() {
  const d = reportData.value || {}
  // 收入/成本/毛利对比柱状图
  const c1 = ensureChart('profit-bar')
  c1?.setOption({
    title: { text: '收入 / 成本 / 毛利 对比', left: 'center' },
    tooltip: { trigger: 'axis' },
    grid: { top: 60, left: 60, right: 40, bottom: 40 },
    xAxis: { type: 'category', data: ['金额'] },
    yAxis: { type: 'value' },
    series: [
      { name: '收入', type: 'bar', data: [toNumber(d.revenue)], itemStyle: { color: '#409eff' } },
      { name: '总成本', type: 'bar', data: [toNumber(d.totalCost)], itemStyle: { color: '#909399' } },
      { name: '毛利', type: 'bar', data: [toNumber(d.grossProfit)], itemStyle: { color: '#67c23a' } },
    ],
  })
  // 成本构成饼图
  const c2 = ensureChart('profit-pie')
  c2?.setOption({
    title: { text: '成本构成', left: 'center' },
    tooltip: { trigger: 'item' },
    series: [
      {
        name: '成本',
        type: 'pie',
        radius: ['40%', '70%'],
        data: [
          { name: '人工', value: toNumber(d.laborCost), itemStyle: { color: '#409eff' } },
          { name: '采购', value: toNumber(d.purchaseCost), itemStyle: { color: '#e6a23c' } },
          { name: '费用', value: toNumber(d.expenseCost), itemStyle: { color: '#f56c6c' } },
        ],
      },
    ],
  })
}

function renderCostCharts() {
  const d = reportData.value || {}
  const c1 = ensureChart('cost-pie')
  c1?.setOption({
    title: { text: '成本占比', left: 'center' },
    tooltip: { trigger: 'item' },
    series: [
      {
        name: '占比',
        type: 'pie',
        radius: '60%',
        data: [
          { name: '人工', value: toNumber(d.laborRatio), itemStyle: { color: '#409eff' } },
          { name: '采购', value: toNumber(d.purchaseRatio), itemStyle: { color: '#e6a23c' } },
          { name: '费用', value: toNumber(d.expenseRatio), itemStyle: { color: '#f56c6c' } },
        ],
      },
    ],
  })
}

function renderEvmChart() {
  const d = reportData.value || {}
  const c1 = ensureChart('evm-line')
  c1?.setOption({
    title: { text: 'EVM 挣值分析', left: 'center' },
    tooltip: { trigger: 'axis' },
    legend: { data: ['PV', 'EV', 'AC'], top: 30 },
    grid: { top: 80, left: 60, right: 40, bottom: 40 },
    xAxis: { type: 'category', data: ['计划值 PV', '挣值 EV', '实际成本 AC'] },
    yAxis: { type: 'value' },
    series: [
      { name: 'PV', type: 'bar', data: [toNumber(d.pv), 0, 0], itemStyle: { color: '#409eff' } },
      { name: 'EV', type: 'bar', data: [0, toNumber(d.ev), 0], itemStyle: { color: '#67c23a' } },
      { name: 'AC', type: 'bar', data: [0, 0, toNumber(d.ac)], itemStyle: { color: '#f56c6c' } },
    ],
  })
  const c2 = ensureChart('evm-index')
  c2?.setOption({
    title: { text: 'CPI / SPI 指标', left: 'center' },
    tooltip: { trigger: 'axis' },
    radar: {
      indicator: [
        { name: 'CPI', max: 2 },
        { name: 'SPI', max: 2 },
        { name: '进度', max: 100 },
        { name: '健康度', max: 100 },
      ],
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: [
              toNumber(d.cpi),
              toNumber(d.spi),
              toNumber(d.progress) || 50,
              toNumber(d.healthScore) || 60,
            ],
            name: '项目',
            areaStyle: { color: 'rgba(64,158,255,0.3)' },
            itemStyle: { color: '#409eff' },
          },
        ],
      },
    ],
  })
}

function renderDualRateChart() {
  const d = reportData.value || {}
  const c1 = ensureChart('dual-bar')
  c1?.setOption({
    title: { text: '双费率利润对比', left: 'center' },
    tooltip: { trigger: 'axis' },
    legend: { data: ['对外报价', '对内成本'], top: 30 },
    grid: { top: 80, left: 60, right: 40, bottom: 40 },
    xAxis: { type: 'category', data: ['收入', '毛利', '毛利率(%)'] },
    yAxis: { type: 'value' },
    series: [
      {
        name: '对外报价',
        type: 'bar',
        data: [toNumber(d.externalRevenue), toNumber(d.externalGrossProfit), toNumber(d.externalMargin) * 100],
        itemStyle: { color: '#409eff' },
      },
      {
        name: '对内成本',
        type: 'bar',
        data: [toNumber(d.internalRevenue), toNumber(d.internalGrossProfit), toNumber(d.internalMargin) * 100],
        itemStyle: { color: '#67c23a' },
      },
    ],
  })
}

function renderRiskChart() {
  const d = reportData.value || {}
  const c1 = ensureChart('risk-pie')
  c1?.setOption({
    title: { text: '项目风险分布', left: 'center' },
    tooltip: { trigger: 'item' },
    series: [
      {
        name: '风险等级',
        type: 'pie',
        radius: ['40%', '70%'],
        data: [
          { name: '高风险', value: toNumber(d.highRiskCount), itemStyle: { color: '#f56c6c' } },
          { name: '中风险', value: toNumber(d.mediumRiskCount), itemStyle: { color: '#e6a23c' } },
          { name: '低风险', value: toNumber(d.lowRiskCount), itemStyle: { color: '#67c23a' } },
        ],
      },
    ],
  })
  const c2 = ensureChart('risk-bar')
  c2?.setOption({
    title: { text: '预警事件分布', left: 'center' },
    tooltip: { trigger: 'axis' },
    grid: { top: 60, left: 60, right: 40, bottom: 40 },
    xAxis: { type: 'category', data: ['高', '中', '低', '合计'] },
    yAxis: { type: 'value' },
    series: [
      {
        type: 'bar',
        data: [
          toNumber(d.highAlerts),
          toNumber(d.mediumAlerts),
          toNumber(d.lowAlerts),
          toNumber(d.alertCount),
        ],
        itemStyle: { color: '#409eff' },
      },
    ],
  })
}

function renderUtilizationChart() {
  const rows = listData.value || []
  const c1 = ensureChart('util-bar')
  c1?.setOption({
    title: { text: '可计费利用率排行', left: 'center' },
    tooltip: { trigger: 'axis' },
    grid: { top: 60, left: 100, right: 40, bottom: 40 },
    xAxis: { type: 'value', max: 100 },
    yAxis: {
      type: 'category',
      data: rows.map((r) => r.employeeName || r.name || '-').reverse(),
    },
    series: [
      {
        type: 'bar',
        data: rows.map((r) => toNumber(r.utilization)).reverse(),
        itemStyle: { color: '#67c23a' },
        label: { show: true, position: 'right', formatter: '{c}%' },
      },
    ],
  })
}

function renderBenchChart() {
  const d = reportData.value || {}
  const rows = d.records || d.items || d.list || []
  const c1 = ensureChart('bench-line')
  c1?.setOption({
    title: { text: 'Bench 闲置成本趋势', left: 'center' },
    tooltip: { trigger: 'axis' },
    grid: { top: 60, left: 60, right: 40, bottom: 40 },
    xAxis: { type: 'category', data: rows.map((r: any) => r.period || r.date || '-') },
    yAxis: { type: 'value' },
    series: [
      {
        type: 'line',
        smooth: true,
        data: rows.map((r: any) => toNumber(r.totalCost || r.cost)),
        areaStyle: { color: 'rgba(230,162,60,0.3)' },
        itemStyle: { color: '#e6a23c' },
      },
    ],
  })
}

function renderSummaryChart() {
  const rows = summaryData.value || []
  const c1 = ensureChart('summary-bar')
  c1?.setOption({
    title: { text: '跨项目利润对比', left: 'center' },
    tooltip: { trigger: 'axis' },
    legend: { data: ['收入', '总成本', '毛利'], top: 30 },
    grid: { top: 80, left: 100, right: 40, bottom: 60 },
    xAxis: {
      type: 'category',
      data: rows.map((r) => r.initiationName || `项目${r.initiationId}`),
      axisLabel: { rotate: 30 },
    },
    yAxis: { type: 'value' },
    series: [
      { name: '收入', type: 'bar', data: rows.map((r) => toNumber(r.revenue)), itemStyle: { color: '#409eff' } },
      { name: '总成本', type: 'bar', data: rows.map((r) => toNumber(r.totalCost)), itemStyle: { color: '#909399' } },
      { name: '毛利', type: 'bar', data: rows.map((r) => toNumber(r.grossProfit)), itemStyle: { color: '#67c23a' } },
    ],
  })
}

watch(tab, () => {
  // tab 切换时重新渲染
  if (reportData.value || listData.value.length || summaryData.value.length) {
    renderChartForTab(tab.value)
    if (tab.value === 'summary') renderSummaryChart()
    if (tab.value === 'utilization') renderUtilizationChart()
  }
})

function handleResize() {
  Object.values(charts).forEach((c) => c?.resize())
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  Object.values(charts).forEach((c) => c?.dispose())
})
</script>

<template>
  <div class="report-page">
    <el-card shadow="never" class="query-card">
      <el-form inline :model="query">
        <el-form-item label="项目 ID">
          <el-input-number v-model="query.initiationId" :min="0" :controls="false" />
        </el-form-item>
        <el-form-item label="期间 (YYYY-MM)">
          <el-input v-model="query.period" placeholder="如 2026-07" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="load(tab)">查询</el-button>
          <el-button @click="query.initiationId = undefined; query.period = ''; reportData = null; listData = []; summaryData = []">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="report-card" v-loading="loading">
      <el-tabs :model-value="tab" @update:model-value="onTabChange">
        <el-tab-pane label="项目利润表" name="profit" />
        <el-tab-pane label="成本归集" name="cost" />
        <el-tab-pane label="回款台账" name="payment" />
        <el-tab-pane label="生命周期" name="lifecycle" />
        <el-tab-pane label="跨项目汇总" name="summary" />
        <el-tab-pane label="EVM 报表" name="evm" />
        <el-tab-pane label="双费率对比" name="dualRate" />
        <el-tab-pane label="风险看板" name="risk" />
        <el-tab-pane label="利用率排行" name="utilization" />
        <el-tab-pane label="Bench 成本" name="bench" />
      </el-tabs>

      <!-- 利润 -->
      <div v-if="tab === 'profit' && reportData" class="grid">
        <div class="kpi"><div class="kpi-label">收入</div><div class="kpi-value money">{{ fmtMoney((reportData as any).revenue) }}</div></div>
        <div class="kpi"><div class="kpi-label">人工成本</div><div class="kpi-value">{{ fmtMoney((reportData as any).laborCost) }}</div></div>
        <div class="kpi"><div class="kpi-label">采购成本</div><div class="kpi-value">{{ fmtMoney((reportData as any).purchaseCost) }}</div></div>
        <div class="kpi"><div class="kpi-label">费用成本</div><div class="kpi-value">{{ fmtMoney((reportData as any).expenseCost) }}</div></div>
        <div class="kpi highlight"><div class="kpi-label">毛利</div><div class="kpi-value money">{{ fmtMoney((reportData as any).grossProfit) }}</div></div>
        <div class="kpi highlight"><div class="kpi-label">毛利率</div><div class="kpi-value">{{ fmtPct((reportData as any).grossMargin) }}</div></div>
      </div>
      <el-row v-if="tab === 'profit' && reportData" :gutter="16" class="chart-row">
        <el-col :span="12"><div :ref="setRef('profit-bar')" class="chart-area" /></el-col>
        <el-col :span="12"><div :ref="setRef('profit-pie')" class="chart-area" /></el-col>
      </el-row>

      <!-- 成本归集 -->
      <template v-else-if="tab === 'cost' && reportData">
        <div class="grid">
          <div class="kpi"><div class="kpi-label">总成本</div><div class="kpi-value money">{{ fmtMoney((reportData as any).totalCost) }}</div></div>
          <div class="kpi"><div class="kpi-label">人工占比</div><div class="kpi-value">{{ fmtPct((reportData as any).laborRatio) }}</div></div>
          <div class="kpi"><div class="kpi-label">采购占比</div><div class="kpi-value">{{ fmtPct((reportData as any).purchaseRatio) }}</div></div>
          <div class="kpi"><div class="kpi-label">费用占比</div><div class="kpi-value">{{ fmtPct((reportData as any).expenseRatio) }}</div></div>
        </div>
        <el-row :gutter="16" class="chart-row">
          <el-col :span="12" :offset="6"><div :ref="setRef('cost-pie')" class="chart-area" /></el-col>
        </el-row>
      </template>

      <!-- 回款台账 -->
      <div v-else-if="tab === 'payment' && reportData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="累计开票">{{ fmtMoney((reportData as any).invoicedAmount) }}</el-descriptions-item>
          <el-descriptions-item label="累计回款">{{ fmtMoney((reportData as any).receivedAmount) }}</el-descriptions-item>
          <el-descriptions-item label="未回款">{{ fmtMoney((reportData as any).outstandingAmount) }}</el-descriptions-item>
          <el-descriptions-item label="回款率">{{ fmtPct((reportData as any).collectionRate) }}</el-descriptions-item>
        </el-descriptions>
        <vxe-table :data="(reportData as any).ledgers || []" border style="margin-top: 12px">
          <vxe-column type="seq" title="#" width="50" />
          <vxe-column field="date" title="日期" width="120" />
          <vxe-column field="type" title="类型" width="100" />
          <vxe-column field="code" title="单号" width="160" />
          <vxe-column field="amount" title="金额" width="140" align="right" :formatter="({ cellValue }: any) => fmtMoney(cellValue)" />
          <vxe-column field="remark" title="备注" min-width="200" />
        </vxe-table>
      </div>

      <!-- 生命周期台账 -->
      <div v-else-if="tab === 'lifecycle' && reportData">
        <el-timeline>
          <el-timeline-item
            v-for="(item, idx) in (reportData as any).stages || []"
            :key="idx"
            :timestamp="item.date"
            :type="item.type as any"
          >
            <h4>{{ item.stage }}</h4>
            <p>{{ item.description }}</p>
          </el-timeline-item>
        </el-timeline>
      </div>

      <!-- 跨项目汇总 -->
      <div v-else-if="tab === 'summary'">
        <vxe-table :data="summaryData" border stripe>
          <vxe-column field="initiationId" title="项目 ID" width="100" align="center" />
          <vxe-column field="initiationName" title="项目名称" min-width="200" show-overflow />
          <vxe-column field="revenue" title="收入" width="140" align="right" :formatter="({ cellValue }: any) => fmtMoney(cellValue)" />
          <vxe-column field="totalCost" title="总成本" width="140" align="right" :formatter="({ cellValue }: any) => fmtMoney(cellValue)" />
          <vxe-column field="grossProfit" title="毛利" width="140" align="right" :formatter="({ cellValue }: any) => fmtMoney(cellValue)" />
          <vxe-column field="grossMargin" title="毛利率" width="120" align="right" :formatter="({ cellValue }: any) => fmtPct(cellValue)" />
        </vxe-table>
        <el-row :gutter="16" class="chart-row">
          <el-col :span="24"><div :ref="setRef('summary-bar')" class="chart-area" /></el-col>
        </el-row>
      </div>

      <!-- EVM -->
      <template v-else-if="tab === 'evm' && reportData">
        <div class="grid">
          <div class="kpi"><div class="kpi-label">PV (计划值)</div><div class="kpi-value money">{{ fmtMoney((reportData as any).pv) }}</div></div>
          <div class="kpi"><div class="kpi-label">EV (挣值)</div><div class="kpi-value money">{{ fmtMoney((reportData as any).ev) }}</div></div>
          <div class="kpi"><div class="kpi-label">AC (实际成本)</div><div class="kpi-value money">{{ fmtMoney((reportData as any).ac) }}</div></div>
          <div class="kpi"><div class="kpi-label">BAC (完工预算)</div><div class="kpi-value money">{{ fmtMoney((reportData as any).bac) }}</div></div>
          <div class="kpi highlight"><div class="kpi-label">CPI</div><div class="kpi-value">{{ (reportData as any).cpi?.toFixed?.(2) || '-' }}</div></div>
          <div class="kpi highlight"><div class="kpi-label">SPI</div><div class="kpi-value">{{ (reportData as any).spi?.toFixed?.(2) || '-' }}</div></div>
        </div>
        <el-row :gutter="16" class="chart-row">
          <el-col :span="12"><div :ref="setRef('evm-line')" class="chart-area" /></el-col>
          <el-col :span="12"><div :ref="setRef('evm-index')" class="chart-area" /></el-col>
        </el-row>
      </template>

      <!-- 双费率对比 -->
      <template v-else-if="tab === 'dualRate' && reportData">
        <div class="grid">
          <div class="kpi"><div class="kpi-label">外部费率总收入</div><div class="kpi-value money">{{ fmtMoney((reportData as any).externalRevenue) }}</div></div>
          <div class="kpi"><div class="kpi-label">内部费率总收入</div><div class="kpi-value money">{{ fmtMoney((reportData as any).internalRevenue) }}</div></div>
          <div class="kpi"><div class="kpi-label">外部毛利</div><div class="kpi-value money">{{ fmtMoney((reportData as any).externalGrossProfit) }}</div></div>
          <div class="kpi"><div class="kpi-label">内部毛利</div><div class="kpi-value money">{{ fmtMoney((reportData as any).internalGrossProfit) }}</div></div>
          <div class="kpi highlight"><div class="kpi-label">外部毛利率</div><div class="kpi-value">{{ fmtPct((reportData as any).externalMargin) }}</div></div>
          <div class="kpi highlight"><div class="kpi-label">内部毛利率</div><div class="kpi-value">{{ fmtPct((reportData as any).internalMargin) }}</div></div>
        </div>
        <el-row :gutter="16" class="chart-row">
          <el-col :span="24"><div :ref="setRef('dual-bar')" class="chart-area" /></el-col>
        </el-row>
      </template>

      <!-- 风险看板 -->
      <template v-else-if="tab === 'risk' && reportData">
        <div class="grid">
          <div class="kpi"><div class="kpi-label">高风险项目</div><div class="kpi-value">{{ (reportData as any).highRiskCount ?? 0 }}</div></div>
          <div class="kpi"><div class="kpi-label">中风险项目</div><div class="kpi-value">{{ (reportData as any).mediumRiskCount ?? 0 }}</div></div>
          <div class="kpi"><div class="kpi-label">低风险项目</div><div class="kpi-value">{{ (reportData as any).lowRiskCount ?? 0 }}</div></div>
          <div class="kpi"><div class="kpi-label">预警事件总数</div><div class="kpi-value">{{ (reportData as any).alertCount ?? 0 }}</div></div>
        </div>
        <el-row :gutter="16" class="chart-row">
          <el-col :span="12"><div :ref="setRef('risk-pie')" class="chart-area" /></el-col>
          <el-col :span="12"><div :ref="setRef('risk-bar')" class="chart-area" /></el-col>
        </el-row>
      </template>

      <!-- 利用率排行 -->
      <div v-else-if="tab === 'utilization'">
        <vxe-table :data="listData" border stripe>
          <vxe-column type="seq" title="#" width="50" />
          <vxe-column field="employeeName" title="姓名" min-width="120" />
          <vxe-column field="department" title="部门" min-width="120" />
          <vxe-column field="utilization" title="利用率" width="120" align="right" :formatter="({ cellValue }: any) => `${Number(cellValue).toFixed(1)}%`" />
          <vxe-column field="billableHours" title="可计费工时" width="120" align="right" />
        </vxe-table>
        <el-row :gutter="16" class="chart-row">
          <el-col :span="24"><div :ref="setRef('util-bar')" class="chart-area" /></el-col>
        </el-row>
      </div>

      <!-- Bench 成本 -->
      <div v-else-if="tab === 'bench' && reportData">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="闲置人员数">{{ (reportData as any).headCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="累计成本">{{ fmtMoney((reportData as any).totalCost) }}</el-descriptions-item>
          <el-descriptions-item label="平均闲置天数">{{ (reportData as any).avgDays ?? 0 }}</el-descriptions-item>
        </el-descriptions>
        <el-row :gutter="16" class="chart-row">
          <el-col :span="24"><div :ref="setRef('bench-line')" class="chart-area" /></el-col>
        </el-row>
      </div>

      <el-empty
        v-if="!reportData && !listData.length && !summaryData.length"
        description="请填写项目 ID 并点击查询，或选择无参报表"
      />
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.report-page {
  .query-card { margin-bottom: 16px; }
  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; }
  .kpi {
    padding: 16px;
    background: var(--el-fill-color-light);
    border-radius: 4px;
    &.highlight { background: var(--el-color-primary-light-9); }
    .kpi-label { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 8px; }
    .kpi-value { font-size: 20px; font-weight: 600; &.money { color: var(--el-color-primary); } }
  }
  .chart-row { margin-top: 16px; }
  .chart-area { width: 100%; height: 320px; }
}
</style>
