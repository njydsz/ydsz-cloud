<!--
  @file 规则依赖拓扑图（P2-6）
  @description 可视化展示规则之间的依赖关系：
               - 力导向 / 环形 / 树形三种布局切换
               - 节点按启用状态着色（绿色=已启用、红色=已停用）
               - 节点大小按被依赖数量缩放
               - 点击节点查看规则详情并跳转编辑
  @module views/execution/rule-engine/dependency-graph
  @author ydsz-pmis-team
  @since 1.5.0
-->
<template>
  <div class="dependency-graph-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">规则依赖拓扑图</span>
          <div class="actions">
            <el-select
              v-model="focusRuleCode"
              placeholder="聚焦规则（可选）"
              clearable
              filterable
              style="width: 240px"
              aria-label="选择聚焦规则"
            >
              <el-option
                v-for="r in rules"
                :key="r.code"
                :label="`${r.name}（${r.code}）`"
                :value="r.code"
              />
            </el-select>
            <el-radio-group v-model="layoutMode" size="small">
              <el-radio-button value="force">力导向</el-radio-button>
              <el-radio-button value="circular">环形</el-radio-button>
              <el-radio-button value="tree">树形</el-radio-button>
            </el-radio-group>
            <el-button :icon="Refresh" :loading="loading" @click="fetchAll">刷新</el-button>
            <el-button @click="goBack">返回</el-button>
          </div>
        </div>
      </template>

      <!-- 图表区域 -->
      <div v-loading="loading" class="chart-wrapper">
        <div v-if="graphNodes.length === 0 && !loading" class="chart-empty">
          <el-empty description="暂无依赖关系数据" />
        </div>
        <div v-show="graphNodes.length > 0" ref="chartRef" class="chart-canvas"></div>
      </div>

      <!-- 图例 -->
      <div class="chart-legend">
        <span class="legend-item"><i class="legend-dot" style="background:#67c23a"></i>已启用</span>
        <span class="legend-item"><i class="legend-dot" style="background:#f56c6c"></i>已停用</span>
        <span class="legend-item"><i class="legend-dot" style="background:#409eff"></i>聚焦规则</span>
        <span class="legend-tip">节点越大表示被依赖越多；点击节点查看详情</span>
      </div>
    </el-card>

    <!-- 规则详情对话框 -->
    <el-dialog v-model="detailVisible" title="规则详情" width="600px">
      <el-descriptions v-if="selectedRule" :column="2" border size="small">
        <el-descriptions-item label="规则编码">{{ selectedRule.code }}</el-descriptions-item>
        <el-descriptions-item label="规则名称">{{ selectedRule.name }}</el-descriptions-item>
        <el-descriptions-item label="类别">{{ selectedRule.category }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="selectedRule.enabled ? 'success' : 'danger'" size="small">
            {{ selectedRule.enabled ? '已启用' : '已停用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="版本">v{{ selectedRule.version }}</el-descriptions-item>
        <el-descriptions-item label="优先级">{{ selectedRule.priority }}</el-descriptions-item>
        <el-descriptions-item label="默认严重度">
          <el-tag :type="severityType(selectedRule.defaultSeverity)" size="small">
            {{ selectedRule.defaultSeverity }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="责任人">{{ selectedRule.owner || '-' }}</el-descriptions-item>
        <el-descriptions-item label="正向依赖" :span="2">
          <el-tag
            v-for="dep in selectedRuleDeps"
            :key="dep.dependsOnRuleCode"
            size="small"
            type="warning"
            style="margin: 2px"
          >{{ dep.dependsOnRuleCode }}（{{ dep.dependencyType }}）</el-tag>
          <span v-if="selectedRuleDeps.length === 0" style="color:#909399">无</span>
        </el-descriptions-item>
        <el-descriptions-item label="被依赖" :span="2">
          <el-tag
            v-for="dep in selectedRuleDependents"
            :key="dep.ruleCode"
            size="small"
            type="info"
            style="margin: 2px"
          >{{ dep.ruleCode }}</el-tag>
          <span v-if="selectedRuleDependents.length === 0" style="color:#909399">无</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="goToRuleEngine">前往规则引擎编辑</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import dagre from 'dagre'
import * as echarts from '@/utils/echarts'
import type { ECharts } from '@/utils/echarts'
import {
  listRules, listDependencies,
  type RuleDefinition, type RuleDependency,
} from '@/api/execution/rule-engine'

defineOptions({ name: 'RuleEngineDependencyGraph' })

const router = useRouter()
const loading = ref(false)

/** 全部规则列表 */
const rules = ref<RuleDefinition[]>([])
/** 全部依赖关系（正向） */
const allDependencies = ref<RuleDependency[]>([])

/** 布局模式 */
const layoutMode = ref<'force' | 'circular' | 'tree'>('force')
/** 聚焦规则编码 */
const focusRuleCode = ref<string>('')

/** 图表容器 ref */
const chartRef = shallowRef<HTMLDivElement>()
/** 图表实例 */
let chartInstance: ECharts | null = null

/** 详情对话框 */
const detailVisible = ref(false)
/** 选中的规则 */
const selectedRule = ref<RuleDefinition | null>(null)
/** 选中规则的正向依赖 */
const selectedRuleDeps = ref<RuleDependency[]>([])
/** 选中规则的反向依赖 */
const selectedRuleDependents = ref<RuleDependency[]>([])

/** 规则编码 → 规则定义 映射 */
const ruleMap = computed(() => {
  const m = new Map<string, RuleDefinition>()
  rules.value.forEach((r) => m.set(r.code, r))
  return m
})

/** 被依赖次数统计（ruleCode → count） */
const dependedCount = computed(() => {
  const m = new Map<string, number>()
  allDependencies.value.forEach((d) => {
    m.set(d.dependsOnRuleCode, (m.get(d.dependsOnRuleCode) || 0) + 1)
  })
  return m
})

/** 图节点列表 */
const graphNodes = computed(() => {
  const nodeSet = new Set<string>()
  // 如果有聚焦规则，只显示该规则 1-hop 邻域
  if (focusRuleCode.value) {
    nodeSet.add(focusRuleCode.value)
    allDependencies.value.forEach((d) => {
      if (d.ruleCode === focusRuleCode.value) nodeSet.add(d.dependsOnRuleCode)
      if (d.dependsOnRuleCode === focusRuleCode.value) nodeSet.add(d.ruleCode)
    })
  } else {
    // 显示全部有依赖关系的规则
    allDependencies.value.forEach((d) => {
      nodeSet.add(d.ruleCode)
      nodeSet.add(d.dependsOnRuleCode)
    })
  }
  return Array.from(nodeSet)
})

/** 图边列表 */
const graphLinks = computed(() => {
  if (focusRuleCode.value) {
    return allDependencies.value
      .filter((d) => d.ruleCode === focusRuleCode.value || d.dependsOnRuleCode === focusRuleCode.value)
      .map((d) => ({ source: d.ruleCode, target: d.dependsOnRuleCode, depType: d.dependencyType }))
  }
  return allDependencies.value.map((d) => ({ source: d.ruleCode, target: d.dependsOnRuleCode, depType: d.dependencyType }))
})

function severityType(s: string): 'danger' | 'warning' | 'info' | 'success' | 'primary' {
  if (s === 'RED') return 'danger'
  if (s === 'YELLOW') return 'warning'
  return 'info'
}

/** 构建节点颜色 */
function nodeColor(ruleCode: string): string {
  if (focusRuleCode.value && ruleCode === focusRuleCode.value) return '#409eff'
  const rule = ruleMap.value.get(ruleCode)
  return rule?.enabled ? '#67c23a' : '#f56c6c'
}

/** 构建节点大小（被依赖越多越大） */
function nodeSize(ruleCode: string): number {
  const count = dependedCount.value.get(ruleCode) || 0
  return 20 + Math.min(count * 6, 40)
}

/** 使用 dagre 计算树形布局位置 */
function computeDagreLayout(
  nodes: { id: string }[],
  links: { source: string; target: string }[],
): Map<string, { x: number; y: number }> {
  const g = new dagre.graphlib.Graph()
  g.setGraph({ rankdir: 'TB', ranksep: 100, nodesep: 50, marginx: 40, marginy: 40 })
  g.setDefaultEdgeLabel(() => ({}))
  nodes.forEach((n) => g.setNode(n.id, { width: 100, height: 40 }))
  links.forEach((l) => g.setEdge(l.source, l.target))
  try {
    dagre.layout(g)
  } catch {
    // 布局失败（可能有环），退回
    return new Map()
  }
  const positions = new Map<string, { x: number; y: number }>()
  nodes.forEach((n) => {
    const node = g.node(n.id)
    if (node) {
      positions.set(n.id, { x: node.x, y: node.y })
    }
  })
  return positions
}

/** 构建 echarts option */
function buildChartOption(): echarts.EChartsOption {
  const nodeIds = graphNodes.value
  const links = graphLinks.value

  const dagrePositions = layoutMode.value === 'tree'
    ? computeDagreLayout(nodeIds.map((id) => ({ id })), links)
    : new Map<string, { x: number; y: number }>()

  const nodes = nodeIds.map((id) => {
    const rule = ruleMap.value.get(id)
    const node: Record<string, unknown> = {
      id,
      name: id,
      symbolSize: nodeSize(id),
      itemStyle: { color: nodeColor(id) },
      label: {
        show: true,
        position: 'right',
        fontSize: 11,
        formatter: () => rule ? `${id}\n${rule.name}` : id,
      },
      tooltip: {
        formatter: () => {
          const r = ruleMap.value.get(id)
          if (!r) return id
          const depCount = dependedCount.value.get(id) || 0
          return [
            `编码: ${r.code}`,
            `名称: ${r.name}`,
            `类别: ${r.category}`,
            `状态: ${r.enabled ? '已启用' : '已停用'}`,
            `被依赖次数: ${depCount}`,
          ].join('<br/>')
        },
      },
      ruleCode: id,
    }
    if (layoutMode.value === 'tree' && dagrePositions.has(id)) {
      const pos = dagrePositions.get(id)!
      node.x = pos.x
      node.y = pos.y
      node.fixed = true
    }
    return node
  })

  const linkData = links.map((l) => ({
    source: l.source,
    target: l.target,
    lineStyle: {
      color: '#c0c4cc',
      width: 1.5,
      curveness: 0.1,
    },
    tooltip: {
      formatter: `${l.source} → ${l.target}（${l.depType}）`,
    },
  }))

  const series: Record<string, unknown> = {
    type: 'graph',
    roam: true,
    draggable: true,
    label: { show: true },
    data: nodes,
    links: linkData,
    emphasis: {
      focus: 'adjacency',
      lineStyle: { width: 3 },
    },
    lineStyle: { color: '#c0c4cc', width: 1.5, curveness: 0.1 },
  }

  if (layoutMode.value === 'force') {
    series.layout = 'force'
    series.force = {
      repulsion: 300,
      edgeLength: 120,
      gravity: 0.1,
    }
  } else if (layoutMode.value === 'circular') {
    series.layout = 'circular'
    series.circular = {
      rotateLabel: true,
    }
  } else {
    series.layout = 'none'
  }

  return {
    tooltip: {},
    series: [series],
    animation: false,
  }
}

/** 初始化图表 */
function initChart() {
  if (!chartRef.value) return
  if (chartInstance) chartInstance.dispose()
  chartInstance = echarts.init(chartRef.value, null, { renderer: 'canvas' })
  chartInstance.setOption(buildChartOption())
  // 点击节点 → 显示详情
  chartInstance.on('click', (params) => {
    const data = params.data as { ruleCode?: string } | undefined
    const ruleCode = data?.ruleCode
    if (ruleCode) {
      openDetail(ruleCode)
    }
  })
}

/** 打开规则详情 */
async function openDetail(ruleCode: string) {
  const rule = ruleMap.value.get(ruleCode)
  if (!rule) {
    ElMessage.warning(`规则 ${ruleCode} 不在规则列表中`)
    return
  }
  selectedRule.value = rule
  selectedRuleDeps.value = allDependencies.value.filter((d) => d.ruleCode === ruleCode)
  selectedRuleDependents.value = allDependencies.value.filter((d) => d.dependsOnRuleCode === ruleCode)
  detailVisible.value = true
}

/** 前往规则引擎页面 */
function goToRuleEngine() {
  if (selectedRule.value) {
    router.push({ path: '/execution/rule-engine', query: { code: selectedRule.value.code } })
  } else {
    router.push('/execution/rule-engine')
  }
}

function goBack() {
  router.push('/execution/rule-engine')
}

/** 拉取全部规则 + 依赖关系 */
async function fetchAll() {
  loading.value = true
  try {
    const { data: ruleList } = await listRules()
    rules.value = ruleList || []

    // 并发拉取每条规则的正向依赖
    const depResults = await Promise.allSettled(
      rules.value.map((r) => listDependencies(r.code)),
    )
    const deps: RuleDependency[] = []
    depResults.forEach((res) => {
      if (res.status === 'fulfilled' && res.value.data) {
        deps.push(...res.value.data)
      }
    })
    allDependencies.value = deps

    if (deps.length === 0) {
      ElMessage.info('当前无规则依赖关系')
    }
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '加载依赖关系失败')
  } finally {
    loading.value = false
  }
}

// ==================== 生命周期 ====================

/** 布局 / 聚焦变化 → 重建图表 */
watch([layoutMode, focusRuleCode], () => {
  nextTick(() => {
    if (graphNodes.value.length > 0) {
      initChart()
    }
  })
})

/** 数据变化 → 更新图表 */
watch(
  () => allDependencies.value,
  () => {
    nextTick(() => {
      if (graphNodes.value.length > 0) {
        initChart()
      }
    })
  },
)

/** 窗口缩放 */
function handleResize() {
  chartInstance?.resize()
}

onMounted(() => {
  fetchAll()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<style scoped lang="scss">
.dependency-graph-page {
  padding: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  .title { font-weight: 600; font-size: 16px; }
  .actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
}
.chart-wrapper {
  min-height: 500px;
}
.chart-canvas {
  width: 100%;
  height: 560px;
}
.chart-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 500px;
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
</style>
