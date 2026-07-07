<!--
  @file 规则执行链路追踪中心（P1-12 / P2-5）
  @description 独立路由页面：按 traceId / 规则编码 / 时间 / 触发状态检索执行链路，
               支持回放、上下文快照查看、详细错误展示。
               P2-5: 新增树形视图（按 traceId 分组展示规则链路）与甘特图视图（按耗时可视化）。
  @module views/execution/rule-engine/traces
  @author ydsz-pmis-team
  @since 1.5.0
-->
<template>
  <div class="trace-center">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">规则执行链路追踪中心</span>
          <div class="actions">
            <el-radio-group v-model="viewMode" size="small" style="margin-right: 12px">
              <el-radio-button value="list">列表</el-radio-button>
              <el-radio-button value="tree">树形</el-radio-button>
              <el-radio-button value="gantt">甘特图</el-radio-button>
            </el-radio-group>
            <el-button :icon="Refresh" @click="fetchTraces" :loading="loading">刷新</el-button>
            <el-button @click="goBack">返回</el-button>
          </div>
        </div>
      </template>

      <!-- 筛选区 -->
      <el-form :inline="true" class="filter-form">
        <el-form-item label="traceId">
          <el-input v-model="filterTraceId" placeholder="按 traceId 精确查询" clearable style="width: 240px" />
        </el-form-item>
        <el-form-item label="规则编码">
          <el-input v-model="filterRuleCode" placeholder="按规则编码" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="触发状态">
          <el-select v-model="filterTriggered" placeholder="全部" clearable style="width: 120px">
            <el-option label="仅触发" :value="true" />
            <el-option label="仅未触发" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchTraces">查询</el-button>
          <el-button text @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 列表视图（P3-1: 已迁移到 VirtualTable，支持虚拟滚动 + 自定义插槽） -->
      <VirtualTable
        v-if="viewMode === 'list'"
        :data="filteredTraces as Record<string, unknown>[]"
        :columns="traceColumns"
        :loading="loading"
        :height="520"
      >
        <template #col-triggered="{ row }">
          <el-tag :type="(row as ExecutionTrace).triggered ? 'danger' : 'info'" size="small">
            {{ (row as ExecutionTrace).triggered ? '触发' : '未触发' }}
          </el-tag>
        </template>
        <template #col-severity="{ row }">
          <el-tag :type="severityType((row as ExecutionTrace).severity)" size="small">
            {{ severityLabel((row as ExecutionTrace).severity) }}
          </el-tag>
        </template>
        <template #col-actions="{ row }">
          <el-button link type="primary" size="small" @click="openDetail(row as ExecutionTrace)">详情</el-button>
          <el-button link type="warning" size="small" @click="replayTraceRow(row as ExecutionTrace)">回放</el-button>
        </template>
      </VirtualTable>

      <!-- 树形视图（P2-5）：按 traceId 分组展示规则链路 -->
      <div v-else-if="viewMode === 'tree'" class="chart-container">
        <div v-if="filteredTraces.length === 0" class="chart-empty">
          <el-empty description="暂无链路数据" />
        </div>
        <div v-else ref="treeChartRef" class="chart-canvas" style="height: 520px"></div>
        <div class="chart-legend">
          <span class="legend-item"><i class="legend-dot" style="background:#409eff"></i>批次（traceId）</span>
          <span class="legend-item"><i class="legend-dot" style="background:#f56c6c"></i>已触发规则</span>
          <span class="legend-item"><i class="legend-dot" style="background:#909399"></i>未触发规则</span>
          <span class="legend-tip">点击规则节点查看详情</span>
        </div>
      </div>

      <!-- 甘特图视图（P2-5）：按耗时可视化 -->
      <div v-else-if="viewMode === 'gantt'" class="chart-container">
        <div v-if="filteredTraces.length === 0" class="chart-empty">
          <el-empty description="暂无链路数据" />
        </div>
        <div v-else ref="ganttChartRef" class="chart-canvas" style="height: 520px"></div>
        <div class="chart-legend">
          <span class="legend-item"><i class="legend-dot" style="background:#f56c6c"></i>红色 RED</span>
          <span class="legend-item"><i class="legend-dot" style="background:#e6a23c"></i>黄色 YELLOW</span>
          <span class="legend-item"><i class="legend-dot" style="background:#909399"></i>通知 / 未触发</span>
          <span class="legend-tip">支持滚轮缩放和拖拽</span>
        </div>
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="执行链路详情" width="800px">
      <el-descriptions v-if="currentTrace" :column="2" border>
        <el-descriptions-item label="Trace ID">{{ currentTrace.traceId }}</el-descriptions-item>
        <el-descriptions-item label="规则编码">{{ currentTrace.ruleCode }}</el-descriptions-item>
        <el-descriptions-item label="规则名称">{{ currentTrace.ruleName }}</el-descriptions-item>
        <el-descriptions-item label="场景">{{ currentTrace.scenario }}</el-descriptions-item>
        <el-descriptions-item label="触发">
          <el-tag :type="currentTrace.triggered ? 'danger' : 'info'" size="small">
            {{ currentTrace.triggered ? '是' : '否' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="严重度">
          <el-tag :type="severityType(currentTrace.severity)" size="small">
            {{ severityLabel(currentTrace.severity) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="条件结果">{{ currentTrace.conditionResult }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ currentTrace.elapsedMs }}ms</el-descriptions-item>
        <el-descriptions-item label="错误" :span="2" v-if="currentTrace.errorMessage">
          <el-text type="danger">{{ currentTrace.errorMessage }}</el-text>
        </el-descriptions-item>
      </el-descriptions>
      <el-tabs style="margin-top: 16px">
        <el-tab-pane label="输入事实">
          <pre class="json-view">{{ formatJson(currentTrace?.factsSnapshot) }}</pre>
        </el-tab-pane>
        <el-tab-pane label="输出结果">
          <pre class="json-view">{{ formatJson(currentTrace?.resultSnapshot) }}</pre>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- 回放结果对话框 -->
    <el-dialog v-model="replayVisible" title="回放结果" width="700px">
      <div v-if="replayResult">
        <el-alert
          :title="replayResult.diff?.summary || '回放中...'"
          :type="(replayResult.diff?.added?.length || 0) > 0 ? 'warning' : 'success'"
          :closable="false"
          show-icon
        />
        <el-descriptions :column="2" border style="margin-top: 12px">
          <el-descriptions-item label="traceId">{{ replayResult.traceId }}</el-descriptions-item>
          <el-descriptions-item label="历史触发">{{ replayResult.historicalTraces?.length || 0 }} 条</el-descriptions-item>
          <el-descriptions-item label="当前触发" :span="2">
            <el-tag v-for="r in replayResult.currentResults" :key="r.ruleCode" size="small" style="margin: 2px">
              {{ r.ruleCode }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-divider>差异分析</el-divider>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-card shadow="never" class="diff-card diff-added">
              <div class="diff-card-title">新增触发</div>
              <div class="diff-card-count">{{ replayResult.diff?.added?.length || 0 }}</div>
              <div class="diff-card-list">
                <el-tag v-for="c in replayResult.diff?.added" :key="c" type="success" size="small">{{ c }}</el-tag>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" class="diff-card diff-removed">
              <div class="diff-card-title">移除触发</div>
              <div class="diff-card-count">{{ replayResult.diff?.removed?.length || 0 }}</div>
              <div class="diff-card-list">
                <el-tag v-for="c in replayResult.diff?.removed" :key="c" type="warning" size="small">{{ c }}</el-tag>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" class="diff-card diff-unchanged">
              <div class="diff-card-title">保持不变</div>
              <div class="diff-card-count">{{ replayResult.diff?.unchanged?.length || 0 }}</div>
              <div class="diff-card-list">
                <el-tag v-for="c in replayResult.diff?.unchanged" :key="c" type="info" size="small">{{ c }}</el-tag>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import * as echarts from '@/utils/echarts'
import type { ECharts } from '@/utils/echarts'
import VirtualTable from '@/components/common/VirtualTable.vue'
import type { ColumnConfig } from '@/components/common/VirtualTable.vue'
import {
  getTrace, getTracesByRule, listRecentTraces, replayTrace,
  type ExecutionTrace, type ReplayResult,
} from '@/api/execution/rule-engine'

defineOptions({ name: 'RuleEngineTraceCenter' })

const router = useRouter()
const loading = ref(false)
const traces = ref<ExecutionTrace[]>([])

const filterTraceId = ref('')
const filterRuleCode = ref('')
const filterTriggered = ref<boolean | null>(null)

const detailVisible = ref(false)
const currentTrace = ref<ExecutionTrace | null>(null)

const replayVisible = ref(false)
const replayResult = ref<ReplayResult | null>(null)

/** P2-5 视图模式：list / tree / gantt */
const viewMode = ref<'list' | 'tree' | 'gantt'>('list')

/** 树形图容器 ref */
const treeChartRef = shallowRef<HTMLDivElement>()
/** 甘特图容器 ref */
const ganttChartRef = shallowRef<HTMLDivElement>()
/** 树形图实例 */
let treeChartInstance: ECharts | null = null
/** 甘特图实例 */
let ganttChartInstance: ECharts | null = null

/** 链路列表列配置 */
const traceColumns: ColumnConfig[] = [
  { field: 'traceId', title: 'Trace ID', width: 220 },
  { field: 'ruleCode', title: '规则编码', width: 180 },
  { field: 'ruleName', title: '规则名称', width: 180 },
  { field: 'triggered', title: '是否触发', width: 100, align: 'center', slot: true },
  { field: 'severity', title: '严重度', width: 100, slot: true },
  { field: 'elapsedMs', title: '耗时(ms)', width: 100, sortable: true },
  { field: 'scenario', title: '场景', width: 100 },
  { field: 'createdAt', title: '执行时间', width: 180 },
  { field: 'actions', title: '操作', width: 180, fixed: 'right', slot: true },
]

const filteredTraces = computed(() => {
  let list = traces.value
  if (filterTraceId.value) {
    list = list.filter(t => t.traceId?.includes(filterTraceId.value))
  }
  if (filterRuleCode.value) {
    list = list.filter(t => t.ruleCode?.includes(filterRuleCode.value))
  }
  if (filterTriggered.value !== null) {
    list = list.filter(t => t.triggered === filterTriggered.value)
  }
  return list
})

function severityType(s: string): 'danger' | 'warning' | 'info' | 'success' | 'primary' {
  if (s === 'RED') return 'danger'
  if (s === 'YELLOW') return 'warning'
  return 'info'
}
function severityLabel(s: string): string {
  if (s === 'RED') return '红色'
  if (s === 'YELLOW') return '黄色'
  if (s === 'NORMAL') return '通知'
  return s || '-'
}
/** 严重度 → 颜色（用于图表） */
function severityColor(s: string): string {
  if (s === 'RED') return '#f56c6c'
  if (s === 'YELLOW') return '#e6a23c'
  return '#909399'
}
function formatJson(obj: any): string {
  if (!obj) return '（空）'
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}

async function fetchTraces() {
  loading.value = true
  try {
    let res
    if (filterTraceId.value) {
      res = await getTrace(filterTraceId.value)
      traces.value = res.data || []
    } else if (filterRuleCode.value) {
      res = await getTracesByRule(filterRuleCode.value, 100)
      traces.value = res.data || []
    } else {
      res = await listRecentTraces(200)
      traces.value = res.data || []
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '查询失败')
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  filterTraceId.value = ''
  filterRuleCode.value = ''
  filterTriggered.value = null
  fetchTraces()
}

function openDetail(row: ExecutionTrace) {
  currentTrace.value = row
  detailVisible.value = true
}

async function replayTraceRow(row: ExecutionTrace) {
  try {
    const res = await replayTrace(row.traceId)
    if (res.code === 0) {
      replayResult.value = res.data
      replayVisible.value = true
    } else {
      ElMessage.error(res.message || '回放失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '回放异常')
  }
}

function goBack() {
  router.push('/rule-engine')
}

// ==================== P2-5 树形视图 ====================

/** 构建树形图 echarts option */
function buildTreeOption(): echarts.EChartsOption {
  const list = filteredTraces.value
  // 按 traceId 分组
  const groups = new Map<string, ExecutionTrace[]>()
  list.forEach((t) => {
    const arr = groups.get(t.traceId) || []
    arr.push(t)
    groups.set(t.traceId, arr)
  })

  const nodes: Record<string, unknown>[] = []
  const links: Record<string, unknown>[] = []
  const categories = [
    { name: '批次' },
    { name: '已触发' },
    { name: '未触发' },
  ]

  groups.forEach((traces, traceId) => {
    const triggeredCount = traces.filter(t => t.triggered).length
    nodes.push({
      id: `trace::${traceId}`,
      name: traceId,
      symbolSize: 36,
      category: 0,
      itemStyle: { color: '#409eff' },
      label: { show: true, position: 'right', fontSize: 11 },
      tooltip: {
        formatter: `批次: ${traceId}<br/>规则数: ${traces.length}<br/>触发: ${triggeredCount}`,
      },
    })
    traces.forEach((t) => {
      const nodeId = `rule::${traceId}::${t.ruleCode}`
      nodes.push({
        id: nodeId,
        name: t.ruleCode,
        symbolSize: 24,
        category: t.triggered ? 1 : 2,
        itemStyle: { color: t.triggered ? '#f56c6c' : '#909399' },
        label: { show: true, position: 'right', fontSize: 10 },
        tooltip: {
          formatter: `规则: ${t.ruleName || t.ruleCode}<br/>触发: ${t.triggered ? '是' : '否'}<br/>严重度: ${severityLabel(t.severity)}<br/>耗时: ${t.elapsedMs}ms`,
        },
        traceData: t,
      })
      links.push({
        source: `trace::${traceId}`,
        target: nodeId,
      })
    })
  })

  return {
    tooltip: {},
    legend: {
      data: categories.map(c => c.name),
      bottom: 10,
      textStyle: { fontSize: 11 },
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        label: { show: true },
        force: {
          repulsion: 200,
          edgeLength: 80,
          gravity: 0.1,
        },
        categories,
        data: nodes,
        links,
        lineStyle: {
          color: '#c0c4cc',
          width: 1,
          curveness: 0.1,
        },
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 2 },
        },
      },
    ],
    animation: false,
  }
}

/** 初始化树形图 */
function initTreeChart() {
  if (!treeChartRef.value) return
  if (treeChartInstance) treeChartInstance.dispose()
  treeChartInstance = echarts.init(treeChartRef.value, null, { renderer: 'canvas' })
  treeChartInstance.setOption(buildTreeOption())
  // 点击规则节点 → 打开详情
  treeChartInstance.on('click', (params) => {
    const data = params.data as { traceData?: ExecutionTrace } | undefined
    const traceData = data?.traceData
    if (traceData) {
      openDetail(traceData)
    }
  })
}

// ==================== P2-5 甘特图视图 ====================

/** 构建甘特图 echarts option */
function buildGanttOption(): echarts.EChartsOption {
  const list = filteredTraces.value
  // Y 轴：规则编码（去重，保留出现顺序）
  const ruleCodes: string[] = []
  list.forEach((t) => {
    if (!ruleCodes.includes(t.ruleCode)) ruleCodes.push(t.ruleCode)
  })

  // 每条 trace 一个条形，值为 elapsedMs
  const dataPoints = list.map((t) => ({
    name: t.ruleCode,
    value: [t.ruleCode, t.elapsedMs],
    itemStyle: { color: severityColor(t.severity) },
    traceData: t,
  }))

  return {
    tooltip: {
      trigger: 'item',
      formatter: (params: { data?: { traceData?: ExecutionTrace } }) => {
        const t = params?.data?.traceData
        if (!t) return ''
        return [
          `规则: ${t.ruleName || t.ruleCode}`,
          `Trace: ${t.traceId}`,
          `触发: ${t.triggered ? '是' : '否'}`,
          `严重度: ${severityLabel(t.severity)}`,
          `耗时: ${t.elapsedMs}ms`,
          `时间: ${t.createdAt}`,
        ].join('<br/>')
      },
    },
    grid: { left: 160, right: 40, top: 30, bottom: 60 },
    xAxis: {
      type: 'value',
      name: '耗时(ms)',
      nameLocation: 'middle',
      nameGap: 30,
      axisLabel: { fontSize: 11 },
    },
    yAxis: {
      type: 'category',
      data: ruleCodes,
      axisLabel: { fontSize: 11 },
    },
    dataZoom: [
      { type: 'slider', xAxisIndex: 0, start: 0, end: 100, height: 20, bottom: 20 },
      { type: 'inside', xAxisIndex: 0 },
      { type: 'inside', yAxisIndex: 0 },
    ],
    series: [
      {
        type: 'bar',
        data: dataPoints,
        barMaxWidth: 24,
        label: {
          show: true,
          position: 'right',
          formatter: (p: { value?: unknown[] }) => {
            const v = p.value
            return Array.isArray(v) ? `${v[1]}ms` : ''
          },
          fontSize: 10,
        },
      },
    ],
    animation: false,
  }
}

/** 初始化甘特图 */
function initGanttChart() {
  if (!ganttChartRef.value) return
  if (ganttChartInstance) ganttChartInstance.dispose()
  ganttChartInstance = echarts.init(ganttChartRef.value, null, { renderer: 'canvas' })
  ganttChartInstance.setOption(buildGanttOption())
  ganttChartInstance.on('click', (params) => {
    const data = params.data as { traceData?: ExecutionTrace } | undefined
    const traceData = data?.traceData
    if (traceData) {
      openDetail(traceData)
    }
  })
}

// ==================== 图表生命周期 ====================

/** 视图模式切换 → 初始化对应图表 */
watch(viewMode, (mode) => {
  if (mode === 'list') return
  nextTick(() => {
    if (mode === 'tree') initTreeChart()
    else if (mode === 'gantt') initGanttChart()
  })
})

/** 数据变化 → 更新当前图表 */
watch(
  () => filteredTraces.value,
  () => {
    nextTick(() => {
      if (viewMode.value === 'tree' && treeChartInstance) {
        treeChartInstance.setOption(buildTreeOption(), true)
      } else if (viewMode.value === 'gantt' && ganttChartInstance) {
        ganttChartInstance.setOption(buildGanttOption(), true)
      }
    })
  },
  { deep: true },
)

/** 窗口缩放 → 自适应图表 */
function handleResize() {
  treeChartInstance?.resize()
  ganttChartInstance?.resize()
}

onMounted(() => {
  fetchTraces()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  treeChartInstance?.dispose()
  ganttChartInstance?.dispose()
  treeChartInstance = null
  ganttChartInstance = null
})
</script>

<style scoped lang="scss">
.trace-center {
  padding: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  .title { font-weight: 600; font-size: 16px; }
  .actions { display: flex; gap: 8px; align-items: center; }
}
.filter-form { margin-bottom: 12px; }

.json-view {
  background: #1e293b;
  color: #e2e8f0;
  padding: 12px;
  border-radius: 4px;
  max-height: 360px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.5;
  font-family: 'JetBrains Mono', 'Courier New', monospace;
}

.diff-card {
  text-align: center;
  .diff-card-title { font-size: 12px; color: #64748b; margin-bottom: 8px; }
  .diff-card-count { font-size: 24px; font-weight: 600; margin-bottom: 8px; }
  .diff-card-list { display: flex; flex-wrap: wrap; gap: 4px; justify-content: center; }
}
.diff-added .diff-card-count { color: #16a34a; }
.diff-removed .diff-card-count { color: #f59e0b; }
.diff-unchanged .diff-card-count { color: #3b82f6; }

/* P2-5 图表容器 */
.chart-container {
  .chart-canvas {
    width: 100%;
    min-height: 400px;
  }
  .chart-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 400px;
  }
  .chart-legend {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 16px;
    padding: 8px 4px;
    margin-top: 4px;
    font-size: 12px;
    color: #606266;

    .legend-item {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
    .legend-dot {
      display: inline-block;
      width: 10px;
      height: 10px;
      border-radius: 50%;
    }
    .legend-tip {
      margin-left: auto;
      color: #909399;
      font-style: italic;
    }
  }
}
</style>
