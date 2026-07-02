<!--
  @file 首页仪表盘
  @description 系统首页仪表盘，基于 Cockpit 总览 API 与 ECharts 可视化展示活跃项目、本月合同/收入/毛利、EVM 健康度分布、近 6 月趋势与预警 TOP 5，对接 @/api/execution/cockpit 与 @/api/execution/alert 模块。
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
import type { EChartsOption } from 'echarts'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { formatDate } from '@/utils/format'
import { getCockpitOverview, getKpiTrend } from '@/api/execution/cockpit'
import type { KpiTrendVO } from '@/api/execution/cockpit'
import { getCockpitAlertTopN } from '@/api/execution/alert'
import { useECharts } from '@/composables/useECharts'
import { isHandledError } from '@/utils/error'

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

/** 全局加载状态 */
const loading = ref(false)
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

// ===== KPI 列表 (动态计算) =====
/** 顶部 4 个 KPI 卡片数据 */
const metrics = computed(() => [
  {
    title: '活跃项目数',
    value: String(kpi.value?.activeProjectCount ?? 0),
    unit: '个',
    color: '#1890ff',
    icon: 'Document',
  },
  {
    title: '本月合同额',
    value: yuanToWan(kpi.value?.totalRevenue),
    unit: '万',
    color: '#52c41a',
    icon: 'Money',
  },
  {
    title: '已确认收入',
    value: yuanToWan(kpi.value?.recognizedRevenue),
    unit: '万',
    color: '#722ed1',
    icon: 'TrendCharts',
  },
  {
    title: '本月毛利',
    value: yuanToWan(kpi.value?.totalGrossProfit),
    unit: '万',
    color: '#fa8c16',
    sub: `毛利率 ${fmtPercent(kpi.value?.grossMargin)}`,
    icon: 'DataAnalysis',
  },
])

// ===== 图表 option 工厂 =====
/** 项目健康度饼图 option */
const healthOption = computed<EChartsOption>(() => ({
  title: { text: '项目健康度分布', left: 'center', textStyle: { fontSize: 14 } },
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: 0, left: 'center' },
  series: [
    {
      name: '健康度',
      type: 'pie',
      radius: ['38%', '70%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}\n{c}' },
      data: [
        { name: '正常', value: kpi.value?.normalProjects ?? 18, itemStyle: { color: '#67c23a' } },
        { name: '黄色', value: kpi.value?.yellowProjects ?? 7, itemStyle: { color: '#e6a23c' } },
        { name: '红色', value: kpi.value?.redProjects ?? 3, itemStyle: { color: '#f56c6c' } },
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
      months.push(`${d.getMonth() + 1}月`)
    }
  }
  // 收入/毛利序列来自后端 kpi-trend 接口（已确认收入 / 毛利）
  const revenueSeries = trendData.value?.confirmedRevenueSeries ?? []
  const profitSeries = trendData.value?.grossProfitSeries ?? []
  return {
    title: { text: '近 6 月收入/毛利趋势', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    legend: { data: ['收入', '毛利'], top: 30 },
    grid: { top: 80, left: 50, right: 30, bottom: 30 },
    xAxis: { type: 'category', data: months, boundaryGap: false },
    yAxis: { type: 'value', name: '万元' },
    series: [
      {
        name: '收入',
        type: 'line',
        smooth: true,
        symbolSize: 8,
        data: revenueSeries,
        itemStyle: { color: '#409eff' },
        areaStyle: { opacity: 0.15 },
      },
      {
        name: '毛利',
        type: 'line',
        smooth: true,
        symbolSize: 8,
        data: profitSeries,
        itemStyle: { color: '#67c23a' },
        areaStyle: { opacity: 0.15 },
      },
    ],
  }
})

/** EVM 健康度柱图 option */
const evmOption = computed<EChartsOption>(() => ({
  title: { text: 'EVM 健康度分布', left: 'center', textStyle: { fontSize: 14 } },
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { top: 50, left: 40, right: 20, bottom: 30 },
  xAxis: { type: 'category', data: ['正常', '黄色预警', '红色预警'] },
  yAxis: { type: 'value', name: '项目数' },
  series: [
    {
      type: 'bar',
      barWidth: '50%',
      data: [
        { value: kpi.value?.evmGreenCount ?? 12, itemStyle: { color: '#67c23a' } },
        { value: kpi.value?.evmYellowCount ?? 5, itemStyle: { color: '#e6a23c' } },
        { value: kpi.value?.evmRedCount ?? 2, itemStyle: { color: '#f56c6c' } },
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
  title: { text: '预警项目 TOP 5', left: 'center', textStyle: { fontSize: 14 } },
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { top: 50, left: 100, right: 30, bottom: 30 },
  xAxis: { type: 'value', name: '预警数' },
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
        itemStyle: { color: a.alertLevel === 'RED' ? '#f56c6c' : '#e6a23c' },
      })),
      label: { show: true, position: 'right' },
    },
  ],
}))

// ===== 数据加载 =====
/** 拉取 Cockpit 总览 KPI 数据 */
async function loadOverview() {
  loading.value = true
  try {
    const { data } = await getCockpitOverview(period.value)
    kpi.value = data as CockpitKpi
  } catch (e) {
    kpi.value = null
    if (!isHandledError(e)) {
      ElMessage.error('数据加载失败，请刷新重试')
    }
  } finally {
    loading.value = false
  }
}

/** 拉取预警 TOP 5 项目列表 */
async function loadAlertTopN() {
  try {
    const { data } = await getCockpitAlertTopN(period.value, 5)
    alertTopN.value = (data as AlertTopNItem[]) || []
  } catch (e) {
    alertTopN.value = []
    if (!isHandledError(e)) {
      ElMessage.error('预警数据加载失败，请刷新重试')
    }
  }
}

/** 拉取近 6 月收入/毛利趋势数据并更新图表 */
async function loadTrendData() {
  try {
    const { data } = await getKpiTrend(6)
    trendData.value = data ?? null
  } catch (e) {
    trendData.value = null
    if (!isHandledError(e)) {
      ElMessage.error('趋势数据加载失败')
    }
  }
}

/** 并发刷新所有数据并重绘所有图表 */
async function refreshAll() {
  await Promise.all([loadOverview(), loadAlertTopN(), loadTrendData()])
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
/**
 * 切换查询期间并刷新所有数据
 * @param newPeriod 新的期间字符串（YYYY-MM）
 */
function changePeriod(newPeriod: string) {
  period.value = newPeriod
  refreshAll()
}

// ===== 周期选项 =====
/** 最近 12 个月的期间选项列表 */
const periodOptions = computed(() => {
  const list: { label: string; value: string }[] = []
  const now = new Date()
  for (let i = 0; i < 12; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    const v = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
    list.push({ label: v, value: v })
  }
  return list
})

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
          <h2>下午好,{{ userStore.realName || userStore.username }}!</h2>
          <p>欢迎使用 PMIS 项目运营管理系统 · 当前时间: {{ formatDate(new Date(), 'YYYY-MM-DD HH:mm') }}</p>
        </div>
        <el-icon class="welcome-icon" :size="60"><Sunny /></el-icon>
      </div>
    </el-card>

    <!-- 周期切换 + KPI -->
    <div class="toolbar">
      <el-select v-model="period" style="width: 140px" @change="changePeriod">
        <el-option
          v-for="opt in periodOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
      <el-button :loading="loading" @click="refreshAll">刷新</el-button>
    </div>

    <el-row :gutter="16" class="metric-row">
      <el-col v-for="m in metrics" :key="m.title" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card class="metric-card" shadow="hover">
          <div class="metric-content">
            <div class="metric-icon" :style="{ background: m.color }">
              <el-icon :size="24"><component :is="m.icon" /></el-icon>
            </div>
            <div class="metric-info">
              <p class="metric-title">{{ m.title }}</p>
              <p class="metric-value">
                <span class="value">{{ m.value }}</span>
                <span class="unit">{{ m.unit }}</span>
              </p>
              <p v-if="(m as any).sub" class="metric-sub">{{ (m as any).sub }}</p>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第一行图表: 健康度 + 趋势 -->
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="never" v-loading="loading">
          <div ref="healthRef" class="chart-area" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="10">
        <el-card shadow="never" v-loading="loading">
          <div ref="trendRef" class="chart-area" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>关键指标</span>
            </div>
          </template>
          <el-scrollbar height="280px">
            <div class="kpi-mini">
              <span class="kpi-mini-label">毛利率</span>
              <span class="kpi-mini-value">{{ fmtPercent(kpi?.grossMargin) }}</span>
            </div>
            <div class="kpi-mini">
              <span class="kpi-mini-label">平均可计费利用率</span>
              <span class="kpi-mini-value">{{ fmtPercent(kpi?.avgUtilization) }}</span>
            </div>
            <div class="kpi-mini">
              <span class="kpi-mini-label">空闲成本</span>
              <span class="kpi-mini-value">{{ yuanToWan(kpi?.benchIdleCost) }} 万</span>
            </div>
            <div class="kpi-mini">
              <span class="kpi-mini-label">EVM 红色预警</span>
              <span class="kpi-mini-value danger">{{ kpi?.evmRedCount ?? 0 }}</span>
            </div>
            <div class="kpi-mini">
              <span class="kpi-mini-label">EVM 黄色预警</span>
              <span class="kpi-mini-value warn">{{ kpi?.evmYellowCount ?? 0 }}</span>
            </div>
          </el-scrollbar>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二行图表: EVM 柱图 + 预警 TOP 5 -->
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :md="10">
        <el-card shadow="never" v-loading="loading">
          <div ref="evmRef" class="chart-area" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="14">
        <el-card shadow="never" v-loading="loading">
          <div ref="alertTopNRef" class="chart-area" />
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
  background: linear-gradient(135deg, #1890ff 0%, #722ed1 100%);
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
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chart-area {
  width: 100%;
  height: 320px;
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
</style>
