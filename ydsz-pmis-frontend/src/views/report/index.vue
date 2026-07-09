<!--
  @fileoverview 报表中心
  @description 项目利润 / 成本明细 / 回款台账 / 生命周期台账 / EVM 挣值 / 双费率对比 / 风险看板 / 利用率 / Bench 成本等核心报表的 ECharts 可视化；对接 @/api/execution/report。
               顶部 Tab 切换 11 类报表视图，下方提供查询条件（项目 ID / 期间）与图表渲染容器。
               所有图表实例通过 useECharts composable 统一管理生命周期（init/setOption/resize/dispose）。
  @module views/report
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
import { ref, reactive, onMounted, nextTick, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from '@/utils/echarts'
import {
  getProjectProfitReport,
  getCostDetailReport,
  getPaymentLedger,
  getLifecycleReport,
  getProfitSummary,
  getEvmReport,
  getDualRateComparison,
  getRiskDashboard,
  getUtilizationRank,
  getBenchCostReport,
  getResourceGantt,
} from '@/api/execution/report'
import { useECharts } from '@/composables/useECharts'
import { chartColors } from '@/utils/chart-theme'

type TabKey =
  | 'profit'
  | 'cost'
  | 'payment'
  | 'lifecycle'
  | 'summary'
  | 'evm'
  | 'dualRate'
  | 'gantt'
  | 'risk'
  | 'utilization'
  | 'bench'

/** el-timeline-item 的 type 属性类型 */
type TimelineType = 'primary' | 'success' | 'warning' | 'danger' | 'info' | ''

const { t } = useI18n()

const tab = ref<TabKey>('profit')
const loading = ref(false)
const reportData = ref<Record<string, unknown> | null>(null)
const summaryData = ref<Record<string, unknown>[]>([])
const listData = ref<Record<string, unknown>[]>([])

const query = reactive({ initiationId: undefined as number | undefined, period: '' })

// ===== ECharts 容器 refs（每个图表一个 ref，配合 useECharts 自动管理生命周期） =====
const profitBarRef = ref<HTMLDivElement | null>(null)
const profitPieRef = ref<HTMLDivElement | null>(null)
const costPieRef = ref<HTMLDivElement | null>(null)
const evmLineRef = ref<HTMLDivElement | null>(null)
const evmIndexRef = ref<HTMLDivElement | null>(null)
const dualBarRef = ref<HTMLDivElement | null>(null)
const riskPieRef = ref<HTMLDivElement | null>(null)
const riskBarRef = ref<HTMLDivElement | null>(null)
const utilBarRef = ref<HTMLDivElement | null>(null)
const benchLineRef = ref<HTMLDivElement | null>(null)
const summaryBarRef = ref<HTMLDivElement | null>(null)

// ===== useECharts 实例化（自动 init / setOption / resize / dispose） =====
const { setOption: setProfitBarOption } = useECharts(profitBarRef)
const { setOption: setProfitPieOption } = useECharts(profitPieRef)
const { setOption: setCostPieOption } = useECharts(costPieRef)
const { setOption: setEvmLineOption } = useECharts(evmLineRef)
const { setOption: setEvmIndexOption } = useECharts(evmIndexRef)
const { setOption: setDualBarOption } = useECharts(dualBarRef)
const { setOption: setRiskPieOption } = useECharts(riskPieRef)
const { setOption: setRiskBarOption } = useECharts(riskBarRef)
const { setOption: setUtilBarOption } = useECharts(utilBarRef)
const { setOption: setBenchLineOption } = useECharts(benchLineRef)
const { setOption: setSummaryBarOption } = useECharts(summaryBarRef)

async function load(target: TabKey) {
  // P0 修复: 调整 initiationId 必传规则
  // - summary / dualRate / risk / utilization / bench 不依赖 initiationId
  // - profit / cost / payment / lifecycle / evm / gantt 需要 initiationId
  if (
    target !== 'summary' &&
    target !== 'dualRate' &&
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
    let res: { data?: unknown } | undefined
    listData.value = []
    reportData.value = null
    summaryData.value = []
    switch (target) {
      case 'profit':
        res = await getProjectProfitReport(query.initiationId!, query.period)
        reportData.value = (res?.data as Record<string, unknown>) ?? null
        break
      case 'cost':
        res = await getCostDetailReport(query.initiationId!, query.period)
        reportData.value = (res?.data as Record<string, unknown>) ?? null
        break
      case 'payment':
        res = await getPaymentLedger(query.initiationId!)
        reportData.value = (res?.data as Record<string, unknown>) ?? null
        break
      case 'lifecycle':
        res = await getLifecycleReport(query.initiationId!)
        reportData.value = (res?.data as Record<string, unknown>) ?? null
        break
      case 'summary':
        res = await getProfitSummary()
        summaryData.value = (res?.data as Record<string, unknown>[]) || []
        renderSummaryChart()
        return
      case 'evm':
        res = await getEvmReport(query.initiationId!)
        listData.value = (res?.data as Record<string, unknown>[]) || []
        break
      case 'dualRate':
        res = await getDualRateComparison(query.period)
        listData.value = (res?.data as Record<string, unknown>[]) || []
        break
      case 'gantt':
        res = await getResourceGantt(query.initiationId!)
        listData.value = (res?.data as Record<string, unknown>[]) || []
        break
      case 'risk':
        res = await getRiskDashboard()
        listData.value = (res?.data as Record<string, unknown>[]) || []
        break
      case 'utilization':
        res = await getUtilizationRank(20)
        listData.value = (res?.data as Record<string, unknown>[]) || []
        renderUtilizationChart()
        return
      case 'bench':
        res = await getBenchCostReport()
        listData.value = (res?.data as Record<string, unknown>[]) || []
        renderBenchChart()
        return
    }
    await nextTick()
    renderChartForTab(target)
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || t('report.messages.loadFailed'))
    reportData.value = null
  } finally {
    loading.value = false
  }
}

function onTabChange(v: string | number) {
  const key = v as TabKey
  tab.value = key
  if (
    key === 'summary' ||
    key === 'risk' ||
    key === 'utilization' ||
    key === 'bench' ||
    (query.initiationId && key)
  ) {
    load(key)
  } else {
    reportData.value = null
    listData.value = []
    summaryData.value = []
  }
}

function fmtMoney(v: unknown) {
  if (v === null || v === undefined) return '-'
  return `¥${Number(v).toLocaleString()}`
}
function fmtPct(v: unknown) {
  if (v === null || v === undefined) return '-'
  return `${(Number(v) * 100).toFixed(2)}%`
}
function toNumber(v: unknown, def = 0) {
  const n = Number(v)
  return Number.isFinite(n) ? n : def
}

/** vxe-column 金额格式化器 */
const moneyFormatter = ({ cellValue }: { cellValue: unknown }) => fmtMoney(cellValue)
/** vxe-column 百分比格式化器 */
const pctFormatter = ({ cellValue }: { cellValue: unknown }) => fmtPct(cellValue)
/** vxe-column 利用率格式化器 */
const utilizationFormatter = ({ cellValue }: { cellValue: unknown }) => `${Number(cellValue).toFixed(1)}%`

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

function renderProfitCharts() {
  const d = reportData.value || {}
  // 收入/成本/毛利对比柱状图
  setProfitBarOption({
    title: { text: t('report.profit.charts.barTitle'), left: 'center' },
    tooltip: { trigger: 'axis' },
    grid: { top: 60, left: 60, right: 40, bottom: 40 },
    xAxis: { type: 'category', data: ['金额'] },
    yAxis: { type: 'value' },
    series: [
      { name: t('report.profit.charts.seriesRevenue'), type: 'bar', data: [toNumber(d.revenue)], itemStyle: { color: chartColors.primary } },
      { name: t('report.profit.charts.seriesTotalCost'), type: 'bar', data: [toNumber(d.totalCost)], itemStyle: { color: chartColors.info } },
      { name: t('report.profit.charts.seriesGrossProfit'), type: 'bar', data: [toNumber(d.grossProfit)], itemStyle: { color: chartColors.success } },
    ],
  } as EChartsOption)
  // 成本构成饼图
  setProfitPieOption({
    title: { text: t('report.profit.charts.pieTitle'), left: 'center' },
    tooltip: { trigger: 'item' },
    series: [
      {
        name: t('report.profit.charts.pieTitle'),
        type: 'pie',
        radius: ['40%', '70%'],
        itemStyle: { borderColor: chartColors.borderColor, borderWidth: 2 },
        data: [
          { name: t('report.profit.charts.costLabor'), value: toNumber(d.laborCost), itemStyle: { color: chartColors.primary } },
          { name: t('report.profit.charts.costPurchase'), value: toNumber(d.purchaseCost), itemStyle: { color: chartColors.warning } },
          { name: t('report.profit.charts.costExpense'), value: toNumber(d.expenseCost), itemStyle: { color: chartColors.danger } },
        ],
      },
    ],
  } as EChartsOption)
}

function renderCostCharts() {
  const d = reportData.value || {}
  setCostPieOption({
    title: { text: t('report.cost.charts.pieTitle'), left: 'center' },
    tooltip: { trigger: 'item' },
    series: [
      {
        name: t('report.cost.charts.pieTitle'),
        type: 'pie',
        radius: '60%',
        itemStyle: { borderColor: chartColors.borderColor, borderWidth: 2 },
        data: [
          { name: t('report.profit.charts.costLabor'), value: toNumber(d.laborRatio), itemStyle: { color: chartColors.primary } },
          { name: t('report.profit.charts.costPurchase'), value: toNumber(d.purchaseRatio), itemStyle: { color: chartColors.warning } },
          { name: t('report.profit.charts.costExpense'), value: toNumber(d.expenseRatio), itemStyle: { color: chartColors.danger } },
        ],
      },
    ],
  } as EChartsOption)
}

function renderEvmChart() {
  const d = reportData.value || {}
  setEvmLineOption({
    title: { text: t('report.evm.charts.lineTitle'), left: 'center' },
    tooltip: { trigger: 'axis' },
    legend: { data: ['PV', 'EV', 'AC'], top: 30 },
    grid: { top: 80, left: 60, right: 40, bottom: 40 },
    xAxis: { type: 'category', data: [t('report.evm.charts.pvName'), t('report.evm.charts.evName'), t('report.evm.charts.acName')] },
    yAxis: { type: 'value' },
    series: [
      { name: 'PV', type: 'bar', data: [toNumber(d.pv), 0, 0], itemStyle: { color: chartColors.primary } },
      { name: 'EV', type: 'bar', data: [0, toNumber(d.ev), 0], itemStyle: { color: chartColors.success } },
      { name: 'AC', type: 'bar', data: [0, 0, toNumber(d.ac)], itemStyle: { color: chartColors.danger } },
    ],
  } as EChartsOption)
  setEvmIndexOption({
    title: { text: t('report.evm.charts.indexTitle'), left: 'center' },
    tooltip: { trigger: 'axis' },
    radar: {
      indicator: [
        { name: t('report.evm.charts.cpiName'), max: 2 },
        { name: t('report.evm.charts.spiName'), max: 2 },
        { name: t('report.evm.charts.progressName'), max: 100 },
        { name: t('report.evm.charts.healthName'), max: 100 },
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
            name: t('report.evm.charts.seriesProject'),
            areaStyle: { color: chartColors.primary },
            itemStyle: { color: chartColors.primary },
          },
        ],
      },
    ],
  } as EChartsOption)
}

function renderDualRateChart() {
  const rows = listData.value || []
  setDualBarOption({
    title: { text: t('report.dualRate.charts.title'), left: 'center' },
    tooltip: { trigger: 'axis' },
    legend: { data: [t('report.dualRate.charts.seriesExternal'), t('report.dualRate.charts.seriesInternal')], top: 30 },
    grid: { top: 80, left: 60, right: 40, bottom: 40 },
    xAxis: { type: 'category', data: rows.map((r) => r.jobLevel || r.level || '-') },
    yAxis: { type: 'value', name: t('report.dualRate.charts.yAxisName') },
    series: [
      {
        name: t('report.dualRate.charts.seriesExternal'),
        type: 'bar',
        data: rows.map((r) => toNumber(r.externalRate || r.cardRate)),
        itemStyle: { color: chartColors.primary },
      },
      {
        name: t('report.dualRate.charts.seriesInternal'),
        type: 'bar',
        data: rows.map((r) => toNumber(r.internalRate || r.internalCost)),
        itemStyle: { color: chartColors.success },
      },
    ],
  } as EChartsOption)
}

function renderRiskChart() {
  const rows = listData.value || []
  type RiskAgg = {
    highRiskCount: number
    mediumRiskCount: number
    lowRiskCount: number
    highAlerts: number
    mediumAlerts: number
    lowAlerts: number
    alertCount: number
  }
  const agg = rows.reduce<RiskAgg>(
    (acc, r) => {
      acc.highRiskCount += toNumber(r.highRiskCount)
      acc.mediumRiskCount += toNumber(r.mediumRiskCount)
      acc.lowRiskCount += toNumber(r.lowRiskCount)
      acc.highAlerts += toNumber(r.highAlerts)
      acc.mediumAlerts += toNumber(r.mediumAlerts)
      acc.lowAlerts += toNumber(r.lowAlerts)
      acc.alertCount += toNumber(r.alertCount)
      return acc
    },
    { highRiskCount: 0, mediumRiskCount: 0, lowRiskCount: 0, highAlerts: 0, mediumAlerts: 0, lowAlerts: 0, alertCount: 0 },
  )
  setRiskPieOption({
    title: { text: t('report.risk.charts.pieTitle'), left: 'center' },
    tooltip: { trigger: 'item' },
    series: [
      {
        name: t('report.risk.charts.pieTitle'),
        type: 'pie',
        radius: ['40%', '70%'],
        itemStyle: { borderColor: chartColors.borderColor, borderWidth: 2 },
        data: [
          { name: t('report.risk.charts.seriesHigh'), value: agg.highRiskCount, itemStyle: { color: chartColors.danger } },
          { name: t('report.risk.charts.seriesMedium'), value: agg.mediumRiskCount, itemStyle: { color: chartColors.warning } },
          { name: t('report.risk.charts.seriesLow'), value: agg.lowRiskCount, itemStyle: { color: chartColors.success } },
        ],
      },
    ],
  } as EChartsOption)
  setRiskBarOption({
    title: { text: t('report.risk.charts.barTitle'), left: 'center' },
    tooltip: { trigger: 'axis' },
    grid: { top: 60, left: 60, right: 40, bottom: 40 },
    xAxis: { type: 'category', data: [t('report.risk.charts.xHigh'), t('report.risk.charts.xMedium'), t('report.risk.charts.xLow'), t('report.risk.charts.xTotal')] },
    yAxis: { type: 'value' },
    series: [
      {
        type: 'bar',
        data: [agg.highAlerts, agg.mediumAlerts, agg.lowAlerts, agg.alertCount],
        itemStyle: { color: chartColors.primary },
      },
    ],
  } as EChartsOption)
}

function renderUtilizationChart() {
  const rows = listData.value || []
  setUtilBarOption({
    title: { text: t('report.utilization.charts.title'), left: 'center' },
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
        itemStyle: { color: chartColors.success },
        label: { show: true, position: 'right', formatter: '{c}%' },
      },
    ],
  } as EChartsOption)
}

function renderBenchChart() {
  const rows = listData.value || []
  setBenchLineOption({
    title: { text: t('report.bench.charts.title'), left: 'center' },
    tooltip: { trigger: 'axis' },
    grid: { top: 60, left: 60, right: 40, bottom: 40 },
    xAxis: { type: 'category', data: rows.map((r) => r.period || r.date || '-') },
    yAxis: { type: 'value' },
    series: [
      {
        type: 'line',
        smooth: true,
        data: rows.map((r) => toNumber(r.totalCost || r.cost)),
        areaStyle: { color: chartColors.warning },
        itemStyle: { color: chartColors.warning },
      },
    ],
  } as EChartsOption)
}

function renderSummaryChart() {
  const rows = summaryData.value || []
  setSummaryBarOption({
    title: { text: t('report.summary.charts.title'), left: 'center' },
    tooltip: { trigger: 'axis' },
    legend: { data: [t('report.profit.charts.seriesRevenue'), t('report.profit.charts.seriesTotalCost'), t('report.profit.charts.seriesGrossProfit')], top: 30 },
    grid: { top: 80, left: 100, right: 40, bottom: 60 },
    xAxis: {
      type: 'category',
      data: rows.map((r) => r.initiationName || `项目${r.initiationId}`),
      axisLabel: { rotate: 30 },
    },
    yAxis: { type: 'value' },
    series: [
      { name: t('report.profit.charts.seriesRevenue'), type: 'bar', data: rows.map((r) => toNumber(r.revenue)), itemStyle: { color: chartColors.primary } },
      { name: t('report.profit.charts.seriesTotalCost'), type: 'bar', data: rows.map((r) => toNumber(r.totalCost)), itemStyle: { color: chartColors.info } },
      { name: t('report.profit.charts.seriesGrossProfit'), type: 'bar', data: rows.map((r) => toNumber(r.grossProfit)), itemStyle: { color: chartColors.success } },
    ],
  } as EChartsOption)
}

watch(tab, () => {
  if (reportData.value || listData.value.length || summaryData.value.length) {
    renderChartForTab(tab.value)
    if (tab.value === 'summary') renderSummaryChart()
    if (tab.value === 'utilization') renderUtilizationChart()
  }
})

onMounted(() => {
  // useECharts 自动管理 init/resize/dispose，无需手动事件监听
})
</script>

<template>
  <div class="report-page">
    <el-card shadow="never" class="query-card">
      <el-form inline :model="query">
        <el-form-item :label="t('report.query.initiationId')">
          <el-input-number v-model="query.initiationId" :min="0" :controls="false" />
        </el-form-item>
        <el-form-item :label="t('report.query.period')">
          <el-input v-model="query.period" :placeholder="t('report.query.periodPlaceholder')" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="load(tab)">{{ t('report.buttons.query') }}</el-button>
          <el-button @click="query.initiationId = undefined; query.period = ''; reportData = null; listData = []; summaryData = []">{{ t('report.buttons.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-loading="loading" shadow="never" class="report-card">
      <el-tabs :model-value="tab" @update:model-value="onTabChange">
        <el-tab-pane :label="t('report.tabs.profit')" name="profit" />
        <el-tab-pane :label="t('report.tabs.cost')" name="cost" />
        <el-tab-pane :label="t('report.tabs.payment')" name="payment" />
        <el-tab-pane :label="t('report.tabs.lifecycle')" name="lifecycle" />
        <el-tab-pane :label="t('report.tabs.summary')" name="summary" />
        <el-tab-pane :label="t('report.tabs.evm')" name="evm" />
        <el-tab-pane :label="t('report.tabs.dualRate')" name="dualRate" />
        <el-tab-pane :label="t('report.tabs.gantt')" name="gantt" />
        <el-tab-pane :label="t('report.tabs.risk')" name="risk" />
        <el-tab-pane :label="t('report.tabs.utilization')" name="utilization" />
        <el-tab-pane :label="t('report.tabs.bench')" name="bench" />
      </el-tabs>

      <!-- 利润 -->
      <div v-if="tab === 'profit' && reportData" class="grid">
        <div class="kpi"><div class="kpi-label">{{ t('report.profit.kpis.revenue') }}</div><div class="kpi-value money">{{ fmtMoney(reportData?.revenue) }}</div></div>
        <div class="kpi"><div class="kpi-label">{{ t('report.profit.kpis.laborCost') }}</div><div class="kpi-value">{{ fmtMoney(reportData?.laborCost) }}</div></div>
        <div class="kpi"><div class="kpi-label">{{ t('report.profit.kpis.purchaseCost') }}</div><div class="kpi-value">{{ fmtMoney(reportData?.purchaseCost) }}</div></div>
        <div class="kpi"><div class="kpi-label">{{ t('report.profit.kpis.expenseCost') }}</div><div class="kpi-value">{{ fmtMoney(reportData?.expenseCost) }}</div></div>
        <div class="kpi highlight"><div class="kpi-label">{{ t('report.profit.kpis.grossProfit') }}</div><div class="kpi-value money">{{ fmtMoney(reportData?.grossProfit) }}</div></div>
        <div class="kpi highlight"><div class="kpi-label">{{ t('report.profit.kpis.grossMargin') }}</div><div class="kpi-value">{{ fmtPct(reportData?.grossMargin) }}</div></div>
      </div>
      <el-row v-if="tab === 'profit' && reportData" :gutter="16" class="chart-row">
        <el-col :span="12"><div ref="profitBarRef" class="chart-area" /></el-col>
        <el-col :span="12"><div ref="profitPieRef" class="chart-area" /></el-col>
      </el-row>

      <!-- 成本归集 -->
      <template v-else-if="tab === 'cost' && reportData">
        <div class="grid">
          <div class="kpi"><div class="kpi-label">{{ t('report.cost.kpis.totalCost') }}</div><div class="kpi-value money">{{ fmtMoney(reportData?.totalCost) }}</div></div>
          <div class="kpi"><div class="kpi-label">{{ t('report.cost.kpis.laborRatio') }}</div><div class="kpi-value">{{ fmtPct(reportData?.laborRatio) }}</div></div>
          <div class="kpi"><div class="kpi-label">{{ t('report.cost.kpis.purchaseRatio') }}</div><div class="kpi-value">{{ fmtPct(reportData?.purchaseRatio) }}</div></div>
          <div class="kpi"><div class="kpi-label">{{ t('report.cost.kpis.expenseRatio') }}</div><div class="kpi-value">{{ fmtPct(reportData?.expenseRatio) }}</div></div>
        </div>
        <el-row :gutter="16" class="chart-row">
          <el-col :span="12" :offset="6"><div ref="costPieRef" class="chart-area" /></el-col>
        </el-row>
      </template>

      <!-- 回款台账 -->
      <div v-else-if="tab === 'payment' && reportData">
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('report.payment.fields.invoicedAmount')">{{ fmtMoney(reportData?.invoicedAmount) }}</el-descriptions-item>
          <el-descriptions-item :label="t('report.payment.fields.receivedAmount')">{{ fmtMoney(reportData?.receivedAmount) }}</el-descriptions-item>
          <el-descriptions-item :label="t('report.payment.fields.outstandingAmount')">{{ fmtMoney(reportData?.outstandingAmount) }}</el-descriptions-item>
          <el-descriptions-item :label="t('report.payment.fields.collectionRate')">{{ fmtPct(reportData?.collectionRate) }}</el-descriptions-item>
        </el-descriptions>
        <vxe-table :data="(reportData?.ledgers as Record<string, unknown>[]) || []" border style="margin-top: 12px">
          <vxe-column type="seq" title="#" width="50" />
          <vxe-column field="date" :title="t('report.payment.columns.date')" width="120" />
          <vxe-column field="type" :title="t('report.payment.columns.type')" width="100" />
          <vxe-column field="code" :title="t('report.payment.columns.code')" width="160" />
          <vxe-column field="amount" :title="t('report.payment.columns.amount')" width="140" align="right" :formatter="moneyFormatter" />
          <vxe-column field="remark" :title="t('report.payment.columns.remark')" min-width="200" />
        </vxe-table>
      </div>

      <!-- 生命周期台账 -->
      <div v-else-if="tab === 'lifecycle' && reportData">
        <el-timeline>
          <el-timeline-item
            v-for="(item, idx) in (reportData?.stages as Array<Record<string, unknown>>) || []"
            :key="idx"
            :timestamp="typeof item.date === 'string' ? item.date : ''"
            :type="item.type as TimelineType"
          >
            <h4>{{ item.stage }}</h4>
            <p>{{ item.description }}</p>
          </el-timeline-item>
        </el-timeline>
      </div>

      <!-- 跨项目汇总 -->
      <div v-else-if="tab === 'summary'">
        <vxe-table :data="summaryData" border stripe>
          <vxe-column field="initiationId" :title="t('report.summary.columns.initiationId')" width="100" align="center" />
          <vxe-column field="initiationName" :title="t('report.summary.columns.initiationName')" min-width="200" show-overflow />
          <vxe-column field="revenue" :title="t('report.summary.columns.revenue')" width="140" align="right" :formatter="moneyFormatter" />
          <vxe-column field="totalCost" :title="t('report.summary.columns.totalCost')" width="140" align="right" :formatter="moneyFormatter" />
          <vxe-column field="grossProfit" :title="t('report.summary.columns.grossProfit')" width="140" align="right" :formatter="moneyFormatter" />
          <vxe-column field="grossMargin" :title="t('report.summary.columns.grossMargin')" width="120" align="right" :formatter="pctFormatter" />
        </vxe-table>
        <el-row :gutter="16" class="chart-row">
          <el-col :span="24"><div ref="summaryBarRef" class="chart-area" /></el-col>
        </el-row>
      </div>

      <!-- EVM -->
      <template v-else-if="tab === 'evm' && reportData">
        <div class="grid">
          <div class="kpi"><div class="kpi-label">{{ t('report.evm.kpis.pv') }}</div><div class="kpi-value money">{{ fmtMoney(reportData?.pv) }}</div></div>
          <div class="kpi"><div class="kpi-label">{{ t('report.evm.kpis.ev') }}</div><div class="kpi-value money">{{ fmtMoney(reportData?.ev) }}</div></div>
          <div class="kpi"><div class="kpi-label">{{ t('report.evm.kpis.ac') }}</div><div class="kpi-value money">{{ fmtMoney(reportData?.ac) }}</div></div>
          <div class="kpi"><div class="kpi-label">{{ t('report.evm.kpis.bac') }}</div><div class="kpi-value money">{{ fmtMoney(reportData?.bac) }}</div></div>
          <div class="kpi highlight"><div class="kpi-label">{{ t('report.evm.kpis.cpi') }}</div><div class="kpi-value">{{ ((reportData?.cpi as number | undefined)?.toFixed?.(2)) || '-' }}</div></div>
          <div class="kpi highlight"><div class="kpi-label">{{ t('report.evm.kpis.spi') }}</div><div class="kpi-value">{{ ((reportData?.spi as number | undefined)?.toFixed?.(2)) || '-' }}</div></div>
        </div>
        <el-row :gutter="16" class="chart-row">
          <el-col :span="12"><div ref="evmLineRef" class="chart-area" /></el-col>
          <el-col :span="12"><div ref="evmIndexRef" class="chart-area" /></el-col>
        </el-row>
      </template>

      <!-- 双费率对比 -->
      <template v-else-if="tab === 'dualRate' && reportData">
        <div class="grid">
          <div class="kpi"><div class="kpi-label">{{ t('report.dualRate.kpis.externalRevenue') }}</div><div class="kpi-value money">{{ fmtMoney(reportData?.externalRevenue) }}</div></div>
          <div class="kpi"><div class="kpi-label">{{ t('report.dualRate.kpis.internalRevenue') }}</div><div class="kpi-value money">{{ fmtMoney(reportData?.internalRevenue) }}</div></div>
          <div class="kpi"><div class="kpi-label">{{ t('report.dualRate.kpis.externalGrossProfit') }}</div><div class="kpi-value money">{{ fmtMoney(reportData?.externalGrossProfit) }}</div></div>
          <div class="kpi"><div class="kpi-label">{{ t('report.dualRate.kpis.internalGrossProfit') }}</div><div class="kpi-value money">{{ fmtMoney(reportData?.internalGrossProfit) }}</div></div>
          <div class="kpi highlight"><div class="kpi-label">{{ t('report.dualRate.kpis.externalMargin') }}</div><div class="kpi-value">{{ fmtPct(reportData?.externalMargin) }}</div></div>
          <div class="kpi highlight"><div class="kpi-label">{{ t('report.dualRate.kpis.internalMargin') }}</div><div class="kpi-value">{{ fmtPct(reportData?.internalMargin) }}</div></div>
        </div>
        <el-row :gutter="16" class="chart-row">
          <el-col :span="24"><div ref="dualBarRef" class="chart-area" /></el-col>
        </el-row>
      </template>

      <!-- 风险看板 -->
      <template v-else-if="tab === 'risk' && reportData">
        <div class="grid">
          <div class="kpi"><div class="kpi-label">{{ t('report.risk.kpis.highRisk') }}</div><div class="kpi-value">{{ reportData?.highRiskCount ?? 0 }}</div></div>
          <div class="kpi"><div class="kpi-label">{{ t('report.risk.kpis.mediumRisk') }}</div><div class="kpi-value">{{ reportData?.mediumRiskCount ?? 0 }}</div></div>
          <div class="kpi"><div class="kpi-label">{{ t('report.risk.kpis.lowRisk') }}</div><div class="kpi-value">{{ reportData?.lowRiskCount ?? 0 }}</div></div>
          <div class="kpi"><div class="kpi-label">{{ t('report.risk.kpis.alertTotal') }}</div><div class="kpi-value">{{ reportData?.alertCount ?? 0 }}</div></div>
        </div>
        <el-row :gutter="16" class="chart-row">
          <el-col :span="12"><div ref="riskPieRef" class="chart-area" /></el-col>
          <el-col :span="12"><div ref="riskBarRef" class="chart-area" /></el-col>
        </el-row>
      </template>

      <!-- 利用率排行 -->
      <div v-else-if="tab === 'utilization'">
        <vxe-table :data="listData" border stripe>
          <vxe-column type="seq" title="#" width="50" />
          <vxe-column field="employeeName" :title="t('report.utilization.columns.employeeName')" min-width="120" />
          <vxe-column field="department" :title="t('report.utilization.columns.department')" min-width="120" />
          <vxe-column field="utilization" :title="t('report.utilization.columns.utilization')" width="120" align="right" :formatter="utilizationFormatter" />
          <vxe-column field="billableHours" :title="t('report.utilization.columns.billableHours')" width="120" align="right" />
        </vxe-table>
        <el-row :gutter="16" class="chart-row">
          <el-col :span="24"><div ref="utilBarRef" class="chart-area" /></el-col>
        </el-row>
      </div>

      <!-- Bench 成本 -->
      <div v-else-if="tab === 'bench' && reportData">
        <el-descriptions :column="3" border>
          <el-descriptions-item :label="t('report.bench.fields.headCount')">{{ reportData?.headCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item :label="t('report.bench.fields.totalCost')">{{ fmtMoney(reportData?.totalCost) }}</el-descriptions-item>
          <el-descriptions-item :label="t('report.bench.fields.avgDays')">{{ reportData?.avgDays ?? 0 }}</el-descriptions-item>
        </el-descriptions>
        <el-row :gutter="16" class="chart-row">
          <el-col :span="24"><div ref="benchLineRef" class="chart-area" /></el-col>
        </el-row>
      </div>

      <el-empty
        v-if="!reportData && !listData.length && !summaryData.length"
        :description="t('report.messages.empty')"
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
    border-radius: $border-radius-sm;
    &.highlight { background: var(--el-color-primary-light-9); }
    .kpi-label { font-size: $font-size-xs; color: $text-secondary; margin-bottom: $spacing-sm; }
    .kpi-value { font-size: $font-size-xl; font-weight: 600; &.money { color: $primary-color; } }
  }
  .chart-row { margin-top: $spacing-md; }
  .chart-area { width: 100%; height: 320px; }
}
</style>
