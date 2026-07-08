<!--
  @fileoverview 分布式任务引擎 - 监控大屏
  @description 任务调度监控总览：
  - 顶部统计卡片：总任务数/运行中/今日触发/今日失败
  - 任务选择器 + 左侧执行趋势折线图（近 7 天触发/成功/失败）
  - 右侧 P95 耗时趋势折线图
  - 底部最近告警列表（FAILED/TIMEOUT 日志）
  - 使用 useECharts composable 渲染图表
  @module views/cronjob/monitor
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useECharts } from '@/composables/useECharts'
import { getJobPage, getJobLogPage, getDailyStats } from '@/api/cronjob'
import type { JobVO, JobLogVO, JobDailyStatsVO, LogStatus } from '@/api/cronjob/types'
import type { EChartsOption } from '@/utils/echarts'

const { t } = useI18n()

// ==================== 统计卡片 ====================

/** 总任务数 */
const totalJobs = ref(0)
/** 运行中任务数（状态为 NORMAL 或 ERROR 视为运行中） */
const runningJobs = ref(0)
/** 今日触发次数 */
const todayFire = ref(0)
/** 今日失败次数 */
const todayFail = ref(0)
/** 卡片加载中 */
const cardLoading = ref(false)

// ==================== 趋势图表 ====================

/** 任务选择器（用于趋势统计） */
const selectedJobId = ref<string>('')
/** 任务选项列表 */
const jobOptions = ref<{ label: string; value: string }[]>([])

/** 趋势图表加载中 */
const trendLoading = ref(false)

/** 执行趋势图表容器 */
const trendChartRef = ref<HTMLDivElement | null>(null)
const { setOption: setTrendOption } = useECharts(trendChartRef)

/** P95 耗时趋势图表容器 */
const p95ChartRef = ref<HTMLDivElement | null>(null)
const { setOption: setP95Option } = useECharts(p95ChartRef)

/** 最近告警列表 */
const alertList = ref<JobLogVO[]>([])
/** 告警加载中 */
const alertLoading = ref(false)

/** Element Plus el-tag type 联合类型 */
type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'

/** 日志状态 Tag 类型映射 */
const statusTagType: Record<LogStatus, TagType> = {
  RUNNING: 'primary',
  SUCCESS: 'success',
  FAILED: 'danger',
  TIMEOUT: 'warning',
  ZOMBIE: 'info',
}

/** 日志状态文案映射 */
const statusLabelMap: Record<LogStatus, string> = {
  RUNNING: t('cronjob.logStatusRunning'),
  SUCCESS: t('cronjob.logStatusSuccess'),
  FAILED: t('cronjob.logStatusFailed'),
  TIMEOUT: t('cronjob.logStatusTimeout'),
  ZOMBIE: t('cronjob.logStatusZombie'),
}

/** 工具：格式化日期为 yyyy-MM-dd */
const formatDate = (date: Date): string => {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** 工具：格式化日期时间为 yyyy-MM-dd HH:mm:ss */
const formatDateTime = (date: Date): string => {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const mi = String(date.getMinutes()).padStart(2, '0')
  const s = String(date.getSeconds()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${mi}:${s}`
}

/** 今日日期范围 */
const todayRange = computed(() => {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0)
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59)
  return { start: formatDateTime(start), end: formatDateTime(end) }
})

/** 近 7 天日期范围 */
const weekRange = computed(() => {
  const now = new Date()
  const start = new Date(now)
  start.setDate(start.getDate() - 6)
  return { start: formatDate(start), end: formatDate(now) }
})

/** 加载统计卡片数据 */
const loadCards = async () => {
  cardLoading.value = true
  try {
    // 总任务数 + 运行中任务数
    const jobResp = await getJobPage({ page: 1, size: 1 })
    totalJobs.value = jobResp.data?.total ?? 0
    // 运行中：查询 NORMAL 状态任务总数
    const normalResp = await getJobPage({ page: 1, size: 1, status: 'NORMAL' })
    runningJobs.value = normalResp.data?.total ?? 0

    // 今日触发次数（全部日志）
    const todayLogResp = await getJobLogPage({
      page: 1,
      size: 1,
      startTime: todayRange.value.start,
      endTime: todayRange.value.end,
    })
    todayFire.value = todayLogResp.data?.total ?? 0

    // 今日失败次数（FAILED + TIMEOUT）
    const todayFailResp = await getJobLogPage({
      page: 1,
      size: 1,
      status: 'FAILED',
      startTime: todayRange.value.start,
      endTime: todayRange.value.end,
    })
    todayFail.value = todayFailResp.data?.total ?? 0
  } catch {
    // 静默失败
  } finally {
    cardLoading.value = false
  }
}

/** 加载任务选项列表 */
const loadJobOptions = async () => {
  try {
    const resp = await getJobPage({ page: 1, size: 200 })
    const records = resp.data?.records ?? []
    jobOptions.value = records.map((j: JobVO) => ({
      label: `${j.jobName} (${j.jobKey})`,
      value: j.id,
    }))
    // 默认选中第一个任务
    if (jobOptions.value.length > 0 && !selectedJobId.value) {
      selectedJobId.value = jobOptions.value[0].value
    }
  } catch {
    // 静默失败
  }
}

/** 加载趋势图表数据 */
const loadTrend = async () => {
  if (!selectedJobId.value) return
  trendLoading.value = true
  try {
    const resp = await getDailyStats({
      jobId: selectedJobId.value,
      startDate: weekRange.value.start,
      endDate: weekRange.value.end,
    })
    const stats: JobDailyStatsVO[] = resp.data ?? []
    renderTrendChart(stats)
    renderP95Chart(stats)
  } catch {
    // 静默失败
  } finally {
    trendLoading.value = false
  }
}

/** 渲染执行趋势折线图 */
const renderTrendChart = (stats: JobDailyStatsVO[]) => {
  const dates = stats.map((s) => s.statsDate)
  const fireData = stats.map((s) => s.fireCount)
  const successData = stats.map((s) => s.successCount)
  const failData = stats.map((s) => s.failCount)
  const option: EChartsOption = {
    tooltip: { trigger: 'axis' },
    legend: {
      data: [t('cronjob.fireCount'), t('cronjob.successCount'), t('cronjob.failCount')],
      top: 0,
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: dates },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: t('cronjob.fireCount'),
        type: 'line',
        smooth: true,
        data: fireData,
        itemStyle: { color: '#409EFF' },
      },
      {
        name: t('cronjob.successCount'),
        type: 'line',
        smooth: true,
        data: successData,
        itemStyle: { color: '#67C23A' },
      },
      {
        name: t('cronjob.failCount'),
        type: 'line',
        smooth: true,
        data: failData,
        itemStyle: { color: '#F56C6C' },
      },
    ],
  }
  setTrendOption(option)
}

/** 渲染 P95 耗时趋势折线图 */
const renderP95Chart = (stats: JobDailyStatsVO[]) => {
  const dates = stats.map((s) => s.statsDate)
  const p95Data = stats.map((s) => s.p95DurationMs ?? 0)
  const option: EChartsOption = {
    tooltip: {
      trigger: 'axis',
      valueFormatter: (val: unknown) => `${val} ms`,
    },
    legend: { data: [t('cronjob.durationP95')], top: 0 },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: dates },
    yAxis: { type: 'value', axisLabel: { formatter: '{value} ms' } },
    series: [
      {
        name: t('cronjob.durationP95'),
        type: 'line',
        smooth: true,
        data: p95Data,
        itemStyle: { color: '#E6A23C' },
        areaStyle: { opacity: 0.2 },
      },
    ],
  }
  setP95Option(option)
}

/** 加载最近告警列表 */
const loadAlerts = async () => {
  alertLoading.value = true
  try {
    const resp = await getJobLogPage({
      page: 1,
      size: 10,
      status: 'FAILED',
    })
    alertList.value = resp.data?.records ?? []
  } catch {
    // 静默失败
  } finally {
    alertLoading.value = false
  }
}

/** 监听任务选择变化，重新加载趋势 */
watch(selectedJobId, (newId) => {
  if (newId) {
    loadTrend()
  }
})

onMounted(() => {
  loadCards()
  loadJobOptions()
  loadAlerts()
})
</script>

<template>
  <div class="cronjob-monitor">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card v-loading="cardLoading" shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-label">{{ t('cronjob.totalJobs') }}</div>
            <div class="stat-value total">{{ totalJobs }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card v-loading="cardLoading" shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-label">{{ t('cronjob.runningJobs') }}</div>
            <div class="stat-value running">{{ runningJobs }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card v-loading="cardLoading" shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-label">{{ t('cronjob.todayFire') }}</div>
            <div class="stat-value fire">{{ todayFire }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card v-loading="cardLoading" shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-label">{{ t('cronjob.todayFail') }}</div>
            <div class="stat-value fail">{{ todayFail }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <div class="chart-toolbar">
      <span class="chart-title">{{ t('cronjob.executionTrend') }}</span>
      <el-select
        v-model="selectedJobId"
        :placeholder="t('cronjob.jobName')"
        filterable
        clearable
        style="width: 320px"
      >
        <el-option
          v-for="opt in jobOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </div>
    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <el-card v-loading="trendLoading" shadow="never">
          <div ref="trendChartRef" class="chart-area" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card v-loading="trendLoading" shadow="never">
          <div ref="p95ChartRef" class="chart-area" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近告警列表 -->
    <el-card shadow="never" class="alert-card">
      <template #header>
        <span class="chart-title">{{ t('cronjob.recentAlerts') }}</span>
      </template>
      <el-table v-loading="alertLoading" :data="alertList" style="width: 100%">
        <el-table-column :label="t('cronjob.jobKey')" min-width="160" show-overflow-tooltip>
          <template #default="scope">
            {{ (scope.row as JobLogVO).jobKey }}
          </template>
        </el-table-column>
        <el-table-column :label="t('cronjob.status')" width="100">
          <template #default="scope">
            <el-tag :type="statusTagType[(scope.row as JobLogVO).status]" size="small">
              {{ statusLabelMap[(scope.row as JobLogVO).status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('cronjob.startTime')" width="170">
          <template #default="scope">
            {{ (scope.row as JobLogVO).startTime || '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('cronjob.durationMs')" width="110">
          <template #default="scope">
            {{ (scope.row as JobLogVO).durationMs ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('cronjob.errorMessage')" min-width="240" show-overflow-tooltip>
          <template #default="scope">
            <span class="error-text">{{ (scope.row as JobLogVO).errorMessage || '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.cronjob-monitor {
  padding: 16px;
}

.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  .stat-content {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 8px 0;

    .stat-label {
      font-size: 14px;
      color: var(--el-text-color-secondary);
      margin-bottom: 8px;
    }

    .stat-value {
      font-size: 32px;
      font-weight: 700;
      line-height: 1;

      &.total {
        color: var(--el-color-primary);
      }
      &.running {
        color: var(--el-color-success);
      }
      &.fire {
        color: var(--el-color-warning);
      }
      &.fail {
        color: var(--el-color-danger);
      }
    }
  }
}

.chart-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;

  .chart-title {
    font-size: 16px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }
}

.chart-row {
  margin-bottom: 16px;
}

.chart-area {
  width: 100%;
  height: 320px;
}

.alert-card {
  margin-bottom: 16px;
}

.error-text {
  color: var(--el-color-danger);
  font-size: 13px;
}
</style>
