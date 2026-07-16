<!--
  @fileoverview 流程设计器（轻量经典模式，SVG 自绘）
  @description
    节点面板 + SVG 画布拖拽 + 边绘制 + JSON 模型导出。
    对标钉钉 / 飞书 / Activiti-Modeler 的轻量可视化设计器。
    实现要点：
      1. 采用 SVG 自绘（避免引入 logic-flow 等重依赖）；
      2. 节点拖拽：mousedown / mousemove / mouseup；
      3. 边绘制：节点右上 / 右下锚点 → 下一节点左上锚点；
      4. 节点属性面板：右侧抽屉编辑；
      5. 模型导出：JSON → 后端转换为 BPMN XML；
      6. 内置 4 套模板（立项 / 变更 / 结项 / 通用）。
    配套自研工作流 v2 引擎，PC 端专用。
  @module views/workflow/components/FlowDesigner
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * @file 流程设计器（轻量版）
 * @description 节点面板 + SVG 画布拖拽 + 边绘制 + JSON 模型导出
 * P0-5: 流程设计器（对标钉钉/飞书/Activiti-Modeler 的可视化设计器）。
 * 实现说明：
 *   1. 采用 SVG 自绘（避免引入 logic-flow 等重依赖，2 周工作量内部消化）
 *   2. 节点拖拽：mousedown/mousemove/mouseup
 *   3. 边绘制：节点右上/右下锚点 → 下一节点左上锚点
 *   4. 节点属性面板：右侧抽屉编辑
 *   5. 模型导出：保存为 JSON + 后端转换为 BPMN XML
 *   6. 流程模板：内置 4 套（立项/变更/结项/通用）
 */
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CirclePlus,
  CircleCheck,
  Select,
  Filter,
  Stopwatch,
  Connection,
  Position,
  Promotion,
} from '@element-plus/icons-vue'
import { deployDefinition } from '@/api/workflow'
import type { FlowDeployDTO } from '@/api/workflow/types'

interface DesignerNode {
  id: string
  type: string
  name: string
  x: number
  y: number
  width: number
  height: number
  /** 节点编码（部署后用于关联 BPMN id） */
  code: string
  /** 办理人 */
  assignee?: string
  assigneeType?: string
  /** 会签类型 */
  performType?: string
  /** 表单 */
  formKey?: string
  /** 优先级 */
  priority?: number
  /** 超时 */
  timeout?: string
  timeoutStrategy?: string
  /** 扩展 */
  ext?: Record<string, unknown>
}

interface DesignerSkip {
  id: string
  sourceId: string
  targetId: string
  condition?: string
  name?: string
  /** 边优先级（多出口排序） */
  priority?: number
}

interface DesignerModel {
  flowCode: string
  flowName: string
  category: string
  version: number
  nodes: DesignerNode[]
  skips: DesignerSkip[]
}

// 节点类型元数据
const NODE_TYPES = [
  { type: 'startEvent', name: '开始', icon: CirclePlus, color: '#52c41a' },
  { type: 'endEvent', name: '结束', icon: CircleCheck, color: '#f5222d' },
  { type: 'userTask', name: '审批', icon: Select, color: '#1890ff' },
  { type: 'serviceTask', name: '服务任务', icon: Position, color: '#722ed1' },
  { type: 'exclusiveGateway', name: '条件', icon: Filter, color: '#fa8c16' },
  { type: 'parallelGateway', name: '并行', icon: Connection, color: '#13c2c2' },
  { type: 'inclusiveGateway', name: '包容', icon: Connection, color: '#2db7f5' },
  { type: 'intermediateCatchEvent', name: '中间事件', icon: Stopwatch, color: '#eb2f96' },
  { type: 'callActivity', name: '子流程', icon: Promotion, color: '#a0d911' },
] as const

// 会签类型
const PERFORM_TYPES = [
  { value: 'OR', label: '或签（任一通过）' },
  { value: 'PARALLEL', label: '会签（全员通过）' },
  { value: 'SEQUENTIAL', label: '顺序会签' },
  { value: 'VOTE', label: '票签（按比例）' },
]

// 办理人类型
const ASSIGNEE_TYPES = [
  { value: 'USER', label: '指定用户' },
  { value: 'ROLE', label: '角色' },
  { value: 'DEPT', label: '部门' },
  { value: 'LEADER', label: '直属上级' },
  { value: 'POSITION', label: '岗位' },
  { value: 'SELF_SELECT', label: '自选' },
  { value: 'MULTI_LEADER', label: '多级上级' },
  { value: 'INITIATOR', label: '发起人' },
  { value: 'SPEL', label: '表达式' },
]

// 超时策略
const TIMEOUT_STRATEGIES = [
  { value: 'NOTIFY', label: '通知' },
  { value: 'PASS', label: '自动通过' },
  { value: 'REJECT', label: '自动驳回' },
  { value: 'ESCALATE', label: '升级上级' },
]

// 内置模板
const TEMPLATES = [
  {
    key: 'project-initiation',
    name: '项目立项流程',
    description: '业务专员 → 部门经理 → 分管副总 → 总经理',
    model: buildInitiationTemplate(),
  },
  {
    key: 'change',
    name: '项目变更流程',
    description: '变更专员 → PMO → 业务负责人 → 分管领导',
    model: buildChangeTemplate(),
  },
  {
    key: 'closure',
    name: '项目结项流程',
    description: 'PM → 部门经理 → 分管领导 → 财务复核 → 总经理',
    model: buildClosureTemplate(),
  },
  {
    key: 'blank',
    name: '空白流程',
    description: '从零开始',
    model: { flowCode: '', flowName: '', category: 'GENERAL', version: 1, nodes: [], skips: [] } as DesignerModel,
  },
]

function buildInitiationTemplate(): DesignerModel {
  return {
    flowCode: 'project_initiation',
    flowName: '项目立项流程',
    category: 'PROJECT',
    version: 1,
    nodes: [
      { id: 'n1', type: 'startEvent', name: '开始', x: 80, y: 200, width: 100, height: 50, code: 'START' },
      { id: 'n2', type: 'userTask', name: '业务专员提交', x: 240, y: 200, width: 120, height: 60, code: 'BUSINESS_SUBMIT', assignee: 'user:${initiatorId}', assigneeType: 'SPEL' },
      { id: 'n3', type: 'userTask', name: '部门经理审核', x: 420, y: 200, width: 120, height: 60, code: 'DEPT_MANAGER', assignee: 'role:dept_manager', assigneeType: 'ROLE' },
      { id: 'n4', type: 'userTask', name: '分管副总审批', x: 600, y: 200, width: 120, height: 60, code: 'VICE_PRESIDENT', assignee: 'role:vice_president', assigneeType: 'ROLE' },
      { id: 'n5', type: 'userTask', name: '总经理审批', x: 780, y: 200, width: 120, height: 60, code: 'GENERAL_MANAGER', assignee: 'role:general_manager', assigneeType: 'ROLE' },
      { id: 'n6', type: 'endEvent', name: '结束', x: 960, y: 200, width: 100, height: 50, code: 'END' },
    ],
    skips: [
      { id: 's1', sourceId: 'n1', targetId: 'n2' },
      { id: 's2', sourceId: 'n2', targetId: 'n3' },
      { id: 's3', sourceId: 'n3', targetId: 'n4' },
      { id: 's4', sourceId: 'n4', targetId: 'n5' },
      { id: 's5', sourceId: 'n5', targetId: 'n6' },
    ],
  }
}

function buildChangeTemplate(): DesignerModel {
  return {
    flowCode: 'project_change',
    flowName: '项目变更流程',
    category: 'PROJECT',
    version: 1,
    nodes: [
      { id: 'n1', type: 'startEvent', name: '开始', x: 80, y: 200, width: 100, height: 50, code: 'START' },
      { id: 'n2', type: 'userTask', name: 'PMO 受理', x: 240, y: 200, width: 120, height: 60, code: 'PMO_RECEIVE', assignee: 'role:pmo', assigneeType: 'ROLE' },
      { id: 'n3', type: 'exclusiveGateway', name: '金额判断', x: 420, y: 200, width: 100, height: 50, code: 'AMOUNT_GATE' },
      { id: 'n4', type: 'userTask', name: '业务负责人', x: 600, y: 100, width: 120, height: 60, code: 'BUSINESS_OWNER', assignee: 'leader:${initiatorId}', assigneeType: 'LEADER' },
      { id: 'n5', type: 'userTask', name: '分管领导', x: 600, y: 300, width: 120, height: 60, code: 'LEADER', assignee: 'leader:${initiatorId}:1', assigneeType: 'MULTI_LEADER' },
      { id: 'n6', type: 'endEvent', name: '结束', x: 780, y: 200, width: 100, height: 50, code: 'END' },
    ],
    skips: [
      { id: 's1', sourceId: 'n1', targetId: 'n2' },
      { id: 's2', sourceId: 'n2', targetId: 'n3' },
      { id: 's3', sourceId: 'n3', targetId: 'n4', condition: '${amount <= 100000}' },
      { id: 's4', sourceId: 'n3', targetId: 'n5', condition: '${amount > 100000}' },
      { id: 's5', sourceId: 'n4', targetId: 'n6' },
      { id: 's6', sourceId: 'n5', targetId: 'n6' },
    ],
  }
}

function buildClosureTemplate(): DesignerModel {
  return {
    flowCode: 'project_closure',
    flowName: '项目结项流程',
    category: 'PROJECT',
    version: 1,
    nodes: [
      { id: 'n1', type: 'startEvent', name: '开始', x: 80, y: 200, width: 100, height: 50, code: 'START' },
      { id: 'n2', type: 'userTask', name: 'PM 提交', x: 240, y: 200, width: 120, height: 60, code: 'PM_SUBMIT', assignee: 'user:${initiatorId}', assigneeType: 'SPEL' },
      { id: 'n3', type: 'userTask', name: '部门经理审核', x: 420, y: 200, width: 120, height: 60, code: 'DEPT_MANAGER', assignee: 'role:dept_manager', assigneeType: 'ROLE' },
      { id: 'n4', type: 'userTask', name: '财务复核', x: 600, y: 100, width: 120, height: 60, code: 'FINANCE_REVIEW', assignee: 'role:finance', assigneeType: 'ROLE' },
      { id: 'n5', type: 'userTask', name: '分管领导', x: 600, y: 300, width: 120, height: 60, code: 'LEADER', assignee: 'leader:${initiatorId}', assigneeType: 'LEADER' },
      { id: 'n6', type: 'userTask', name: '总经理审批', x: 780, y: 200, width: 120, height: 60, code: 'GENERAL_MANAGER', assignee: 'role:general_manager', assigneeType: 'ROLE' },
      { id: 'n7', type: 'endEvent', name: '结束', x: 960, y: 200, width: 100, height: 50, code: 'END' },
    ],
    skips: [
      { id: 's1', sourceId: 'n1', targetId: 'n2' },
      { id: 's2', sourceId: 'n2', targetId: 'n3' },
      { id: 's3', sourceId: 'n3', targetId: 'n4' },
      { id: 's4', sourceId: 'n3', targetId: 'n5' },
      { id: 's5', sourceId: 'n4', targetId: 'n6' },
      { id: 's6', sourceId: 'n5', targetId: 'n6' },
      { id: 's7', sourceId: 'n6', targetId: 'n7' },
    ],
  }
}

const model = reactive<DesignerModel>({
  flowCode: '',
  flowName: '',
  category: 'GENERAL',
  version: 1,
  nodes: [],
  skips: [],
})

const selectedNode = ref<DesignerNode | null>(null)
const drawState = ref<{
  isDragging: boolean
  dragNode: DesignerNode | null
  offsetX: number
  offsetY: number
  isLinking: boolean
  linkFrom: DesignerNode | null
  mouseX: number
  mouseY: number
}>({
  isDragging: false,
  dragNode: null,
  offsetX: 0,
  offsetY: 0,
  isLinking: false,
  linkFrom: null,
  mouseX: 0,
  mouseY: 0,
})

const canvasRef = ref<HTMLElement | null>(null)
const viewBox = ref('0 0 1400 600')

/** 从节点类型查找元数据 */
function getNodeTypeMeta(type: string) {
  return NODE_TYPES.find((t) => t.type === type) || NODE_TYPES[2]
}

/** 添加节点 */
function addNode(type: string) {
  const meta = getNodeTypeMeta(type)
  const newNode: DesignerNode = {
    id: `n${Date.now()}`,
    type,
    name: meta.name,
    x: 100 + Math.random() * 400,
    y: 100 + Math.random() * 200,
    width: type === 'startEvent' || type === 'endEvent' ? 100 : 120,
    height: type === 'exclusiveGateway' || type === 'parallelGateway' ? 50 : 60,
    code: `${type.toUpperCase()}_${model.nodes.length + 1}`,
  }
  model.nodes.push(newNode)
}

/** 删除节点 */
function deleteNode(node: DesignerNode) {
  const idx = model.nodes.indexOf(node)
  if (idx >= 0) model.nodes.splice(idx, 1)
  // 删除关联的边
  model.skips = model.skips.filter(
    (s) => s.sourceId !== node.id && s.targetId !== node.id,
  )
  if (selectedNode.value === node) selectedNode.value = null
}

/** 选择节点 */
function selectNode(n: DesignerNode) {
  selectedNode.value = n
}

/** 应用模板 */
function applyTemplate(t: (typeof TEMPLATES)[number]) {
  model.flowCode = t.model.flowCode
  model.flowName = t.model.flowName
  model.category = t.model.category
  model.version = t.model.version
  model.nodes = JSON.parse(JSON.stringify(t.model.nodes))
  model.skips = JSON.parse(JSON.stringify(t.model.skips))
  ElMessage.success(`已应用模板：${t.name}`)
}

/** 清空 */
function clearCanvas() {
  ElMessageBox.confirm('确定清空画布？', '提示', {
    type: 'warning',
  })
    .then(() => {
      model.nodes = []
      model.skips = []
      selectedNode.value = null
    })
    .catch(() => {})
}

// ============== 拖拽 ==============

function onNodeMouseDown(e: MouseEvent, n: DesignerNode) {
  if (e.shiftKey) {
    // Shift + 点击：开始连线
    drawState.value.isLinking = true
    drawState.value.linkFrom = n
    drawState.value.mouseX = e.offsetX
    drawState.value.mouseY = e.offsetY
    e.preventDefault()
    return
  }
  drawState.value.isDragging = true
  drawState.value.dragNode = n
  // 转换为 SVG viewBox 坐标
  const svg = (e.currentTarget as SVGElement).ownerSVGElement
  if (!svg) return
  const pt = svg.createSVGPoint()
  pt.x = e.clientX
  pt.y = e.clientY
  const ctm = svg.getScreenCTM()
  if (!ctm) return
  const svgPt = pt.matrixTransform(ctm.inverse())
  drawState.value.offsetX = svgPt.x - n.x
  drawState.value.offsetY = svgPt.y - n.y
  e.preventDefault()
}

function onSvgMouseMove(e: MouseEvent) {
  const svg = e.currentTarget as SVGSVGElement
  const pt = svg.createSVGPoint()
  pt.x = e.clientX
  pt.y = e.clientY
  const ctm = svg.getScreenCTM()
  if (!ctm) return
  const svgPt = pt.matrixTransform(ctm.inverse())
  drawState.value.mouseX = svgPt.x
  drawState.value.mouseY = svgPt.y

  if (drawState.value.isDragging && drawState.value.dragNode) {
    drawState.value.dragNode.x = Math.max(0, svgPt.x - drawState.value.offsetX)
    drawState.value.dragNode.y = Math.max(0, svgPt.y - drawState.value.offsetY)
  }
}

function onSvgMouseUp(e: MouseEvent) {
  if (drawState.value.isLinking && drawState.value.linkFrom) {
    // 找到落点下的节点
    const target = e.target as Element
    const group = target.closest('.designer-node')
    if (group) {
      const targetId = group.getAttribute('data-node-id')
      if (targetId && targetId !== drawState.value.linkFrom.id) {
        // 添加边
        const exists = model.skips.find(
          (s) => s.sourceId === drawState.value.linkFrom!.id && s.targetId === targetId,
        )
        if (!exists) {
          model.skips.push({
            id: `s${Date.now()}`,
            sourceId: drawState.value.linkFrom.id,
            targetId,
          })
        }
      }
    }
  }
  drawState.value.isDragging = false
  drawState.value.dragNode = null
  drawState.value.isLinking = false
  drawState.value.linkFrom = null
}

function onSvgMouseDown(e: MouseEvent) {
  // 点击空白处取消选择
  if (e.target === e.currentTarget) {
    selectedNode.value = null
  }
}

// ============== 边渲染 ==============

function skipPath(s: DesignerSkip): string {
  const from = model.nodes.find((n) => n.id === s.sourceId)
  const to = model.nodes.find((n) => n.id === s.targetId)
  if (!from || !to) return ''
  const x1 = from.x + from.width
  const y1 = from.y + from.height / 2
  const x2 = to.x
  const y2 = to.y + to.height / 2
  const cx1 = x1 + Math.max(40, (x2 - x1) * 0.4)
  const cx2 = x2 - Math.max(40, (x2 - x1) * 0.4)
  return `M ${x1} ${y1} C ${cx1} ${y1} ${cx2} ${y2} ${x2} ${y2}`
}

function deleteSkip(s: DesignerSkip) {
  const idx = model.skips.indexOf(s)
  if (idx >= 0) model.skips.splice(idx, 1)
}

// ============== 校验 ==============

function validate(): string | null {
  if (!model.flowCode || !model.flowName) {
    return '请填写流程编码和名称'
  }
  if (model.nodes.length === 0) {
    return '画布无节点'
  }
  const startCount = model.nodes.filter((n) => n.type === 'startEvent').length
  const endCount = model.nodes.filter((n) => n.type === 'endEvent').length
  if (startCount === 0) return '缺少开始节点'
  if (startCount > 1) return '只能有 1 个开始节点'
  if (endCount === 0) return '缺少结束节点'
  // 检查孤立节点
  const inSkips = new Set<string>()
  model.skips.forEach((s) => {
    inSkips.add(s.sourceId)
    inSkips.add(s.targetId)
  })
  const orphans = model.nodes.filter(
    (n) => n.type !== 'startEvent' && !inSkips.has(n.id),
  )
  if (orphans.length > 0) {
    return `以下节点未连接：${orphans.map((n) => n.name).join('、')}`
  }
  return null
}

// ============== 部署 ==============

async function onDeploy() {
  const err = validate()
  if (err) {
    ElMessage.warning(err)
    return
  }
  const payload: FlowDeployDTO = {
    flowCode: model.flowCode,
    flowName: model.flowName,
    category: model.category,
    version: model.version,
    jsonModel: JSON.stringify(model),
  }
  try {
    const res = await deployDefinition(payload)
    if (res.data?.code === 0) {
      ElMessage.success('部署成功')
    } else {
      ElMessage.error(res.data?.message || '部署失败')
    }
  } catch (e) {
    ElMessage.error('部署失败：' + (e as Error).message)
  }
}

async function onExportJson() {
  const json = JSON.stringify(model, null, 2)
  const blob = new Blob([json], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${model.flowCode || 'flow'}.json`
  a.click()
  URL.revokeObjectURL(url)
}

const stats = computed(() => ({
  nodeCount: model.nodes.length,
  skipCount: model.skips.length,
  approvalCount: model.nodes.filter((n) => n.type === 'userTask').length,
  gatewayCount: model.nodes.filter(
    (n) => n.type.includes('Gateway') || n.type.includes('gateway'),
  ).length,
}))
</script>

<template>
  <div class="flow-designer">
    <!-- 左侧节点面板 -->
    <div class="flow-designer__panel">
      <div class="panel-section">
        <div class="panel-section__title">{{ $t('common.flowNode') }}</div>
        <div class="panel-section__content">
          <div
            v-for="t in NODE_TYPES"
            :key="t.type"
            class="node-item"
            :style="{ borderColor: t.color, color: t.color }"
            @click="addNode(t.type)"
          >
            <el-icon :size="14" :color="t.color"><component :is="t.icon" /></el-icon>
            <span>{{ t.name }}</span>
          </div>
        </div>
      </div>
      <div class="panel-section">
        <div class="panel-section__title">{{ $t('common.flowTemplate') }}</div>
        <div class="panel-section__content">
          <div
            v-for="t in TEMPLATES"
            :key="t.key"
            class="template-item"
            @click="applyTemplate(t)"
          >
            <div class="template-item__name">{{ t.name }}</div>
            <div class="template-item__desc">{{ t.description }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 画布 -->
    <div ref="canvasRef" class="flow-designer__canvas">
      <div class="canvas-toolbar">
        <div class="canvas-toolbar__info">
          <el-tag size="small" type="info">节点 {{ stats.nodeCount }}</el-tag>
          <el-tag size="small" type="info">边 {{ stats.skipCount }}</el-tag>
          <el-tag size="small" type="info">审批 {{ stats.approvalCount }}</el-tag>
          <el-tag size="small" type="info">网关 {{ stats.gatewayCount }}</el-tag>
        </div>
        <div class="canvas-toolbar__actions">
          <el-button size="small" @click="clearCanvas">{{ $t('common.clear') }}</el-button>
          <el-button size="small" @click="onExportJson">{{ $t('common.exportJson') }}</el-button>
          <el-button size="small" type="primary" @click="onDeploy">{{ $t('common.deploy') }}</el-button>
        </div>
      </div>
      <svg
        :viewBox="viewBox"
        xmlns="http://www.w3.org/2000/svg"
        class="designer-svg"
        @mousemove="onSvgMouseMove"
        @mouseup="onSvgMouseUp"
        @mousedown="onSvgMouseDown"
      >
        <defs>
          <marker
            id="arrow-skip"
            viewBox="0 0 10 10"
            refX="9"
            refY="5"
            markerWidth="8"
            markerHeight="8"
            orient="auto-start-reverse"
          >
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#1890ff" />
          </marker>
        </defs>

        <!-- 边 -->
        <g class="skips">
          <g v-for="s in model.skips" :key="s.id" class="skip-group">
            <path
              :d="skipPath(s)"
              stroke="#1890ff"
              stroke-width="2"
              fill="none"
              marker-end="url(#arrow-skip)"
              class="skip-path"
            />
            <text
              :x="
                (model.nodes.find((n) => n.id === s.sourceId)?.x || 0) +
                ((model.nodes.find((n) => n.id === s.targetId)?.x || 0) -
                  (model.nodes.find((n) => n.id === s.sourceId)?.x || 0)) /
                  2
              "
              :y="
                ((model.nodes.find((n) => n.id === s.sourceId)?.y || 0) +
                  (model.nodes.find((n) => n.id === s.targetId)?.y || 0)) /
                  2
              "
              text-anchor="middle"
              class="skip-label"
            >
              {{ s.name || s.condition || '' }}
            </text>
            <circle
              :cx="
                (model.nodes.find((n) => n.id === s.sourceId)?.x || 0) +
                ((model.nodes.find((n) => n.id === s.targetId)?.x || 0) -
                  (model.nodes.find((n) => n.id === s.sourceId)?.x || 0)) /
                  2
              "
              :cy="
                ((model.nodes.find((n) => n.id === s.sourceId)?.y || 0) +
                  (model.nodes.find((n) => n.id === s.targetId)?.y || 0)) /
                  2
              "
              r="8"
              fill="#fff"
              stroke="#ef4444"
              stroke-width="1"
              class="skip-delete"
              @click="deleteSkip(s)"
            />
            <text
              :x="
                (model.nodes.find((n) => n.id === s.sourceId)?.x || 0) +
                ((model.nodes.find((n) => n.id === s.targetId)?.x || 0) -
                  (model.nodes.find((n) => n.id === s.sourceId)?.x || 0)) /
                  2
              "
              :y="
                ((model.nodes.find((n) => n.id === s.sourceId)?.y || 0) +
                  (model.nodes.find((n) => n.id === s.targetId)?.y || 0)) /
                  2 +
                4
              "
              text-anchor="middle"
              class="skip-delete-x"
              @click="deleteSkip(s)"
            >
              ×
            </text>
          </g>
        </g>

        <!-- 正在绘制的临时边 -->
        <path
          v-if="drawState.isLinking && drawState.linkFrom"
          :d="`M ${drawState.linkFrom.x + drawState.linkFrom.width} ${
            drawState.linkFrom.y + drawState.linkFrom.height / 2
          } L ${drawState.mouseX} ${drawState.mouseY}`"
          stroke="#52c41a"
          stroke-width="2"
          stroke-dasharray="4 2"
          fill="none"
        />

        <!-- 节点 -->
        <g
          v-for="n in model.nodes"
          :key="n.id"
          :transform="`translate(${n.x}, ${n.y})`"
          :data-node-id="n.id"
          class="designer-node"
          :class="{ 'designer-node--selected': selectedNode === n }"
          @mousedown="onNodeMouseDown($event, n)"
          @click.stop="selectNode(n)"
        >
          <rect
            v-if="!n.type.includes('Gateway') && !n.type.includes('gateway')"
            :width="n.width"
            :height="n.height"
            :fill="selectedNode === n ? '#e6f7ff' : '#fff'"
            :stroke="getNodeTypeMeta(n.type).color"
            :stroke-width="selectedNode === n ? 2.5 : 1.5"
            rx="6"
            ry="6"
          />
          <circle
            v-else
            :cx="n.width / 2"
            :cy="n.height / 2"
            :r="Math.min(n.width, n.height) / 2"
            :fill="selectedNode === n ? '#e6f7ff' : '#fff'"
            :stroke="getNodeTypeMeta(n.type).color"
            :stroke-width="selectedNode === n ? 2.5 : 1.5"
          />
          <text
            :x="n.width / 2"
            :y="n.height / 2 - 4"
            text-anchor="middle"
            class="node-text"
          >
            {{ n.name }}
          </text>
          <text
            :x="n.width / 2"
            :y="n.height / 2 + 14"
            text-anchor="middle"
            class="node-subtext"
          >
            {{ n.code }}
          </text>
        </g>
      </svg>
      <div class="canvas-hint">
        {{ $t('common.nodeHint') }}
      </div>
    </div>

    <!-- 右侧属性面板 -->
    <div class="flow-designer__properties">
      <div v-if="!selectedNode" class="empty-properties">
        <el-icon :size="48" color="#cbd5e1"><Position /></el-icon>
        <p>{{ $t('common.selectNodeEdit') }}</p>
      </div>
      <div v-else class="property-form">
        <div class="property-form__title">
          {{ $t('common.nodeProperties') }}
          <el-button
            type="danger"
            size="small"
            text
            @click="deleteNode(selectedNode)"
          >
            {{ $t('common.deleteNode') }}
          </el-button>
        </div>
        <el-form label-position="top" size="small">
          <el-form-item label="节点 ID">
            <el-input v-model="selectedNode.code" />
          </el-form-item>
          <el-form-item label="节点名称">
            <el-input v-model="selectedNode.name" />
          </el-form-item>
          <el-form-item label="类型">
            <el-tag>{{ getNodeTypeMeta(selectedNode.type).name }}</el-tag>
          </el-form-item>
          <template v-if="selectedNode.type === 'userTask'">
            <el-form-item label="办理人类型">
              <el-select v-model="selectedNode.assigneeType" placeholder="请选择" style="width: 100%">
                <el-option
                  v-for="a in ASSIGNEE_TYPES"
                  :key="a.value"
                  :label="a.label"
                  :value="a.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="办理人">
              <el-input
                v-model="selectedNode.assignee"
                placeholder="user:1001 / role:dept_manager / ${initiator}"
              />
            </el-form-item>
            <el-form-item label="会签类型">
              <el-select v-model="selectedNode.performType" style="width: 100%">
                <el-option
                  v-for="p in PERFORM_TYPES"
                  :key="p.value"
                  :label="p.label"
                  :value="p.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="表单 Key">
              <el-input v-model="selectedNode.formKey" />
            </el-form-item>
            <el-form-item label="优先级 (1-100)">
              <el-input-number
                v-model="selectedNode.priority"
                :min="1"
                :max="100"
              />
            </el-form-item>
            <el-form-item label="超时时长">
              <el-input v-model="selectedNode.timeout" placeholder="如 24h / 2d" />
            </el-form-item>
            <el-form-item label="超时策略">
              <el-select v-model="selectedNode.timeoutStrategy" style="width: 100%">
                <el-option
                  v-for="s in TIMEOUT_STRATEGIES"
                  :key="s.value"
                  :label="s.label"
                  :value="s.value"
                />
              </el-select>
            </el-form-item>
          </template>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.flow-designer {
  display: grid;
  grid-template-columns: 220px 1fr 320px;
  height: calc(100vh - 200px);
  background: #f1f5f9;

  &__panel {
    background: #fff;
    border-right: 1px solid #e2e8f0;
    overflow-y: auto;
  }

  &__canvas {
    position: relative;
    background: #f8fafc;
    overflow: hidden;
  }

  &__properties {
    background: #fff;
    border-left: 1px solid #e2e8f0;
    overflow-y: auto;
  }
}

.panel-section {
  border-bottom: 1px solid #e2e8f0;

  &__title {
    padding: 12px 16px;
    font-weight: 600;
    font-size: 13px;
    color: #1e293b;
    background: #f8fafc;
    border-bottom: 1px solid #e2e8f0;
  }

  &__content {
    padding: 8px;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 6px;
  }
}

.node-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px;
  background: #fff;
  border: 1px solid;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  user-select: none;
  transition: all 0.2s;

  &:hover {
    background: #f8fafc;
    transform: translateY(-1px);
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
  }
}

.template-item {
  grid-column: span 2;
  padding: 10px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;

  &__name {
    font-weight: 600;
    color: #1e293b;
    font-size: 13px;
  }

  &__desc {
    color: #64748b;
    font-size: 11px;
    margin-top: 2px;
  }

  &:hover {
    border-color: #1890ff;
    background: #f0f9ff;
  }
}

.canvas-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;

  &__info {
    display: flex;
    gap: 8px;
  }
}

.designer-svg {
  width: 100%;
  height: calc(100% - 60px);
  background: #fff;
  cursor: default;
}

.designer-node {
  cursor: move;
  user-select: none;

  &--selected rect,
  &--selected circle {
    stroke-dasharray: 0;
  }
}

.node-text {
  fill: #1e293b;
  font-size: 13px;
  font-weight: 500;
  pointer-events: none;
}

.node-subtext {
  fill: #94a3b8;
  font-size: 10px;
  pointer-events: none;
}

.skip-label {
  fill: #1890ff;
  font-size: 11px;
  paint-order: stroke;
  stroke: #fff;
  stroke-width: 3;
  pointer-events: none;
}

.skip-delete {
  cursor: pointer;
}

.skip-delete-x {
  fill: #ef4444;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  pointer-events: none;
}

.skip-path:hover {
  stroke-width: 3;
}

.canvas-hint {
  position: absolute;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(15, 23, 42, 0.8);
  color: #fff;
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 12px;
  pointer-events: none;
}

.empty-properties {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #94a3b8;

  p {
    margin-top: 12px;
    font-size: 13px;
  }
}

.property-form {
  padding: 16px;

  &__title {
    font-weight: 600;
    font-size: 14px;
    color: #1e293b;
    margin-bottom: 16px;
    padding-bottom: 12px;
    border-bottom: 1px solid #e2e8f0;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
}
</style>
