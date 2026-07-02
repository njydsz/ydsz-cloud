/**
 * @file 工作流 DTO 类型定义
 * @module api/workflow/types
 */

/** 流程定义 */
export interface FlowDefinitionDTO {
  id: number
  flowCode: string
  flowName: string
  version: number
  category?: string
  status: 'DRAFT' | 'PUBLISHED' | 'DEPRECATED' | 'OFFLINE'
  formPath?: string
  bpmnXml?: string
  jsonModel?: string
  createBy?: number
  createTime?: string
  updateBy?: number
  updateTime?: string
}

/** 流程实例 */
export interface FlowInstanceDTO {
  id: number
  flowCode: string
  flowName?: string
  definitionId?: number
  businessType?: string
  businessKey?: string
  businessNo?: string
  title?: string
  initiatorId?: number
  initiatorName?: string
  status: 'RUNNING' | 'SUSPENDED' | 'COMPLETED' | 'TERMINATED' | 'REJECTED'
  currentNodeCode?: string
  currentNodeName?: string
  variableJson?: string
  startTime?: string
  endTime?: string
  durationMs?: number
  tenantId?: number
  providerTraceId?: string
}

/** 任务 */
export interface FlowTaskDTO {
  id: number
  instanceId: number
  flowCode: string
  flowName?: string
  nodeCode: string
  nodeName?: string
  nodeType?: number
  businessType?: string
  businessId?: string
  businessNo?: string
  title?: string
  assignorId?: number
  assignorName?: string
  assigneeType?: string
  assigneeId?: string
  assigneeName?: string
  performType?: string
  approveCount?: number
  approveFinished?: number
  taskStatus: 'PENDING' | 'CLAIMED' | 'COMPLETED' | 'REJECTED' | 'SKIPPED' | 'CANCELLED' | 'TIMEOUT' | 'DELEGATED' | 'FROZEN'
  comment?: string
  claimAt?: string
  finishAt?: string
  durationMs?: number
  dueAt?: string
  createTime?: string
}

/** 抄送 */
export interface FlowCcDTO {
  id: number
  instanceId: number
  taskId?: number
  nodeCode: string
  nodeName?: string
  flowCode: string
  flowName?: string
  businessKey?: string
  ccUserId: number
  ccUserName?: string
  ccType: 'CC_NODE' | 'MANUAL_CC' | 'AUTO_CC'
  triggerUserId?: number
  triggerUserName?: string
  title?: string
  content?: string
  readStatus: 'UNREAD' | 'READ'
  readAt?: string
  createTime?: string
}

/** 抄送查询 */
export interface FlowCcQuery {
  readStatus?: 'UNREAD' | 'READ'
  flowCode?: string
  pageNum?: number
  pageSize?: number
}

/** 任务查询 */
export interface FlowTaskQuery {
  assigneeId?: number
  businessType?: string
  flowCode?: string
  startTime?: string
  endTime?: string
  pageNum?: number
  pageSize?: number
}

/** 启动流程 */
export interface FlowStartProcessDTO {
  flowCode: string
  businessType?: string
  businessKey?: string
  businessNo?: string
  title?: string
  variables?: Record<string, unknown>
  initiatorId?: number
}

/** 任务操作 DTO */
export interface FlowTaskOperateDTO {
  taskId: number
  comment?: string
  targetUserId?: number
  targetUserName?: string
  targetNodeCode?: string
  variables?: Record<string, unknown>
}

/** 流程部署 */
export interface FlowDeployDTO {
  flowCode: string
  flowName: string
  category?: string
  version?: number
  bpmnXml?: string
  jsonModel?: string
  formPath?: string
}

/** 流程图 DTO */
export interface FlowDiagramDTO {
  instanceId: number
  flowCode: string
  flowName?: string
  status?: string
  nodes: FlowDiagramNodeDTO[]
  skips: FlowDiagramSkipDTO[]
  activeNodeCodes: string[]
  completedNodeCodes: string[]
}

export interface FlowDiagramNodeDTO {
  nodeCode: string
  nodeName?: string
  nodeType: number
  x?: number
  y?: number
  width?: number
  height?: number
  permissionFlag?: string
  ext?: string
}

export interface FlowDiagramSkipDTO {
  skipCode?: string
  skipName?: string
  sourceRef: string
  targetRef: string
  condition?: string
  skipType?: string
}

/** 时间线 DTO */
export interface FlowTimelineDTO {
  instanceId: number
  events: FlowTimelineEventDTO[]
}

export interface FlowTimelineEventDTO {
  id?: number
  eventType: 'START' | 'TASK_CREATED' | 'TASK_COMPLETED' | 'URGE' | 'TRANSFER' | 'DELEGATE' | 'COUNTERSIGN' | 'TIMEOUT' | 'TERMINATE' | 'COMPLETE' | 'REJECT' | 'SUSPEND' | 'ACTIVATE' | 'RECALL' | 'JUMP' | 'CC'
  nodeCode?: string
  nodeName?: string
  userId?: number
  userName?: string
  targetUserId?: number
  targetUserName?: string
  comment?: string
  action?: string
  durationMs?: number
  createdAt: string
}

/** 节点耗时统计 */
export interface FlowNodeDurationStatDTO {
  flowCode: string
  flowName?: string
  nodeCode: string
  nodeName?: string
  instanceCount: number
  avgDurationMs: number
  maxDurationMs: number
  minDurationMs: number
  overdueCount: number
}

/** P2-4: 流程回放步骤 */
export interface FlowReplayStepDTO {
  /** 步骤序号（从 0 开始） */
  stepIndex: number
  /** 步骤类型：START / HIS_TASK / AUDIT_LOG / CURRENT_TASK / END */
  type: 'START' | 'HIS_TASK' | 'AUDIT_LOG' | 'CURRENT_TASK' | 'END'
  /** 发生时间（ISO 字符串） */
  timestamp?: string
  /** 节点编码 */
  nodeCode?: string
  /** 节点名称 */
  nodeName?: string
  /** 操作人 ID（数字或字符串） */
  actor?: number | string
  /** 操作人姓名 */
  actorName?: string
  /** 操作动作：PASS / REJECT / TRANSFER / DELEGATE / URGE / ... */
  action?: string
  /** 审批意见 */
  comment?: string
  /** 节点回放后状态：ENTERED / PASSED / REJECTED / ACTIVE / SKIPPED / OBSERVED / FINISHED */
  nodeState?: string
  /** 本步耗时（毫秒） */
  durationMs?: number
  /**
   * P3-1: 节点坐标 {x, y, width, height}，来自 BPMNDI 段或前端设计器
   * 用于回放时自动滚屏到当前节点
   */
  coordinate?: {
    x: number
    y: number
    width: number
    height: number
  }
}
