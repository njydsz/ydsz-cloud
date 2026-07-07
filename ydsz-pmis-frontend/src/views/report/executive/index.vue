<!--
  @fileoverview 高管看板
  @description 高管层驾驶舱：核心 KPI 概览 + 健康度评分 + 项目群对比 + KPI 趋势 + 告警事件摘要。
               顶部 6 张关键指标卡，下方通过 useECharts 渲染项目群横向柱状图与 30 天 KPI 折线趋势，
               支持 60 秒自动轮询刷新（autoRefresh 开关可关闭）。
  @module views/report/executive
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * 高管看板（批次18）
 *
 * 核心 KPI + 健康度评分 + 项目群对比 + KPI 趋势
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useECharts } from '@/composables/useECharts'
import {
  getExecutiveOverview,
  getKpiTrend,
  getAlertSummary,
} from '@/api/execution/cockpit'
import type {
  ExecutiveOverviewVO,
  KpiTrendVO,
  AlertEventDTO,
  ProjectGroupKpiDTO,
} from '@/api/execution/cockpit/types'

defineOptions({ name: 'ExecutiveOverview' })

const { t } = useI18n()

/**
 * 解析后端返回的 i18n 消息键
 * 后端 inferGroupName() 返回格式：cockpit.group.reserve|3 或 cockpit.group.unclassified
 * 前端根据 key 翻译并填充 {level} 参数
 */
function resolveGroupName(name: string | undefined | null): string {
  if (!name) return '-'
  if (name.startsWith('cockpit.group.')) {
    const [key, level] = name.split('|')
    if (level) {
      return t(key, { level })
    }
    return t(key)
  }
  return name
}

// ========== 状态 ==========
const loading = ref(false)
const executive = ref<ExecutiveOverviewVO | null>(null)
const trend = ref<KpiTrendVO | null>(null)
const alert = ref<{ redCount: number; yellowCount: number; totalCount: number; events: AlertEventDTO[] } | null>(null)
const lastUpdated = ref<string>('')

let pollTimer: number | null = null
const autoRefresh = ref(true)
const REFRESH_INTERVAL = 60_000 // 60 秒

// ========== KPI 卡片 ==========
const kpiCards = computed(() => {
  const e = executive.value
  if (!e) return []
  return [
    {
      key: 'projects',
      label: t('executive.kpi.activeProjects'),
      value: e.activeProjects ?? 0,
      unit: t('executive.kpi.unitProjects'),
      tone: 'primary' as const,
    },
    {
      key: 'contract',
      label: t('executive.kpi.totalContractAmount'),
      value: fmtYuan(e.totalContractAmount),
      unit: t('executive.kpi.unitYuan'),
      tone: 'primary' as const,
    },
    {
      key: 'revenue',
      label: t('executive.kpi.confirmedRevenue'),
      value: fmtYuan(e.confirmedRevenue),
      unit: t('executive.kpi.unitYuan'),
      tone: 'success' as const,
    },
    {
      key: 'cost',
      label: t('executive.kpi.totalCost'),
      value: fmtYuan(e.totalCost),
      unit: t('executive.kpi.unitYuan'),
      tone: 'warning' as const,
    },
    {
      key: 'profit',
      label: t('executive.kpi.grossProfit'),
      value: fmtYuan(e.grossProfit),
      unit: t('executive.kpi.unitYuan'),
      tone: (e.grossProfit ?? 0) >= 0 ? ('success' as const) : ('danger' as const),
    },
    {
      key: 'margin',
      label: t('executive.kpi.grossMargin'),
      value: pct1(e.grossMargin),
      unit: '',
      tone: ((e.grossMargin ?? 0) >= 0.15 ? 'success' : (e.grossMargin ?? 0) >= 0.05 ? 'warning' : 'danger') as
        | 'success'
        | 'warning'
        | 'danger',
    },
    {
      key: 'util',
      label: t('executive.kpi.billableUtilization'),
      value: pct1(e.avgBillableUtilization),
      unit: '',
      tone: ((e.avgBillableUtilization ?? 0) >= 0.7 ? 'success' : 'warning') as 'success' | 'warning',
    },
    {
      key: 'bench',
      label: t('executive.kpi.benchIdleCost'),
      value: fmtYuan(e.benchIdleCost),
      unit: t('executive.kpi.unitYuan'),
      tone: ((e.benchIdleCost ?? 0) > 500_000 ? 'warning' : 'success') as 'warning' | 'success',
    },
  ]
})

const healthGradeColor = computed(() => {
  const g = executive.value?.healthGrade
  if (g === 'A') return '#67C23A'
  if (g === 'B') return '#409EFF'
  if (g === 'C') return '#E6A23C'
  return '#F56C6C'
})

// ========== ECharts: KPI 趋势 ==========
const trendRef = ref<HTMLDivElement | null>(null)
const { setOption: setTrendOption } = useECharts(trendRef)
const updateTrendChart = () => {
  if (!trend.value) return
  // 注意：局部变量不能命名为 t，否则会遮蔽 useI18n() 返回的翻译函数 t
  const trendData = trend.value
  setTrendOption(
    {
      tooltip: { trigger: 'axis' },
      legend: { data: [t('executive.chart.legendContract'), t('executive.chart.legendRevenue'), t('executive.chart.legendProfit')] },
      grid: { left: 50, right: 30, top: 40, bottom: 30 },
      xAxis: { type: 'category', data: trendData.periods || [] },
      yAxis: [
        { type: 'value', name: t('executive.chart.yAxisAmount'), position: 'left' },
        { type: 'value', name: t('executive.chart.yAxisMarginPct'), position: 'right', min: 0, max: 100 },
      ],
      series: [
        { name: t('executive.chart.legendContract'), type: 'bar', data: trendData.contractAmountSeries || [], yAxisIndex: 0, itemStyle: { color: '#409EFF' } },
        { name: t('executive.chart.legendRevenue'), type: 'bar', data: trendData.confirmedRevenueSeries || [], yAxisIndex: 0, itemStyle: { color: '#67C23A' } },
        { name: t('executive.chart.legendProfit'), type: 'line', data: trendData.grossProfitSeries || [], yAxisIndex: 0, smooth: true, itemStyle: { color: '#E6A23C' } },
        {
          name: t('executive.chart.legendMargin'),
          type: 'line',
          data: trendData.grossMarginPctSeries || [],
          yAxisIndex: 1,
          smooth: true,
          lineStyle: { type: 'dashed' },
          itemStyle: { color: '#F56C6C' },
        },
      ],
    },
    true,
  )
}

// ========== ECharts: 项目群对比 ==========
const groupRef = ref<HTMLDivElement | null>(null)
const { setOption: setGroupOption } = useECharts(groupRef)
const updateGroupChart = () => {
  const groups = executive.value?.projectGroups || []
  if (groups.length === 0) return
  const groupNames = groups.map((g: ProjectGroupKpiDTO) => resolveGroupName(g.groupName) || g.groupCode || '-')
  const contractData = groups.map((g: ProjectGroupKpiDTO) => Number(g.totalContractAmount || 0))
  const profitData = groups.map((g: ProjectGroupKpiDTO) => Number(g.grossProfit || 0))
  setGroupOption(
    {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { data: [t('executive.chart.legendContract'), t('executive.chart.legendProfit')] },
      grid: { left: 60, right: 30, top: 40, bottom: 60 },
      xAxis: { type: 'category', data: groupNames, axisLabel: { rotate: 30 } },
      yAxis: { type: 'value', name: t('executive.chart.yAxisAmount') },
      series: [
        { name: t('executive.chart.legendContract'), type: 'bar', data: contractData, itemStyle: { color: '#409EFF' } },
        { name: t('executive.chart.legendProfit'), type: 'bar', data: profitData, itemStyle: { color: '#67C23A' } },
      ],
    } as any,
    true,
  )
}

// ========== 数据加载 ==========
async function loadAll() {
  loading.value = true
  try {
    const [e, t, a] = await Promise.all([
      getExecutiveOverview().catch(() => null),
      getKpiTrend(12).catch(() => null),
      getAlertSummary().catch(() => null),
    ])
    executive.value = e?.data ?? null
    trend.value = t?.data ?? null
    const aData = a?.data
    alert.value = aData
      ? { redCount: aData.redCount ?? 0, yellowCount: aData.yellowCount ?? 0, totalCount: aData.totalCount ?? 0, events: aData.events || [] }
      : null
    lastUpdated.value = new Date().toLocaleTimeString('zh-CN')
    updateTrendChart()
    updateGroupChart()
  } catch (err) {
    ElMessage.error(t('executive.messages.loadFailed', { message: (err as Error).message }))
  } finally {
    loading.value = false
  }
}

/** 启动轮询定时器（先清除已有定时器，避免重复） */
function startPolling() {
  stopPolling()
  if (!autoRefresh.value) return
  pollTimer = window.setInterval(() => loadAll(), REFRESH_INTERVAL)
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

function severityTag(severity?: string): 'danger' | 'warning' | 'info' {
  if (severity === 'RED') return 'danger'
  if (severity === 'YELLOW') return 'warning'
  return 'info'
}

function fmtYuan(v?: number | null): string {
  if (v === null || v === undefined) return '0'
  if (Math.abs(v) >= 1e8) return (v / 1e8).toFixed(2) + ' 亿'
  if (Math.abs(v) >= 1e4) return (v / 1e4).toFixed(2) + ' 万'
  return v.toFixed(0)
}

/**
 * 百分比格式化（保留 1 位小数）
 * @param v 0-1 之间的比例值
 * @returns 形如 12.3% 的字符串
 */
function pct1(v?: number | null): string {
  if (v === null || v === undefined) return '0%'
  return (v * 100).toFixed(1) + '%'
}

onMounted(() => {
  loadAll()
  startPolling()
})
onBeforeUnmount(() => stopPolling())
</script>

<template>
  <div class="executive-page">
    <el-card shadow="never" class="header-card">
      <div class="header-row">
        <div>
          <div class="page-title">{{ t('executive.title') }}</div>
          <div class="page-subtitle">{{ t('executive.subtitle') }}</div>
        </div>
        <div class="header-right">
          <el-tag v-if="lastUpdated" type="info" effect="plain" size="small">
            {{ t('executive.lastUpdated', { time: lastUpdated }) }}
          </el-tag>
          <el-switch
            v-model="autoRefresh"
            inline-prompt
            :active-text="t('executive.autoRefresh')"
            :inactive-text="t('executive.manual')"
            @change="toggleAutoRefresh"
          />
          <el-button :icon="'Refresh'" :loading="loading" @click="loadAll">{{ t('executive.refreshNow') }}</el-button>
        </div>
      </div>
    </el-card>

    <!-- 顶部 KPI 卡片 -->
    <el-row v-loading="loading" :gutter="12" class="kpi-row">
      <el-col v-for="c in kpiCards" :key="c.key" :xs="12" :sm="8" :md="6" :lg="3">
        <el-card shadow="hover" class="kpi-card" :class="`tone-${c.tone}`">
          <div class="kpi-label">{{ c.label }}</div>
          <div class="kpi-value">{{ c.value }}<span class="kpi-unit">{{ c.unit }}</span></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 健康度评分 + 预警摘要 -->
    <el-row :gutter="16" class="middle-row">
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="health-card">
          <template #header>
            <span>{{ t('executive.health.title') }}</span>
            <el-tag :type="executive?.healthGrade === 'D' ? 'danger' : 'success'" effect="dark" size="small" style="margin-left: 8px">
              {{ executive?.healthGrade || '-' }}
            </el-tag>
          </template>
          <div class="health-score" :style="{ color: healthGradeColor }">
            {{ executive?.healthScore?.toFixed(0) || 0 }}
          </div>
          <div class="health-tip">
            <div>{{ t('executive.health.healthRatio', { ratio: pct1(executive?.healthRatio) }) }}</div>
            <div>{{ t('executive.health.riskProjects', { count: executive?.riskProjectCount ?? 0, ratio: pct1(executive?.riskProjectRatio) }) }}</div>
            <div>{{ t('executive.health.evmStats', { red: executive?.evmRedCount ?? 0, yellow: executive?.evmYellowCount ?? 0, green: executive?.evmGreenCount ?? 0 }) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="16">
        <el-card shadow="never" class="alert-card">
          <template #header>
            <span>{{ t('executive.alert.title') }}</span>
            <el-tag v-if="alert" type="danger" effect="dark" size="small" style="margin-left: 8px">{{ t('executive.alert.red', { count: alert.redCount }) }}</el-tag>
            <el-tag v-if="alert" type="warning" effect="dark" size="small" style="margin-left: 4px">{{ t('executive.alert.yellow', { count: alert.yellowCount }) }}</el-tag>
            <el-tag v-if="alert" type="info" effect="plain" size="small" style="margin-left: 4px">{{ t('executive.alert.total', { count: alert.totalCount }) }}</el-tag>
          </template>
          <el-empty v-if="!alert || alert.totalCount === 0" :description="t('executive.alert.empty')" :image-size="60" />
          <ul v-else class="alert-list">
            <li v-for="ev in alert.events.slice(0, 5)" :key="ev.eventId" class="alert-item">
              <el-tag :type="severityTag(ev.severity)" effect="dark" size="small">{{ ev.severity }}</el-tag>
              <span class="alert-title">{{ ev.title }}</span>
              <span class="alert-desc">{{ ev.description }}</span>
            </li>
          </ul>
        </el-card>
      </el-col>
    </el-row>

    <!-- KPI 趋势 + 项目群对比 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :md="14">
        <el-card shadow="never" :header="t('executive.chart.trendTitle')">
          <div ref="trendRef" class="chart-area" style="height: 320px" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="10">
        <el-card shadow="never" :header="t('executive.chart.groupTitle')">
          <div ref="groupRef" class="chart-area" style="height: 320px" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style lang="scss" scoped>
.executive-page {
  padding: 16px;
  .header-card {
    margin-bottom: 16px;
  }
  .header-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    .page-title {
      font-size: 18px;
      font-weight: 600;
    }
    .page-subtitle {
      font-size: 12px;
      color: var(--el-text-color-secondary);
      margin-top: 4px;
    }
    .header-right {
      display: flex;
      gap: 12px;
      align-items: center;
    }
  }
  .kpi-row {
    margin-bottom: 16px;
  }
  .kpi-card {
    text-align: center;
    .kpi-label {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
    .kpi-value {
      font-size: 22px;
      font-weight: 600;
      margin-top: 4px;
      .kpi-unit {
        font-size: 12px;
        margin-left: 4px;
        color: var(--el-text-color-secondary);
      }
    }
    &.tone-success .kpi-value {
      color: #67c23a;
    }
    &.tone-warning .kpi-value {
      color: #e6a23c;
    }
    &.tone-danger .kpi-value {
      color: #f56c6c;
    }
    &.tone-primary .kpi-value {
      color: #409eff;
    }
  }
  .middle-row {
    margin-bottom: 16px;
  }
  .health-card {
    text-align: center;
    .health-score {
      font-size: 64px;
      font-weight: 700;
      line-height: 1;
    }
    .health-tip {
      margin-top: 16px;
      font-size: 12px;
      color: var(--el-text-color-secondary);
      line-height: 1.8;
      text-align: left;
    }
  }
  .alert-card {
    .alert-list {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .alert-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 6px 0;
      font-size: 13px;
      .alert-title {
        font-weight: 600;
        min-width: 240px;
      }
      .alert-desc {
        color: var(--el-text-color-secondary);
        font-size: 12px;
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
  }
}
</style>
