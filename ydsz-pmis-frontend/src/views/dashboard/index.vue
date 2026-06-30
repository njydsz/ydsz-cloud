<script setup lang="ts">
/**
 * 仪表盘
 *
 * 接入 Cockpit 总览 API + ECharts 可视化。
 * KPI: 活跃项目数、本月合同额、已确认收入、本月毛利
 * 图表: 项目健康度饼图、收入趋势折线图
 */
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { useUserStore } from '@/store/modules/user'
import { formatDate } from '@/utils/format'
import { getCockpitOverview } from '@/api/execution/cockpit'

const userStore = useUserStore()

const metrics = ref<Array<{ title: string; value: string; unit: string; trend: string; color: string; icon: string }>>([
  { title: '活跃项目数', value: '0', unit: '个', trend: '', color: '#1890ff', icon: 'Document' },
  { title: '本月合同额', value: '0', unit: '万', trend: '', color: '#52c41a', icon: 'Money' },
  { title: '已确认收入', value: '0', unit: '万', trend: '', color: '#722ed1', icon: 'TrendCharts' },
  { title: '本月毛利', value: '0', unit: '万', trend: '', color: '#fa8c16', icon: 'DataAnalysis' },
])

const todoList = ref<Array<{ id: number; title: string; priority: string; time: string }>>([
  { id: 1, title: '审批项目立项申请 (建议优先接入 PMIS 项目模块)', priority: 'high', time: formatDate(new Date(), 'YYYY-MM-DD HH:mm') },
  { id: 2, title: '审核本周工时填报', priority: 'medium', time: formatDate(new Date(), 'YYYY-MM-DD HH:mm') },
  { id: 3, title: '关注风险预警', priority: 'medium', time: formatDate(new Date(), 'YYYY-MM-DD HH:mm') },
  { id: 4, title: '参加项目复盘会', priority: 'low', time: formatDate(new Date(), 'YYYY-MM-DD HH:mm') },
])

const newsList = ref<Array<{ id: number; title: string; date: string }>>([
  { id: 1, title: '【公司公告】2026 年度 H1 优秀员工评选启动', date: '2026-06-29' },
  { id: 2, title: '【系统公告】PMIS V1.0 正式发布，全员启用', date: '2026-06-30' },
  { id: 3, title: '【制度更新】《项目财务核算管理制度》修订发布', date: '2026-06-28' },
])

const healthRef = ref<HTMLDivElement | null>(null)
const trendRef = ref<HTMLDivElement | null>(null)
let healthChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null

async function loadOverview() {
  try {
    const period = new Date().toISOString().slice(0, 7)
    const { data } = await getCockpitOverview(period)
    const d = data as any
    metrics.value = [
      { title: '活跃项目数', value: String(d?.activeProjectCount ?? 0), unit: '个', trend: '', color: '#1890ff', icon: 'Document' },
      { title: '本月合同额', value: ((Number(d?.totalRevenue || 0) / 10000).toFixed(1)), unit: '万', trend: '', color: '#52c41a', icon: 'Money' },
      { title: '已确认收入', value: ((Number(d?.recognizedRevenue || 0) / 10000).toFixed(1)), unit: '万', trend: '', color: '#722ed1', icon: 'TrendCharts' },
      { title: '本月毛利', value: ((Number(d?.totalGrossProfit || 0) / 10000).toFixed(1)), unit: '万', trend: '', color: '#fa8c16', icon: 'DataAnalysis' },
    ]
    await nextTick()
    renderHealth(d)
    renderTrend(d)
  } catch {
    // 接口不可用时使用默认占位
    await nextTick()
    renderHealth(null)
    renderTrend(null)
  }
}

function renderHealth(d: any) {
  if (!healthChart) return
  healthChart.setOption({
    title: { text: '项目健康度分布', left: 'center' },
    tooltip: { trigger: 'item' },
    legend: { bottom: 10 },
    series: [
      {
        name: '健康度',
        type: 'pie',
        radius: ['40%', '70%'],
        data: [
          { name: '正常', value: d?.normalProjects ?? 18, itemStyle: { color: '#67c23a' } },
          { name: '黄色', value: d?.yellowProjects ?? 7, itemStyle: { color: '#e6a23c' } },
          { name: '红色', value: d?.redProjects ?? 3, itemStyle: { color: '#f56c6c' } },
        ],
      },
    ],
  })
}

function renderTrend(d: any) {
  if (!trendChart) return
  trendChart.setOption({
    title: { text: '近 6 月收入/毛利趋势', left: 'center' },
    tooltip: { trigger: 'axis' },
    legend: { data: ['收入', '毛利'], top: 30 },
    grid: { top: 80, left: 50, right: 30, bottom: 30 },
    xAxis: { type: 'category', data: ['1月', '2月', '3月', '4月', '5月', '6月'] },
    yAxis: { type: 'value' },
    series: [
      { name: '收入', type: 'line', smooth: true, data: [420, 480, 530, 580, 620, 685], itemStyle: { color: '#409eff' }, areaStyle: { opacity: 0.2 } },
      { name: '毛利', type: 'line', smooth: true, data: [120, 140, 158, 170, 185, 198], itemStyle: { color: '#67c23a' }, areaStyle: { opacity: 0.2 } },
    ],
  })
}

function handleResize() {
  healthChart?.resize()
  trendChart?.resize()
}

onMounted(async () => {
  if (!userStore.userInfo) {
    userStore.fetchUserInfo().catch(() => { /* 已由全局拦截 */ })
  }
  await nextTick()
  if (healthRef.value) healthChart = echarts.init(healthRef.value)
  if (trendRef.value) trendChart = echarts.init(trendRef.value)
  await loadOverview()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  healthChart?.dispose()
  trendChart?.dispose()
})
</script>

<template>
  <div class="dashboard">
    <!-- 欢迎卡片 -->
    <el-card class="welcome-card" shadow="never">
      <div class="welcome-content">
        <div>
          <h2>下午好，{{ userStore.realName || userStore.username }}！</h2>
          <p>欢迎使用 PMIS 项目运营管理系统 · 当前时间：{{ formatDate(new Date(), 'YYYY-MM-DD HH:mm') }}</p>
        </div>
        <el-icon class="welcome-icon" :size="60"><Sunny /></el-icon>
      </div>
    </el-card>

    <!-- 关键指标 -->
    <el-row :gutter="16" class="metric-row">
      <el-col v-for="m in metrics" :key="m.title" :span="6">
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
              <p v-if="m.trend" class="metric-trend" :class="{ up: m.trend.startsWith('+') }">{{ m.trend }}</p>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="8">
        <el-card shadow="never">
          <div ref="healthRef" class="chart-area" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <div ref="trendRef" class="chart-area" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>待办事项</span>
              <el-badge :value="todoList.length" type="warning" />
            </div>
          </template>
          <el-scrollbar height="280px">
            <div v-for="todo in todoList" :key="todo.id" class="todo-item">
              <el-tag :type="todo.priority === 'high' ? 'danger' : todo.priority === 'medium' ? 'warning' : 'info'" size="small">
                {{ todo.priority === 'high' ? '紧急' : todo.priority === 'medium' ? '普通' : '低' }}
              </el-tag>
              <span class="todo-title">{{ todo.title }}</span>
              <span class="todo-time">{{ todo.time }}</span>
            </div>
          </el-scrollbar>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>
            <span>系统公告</span>
          </template>
          <el-scrollbar height="160px">
            <div v-for="news in newsList" :key="news.id" class="news-item">
              <el-icon color="#1890ff"><Bell /></el-icon>
              <span class="news-title">{{ news.title }}</span>
              <span class="news-date">{{ news.date }}</span>
            </div>
          </el-scrollbar>
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

.metric-row {
  margin-bottom: $spacing-md;
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
  }

  .metric-info {
    flex: 1;
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

  .metric-trend {
    font-size: $font-size-xs;
    color: $text-placeholder;

    &.up {
      color: $success-color;
    }
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

.todo-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm 0;
  border-bottom: 1px solid $border-extra-light;

  .todo-title {
    flex: 1;
    color: $text-regular;
  }

  .todo-time {
    font-size: $font-size-sm;
    color: $text-placeholder;
  }
}

.news-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm 0;
  border-bottom: 1px dashed $border-extra-light;

  .news-title {
    flex: 1;
    color: $text-regular;
  }

  .news-date {
    font-size: $font-size-sm;
    color: $text-placeholder;
  }
}
</style>
