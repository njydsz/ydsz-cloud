<!--
  @fileoverview 变量血缘图组件 (Vue 3)
  @description 可视化展示变量在规则中的使用关系：
  - 变量 → 规则的引用关系图
  - 高亮未使用变量和热点变量
  - 支持点击变量查看引用详情
  @module components/rule-engine/VariableLineageGraph
  @author ydsz-pmis-team
  @since 2.0.0
-->
<script setup lang="ts">
/**
 * VariableLineageGraph - 变量血缘图
 *
 * Props:
 *  - variables: 变量列表 [{ name, type, description }]
 *  - rules: 规则列表 [{ code, name, conditionExpression }]
 */
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from '@/utils/echarts'
import type { VariableDefinition, RuleDefinition } from '@/api/rule-engine'

interface Props {
  variables: VariableDefinition[]
  rules: RuleDefinition[]
}

const props = defineProps<Props>()

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

// 选中的变量
const selectedVar = ref<string | null>(null)
const linkedRules = computed(() => {
  if (!selectedVar.value) return []
  return props.rules
    .filter(r => r.conditionExpression?.includes(selectedVar.value))
    .map(r => ({ code: r.code, name: r.name, condition: r.conditionExpression }))
})

// 变量使用统计
const varUsage = computed(() => {
  const map = new Map<string, number>()
  for (const v of props.variables) {
    let count = 0
    for (const r of props.rules) {
      if (r.conditionExpression?.includes(v.name)) count++
      if (r.severityExpression?.includes(v.name)) count++
    }
    map.set(v.name, count)
  }
  return map
})

// 构建图数据
const graphData = computed(() => {
  const nodes: any[] = []
  const links: any[] = []

  // 变量节点
  for (const v of props.variables) {
    const usage = varUsage.value.get(v.name) || 0
    nodes.push({
      id: `var:${v.name}`,
      name: v.name,
      category: 0,
      symbolSize: 20 + usage * 5,
      itemStyle: {
        color: usage === 0 ? '#c0c4cc' : usage > 3 ? '#f56c6c' : '#409eff'
      },
      label: { show: true, fontSize: 11 },
      data: { type: 'variable', usage, description: v.description }
    })
  }

  // 规则节点
  for (const r of props.rules) {
    nodes.push({
      id: `rule:${r.code}`,
      name: r.code,
      category: 1,
      symbolSize: 25,
      itemStyle: { color: '#67c23a' },
      label: { show: true, fontSize: 10 },
      data: { type: 'rule', name: r.name }
    })
  }

  // 引用边
  for (const v of props.variables) {
    for (const r of props.rules) {
      if (r.conditionExpression?.includes(v.name) || r.severityExpression?.includes(v.name)) {
        links.push({
          source: `var:${v.name}`,
          target: `rule:${r.code}`,
          lineStyle: { width: 1, opacity: 0.4 }
        })
      }
    }
  }

  return { nodes, links }
})

function renderChart() {
  if (!chartRef.value) return

  if (!chart) {
    chart = echarts.init(chartRef.value)
    chart.on('click', (params: any) => {
      if (params.data?.data?.type === 'variable') {
        selectedVar.value = params.data.name
      }
    })
  }

  const data = graphData.value
  chart.setOption({
    tooltip: {
      formatter: (params: any) => {
        if (params.data?.data?.type === 'variable') {
          const d = params.data.data
          return `<b>${params.data.name}</b><br/>类型: 变量<br/>被引用: ${d.usage} 次<br/>${d.description || ''}`
        } else if (params.data?.data?.type === 'rule') {
          return `<b>${params.data.name}</b><br/>类型: 规则<br/>${params.data.data.name || ''}`
        }
        return params.data?.name || ''
      }
    },
    legend: {
      data: ['变量', '规则'],
      bottom: 10
    },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      force: {
        repulsion: 200,
        edgeLength: 80,
        gravity: 0.1
      },
      categories: [
        { name: '变量' },
        { name: '规则' }
      ],
      data: data.nodes,
      links: data.links,
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: 6,
      lineStyle: {
        color: '#dcdfe6',
        curveness: 0.1
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 3 }
      }
    }]
  })
}

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  nextTick(() => renderChart())
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})

watch(() => [props.variables, props.rules], () => {
  nextTick(() => renderChart())
}, { deep: true })
</script>

<template>
  <div class="lineage-graph">
    <div ref="chartRef" class="chart-container" />

    <!-- 引用详情面板 -->
    <el-drawer
      v-model="selectedVar"
      :title="`变量 ${selectedVar} 的引用关系`"
      size="400px"
      direction="rtl"
    >
      <div v-if="linkedRules.length > 0">
        <el-table :data="linkedRules" stripe size="small">
          <el-table-column prop="code" label="规则编码" width="120" />
          <el-table-column prop="name" label="规则名称" min-width="150" />
          <el-table-column prop="condition" label="条件表达式" min-width="200" show-overflow-tooltip />
        </el-table>
      </div>
      <el-empty v-else description="该变量未被任何规则引用" />
    </el-drawer>
  </div>
</template>

<style scoped>
.lineage-graph {
  height: 100%;
  position: relative;
}

.chart-container {
  width: 100%;
  height: 500px;
}
</style>
