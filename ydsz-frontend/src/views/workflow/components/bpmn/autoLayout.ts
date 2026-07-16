/**
 * @file BPMN 自动布局工具（基于 dagre）
 * @module components/bpmn/autoLayout
 * @description P2-3: 引入 dagre 分层有向图布局，对标钉钉/飞书审批流自动排版能力。
 *   解决问题：bpmn-js 内置的 BpmnAutoPlace 仅"放在右侧固定偏移"，对网关分叉/并行汇聚
 *   场景节点严重重叠、连线交叉；用户从模板加载的 XML 无 BPMNDI 段时节点挤在一处。
 *
 *   实现策略：
 *   1. 从 elementRegistry 读取所有 shape 和 connection
 *   2. 构建 dagre 图：节点带 width/height，边带 source/target
 *   3. 调用 dagre.layout，rankdir=LR（横向审批流习惯），nodesep=50、ranksep=80
 *   4. 通过 modeling.moveShape 批量移动到目标坐标
 *   5. 连线由 bpmn-js 的 layoutConnection 模块自动重算 waypoints
 *
 *   持久化：moveShape 触发 commandStack.changed，saveXML 时新坐标写入 BPMNDI 段，
 *   部署后实例详情/回放均能透传。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

import dagre from 'dagre'
import type Modeler from 'bpmn-js/lib/Modeler'
import type { Element as BpmnElement, Shape, Connection } from 'bpmn-js/lib/model/Types'

/** bpmn-js elementRegistry 服务类型（仅声明 autoLayout 所需方法，避免 unknown） */
interface BpmnElementRegistry {
  getAll(): BpmnElement[]
}

/** bpmn-js modeling 服务类型（仅声明 autoLayout 所需方法，避免 unknown） */
interface BpmnModeling {
  moveShape(shape: Shape, delta: { x: number; y: number }): void
  layoutConnection(connection: Connection): void
}

/** dagre 节点最小宽度兜底（防止 0 宽度导致布局异常） */
const MIN_NODE_WIDTH = 36
/** dagre 节点最小高度兜底 */
const MIN_NODE_HEIGHT = 36
/** 同层节点垂直间距（px） */
const NODE_SEP = 50
/** 层与层水平间距（px） */
const RANK_SEP = 80
/** 整体左边距（避免节点贴边） */
const CANVAS_PADDING_X = 120
/** 整体上边距 */
const CANVAS_PADDING_Y = 80

/**
 * 对当前画布执行一次自动布局
 *
 * @param modeler bpmn-js Modeler 实例
 * @param options 可选布局参数（覆盖默认值）
 * @throws Error 当画布无元素或布局失败时抛出
 */
export function autoLayout(
  modeler: Modeler,
  options?: AutoLayoutOptions,
): void {
  const elementRegistry = modeler.get('elementRegistry') as unknown as BpmnElementRegistry
  const modeling = modeler.get('modeling') as unknown as BpmnModeling

  const allElements = elementRegistry.getAll()
  const shapes = allElements.filter((el: BpmnElement): el is Shape => el.type !== 'label' && !el.waypoints)
  const connections = allElements.filter((el: BpmnElement): el is Connection => Array.isArray(el.waypoints))

  if (shapes.length === 0) {
    throw new Error('画布无可布局的节点')
  }

  // 构建 dagre 图
  const g = new dagre.graphlib.Graph<GraphNode>({ multigraph: true, compound: false })
  g.setGraph({
    rankdir: options?.rankdir ?? 'LR',
    nodesep: options?.nodesep ?? NODE_SEP,
    ranksep: options?.ranksep ?? RANK_SEP,
    marginx: options?.marginx ?? CANVAS_PADDING_X,
    marginy: options?.marginy ?? CANVAS_PADDING_Y,
  })
  g.setDefaultEdgeLabel(() => ({}))

  // 添加节点
  for (const shape of shapes) {
    const w = Math.max(shape.width ?? MIN_NODE_WIDTH, MIN_NODE_WIDTH)
    const h = Math.max(shape.height ?? MIN_NODE_HEIGHT, MIN_NODE_HEIGHT)
    g.setNode(shape.id, { id: shape.id, width: w, height: h })
  }

  // 添加边（仅保留两端节点都在图内的边，避免悬挂边）
  for (const conn of connections) {
    const src = conn.source?.id
    const tgt = conn.target?.id
    if (src && tgt && g.hasNode(src) && g.hasNode(tgt)) {
      g.setEdge(src, tgt, { id: conn.id })
    }
  }

  // 执行布局
  dagre.layout(g)

  // 批量移动节点到目标坐标
  // dagre 返回的 x/y 是节点中心点，bpmn-js 的 moveShape 需要 dx/dy（左上角偏移量）
  for (const shape of shapes) {
    const node = g.node(shape.id)
    if (!node) continue
    const targetCenterX = node.x
    const targetCenterY = node.y
    const halfW = (shape.width ?? MIN_NODE_WIDTH) / 2
    const halfH = (shape.height ?? MIN_NODE_HEIGHT) / 2
    const targetX = targetCenterX - halfW
    const targetY = targetCenterY - halfH
    const currentX = shape.x ?? 0
    const currentY = shape.y ?? 0
    const dx = targetX - currentX
    const dy = targetY - currentY
    if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) continue
    modeling.moveShape(shape, { x: dx, y: dy })
  }

  // 连线 waypoints 由 bpmn-js 的 layoutConnection 在 moveShape 后自动重算
  // 显式触发一次 connection 重排，避免某些场景下连线未更新
  for (const conn of connections) {
    try {
      modeling.layoutConnection(conn)
    } catch {
      // 单条连线重排失败不影响整体布局
    }
  }
}

/** dagre 图节点数据结构 */
interface GraphNode {
  id: string
  width: number
  height: number
  x?: number
  y?: number
}

/** 自动布局可选参数 */
export interface AutoLayoutOptions {
  /** 布局方向，默认 LR（左到右） */
  rankdir?: 'TB' | 'BT' | 'LR' | 'RL'
  /** 同层节点间距 */
  nodesep?: number
  /** 层间距 */
  ranksep?: number
  /** 水平边距 */
  marginx?: number
  /** 垂直边距 */
  marginy?: number
}
