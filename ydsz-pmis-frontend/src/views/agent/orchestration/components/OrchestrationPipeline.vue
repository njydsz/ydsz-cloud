<script setup lang="ts">
/**
 * 编排流程图 (内嵌 SVG 自绘)
 *
 * 根据 4 种模式呈现不同形态：
 *  - SEQUENTIAL：横向流水线，箭头连接
 *  - PARALLEL：  扇形发散，所有 Agent 并发到协调器
 *  - VOTING：    多 Agent 投票汇聚到一个融合节点
 *  - CASCADE：   纵向阶梯，达标即停
 *
 * 节点颜色按告警等级着色：RED / YELLOW / NORMAL。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { OrchestrationModeCode, OrchestrationResult } from '@/api/agent/orchestration/types'

const { t } = useI18n()

const props = defineProps<{
  mode: OrchestrationModeCode
  agentTypes: string[]
  result: OrchestrationResult | null
}>()

const NODE_W = 110
const NODE_H = 50

interface NodeView {
  x: number
  y: number
  label: string
  level: string
  desc: string
}

interface EdgeView {
  d: string
  color: string
}

const view = computed<{ width: number; height: number; nodes: NodeView[]; edges: EdgeView[] }>(() => {
  const agents = props.agentTypes.length ? props.agentTypes : ['?']
  const executed = props.result?.executedAgents || []
  const executedSet = new Set(executed)
  const finalAgent = props.result?.finalResult?.agentType || null

  // 节点颜色
  function levelOf(agent: string): string {
    if (!props.result) return 'PENDING'
    const ar = props.result.agentResults[agent]
    if (!ar) return 'UNKNOWN'
    return ar.alertLevel || 'NORMAL'
  }

  function colorFor(level: string): string {
    switch (level) {
      case 'RED': return '#F56C6C'
      case 'YELLOW': return '#E6A23C'
      case 'NORMAL': return '#67C23A'
      case 'INFO': return '#909399'
      default: return '#C0C4CC'
    }
  }

  const nodes: NodeView[] = []
  const edges: EdgeView[] = []

  if (props.mode === 'SEQUENTIAL' || props.mode === 'CASCADE') {
    // 横向流水线
    const padding = 20
    const gap = 60
    const totalW = padding * 2 + agents.length * NODE_W + (agents.length - 1) * gap
    const totalH = NODE_H + padding * 2 + 40
    agents.forEach((agent, i) => {
      const x = padding + i * (NODE_W + gap)
      const y = padding + 20
      const level = levelOf(agent)
      nodes.push({ x, y, label: agent, level, desc: getAgentDesc(agent) })
      if (i > 0) {
        const x1 = x - gap
        edges.push({ d: `M ${x1} ${y + NODE_H / 2} L ${x} ${y + NODE_H / 2}`, color: '#909399' })
      }
    })
    // 达标节点添加 ✓ 标记
    if (props.mode === 'CASCADE' && props.result) {
      // 已执行节点加描边
      nodes.forEach((n) => {
        if (executedSet.has(n.label)) {
          n.desc = `${n.desc} ✓`
        }
      })
    }
    return { width: totalW, height: totalH, nodes, edges }
  }

  if (props.mode === 'PARALLEL') {
    // 扇形发散到协调器
    const padding = 20
    const gapY = 60
    const cx = padding + NODE_W + 80
    const totalH = padding * 2 + agents.length * NODE_H + (agents.length - 1) * gapY
    const totalW = padding * 2 + NODE_W + 80 + NODE_W + padding
    agents.forEach((agent, i) => {
      const y = padding + i * (NODE_H + gapY)
      const x = padding
      const level = levelOf(agent)
      nodes.push({ x, y, label: agent, level, desc: getAgentDesc(agent) })
      // 箭头到协调器
      edges.push({
        d: `M ${x + NODE_W} ${y + NODE_H / 2} Q ${cx - 40} ${y + NODE_H / 2} ${cx} ${totalH / 2 - NODE_H / 2}`,
        color: colorFor(level),
      })
    })
    // 协调器节点
    const coordY = totalH / 2 - NODE_H / 2
    nodes.push({
      x: cx,
      y: coordY,
      label: '协调器',
      level: levelOf(finalAgent || ''),
      desc: '取 score 最高',
    })
    return { width: totalW, height: totalH, nodes, edges }
  }

  if (props.mode === 'VOTING') {
    // 投票汇聚
    const padding = 20
    const gapY = 50
    const cx = padding + NODE_W + 80
    const totalH = padding * 2 + agents.length * NODE_H + (agents.length - 1) * gapY + 80
    const totalW = padding * 2 + NODE_W + 80 + NODE_W + padding
    agents.forEach((agent, i) => {
      const y = padding + i * (NODE_H + gapY)
      const x = padding
      const level = levelOf(agent)
      nodes.push({ x, y, label: agent, level, desc: getAgentDesc(agent) })
      edges.push({
        d: `M ${x + NODE_W} ${y + NODE_H / 2} L ${cx} ${padding + totalH / 2 - NODE_H / 2 - 40}`,
        color: colorFor(level),
      })
    })
    // 融合节点
    const fuseY = padding + totalH / 2 - NODE_H / 2 - 40
    nodes.push({ x: cx, y: fuseY, label: '融合器', level: levelOf(finalAgent || ''), desc: '按权重 × 严重度' })
    // 融合 → 最终
    const finalY = fuseY + NODE_H + 40
    edges.push({ d: `M ${cx + NODE_W / 2} ${fuseY + NODE_H} L ${cx + NODE_W / 2} ${finalY}`, color: '#409EFF' })
    nodes.push({ x: cx, y: finalY, label: '最终结果', level: levelOf(finalAgent || ''), desc: finalAgent || '-' })
    return { width: totalW, height: totalH + 40, nodes, edges }
  }

  return { width: 600, height: 200, nodes: [], edges: [] }
})

function getAgentDesc(code: string): string {
  const map: Record<string, string> = {
    RISK_WARNING:      '项目风险',
    RESOURCE_RECOMMEND: '资源调度',
    PROFIT_FORECAST:   '利润预测',
    WIN_RATE_PREDICT:  '赢率预测',
    TIMESHEET_ANOMALY: '工时异常',
  }
  return map[code] || code
}

function nodeFill(level: string): string {
  if (!props.result) return '#F5F7FA'
  switch (level) {
    case 'RED': return '#F56C6C'
    case 'YELLOW': return '#E6A23C'
    case 'NORMAL': return '#67C23A'
    case 'INFO': return '#909399'
    default: return '#F5F7FA'
  }
}

function nodeTextColor(level: string): string {
  if (!props.result || level === 'UNKNOWN' || level === 'PENDING') return '#303133'
  return '#FFF'
}

/**
 * 根据告警等级返回节点描边虚线样式
 * @param level 告警等级
 * @returns SVG stroke-dasharray 值
 */
function nodeStrokeDash(level: string): string {
  return level === 'PENDING' || level === 'UNKNOWN' ? '4 3' : '0'
}

/**
 * 根据告警等级返回节点描边色
 * @param level 告警等级
 * @returns 描边色值，已执行节点为 transparent
 */
function nodeStroke(level: string): string {
  return level === 'UNKNOWN' || level === 'PENDING' ? '#C0C4CC' : 'transparent'
}
</script>

<template>
  <div class="orchestration-pipeline">
    <svg
      v-if="view.nodes.length"
      :viewBox="`0 0 ${view.width} ${view.height}`"
      :width="view.width"
      :height="view.height"
      class="pipeline-svg"
    >
      <!-- 边 -->
      <g class="edges">
        <path
          v-for="(e, i) in view.edges"
          :key="`e-${i}`"
          :d="e.d"
          :stroke="e.color"
          stroke-width="1.5"
          fill="none"
          marker-end="url(#arrowhead)"
          stroke-dasharray="0"
        />
      </g>
      <!-- 箭头定义 -->
      <defs>
        <marker
          id="arrowhead"
          markerWidth="10"
          markerHeight="10"
          refX="9"
          refY="3"
          orient="auto"
        >
          <path d="M0,0 L0,6 L9,3 z" fill="#909399" />
        </marker>
      </defs>

      <!-- 节点 -->
      <g class="nodes">
        <g
          v-for="(n, i) in view.nodes"
          :key="`n-${i}`"
          :transform="`translate(${n.x}, ${n.y})`"
        >
          <rect
            :width="NODE_W"
            :height="NODE_H"
            rx="6"
            :fill="nodeFill(n.level)"
            :stroke="nodeStroke(n.level)"
            :stroke-dasharray="nodeStrokeDash(n.level)"
            stroke-width="1.5"
          />
          <text
            :x="NODE_W / 2"
            :y="20"
            text-anchor="middle"
            :fill="nodeTextColor(n.level)"
            font-size="12"
            font-weight="600"
          >{{ n.label }}</text>
          <text
            :x="NODE_W / 2"
            :y="38"
            text-anchor="middle"
            :fill="nodeTextColor(n.level)"
            font-size="10"
            opacity="0.85"
          >{{ n.desc }}</text>
          <text
            v-if="n.level !== 'UNKNOWN' && n.level !== 'PENDING'"
            :x="NODE_W - 8"
            :y="14"
            text-anchor="end"
            :fill="nodeTextColor(n.level)"
            font-size="9"
          >{{ n.level }}</text>
        </g>
      </g>
    </svg>

    <el-empty v-else description="请选择参与编排的 Agent" :image-size="80" />

    <div v-if="result" class="legend">
      <el-tag :color="'#67C23A'" effect="dark" size="small" style="color:#fff">NORMAL</el-tag>
      <el-tag :color="'#E6A23C'" effect="dark" size="small" style="color:#fff">YELLOW</el-tag>
      <el-tag :color="'#F56C6C'" effect="dark" size="small" style="color:#fff">RED</el-tag>
      <span class="legend-tip">{{ t('agent.orchestration.legend.tip') }}</span>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.orchestration-pipeline {
  width: 100%;
  overflow-x: auto;
  .pipeline-svg {
    display: block;
    margin: 0 auto;
  }
  .legend {
    margin-top: 12px;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    .legend-tip { margin-left: 8px; }
  }
}
</style>
