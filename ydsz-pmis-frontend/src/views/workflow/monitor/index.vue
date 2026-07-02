<script setup lang="ts">
/**
 * @file 流程运行中心
 * @module views/workflow/monitor
 * @description 管理员视角：监控所有运行中的流程实例 + 强制操作（终止/挂起/激活/跳转）
 * P0-9: 流程运行中心（对标 Activiti Admin / Flowable Admin）。
 */
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  pageInstances,
  terminateInstance,
  suspendInstance,
  activateInstance,
  listOverdueTasks,
  nodeDurationStats,
} from '@/api/workflow'
import type {
  FlowInstanceDTO,
  FlowTaskDTO,
  FlowNodeDurationStatDTO,
} from '@/api/workflow/types'
import * as echarts from 'echarts/core'

const router = useRouter()

// 监控列表
const query = reactive({
  flowCode: '',
  flowName: '',
  status: 'RUNNING',
  pageNum: 1,
  pageSize: 20,
})
const list = ref<FlowInstanceDTO[]>([])
const total = ref(0)
const loading = ref(false)

async function loadList() {
  loading.value = true
  try {
    const res = await pageInstances(query)
    if (res.data?.code === 0) {
      list.value = res.data.data?.records || []
      total.value = res.data.data?.total || 0
    }
  } finally {
    loading.value = false
  }
}

async function forceTerminate(row: FlowInstanceDTO) {
  try {
    const { value } = await ElMessageBox.prompt('请输入终止原因', '强制终止', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputType: 'textarea',
    })
    const res = await terminateInstance(row.id, value)
    if (res.data?.code === 0) {
      ElMessage.success('已终止')
      loadList()
    } else {
      ElMessage.error(res.data?.message || '操作失败')
    }
  } catch {
    // cancel
  }
}

async function forceSuspend(row: FlowInstanceDTO) {
  const res = await suspendInstance(row.id)
  if (res.data?.code === 0) {
    ElMessage.success('已挂起')
    loadList()
  } else {
    ElMessage.error(res.data?.message || '操作失败')
  }
}

async function forceActivate(row: FlowInstanceDTO) {
  const res = await activateInstance(row.id)
  if (res.data?.code === 0) {
    ElMessage.success('已激活')
    loadList()
  } else {
    ElMessage.error(res.data?.message || '操作失败')
  }
}

function goInstance(row: FlowInstanceDTO) {
  router.push({ path: '/workflow/instance', query: { id: String(row.id) } })
}

// 超期任务
const overdueLoading = ref(false)
const overdueList = ref<FlowTaskDTO[]>([])
const overdueTotal = ref(0)
const overdueQuery = reactive({ pageNum: 1, pageSize: 10 })

async function loadOverdue() {
  overdueLoading.value = true
  try {
    const res = await listOverdueTasks(overdueQuery)
    if (res.data?.code === 0) {
      overdueList.value = res.data.data?.records || []
      overdueTotal.value = res.data.data?.total || 0
    }
  } finally {
    overdueLoading.value = false
  }
}

// 节点耗时统计
const statsLoading = ref(false)
const statsList = ref<FlowNodeDurationStatDTO[]>([])

async function loadStats() {
  statsLoading.value = true
  try {
    const res = await nodeDurationStats({})
    if (res.data?.code === 0) {
      statsList.value = res.data.data || []
      renderChart()
    }
  } finally {
    statsLoading.value = false
  }
}

// ECharts 节点耗时 Top 10
const chartRef = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null

function renderChart() {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }
  const top = statsList.value
    .sort((a, b) => (b.avgDurationMs || 0) - (a.avgDurationMs || 0))
    .slice(0, 10)
  chart.setOption({
    title: { text: '节点平均耗时 TOP 10', left: 'center' },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 100, right: 30, top: 50, bottom: 30 },
    xAxis: { type: 'value', name: '毫秒' },
    yAxis: {
      type: 'category',
      data: top.map((s) => s.nodeName || s.nodeCode).reverse(),
    },
    series: [
      {
        type: 'bar',
        data: top
          .map((s) => ({
            value: Math.round(s.avgDurationMs || 0),
            itemStyle: { color: (s.overdueCount || 0) > 0 ? '#f5222d' : '#1890ff' },
          }))
          .reverse(),
        label: { show: true, position: 'right', formatter: '{c} ms' },
      },
    ],
  })
}

// 状态统计（KPI）
const stats = computed(() => {
  return {
    running: list.value.length,
    overdue: overdueList.value.length,
    avgDuration:
      statsList.value.length > 0
        ? Math.round(
            statsList.value.reduce((s, x) => s + (x.avgDurationMs || 0), 0) /
              statsList.value.length,
          )
        : 0,
  }
})

const statusMap: Record<string, { label: string; type: string }> = {
  RUNNING: { label: '审批中', type: 'warning' },
  SUSPENDED: { label: '已挂起', type: 'info' },
  COMPLETED: { label: '已完成', type: 'success' },
  TERMINATED: { label: '已终止', type: 'danger' },
  REJECTED: { label: '已驳回', type: 'danger' },
}

onMounted(() => {
  loadList()
  loadOverdue()
  loadStats()
  window.addEventListener('resize', resize)
})
onUnmounted(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
})
function resize() {
  chart?.resize()
}

function durationLabel(ms?: number) {
  if (!ms || ms <= 0) return '-'
  const s = Math.floor(ms / 1000)
  if (s < 60) return `${s}秒`
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}分`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}时${m % 60}分`
  return `${Math.floor(h / 24)}天${h % 24}时`
}
</script>

<template>
  <div class="workflow-monitor">
    <div class="page-header">
      <h2>流程运行中心</h2>
      <p class="page-header__sub">管理员视角：监控所有流程 + 强制操作 + 节点耗时分析</p>
    </div>

    <!-- KPI 卡片 -->
    <div class="kpi-row">
      <el-card shadow="hover" class="kpi-card">
        <div class="kpi-label">运行中</div>
        <div class="kpi-value" style="color: #1890ff">{{ stats.running }}</div>
      </el-card>
      <el-card shadow="hover" class="kpi-card">
        <div class="kpi-label">超期任务</div>
        <div class="kpi-value" style="color: #f5222d">{{ stats.overdue }}</div>
      </el-card>
      <el-card shadow="hover" class="kpi-card">
        <div class="kpi-label">平均节点耗时</div>
        <div class="kpi-value" style="color: #fa8c16">{{ durationLabel(stats.avgDuration) }}</div>
      </el-card>
    </div>

    <!-- 流程实例列表 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <span>运行中的流程</span>
          <el-select
            v-model="query.status"
            placeholder="状态"
            clearable
            style="width: 140px"
            @change="loadList"
          >
            <el-option label="审批中" value="RUNNING" />
            <el-option label="已挂起" value="SUSPENDED" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已终止" value="TERMINATED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
        </div>
      </template>
      <div class="filter-bar">
        <el-input
          v-model="query.flowCode"
          placeholder="流程编码"
          clearable
          style="width: 200px"
          @keyup.enter="loadList"
        />
        <el-input
          v-model="query.flowName"
          placeholder="流程名称"
          clearable
          style="width: 200px"
          @keyup.enter="loadList"
        />
        <el-button type="primary" @click="loadList">查询</el-button>
        <el-button @click="() => { query.pageNum = 1; loadList() }">重置</el-button>
      </div>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="实例 ID" width="80" />
        <el-table-column prop="flowName" label="流程" min-width="160" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="businessNo" label="业务单号" width="160" />
        <el-table-column prop="initiatorName" label="发起人" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="(statusMap[row.status]?.type as any) || 'info'" size="small">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentNodeName" label="当前节点" width="120" />
        <el-table-column label="发起时间" width="160">
          <template #default="{ row }">
            {{ row.startTime ? dayjs(row.startTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text @click="goInstance(row)">详情</el-button>
            <el-button
              v-if="row.status === 'RUNNING'"
              size="small"
              text
              type="warning"
              @click="forceSuspend(row)"
            >挂起</el-button>
            <el-button
              v-if="row.status === 'SUSPENDED'"
              size="small"
              text
              type="primary"
              @click="forceActivate(row)"
            >激活</el-button>
            <el-button
              v-if="row.status === 'RUNNING' || row.status === 'SUSPENDED'"
              size="small"
              text
              type="danger"
              @click="forceTerminate(row)"
            >终止</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="loadList"
        @size-change="loadList"
      />
    </el-card>

    <div class="two-col">
      <el-card shadow="never" class="section">
        <template #header>
          <span>超期任务 TOP 10</span>
        </template>
        <el-table :data="overdueList" v-loading="overdueLoading" size="small">
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column prop="flowName" label="流程" width="140" show-overflow-tooltip />
          <el-table-column prop="assigneeName" label="办理人" width="100" />
          <el-table-column prop="nodeName" label="节点" width="100" />
          <el-table-column label="截止时间" width="160">
            <template #default="{ row }">
              <span style="color: #f5222d">{{ dayjs(row.dueAt).format('YYYY-MM-DD HH:mm') }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
      <el-card shadow="never" class="section">
        <template #header>
          <span>节点耗时分析</span>
        </template>
        <div ref="chartRef" style="width: 100%; height: 360px" v-loading="statsLoading"></div>
      </el-card>
    </div>
  </div>
</template>

<style scoped lang="scss">
.workflow-monitor {
  padding: 16px;
}

.page-header {
  margin-bottom: 16px;

  h2 {
    margin: 0;
    font-size: 20px;
    color: #1e293b;
  }

  &__sub {
    margin: 4px 0 0;
    color: #64748b;
    font-size: 13px;
  }
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.kpi-card {
  text-align: center;

  .kpi-label {
    font-size: 13px;
    color: #64748b;
    margin-bottom: 8px;
  }
  .kpi-value {
    font-size: 28px;
    font-weight: 700;
  }
}

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

.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
