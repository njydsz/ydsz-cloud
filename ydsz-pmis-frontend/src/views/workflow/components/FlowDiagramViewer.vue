<script setup lang="ts">
/**
 * @file 流程图查看器（SVG 自绘）
 * @description 消费后端 getDiagram 接口，渲染节点+边+高亮当前节点。
 * P0-7: 流程图查看器（对标钉钉/飞书审批流程图）。
 * 设计要点：
 *   1. 不引入新依赖，使用 SVG + Vue 3 渲染
 *   2. 节点 4 种状态：已完成(灰) / 当前(蓝) / 未到达(白) / 已驳回(红)
 *   3. 边的状态：已完成(实线) / 未到达(虚线) / 已驳回(红线)
 *   4. 鼠标悬停节点显示扩展属性
 *   5. 支持缩放和平移
 */
import { computed, ref } from 'vue'
import type { FlowDiagramDTO, FlowDiagramNodeDTO, FlowDiagramSkipDTO } from '@/api/workflow/types'

const props = withDefaults(
  defineProps<{
    diagram: FlowDiagramDTO
    /** 节点尺寸（默认 120x50） */
    nodeWidth?: number
    nodeHeight?: number
    /** 是否只读（不允许拖拽） */
    readonly?: boolean
  }>(),
  { nodeWidth: 120, nodeHeight: 50, readonly: true },
)

const emit = defineEmits<{
  (e: 'nodeClick', node: FlowDiagramNodeDTO): void
  (e: 'skipClick', skip: FlowDiagramSkipDTO): void
}>()

const hoveredNode = ref<FlowDiagramNodeDTO | null>(null)
const hoveredSkip = ref<FlowDiagramSkipDTO | null>(null)

const scale = ref(1)
const offsetX = ref(0)
const offsetY = ref(0)

/** 节点位置（无坐标时用 BFS 自动布局） */
const nodePositions = computed(() => {
  const map = new Map<string, { x: number; y: number }>()
  const nodes = props.diagram.nodes || []
  if (nodes.length === 0) return map

  // 优先使用节点 ext 中的 x/y，否则自动布局
  const haveCoord = nodes.some(
    (n) => typeof n.x === 'number' && typeof n.y === 'number',
  )
  if (haveCoord) {
    for (const n of nodes) {
      if (typeof n.x === 'number' && typeof n.y === 'number') {
        map.set(n.nodeCode, { x: n.x, y: n.y })
      }
    }
    return map
  }
  // 自动布局：按 startEvent 找起点，BFS 分层
  const start = nodes.find((n) => n.nodeType === 0)
  if (!start) {
    nodes.forEach((n, i) => {
      map.set(n.nodeCode, { x: 100 + (i % 4) * 220, y: 100 + Math.floor(i / 4) * 120 })
    })
    return map
  }
  // 构建邻接表
  const adj = new Map<string, string[]>()
  for (const s of props.diagram.skips || []) {
    if (!adj.has(s.sourceRef)) adj.set(s.sourceRef, [])
    adj.get(s.sourceRef)!.push(s.targetRef)
  }
  // BFS
  const visited = new Set<string>()
  const queue: { code: string; level: number; idx: number }[] = [
    { code: start.nodeCode, level: 0, idx: 0 },
  ]
  const levelCount = new Map<number, number>()
  while (queue.length) {
    const cur = queue.shift()!
    if (visited.has(cur.code)) continue
    visited.add(cur.code)
    levelCount.set(cur.level, (levelCount.get(cur.level) || 0) + 1)
    map.set(cur.code, {
      x: 100 + cur.level * 220,
      y: 100 + (levelCount.get(cur.level)! - 1) * 120,
    })
    for (const next of adj.get(cur.code) || []) {
      if (!visited.has(next)) {
        queue.push({
          code: next,
          level: cur.level + 1,
          idx: (levelCount.get(cur.level + 1) || 0),
        })
      }
    }
  }
  // 兜底：未访问节点放右下
  let dx = 0
  for (const n of nodes) {
    if (!map.has(n.nodeCode)) {
      map.set(n.nodeCode, { x: 800 + dx * 180, y: 500 })
      dx++
    }
  }
  return map
})

/** SVG 视图边界 */
const viewBox = computed(() => {
  const positions = Array.from(nodePositions.value.values())
  if (positions.length === 0) return '0 0 800 400'
  const minX = Math.min(...positions.map((p) => p.x))
  const minY = Math.min(...positions.map((p) => p.y))
  const maxX = Math.max(...positions.map((p) => p.x)) + props.nodeWidth
  const maxY = Math.max(...positions.map((p) => p.y)) + props.nodeHeight
  return `${minX - 40} ${minY - 40} ${maxX - minX + 80} ${maxY - minY + 80}`
})

/** 节点状态 */
function getNodeStatus(code: string): 'completed' | 'active' | 'pending' | 'rejected' {
  if (props.diagram.completedNodeCodes?.includes(code)) return 'completed'
  if (props.diagram.activeNodeCodes?.includes(code)) return 'active'
  if (props.diagram.status === 'REJECTED') return 'rejected'
  return 'pending'
}

const nodeFill = (status: ReturnType<typeof getNodeStatus>) => {
  switch (status) {
    case 'completed':
      return '#f0f9ff'
    case 'active':
      return '#e6f7ff'
    case 'rejected':
      return '#fef2f2'
    default:
      return '#ffffff'
  }
}

const nodeStroke = (status: ReturnType<typeof getNodeStatus>) => {
  switch (status) {
    case 'completed':
      return '#94a3b8'
    case 'active':
      return '#1890ff'
    case 'rejected':
      return '#ef4444'
    default:
      return '#cbd5e1'
  }
}

/** 边状态 */
function getSkipStatus(s: FlowDiagramSkipDTO): 'completed' | 'active' | 'pending' {
  const fromStatus = getNodeStatus(s.sourceRef)
  if (fromStatus === 'completed') return 'completed'
  if (fromStatus === 'active') return 'active'
  return 'pending'
}

const skipStroke = (status: ReturnType<typeof getSkipStatus>) => {
  switch (status) {
    case 'completed':
      return '#94a3b8'
    case 'active':
      return '#1890ff'
    default:
      return '#cbd5e1'
  }
}

const skipDasharray = (status: ReturnType<typeof getSkipStatus>) => {
  return status === 'pending' ? '6 4' : '0'
}

/** 计算边路径：源节点中心 → 目标节点中心 */
function skipPath(s: FlowDiagramSkipDTO): string {
  const from = nodePositions.value.get(s.sourceRef)
  const to = nodePositions.value.get(s.targetRef)
  if (!from || !to) return ''
  const x1 = from.x + props.nodeWidth / 2
  const y1 = from.y + props.nodeHeight / 2
  const x2 = to.x + props.nodeWidth / 2
  const y2 = to.y + props.nodeHeight / 2
  // 控制点偏移
  const cx1 = x1 + (x2 - x1) * 0.4
  const cx2 = x2 - (x2 - x1) * 0.4
  // 端点缩进（避免箭头遮住节点）
  const angle = Math.atan2(y2 - y1, x2 - x1)
  const sx = x2 - (props.nodeWidth / 2) * Math.cos(angle)
  const sy = y2 - (props.nodeHeight / 2) * Math.sin(angle)
  return `M ${x1} ${y1} C ${cx1} ${y1} ${cx2} ${y2} ${sx} ${sy}`
}

/** 节点类型标签 */
function nodeTypeLabel(type: number): string {
  switch (type) {
    case 0:
      return '开始'
    case 1:
      return '审批'
    case 2:
      return '抄送'
    case 3:
      return '条件'
    case 4:
      return '并行'
    case 5:
      return '包容'
    case 6:
      return '结束'
    case 7:
      return '子流程'
    default:
      return '节点'
  }
}

function onNodeClick(n: FlowDiagramNodeDTO) {
  emit('nodeClick', n)
}
function onSkipClick(s: FlowDiagramSkipDTO) {
  emit('skipClick', s)
}

/** 缩放/平移 */
function zoomIn() {
  scale.value = Math.min(scale.value * 1.2, 3)
}
function zoomOut() {
  scale.value = Math.max(scale.value / 1.2, 0.4)
}
function reset() {
  scale.value = 1
  offsetX.value = 0
  offsetY.value = 0
}

// ==================== P2-2: Minimap ====================
const MINIMAP_WIDTH = 160
const MINIMAP_HEIGHT = 100

/** 小地图 viewBox */
const minimapViewBox = computed(() => {
  const positions = Array.from(nodePositions.value.values())
  if (positions.length === 0) return `0 0 ${MINIMAP_WIDTH} ${MINIMAP_HEIGHT}`
  const minX = Math.min(...positions.map((p) => p.x))
  const minY = Math.min(...positions.map((p) => p.y))
  const maxX = Math.max(...positions.map((p) => p.x)) + props.nodeWidth
  const maxY = Math.max(...positions.map((p) => p.y)) + props.nodeHeight
  return `${minX - 10} ${minY - 10} ${maxX - minX + 20} ${maxY - minY + 20}`
})
</script>

<template>
  <div class="flow-diagram">
    <div class="flow-diagram__toolbar">
      <el-button-group>
        <el-button size="small" @click="zoomIn" title="放大">
          <el-icon><Plus /></el-icon>
        </el-button>
        <el-button size="small" @click="zoomOut" title="缩小">
          <el-icon><Minus /></el-icon>
        </el-button>
        <el-button size="small" @click="reset" title="重置">还原</el-button>
      </el-button-group>
      <div class="flow-diagram__legend">
        <span class="legend-item">
          <span class="legend-dot" style="background: #94a3b8"></span>已完成
        </span>
        <span class="legend-item">
          <span class="legend-dot" style="background: #1890ff"></span>当前
        </span>
        <span class="legend-item">
          <span class="legend-dot" style="background: #cbd5e1"></span>未到达
        </span>
        <span class="legend-item">
          <span class="legend-dot" style="background: #ef4444"></span>已驳回
        </span>
      </div>
    </div>
    <div class="flow-diagram__svg-wrap">
      <svg
        :viewBox="viewBox"
        xmlns="http://www.w3.org/2000/svg"
        preserveAspectRatio="xMidYMid meet"
        class="flow-diagram__svg"
        :style="{
          transform: `scale(${scale}) translate(${offsetX}px, ${offsetY}px)`,
        }"
      >
        <defs>
          <marker
            id="arrow-completed"
            viewBox="0 0 10 10"
            refX="9"
            refY="5"
            markerWidth="8"
            markerHeight="8"
            orient="auto-start-reverse"
          >
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#94a3b8" />
          </marker>
          <marker
            id="arrow-active"
            viewBox="0 0 10 10"
            refX="9"
            refY="5"
            markerWidth="8"
            markerHeight="8"
            orient="auto-start-reverse"
          >
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#1890ff" />
          </marker>
          <marker
            id="arrow-pending"
            viewBox="0 0 10 10"
            refX="9"
            refY="5"
            markerWidth="8"
            markerHeight="8"
            orient="auto-start-reverse"
          >
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#cbd5e1" />
          </marker>
        </defs>

        <!-- 边 -->
        <g class="flow-diagram__skips">
          <path
            v-for="(s, idx) in diagram.skips || []"
            :key="`skip-${idx}`"
            :d="skipPath(s)"
            :stroke="skipStroke(getSkipStatus(s))"
            :stroke-dasharray="skipDasharray(getSkipStatus(s))"
            :marker-end="`url(#arrow-${getSkipStatus(s)})`"
            stroke-width="2"
            fill="none"
            class="skip-path"
            @click="onSkipClick(s)"
            @mouseenter="hoveredSkip = s"
            @mouseleave="hoveredSkip = null"
          />
        </g>

        <!-- 节点 -->
        <g class="flow-diagram__nodes">
          <g
            v-for="n in diagram.nodes || []"
            :key="n.nodeCode"
            :transform="`translate(${nodePositions.get(n.nodeCode)?.x || 0}, ${
              nodePositions.get(n.nodeCode)?.y || 0
            })`"
            class="node-group"
            :class="`node-status-${getNodeStatus(n.nodeCode)}`"
            @click="onNodeClick(n)"
            @mouseenter="hoveredNode = n"
            @mouseleave="hoveredNode = null"
          >
            <rect
              :width="nodeWidth"
              :height="nodeHeight"
              :fill="nodeFill(getNodeStatus(n.nodeCode))"
              :stroke="nodeStroke(getNodeStatus(n.nodeCode))"
              :stroke-width="getNodeStatus(n.nodeCode) === 'active' ? 2.5 : 1.5"
              rx="6"
              ry="6"
            />
            <text
              :x="nodeWidth / 2"
              :y="22"
              text-anchor="middle"
              class="node-text node-text--name"
            >
              {{ n.nodeName || n.nodeCode }}
            </text>
            <text
              :x="nodeWidth / 2"
              :y="40"
              text-anchor="middle"
              class="node-text node-text--type"
            >
              {{ nodeTypeLabel(n.nodeType) }}
            </text>
          </g>
        </g>
      </svg>
    </div>
    <div v-if="hoveredNode" class="flow-diagram__tooltip">
      <div><b>{{ hoveredNode.nodeName || hoveredNode.nodeCode }}</b></div>
      <div class="tooltip-row">
        <span class="tooltip-label">类型：</span>
        <span>{{ nodeTypeLabel(hoveredNode.nodeType) }}</span>
      </div>
      <div class="tooltip-row">
        <span class="tooltip-label">状态：</span>
        <span>{{ getNodeStatus(hoveredNode.nodeCode) }}</span>
      </div>
      <div v-if="hoveredNode.permissionFlag" class="tooltip-row">
        <span class="tooltip-label">办理人：</span>
        <span>{{ hoveredNode.permissionFlag }}</span>
      </div>
    </div>

    <!-- P2-2: 小地图 -->
    <div v-if="diagram.nodes && diagram.nodes.length > 0" class="flow-diagram__minimap">
      <svg :viewBox="minimapViewBox" xmlns="http://www.w3.org/2000/svg" preserveAspectRatio="xMidYMid meet">
        <!-- 节点 -->
        <rect
          v-for="n in diagram.nodes || []"
          :key="`mini-${n.nodeCode}`"
          :x="nodePositions.get(n.nodeCode)?.x || 0"
          :y="nodePositions.get(n.nodeCode)?.y || 0"
          :width="nodeWidth"
          :height="nodeHeight"
          :fill="nodeFill(getNodeStatus(n.nodeCode))"
          :stroke="nodeStroke(getNodeStatus(n.nodeCode))"
          stroke-width="1"
          rx="3"
        />
        <!-- 当前节点高亮 -->
        <circle
          v-for="code in diagram.activeNodeCodes || []"
          :key="`mini-active-${code}`"
          :cx="(nodePositions.get(code)?.x || 0) + nodeWidth / 2"
          :cy="(nodePositions.get(code)?.y || 0) + nodeHeight / 2"
          r="4"
          fill="#1890ff"
          opacity="0.8"
        />
      </svg>
      <span class="minimap-label">缩略图</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.flow-diagram {
  position: relative;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  min-height: 400px;

  &__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 12px;
    border-bottom: 1px solid #e2e8f0;
    background: #fff;
    border-radius: 6px 6px 0 0;
  }

  &__legend {
    display: flex;
    gap: 16px;
    font-size: 12px;
    color: #475569;

    .legend-item {
      display: flex;
      align-items: center;
      gap: 4px;
    }

    .legend-dot {
      display: inline-block;
      width: 10px;
      height: 10px;
      border-radius: 50%;
    }
  }

  &__svg-wrap {
    width: 100%;
    height: 480px;
    overflow: auto;
    padding: 16px;
    background: #fff;
  }

  &__svg {
    width: 100%;
    height: 100%;
    transform-origin: 0 0;
    transition: transform 0.2s;
  }

  &__tooltip {
    position: absolute;
    right: 16px;
    bottom: 16px;
    padding: 12px;
    background: rgba(15, 23, 42, 0.9);
    color: #fff;
    border-radius: 6px;
    font-size: 12px;
    pointer-events: none;
    max-width: 280px;

    .tooltip-row {
      margin-top: 4px;
    }

    .tooltip-label {
      color: #94a3b8;
    }
  }

  &__minimap {
    position: absolute;
    left: 16px;
    bottom: 16px;
    width: 160px;
    height: 100px;
    background: rgba(255, 255, 255, 0.95);
    border: 1px solid #e2e8f0;
    border-radius: 4px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
    overflow: hidden;
    z-index: 10;

    svg {
      width: 100%;
      height: 100%;
    }

    .minimap-label {
      position: absolute;
      top: 2px;
      right: 4px;
      font-size: 10px;
      color: #94a3b8;
    }
  }
}

.node-group {
  cursor: pointer;
  transition: filter 0.2s;

  &:hover {
    filter: brightness(1.05);
  }
}

.node-text {
  fill: #1e293b;
  font-size: 13px;
  font-weight: 500;
  pointer-events: none;

  &--type {
    font-size: 11px;
    font-weight: 400;
    fill: #64748b;
  }
}

.node-status-active .node-text--name {
  fill: #1890ff;
  font-weight: 600;
}

.skip-path {
  cursor: pointer;
  transition: stroke-width 0.2s;

  &:hover {
    stroke-width: 3;
  }
}
</style>
