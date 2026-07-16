<!--
  @file 规则链可视化编排画布（P0-1）
  @description 节点拖拽 + 边连接 + 缩放/平移 + 撤销/重做 + dagre 自动布局
  @module views/execution/rule-engine/designer
  @author ydsz-pmis-team
  @since 1.5.0
-->
<template>
  <div class="rule-chain-designer">
    <!-- 顶部工具栏 -->
    <div class="designer-toolbar">
      <el-button-group>
        <el-button :icon="Refresh" @click="autoLayout" title="自动布局 (dagre)">{{ t('execution.ruleEngine.autoLayout') }}</el-button>
        <el-button :icon="Plus" @click="addChainNode('THEN')" type="primary" plain>{{ t('execution.ruleEngine.addSequenceNode') }}</el-button>
        <el-button :icon="Plus" @click="addChainNode('IF')" plain>{{ t('execution.ruleEngine.addConditionNode') }}</el-button>
        <el-button :icon="Plus" @click="addChainNode('FOR')" plain>{{ t('execution.ruleEngine.addLoopNode') }}</el-button>
        <el-button :icon="Plus" @click="addSingleNode" plain>{{ t('execution.ruleEngine.referenceRule') }}</el-button>
      </el-button-group>

      <el-button-group>
        <el-button :disabled="!canUndo" :icon="Back" @click="undo" title="撤销 (Ctrl+Z)">{{ t('common.undo') }}</el-button>
        <el-button :disabled="!canRedo" :icon="RefreshRight" @click="redo" title="重做 (Ctrl+Y)">{{ t('common.redo') }}</el-button>
        <el-button :icon="Check" @click="validateGraph" type="warning" plain>{{ t('common.validate') }}</el-button>
      </el-button-group>

      <div class="toolbar-spacer" />

      <el-button-group>
        <el-button :icon="ZoomIn" @click="zoomBy(1.1)" title="放大" />
        <el-button :icon="ZoomOut" @click="zoomBy(0.9)" title="缩小" />
        <el-button @click="resetView">{{ Math.round(viewport.zoom * 100) }}%</el-button>
      </el-button-group>

      <el-button type="primary" :icon="Check" :loading="saving" @click="saveGraph">{{ t('common.save') }}</el-button>
      <el-button type="success" :icon="VideoPlay" :loading="runLoading" @click="openRunDialog">{{ t('execution.ruleEngine.runSimulation') }}</el-button>
      <el-button :icon="Close" @click="goBack">{{ t('common.back') }}</el-button>
    </div>

    <!-- 画布主区 -->
    <div class="designer-canvas-wrap" ref="canvasRef" @wheel.prevent="onWheel">
      <svg
        class="designer-canvas"
        :viewBox="`${viewport.x} ${viewport.y} ${1000 / viewport.zoom} ${600 / viewport.zoom}`"
        @mousedown="onCanvasMouseDown"
        @mousemove="onCanvasMouseMove"
        @mouseup="onCanvasMouseUp"
        @mouseleave="onCanvasMouseUp"
      >
        <!-- 连线层（先画线，再画节点，节点在上层） -->
        <g class="edges-layer">
          <g
            v-for="edge in graph.edges"
            :key="edge.edgeId"
            class="edge"
            @click="selectEdge(edge)"
          >
            <path
              :d="edgePath(edge)"
              :class="['edge-path', { selected: selectedEdgeId === edge.edgeId }]"
              :marker-end="`url(#arrowhead)`"
              fill="none"
            />
            <text
              v-if="edge.label"
              :x="edgeMidpoint(edge).x"
              :y="edgeMidpoint(edge).y - 6"
              class="edge-label"
              text-anchor="middle"
            >
              {{ edge.label }}
            </text>
          </g>
        </g>

        <!-- 节点层 -->
        <g class="nodes-layer">
          <g
            v-for="node in graph.nodes"
            :key="node.nodeId"
            :class="['node', `node-${node.nodeType?.toLowerCase()}`, { selected: selectedNodeId === node.nodeId }]"
            :transform="`translate(${node.position?.x || 0}, ${node.position?.y || 0})`"
            @mousedown.stop="onNodeMouseDown($event, node)"
            @click.stop="selectNode(node)"
            @dblclick.stop="editNode(node)"
          >
            <!-- 节点矩形 -->
            <rect
              :width="NODE_WIDTH"
              :height="NODE_HEIGHT"
              rx="8"
              ry="8"
              class="node-rect"
            />
            <!-- 节点类型徽标 -->
            <text x="8" y="20" class="node-type-badge">{{ typeBadge(node) }}</text>
            <!-- 节点标题 -->
            <text x="12" y="40" class="node-label">{{ node.label || defaultNodeLabel(node) }}</text>
            <!-- 节点子标题 -->
            <text x="12" y="58" class="node-sub">{{ nodeSubtitle(node) }}</text>
            <!-- 删除按钮 -->
            <g
              v-if="selectedNodeId === node.nodeId"
              class="node-delete"
              :transform="`translate(${NODE_WIDTH - 18}, 6)`"
              @click.stop="removeNode(node)"
            >
              <circle cx="6" cy="6" r="6" class="delete-bg" />
              <text x="6" y="10" class="delete-x" text-anchor="middle">×</text>
            </g>
            <!-- 连接锚点（右侧） -->
            <circle
              :cx="NODE_WIDTH"
              :cy="NODE_HEIGHT / 2"
              r="5"
              class="anchor anchor-right"
              @mousedown.stop="onAnchorMouseDown($event, node, 'out')"
              @mouseup.stop="onAnchorMouseUp($event, node)"
            />
            <!-- 连接锚点（左侧） -->
            <circle
              cx="0"
              :cy="NODE_HEIGHT / 2"
              r="5"
              class="anchor anchor-left"
              @mouseup.stop="onAnchorMouseUp($event, null)"
            />
          </g>
        </g>

        <!-- 箭头定义 -->
        <defs>
          <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
            <polygon points="0 0, 10 3.5, 0 7" fill="#64748b" />
          </marker>
        </defs>
      </svg>

      <!-- 临时连线（拖拽中） -->
      <svg class="designer-canvas-overlay" :viewBox="`0 0 1000 600`" preserveAspectRatio="none">
        <path
          v-if="pendingEdge"
          :d="`M ${pendingEdge.x1} ${pendingEdge.y1} L ${pendingEdge.x2} ${pendingEdge.y2}`"
          class="pending-edge"
          fill="none"
        />
      </svg>

      <!-- 空状态 -->
      <div v-if="graph.nodes.length === 0" class="canvas-empty">
        <el-empty description="画布为空，点击上方按钮添加节点开始编排" />
      </div>
    </div>

    <!-- 底部问题面板 -->
    <div v-if="issues.length > 0" class="issue-panel">
      <div class="issue-header">
        <el-icon><Warning /></el-icon>
        <span>画布问题 ({{ issues.length }})</span>
        <el-button text @click="issues = []">{{ t('common.close') }}</el-button>
      </div>
      <ul class="issue-list">
        <li v-for="(it, idx) in issues" :key="idx" :class="['issue-item', `issue-${it.level?.toLowerCase()}`]">
          <el-tag :type="it.level === 'ERROR' ? 'danger' : 'warning'" size="small">{{ it.level }}</el-tag>
          <span class="issue-code">[{{ it.code }}]</span>
          <span class="issue-msg">{{ it.message }}</span>
        </li>
      </ul>
    </div>

    <!-- 运行仿真对话框（P0-1 执行闭环） -->
    <el-dialog v-model="runDialogVisible" title="画布 Dry-run 仿真" width="860px" :close-on-click-modal="false">
      <el-alert
        :title="`当前画布关联规则: ${graph.ruleCode || route.params.ruleCode}`"
        type="info"
        :closable="false"
        show-icon
        class="mb-3"
      />
      <el-form label-width="100px">
        <el-form-item label="事实数据">
          <el-input
            v-model="runFactsText"
            type="textarea"
            :rows="10"
            placeholder='{"budgetUsedRatio":0.95,"spi":0.85}'
            class="json-input"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="runLoading" @click="handleRun">
            <el-icon><VideoPlay /></el-icon>{{ t('execution.ruleEngine.runSimulation') }}
          </el-button>
        </el-form-item>
      </el-form>
      <el-divider content-position="left">{{ t('execution.ruleEngine.simulationResult') }}</el-divider>
      <el-table :data="runResults" border stripe size="small" empty-text="暂无仿真结果">
        <el-table-column prop="ruleCode" label="规则编码" width="160" show-overflow-tooltip />
        <el-table-column prop="ruleName" label="规则名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="是否触发" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.triggered ? 'danger' : 'info'" size="small">
              {{ row.triggered ? '触发' : '未触发' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="严重度" width="90">
          <template #default="{ row }">
            <el-tag :type="severityOf(row.severity)" size="small">{{ row.severity || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="elapsedMs" label="耗时(ms)" width="100" />
      </el-table>
      <template #footer>
        <el-button @click="runDialogVisible = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>

    <!-- 节点配置对话框 -->
    <el-dialog v-model="editDialogVisible" :title="editingNode ? '编辑节点' : '添加节点'" width="540px">      <el-form v-if="editingNode" :model="editingNode" label-width="100px">
        <el-form-item label="节点类型">
          <el-tag>{{ typeBadge(editingNode) }}</el-tag>
        </el-form-item>
        <el-form-item label="节点 ID">
          <el-input v-model="editingNode.nodeId" disabled />
        </el-form-item>
        <el-form-item label="链类型">
          <el-select v-model="editingNode.chainType" @change="markDirty">
            <el-option label="THEN (顺序)" value="THEN" />
            <el-option label="WHEN (并行)" value="WHEN" />
            <el-option label="IF (条件)" value="IF" />
            <el-option label="ELIF (多条件)" value="ELIF" />
            <el-option label="SWITCH (分支)" value="SWITCH" />
            <el-option label="FOR (循环)" value="FOR" />
            <el-option label="WHILE (条件循环)" value="WHILE" />
            <el-option label="BREAK (中断)" value="BREAK" />
          </el-select>
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model="editingNode.label" @change="markDirty" />
        </el-form-item>
        <el-form-item v-if="editingNode.nodeType === 'SINGLE'" label="引用规则">
          <el-select v-model="editingNode.ruleCode" filterable @change="markDirty">
            <el-option
              v-for="r in availableRules"
              :key="r.code"
              :label="`${r.code} - ${r.name}`"
              :value="r.code"
            />
          </el-select>
        </el-form-item>
        <template v-if="editingNode.chainType === 'FOR'">
          <el-form-item label="迭代集合">
            <el-input :model-value="getMetadata('iterableExpression')" placeholder="如 items"
              @update:model-value="(v: string) => setMetadata('iterableExpression', v)" @change="markDirty" />
          </el-form-item>
          <el-form-item label="迭代变量">
            <el-input :model-value="getMetadata('iterationVar')" placeholder="如 item"
              @update:model-value="(v: string) => setMetadata('iterationVar', v)" @change="markDirty" />
          </el-form-item>
        </template>
        <template v-if="editingNode.chainType === 'WHILE'">
          <el-form-item label="循环条件">
            <el-input :model-value="getMetadata('whileCondition')" placeholder="如 count < 10"
              @update:model-value="(v: string) => setMetadata('whileCondition', v)" @change="markDirty" />
          </el-form-item>
          <el-form-item label="最大迭代">
            <el-input-number :model-value="getMetadata('maxIterations')" :min="1" :max="10000"
              @update:model-value="(v: number | undefined) => setMetadata('maxIterations', v)" @change="markDirty" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="confirmEdit">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Refresh, RefreshRight, Check, Close, Warning,
  Back, ZoomIn, ZoomOut, VideoPlay,
} from '@element-plus/icons-vue'
import * as dagre from 'dagre'
import {
  getChainGraph, saveChainGraph, validateChainGraph, listRules, dryRunGraph,
} from '@/api/rule-engine'
import type {
  ChainNodeDTO, ChainEdgeDTO, RuleChainGraph, RuleChainGraphViewIssue, RuleResult,
} from '@/api/rule-engine'

defineOptions({ name: 'RuleChainDesigner' })

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

// 节点常量
const NODE_WIDTH = 180
const NODE_HEIGHT = 72

// 画布状态
const graph = ref<RuleChainGraph>({
  graphId: '',
  name: '',
  ruleCode: '',
  nodes: [],
  edges: [],
  viewport: { x: 0, y: 0, zoom: 1.0 },
  status: 'DRAFT',
  version: '1.0.0',
})
const viewport = ref({ x: 0, y: 0, zoom: 1.0 })
const saving = ref(false)
const issues = ref<RuleChainGraphViewIssue[]>([])
const availableRules = ref<Array<{ code: string; name: string }>>([])

// 选中状态
const selectedNodeId = ref<string | null>(null)
const selectedEdgeId = ref<string | null>(null)
const editingNode = ref<ChainNodeDTO | null>(null)
const editDialogVisible = ref(false)

// 拖拽状态
const dragState = ref<{
  type: 'node' | 'pan' | 'connect' | null
  startX: number
  startY: number
  nodeId?: string
  offsetX?: number
  offsetY?: number
}>({ type: null, startX: 0, startY: 0 })

// 临时连线
const pendingEdge = ref<{ x1: number; y1: number; x2: number; y2: number } | null>(null)

// 撤销/重做栈
const history = ref<RuleChainGraph[]>([])
const historyIndex = ref(-1)
const maxHistory = 50

// 计算属性
const canUndo = computed(() => historyIndex.value > 0)
const canRedo = computed(() => historyIndex.value < history.value.length - 1)

const canvasRef = ref<HTMLDivElement>()

// 严重度标签类型映射（用于仿真结果展示）
function severityOf(severity?: string): 'danger' | 'warning' | 'info' | 'success' {
  if (!severity) return 'info'
  if (severity === 'RED') return 'danger'
  if (severity === 'YELLOW') return 'warning'
  return 'info'
}

// ==================== 运行仿真（P0-1 执行闭环） ====================
const runDialogVisible = ref(false)
const runLoading = ref(false)
const runFactsText = ref('{\n  "budgetUsedRatio": 0.95,\n  "spi": 0.85\n}')
const runResults = ref<RuleResult[]>([])

function openRunDialog() {
  runResults.value = []
  runFactsText.value = '{\n  "budgetUsedRatio": 0.95,\n  "spi": 0.85\n}'
  runDialogVisible.value = true
}

async function handleRun() {
  let facts: Record<string, unknown>
  try {
    facts = JSON.parse(runFactsText.value)
  } catch {
    ElMessage.error('事实数据 JSON 格式不正确')
    return
  }
  const ruleCode = route.params.ruleCode as string
  runLoading.value = true
  try {
    const res = await dryRunGraph(ruleCode, facts)
    if (res.code === 0) {
      runResults.value = res.data || []
      const triggered = runResults.value.filter(r => r.triggered).length
      ElMessage.success(`仿真完成，共触发 ${triggered} 条规则`)
    } else {
      ElMessage.error(res.message || '仿真失败')
    }
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '仿真异常')
  } finally {
    runLoading.value = false
  }
}

// ==================== 历史记录 ====================
function snapshot() {
  history.value = history.value.slice(0, historyIndex.value + 1)
  history.value.push(JSON.parse(JSON.stringify(graph.value)))
  if (history.value.length > maxHistory) history.value.shift()
  historyIndex.value = history.value.length - 1
}
function undo() {
  if (!canUndo.value) return
  historyIndex.value--
  graph.value = JSON.parse(JSON.stringify(history.value[historyIndex.value]))
  syncViewport()
}
function redo() {
  if (!canRedo.value) return
  historyIndex.value++
  graph.value = JSON.parse(JSON.stringify(history.value[historyIndex.value]))
  syncViewport()
}
function markDirty() {
  snapshot()
}

// ==================== 视口 ====================
function syncViewport() {
  if (graph.value.viewport) {
    viewport.value = { ...graph.value.viewport }
  }
}
function setViewport(v: { x: number; y: number; zoom: number }) {
  viewport.value = v
  graph.value.viewport = { ...v }
}

function zoomBy(factor: number) {
  setViewport({ ...viewport.value, zoom: Math.max(0.2, Math.min(3.0, viewport.value.zoom * factor)) })
}
function resetView() {
  setViewport({ x: 0, y: 0, zoom: 1.0 })
}
function onWheel(e: WheelEvent) {
  if (e.ctrlKey || e.metaKey) {
    const factor = e.deltaY < 0 ? 1.1 : 0.9
    zoomBy(factor)
  } else {
    setViewport({
      ...viewport.value,
      x: viewport.value.x + e.deltaX / viewport.value.zoom,
      y: viewport.value.y + e.deltaY / viewport.value.zoom,
    })
  }
}

// ==================== 节点选中 ====================
function selectNode(node: ChainNodeDTO) {
  selectedNodeId.value = node.nodeId
  selectedEdgeId.value = null
}
function selectEdge(edge: ChainEdgeDTO) {
  selectedEdgeId.value = edge.edgeId
  selectedNodeId.value = null
}

// ==================== 拖拽节点 ====================
function onNodeMouseDown(e: MouseEvent, node: ChainNodeDTO) {
  if (e.button !== 0) return
  selectNode(node)
  const rect = (e.currentTarget as SVGGElement).getBoundingClientRect()
  dragState.value = {
    type: 'node',
    startX: e.clientX,
    startY: e.clientY,
    nodeId: node.nodeId,
    offsetX: e.clientX - rect.left,
    offsetY: e.clientY - rect.top,
  }
}

function onCanvasMouseDown(e: MouseEvent) {
  if (e.target !== e.currentTarget && !(e.target as SVGElement).classList?.contains('designer-canvas')) {
    return
  }
  if ((e.target as SVGElement).tagName === 'rect' || (e.target as SVGElement).tagName === 'svg') {
    dragState.value = { type: 'pan', startX: e.clientX, startY: e.clientY }
  }
}

function onCanvasMouseMove(e: MouseEvent) {
  if (!dragState.value.type) return
  const ds = dragState.value
  if (ds.type === 'node' && ds.nodeId) {
    const node = graph.value.nodes.find(n => n.nodeId === ds.nodeId)
    if (!node) return
    const canvasRect = canvasRef.value!.getBoundingClientRect()
    const x = (e.clientX - canvasRect.left) / viewport.value.zoom - viewport.value.x
      - (ds.offsetX || 0) / viewport.value.zoom
    const y = (e.clientY - canvasRect.top) / viewport.value.zoom - viewport.value.y
      - (ds.offsetY || 0) / viewport.value.zoom
    node.position = { x: Math.max(0, x), y: Math.max(0, y) }
  } else if (ds.type === 'pan') {
    setViewport({
      ...viewport.value,
      x: viewport.value.x - (e.clientX - ds.startX) / viewport.value.zoom,
      y: viewport.value.y - (e.clientY - ds.startY) / viewport.value.zoom,
    })
    dragState.value.startX = e.clientX
    dragState.value.startY = e.clientY
  } else if (ds.type === 'connect') {
    const canvasRect = canvasRef.value!.getBoundingClientRect()
    pendingEdge.value = {
      x1: pendingEdge.value!.x1,
      y1: pendingEdge.value!.y1,
      x2: (e.clientX - canvasRect.left) / viewport.value.zoom - viewport.value.x,
      y2: (e.clientY - canvasRect.top) / viewport.value.zoom - viewport.value.y,
    }
  }
}

function onCanvasMouseUp() {
  if (dragState.value.type) {
    if (dragState.value.type === 'node') {
      snapshot()
    }
    dragState.value = { type: null, startX: 0, startY: 0 }
    pendingEdge.value = null
  }
}

// ==================== 连线（拖拽连接） ====================
function onAnchorMouseDown(e: MouseEvent, node: ChainNodeDTO, dir: 'out') {
  if (dir !== 'out') return
  const x = (node.position?.x || 0) + NODE_WIDTH
  const y = (node.position?.y || 0) + NODE_HEIGHT / 2
  pendingEdge.value = { x1: x, y1: y, x2: x, y2: y }
  dragState.value = { type: 'connect', startX: e.clientX, startY: e.clientY }
}

function onAnchorMouseUp(_e: MouseEvent, target: ChainNodeDTO | null) {
  if (dragState.value.type !== 'connect' || !pendingEdge.value) return
  if (target) {
    const sourceNode = graph.value.nodes.find(n =>
      n.position && Math.abs(n.position.x + NODE_WIDTH - pendingEdge.value!.x1) < 5
    )
    if (sourceNode && sourceNode.nodeId !== target.nodeId) {
      // 检查重复边
      const exists = graph.value.edges.some(e =>
        e.sourceNodeId === sourceNode.nodeId && e.targetNodeId === target.nodeId
      )
      if (exists) {
        ElMessage.warning('已存在相同连线')
      } else {
        graph.value.edges.push({
          edgeId: `edge-${Date.now()}`,
          sourceNodeId: sourceNode.nodeId,
          targetNodeId: target.nodeId,
          edgeType: 'THEN',
          label: 'THEN',
        })
        snapshot()
        ElMessage.success('已添加连线')
      }
    }
  }
  dragState.value = { type: null, startX: 0, startY: 0 }
  pendingEdge.value = null
}

// ==================== 节点增删 ====================
function addChainNode(chainType: 'THEN' | 'WHEN' | 'IF' | 'ELIF' | 'SWITCH' | 'FOR' | 'WHILE' | 'BREAK') {
  const node: ChainNodeDTO = {
    nodeId: `node-${Date.now()}`,
    nodeType: 'CHAIN',
    chainType,
    label: chainTypeLabel(chainType),
    position: { x: 100 + graph.value.nodes.length * 50, y: 100 + graph.value.nodes.length * 30 },
    metadata: {},
  }
  graph.value.nodes.push(node)
  snapshot()
}
function addSingleNode() {
  const node: ChainNodeDTO = {
    nodeId: `node-${Date.now()}`,
    nodeType: 'SINGLE',
    label: '选择规则',
    ruleCode: '',
    position: { x: 100 + graph.value.nodes.length * 50, y: 200 + graph.value.nodes.length * 30 },
  }
  graph.value.nodes.push(node)
  snapshot()
}
function removeNode(node: ChainNodeDTO) {
  ElMessageBox.confirm(`确认删除节点 ${node.label}?`, '提示', { type: 'warning' })
    .then(() => {
      graph.value.nodes = graph.value.nodes.filter(n => n.nodeId !== node.nodeId)
      graph.value.edges = graph.value.edges.filter(
        e => e.sourceNodeId !== node.nodeId && e.targetNodeId !== node.nodeId,
      )
      selectedNodeId.value = null
      snapshot()
    }).catch(() => {})
}
function editNode(node: ChainNodeDTO) {
  const copy = JSON.parse(JSON.stringify(node)) as ChainNodeDTO
  if (!copy.metadata) copy.metadata = {}
  editingNode.value = copy
  editDialogVisible.value = true
}
function confirmEdit() {
  if (!editingNode.value) return
  const idx = graph.value.nodes.findIndex(n => n.nodeId === editingNode.value!.nodeId)
  if (idx >= 0) {
    graph.value.nodes[idx] = JSON.parse(JSON.stringify(editingNode.value))
    snapshot()
  }
  editDialogVisible.value = false
  editingNode.value = null
}

// ==================== 工具 ====================
function typeBadge(node: ChainNodeDTO): string {
  if (node.nodeType === 'CHAIN') return `⛓ ${node.chainType || 'THEN'}`
  return '📌 规则'
}
function defaultNodeLabel(node: ChainNodeDTO): string {
  if (node.nodeType === 'CHAIN') return chainTypeLabel(node.chainType || 'THEN')
  return node.ruleCode ? `引用 ${node.ruleCode}` : '未选择规则'
}
function chainTypeLabel(type: string): string {
  const map: Record<string, string> = {
    THEN: '顺序执行', WHEN: '并行执行', IF: '条件分支',
    ELIF: '多条件分支', SWITCH: '分支选择', FOR: '循环', WHILE: '条件循环', BREAK: '中断',
  }
  return map[type] || type
}
function nodeSubtitle(node: ChainNodeDTO): string {
  if (node.nodeType === 'SINGLE') return node.ruleCode || '点击编辑选择规则'
  return '链容器节点'
}
function getMetadata(key: string): any {
  if (!editingNode.value) return ''
  if (!editingNode.value.metadata) editingNode.value.metadata = {}
  return editingNode.value.metadata[key] ?? ''
}
function setMetadata(key: string, value: any) {
  if (!editingNode.value) return
  if (!editingNode.value.metadata) editingNode.value.metadata = {}
  editingNode.value.metadata[key] = value
}
function edgePath(edge: ChainEdgeDTO): string {
  const source = graph.value.nodes.find(n => n.nodeId === edge.sourceNodeId)
  const target = graph.value.nodes.find(n => n.nodeId === edge.targetNodeId)
  if (!source || !target) return ''
  const x1 = (source.position?.x || 0) + NODE_WIDTH
  const y1 = (source.position?.y || 0) + NODE_HEIGHT / 2
  const x2 = target.position?.x || 0
  const y2 = (target.position?.y || 0) + NODE_HEIGHT / 2
  const dx = (x2 - x1) * 0.5
  return `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`
}
function edgeMidpoint(edge: ChainEdgeDTO): { x: number; y: number } {
  const source = graph.value.nodes.find(n => n.nodeId === edge.sourceNodeId)
  const target = graph.value.nodes.find(n => n.nodeId === edge.targetNodeId)
  if (!source || !target) return { x: 0, y: 0 }
  return {
    x: ((source.position?.x || 0) + NODE_WIDTH + (target.position?.x || 0)) / 2,
    y: ((source.position?.y || 0) + (target.position?.y || 0) + NODE_HEIGHT) / 2,
  }
}

// ==================== 自动布局 ====================
function autoLayout() {
  if (graph.value.nodes.length === 0) {
    ElMessage.info('画布为空，无需布局')
    return
  }
  const g = new dagre.graphlib.Graph()
  g.setGraph({ rankdir: 'TB', nodesep: 60, ranksep: 80, marginx: 20, marginy: 20 })
  g.setDefaultEdgeLabel(() => ({}))
  for (const n of graph.value.nodes) {
    g.setNode(n.nodeId, { width: NODE_WIDTH, height: NODE_HEIGHT })
  }
  for (const e of graph.value.edges) {
    g.setEdge(e.sourceNodeId, e.targetNodeId)
  }
  dagre.layout(g)
  for (const n of graph.value.nodes) {
    const node = g.node(n.nodeId)
    if (node) {
      n.position = { x: node.x - NODE_WIDTH / 2, y: node.y - NODE_HEIGHT / 2 }
    }
  }
  snapshot()
  ElMessage.success('自动布局完成')
}

// ==================== 校验 ====================
async function validateGraph() {
  const res = await validateChainGraph(graph.value)
  if (res.code === 0) {
    issues.value = res.data || []
    if (issues.value.length === 0) {
      ElMessage.success('画布结构合法')
    } else {
      const errCount = issues.value.filter(i => i.level === 'ERROR').length
      const warnCount = issues.value.filter(i => i.level === 'WARN').length
      ElMessage.warning(`画布问题: ${errCount} 错误 / ${warnCount} 警告`)
    }
  }
}

// ==================== 保存 ====================
async function saveGraph() {
  saving.value = true
  try {
    const res = await saveChainGraph(route.params.ruleCode as string, graph.value)
    if (res.code === 0) {
      const data = res.data || {}
      if (data.valid === false) {
        ElMessageBox.alert(data.message || '画布不合法', '校验失败', { type: 'error' })
        issues.value = data.issues || []
        return
      }
      ElMessage.success('保存成功')
      if (data.graph) {
        graph.value = data.graph
        syncViewport()
      }
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } finally {
    saving.value = false
  }
}

// ==================== 路由 ====================
function goBack() {
  router.push('/rule-engine')
}

// ==================== 快捷键 ====================
function onKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey) {
    e.preventDefault()
    undo()
  } else if ((e.ctrlKey || e.metaKey) && (e.key === 'y' || (e.shiftKey && e.key === 'Z'))) {
    e.preventDefault()
    redo()
  } else if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault()
    saveGraph()
  }
}

// ==================== 生命周期 ====================
onMounted(async () => {
  const ruleCode = route.params.ruleCode as string
  graph.value.ruleCode = ruleCode

  // 加载画布
  const res = await getChainGraph(ruleCode)
  if (res.code === 0 && res.data) {
    graph.value = res.data
    syncViewport()
  }
  // 加载可用规则列表
  const rulesRes = await listRules()
  if (rulesRes.code === 0) {
    availableRules.value = (rulesRes.data || []).map((r: any) => ({ code: r.code, name: r.name }))
  }
  // 初始化历史
  history.value = [JSON.parse(JSON.stringify(graph.value))]
  historyIndex.value = 0
  // 监听快捷键
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
})

// 视口变化同步到 graph
watch(viewport, (v) => {
  graph.value.viewport = { ...v }
}, { deep: true })
</script>

<style scoped lang="scss">
.rule-chain-designer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  background: #f8fafc;
  border-radius: 8px;
  overflow: hidden;
}

.designer-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  flex-shrink: 0;

  .toolbar-spacer { flex: 1; }
}

.designer-canvas-wrap {
  flex: 1;
  position: relative;
  overflow: hidden;
  cursor: grab;

  &:active { cursor: grabbing; }
}

.designer-canvas,
.designer-canvas-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  user-select: none;
}

.designer-canvas-overlay {
  pointer-events: none;
  z-index: 2;
}

.canvas-empty {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.node {
  cursor: move;
  transition: filter 0.15s;

  &.selected .node-rect {
    stroke: #2563eb;
    stroke-width: 2;
  }
}

.node-rect {
  fill: #fff;
  stroke: #94a3b8;
  stroke-width: 1.5;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.06));
}

.node-chain .node-rect { fill: #f0f9ff; stroke: #38bdf8; }
.node-single .node-rect { fill: #fffbeb; stroke: #f59e0b; }

.node-type-badge {
  font-size: 11px;
  font-weight: 600;
  fill: #475569;
}

.node-label {
  font-size: 14px;
  font-weight: 600;
  fill: #0f172a;
}

.node-sub {
  font-size: 11px;
  fill: #64748b;
}

.anchor {
  fill: #2563eb;
  opacity: 0;
  cursor: crosshair;
  transition: opacity 0.15s;
}
.node:hover .anchor { opacity: 1; }
.anchor-right { cursor: crosshair; }

.node-delete {
  cursor: pointer;
  .delete-bg { fill: #ef4444; }
  .delete-x {
    fill: #fff;
    font-size: 12px;
    font-weight: bold;
  }
}

.edge-path {
  stroke: #64748b;
  stroke-width: 1.5;
  cursor: pointer;
  transition: stroke 0.15s;

  &.selected { stroke: #2563eb; stroke-width: 2.5; }
  &:hover { stroke: #2563eb; }
}

.edge-label {
  font-size: 11px;
  fill: #475569;
  paint-order: stroke;
  stroke: #fff;
  stroke-width: 3;
}

.pending-edge {
  stroke: #2563eb;
  stroke-width: 2;
  stroke-dasharray: 4 4;
  pointer-events: none;
}

.issue-panel {
  flex-shrink: 0;
  background: #fff;
  border-top: 1px solid #e2e8f0;
  max-height: 200px;
  overflow-y: auto;

  .issue-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    background: #fef2f2;
    border-bottom: 1px solid #fecaca;
    font-weight: 600;
  }

  .issue-list {
    list-style: none;
    padding: 0;
    margin: 0;
  }

  .issue-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 16px;
    border-bottom: 1px solid #f1f5f9;
    font-size: 13px;

    .issue-code { color: #64748b; font-family: monospace; }
    .issue-msg { color: #1e293b; }
  }
}
</style>
