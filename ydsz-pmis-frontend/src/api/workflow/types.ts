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
  /** P1-1: 任务优先级（1-100，默认 50） */
  priority?: number
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
  /**
   * 目标节点编码
   * - REJECT：单节点退回（向后兼容）
   * - GAP-P2-9 自由流（JUMP）：运行时动态指定下一节点，目标节点需 ext.freeJump=true
   */
  targetNodeCode?: string
  /** GAP-P0-2: 多节点同退目标列表（非空时优先于 targetNodeCode） */
  targetNodeCodes?: string[]
  /** GAP-P2-9: 自由流（JUMP）运行时指定目标节点办理人列表（如 ['1001','1002']） */
  targetAssignees?: string[]
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

// ===========================================
// 监控仪表盘 DTO
// ===========================================

/** 监控概览统计数据 */
export interface MonitorOverviewDTO {
  /** 运行中实例数 */
  runningCount: number
  /** 今日新增实例 */
  todayNewCount: number
  /** 待办任务总数 */
  pendingTaskCount: number
  /** 超时任务数 */
  overdueTaskCount: number
  /** 今日完成数 */
  todayCompletedCount: number
}

/** 异常流程实例 */
export interface AnomalyInstanceDTO {
  id: number
  flowCode: string
  flowName?: string
  title?: string
  initiatorName?: string
  status: string
  currentNodeName?: string
  anomalyType: 'TIMEOUT' | 'STUCK' | 'CIRCULAR_APPROVAL' | 'REPEATED_REJECT'
  anomalyTypeLabel?: string
  overdueDays?: number
  startTime?: string
  dueAt?: string
  warnLevel: 'RED' | 'YELLOW' | 'ORANGE'
}

/** 实例趋势数据点 */
export interface InstanceTrendItemDTO {
  date: string
  newCount: number
  completedCount: number
}

/** 审批人效率统计 */
export interface ApproverEfficiencyDTO {
  userId: number
  userName: string
  department?: string
  completedCount: number
  avgDurationMs: number
  totalDurationMs: number
}

/** 流程类型分布 */
export interface FlowTypeDistributionDTO {
  flowCode: string
  flowName: string
  count: number
  percentage?: number
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

// ===========================================
// 表单设计器
// ===========================================

/** 表单 schema 保存/更新 */
export interface FormSchemaDTO {
  /** 表单编码（唯一标识） */
  formCode: string
  /** 表单名称 */
  formName: string
  /** 表单 schema JSON 字符串 */
  formSchema: string
  /** 表单描述 */
  description?: string
}

/** 表单 schema 查询结果 */
export interface FormSchemaVO {
  /** 表单编码 */
  formCode: string
  /** 表单名称 */
  formName: string
  /** 表单 schema JSON 字符串 */
  formSchema: string
  /** 表单描述 */
  description?: string
  /** 创建时间 */
  createTime?: string
  /** 更新时间 */
  updateTime?: string
}

// ===========================================
// P1-1: 运行时表单引擎
// ===========================================

/** 字段权限类型 */
export type FieldPermission = 'EDIT' | 'READONLY' | 'HIDDEN'

/** 表单渲染数据（后端 form-render 接口返回） */
export interface FormRenderDataDTO {
  /** 表单 schema（form-create rule JSON） */
  formSchema: Record<string, unknown> | string
  /** 字段权限映射：fieldName -> 权限 */
  fieldPermissions: Record<string, FieldPermission>
}

/** 节点表单字段配置 */
export interface NodeFormConfigDTO {
  /** 流程定义 ID */
  definitionId: number
  /** 节点编码 */
  nodeCode: string
  /** 字段权限配置：fieldName -> 权限 */
  fieldPermissions: Record<string, FieldPermission>
  /** 表单 schema（可选） */
  formSchema?: string
}

// ===========================================
// P1-2: 委托授权
// ===========================================

/** 委托授权范围类型 */
export type DelegateScopeType = 'ALL' | 'FLOW' | 'FLOW_NODE' | 'ROLE'

/** 委托授权记录 */
export interface DelegateAuthDTO {
  id: number
  /** 授权人 ID */
  ownerId: number
  /** 授权人姓名 */
  ownerName?: string
  /** 代理人 ID */
  delegateId: number
  /** 代理人姓名 */
  delegateName?: string
  /** 授权范围类型 */
  scopeType: DelegateScopeType
  /** 范围值（ALL 时为空；FLOW 时为 flowCode；FLOW_NODE 时为 flowCode:nodeCode；ROLE 时为 roleCode） */
  scopeValue?: string
  /** 开始时间 */
  startTime?: string
  /** 结束时间 */
  endTime?: string
  /** 是否启用 */
  enabled: boolean
  /** 是否已撤回 */
  revoked?: boolean
  createTime?: string
  updateTime?: string
}

/** 创建委托授权请求 */
export interface CreateDelegateAuthDTO {
  /** 代理人 ID */
  delegateId: number
  /** 代理人姓名 */
  delegateName?: string
  /** 授权范围类型 */
  scopeType: DelegateScopeType
  /** 范围值 */
  scopeValue?: string
  /** 开始时间 */
  startTime?: string
  /** 结束时间 */
  endTime?: string
}

/** 委托处理记录 */
export interface DelegateLogDTO {
  id: number
  /** 原授权人 ID */
  ownerId: number
  ownerName?: string
  /** 代理人 ID */
  delegateId: number
  delegateName?: string
  /** 任务 ID */
  taskId?: number
  /** 流程编码 */
  flowCode?: string
  /** 流程名称 */
  flowName?: string
  /** 节点名称 */
  nodeName?: string
  /** 操作动作 */
  action?: string
  /** 操作时间 */
  operateTime?: string
}

// ===========================================
// P1-2: SLA
// ===========================================

/** SLA 策略类型 */
export type SlaStrategy = 'REMIND' | 'ESCALATE' | 'AUTO_PASS' | 'AUTO_REJECT'

/** SLA 超时任务 */
export interface SlaOverdueTaskDTO {
  taskId: number
  instanceId: number
  flowCode: string
  flowName?: string
  nodeCode: string
  nodeName?: string
  assigneeId?: string
  assigneeName?: string
  /** 任务创建时间 */
  createTime?: string
  /** 截止时间 */
  dueAt?: string
  /** 超时天数 */
  overdueDays?: number
  /** SLA 策略 */
  strategy?: SlaStrategy
  /** 任务标题 */
  title?: string
}

/**
 * P1-2: 节点级 SLA 规则配置
 *
 * 与后端 `FlowNodeDO.slaConfig` JSON 字段对应，由 `FlowSlaServiceImpl` 解析执行。
 */
export interface SlaRuleConfigDTO {
  /** 超时阈值（分钟）。必填，>0 才算开启 SLA */
  timeoutMinutes: number
  /** 超时后的最终动作 */
  action: SlaStrategy
  /** 提醒间隔（分钟），默认 60 */
  reminderIntervalMinutes?: number
  /** 最大提醒次数，达到后执行最终动作，默认 3 */
  maxReminders?: number
  /** 升级目标用户 ID（action=ESCALATE 时使用，可空默认管理员） */
  escalateUserId?: number | null
  /** 自动操作备注（AUTO_PASS/AUTO_REJECT 时写入审批意见） */
  autoComment?: string
}

// ===========================================
// P1-2: 灰度发布
// ===========================================

/** 灰度策略类型 */
export type CanaryStrategy = 'PERCENTAGE' | 'WHITELIST' | 'PERCENTAGE_AND_WHITELIST'

/** 灰度发布状态 */
export type CanaryStatus = 'DRAFT' | 'ROLLING_OUT' | 'PROMOTED' | 'ROLLED_BACK'

/** 灰度发布记录 */
export interface CanaryRolloutDTO {
  id: number
  /** 流程定义 ID */
  definitionId: number
  /** 流程编码 */
  flowCode: string
  /** 流程名称 */
  flowName?: string
  /** 灰度策略 */
  strategy: CanaryStrategy
  /** 灰度比例（0-100） */
  percentage?: number
  /** 白名单用户 ID 列表 */
  whitelist?: number[]
  /** 灰度状态 */
  status: CanaryStatus
  /** 开始时间 */
  startTime?: string
  /** 结束时间 */
  endTime?: string
  createBy?: number
  createTime?: string
}

/** 灰度发布日志 */
export interface CanaryRolloutLogDTO {
  id: number
  /** 流程编码 */
  flowCode: string
  /** 流程名称 */
  flowName?: string
  /** 操作类型：PUBLISH / ADJUST / PROMOTE / ROLLBACK */
  action: string
  /** 灰度比例 */
  percentage?: number
  /** 操作人 */
  operatorId?: number
  operatorName?: string
  /** 操作详情 */
  detail?: string
  /** 操作时间 */
  operateTime?: string
}

/** 启动灰度请求 */
export interface PublishCanaryDTO {
  strategy: CanaryStrategy
  percentage?: number
  whitelist?: number[]
}

// ===========================================
// P1-3: 版本管理 + 模拟运行
// ===========================================

/** 流程定义版本信息 */
export interface FlowVersionDTO {
  /** 版本号 */
  version: number
  /** 流程定义 ID */
  definitionId: number
  /** 流程编码 */
  flowCode: string
  /** 流程名称 */
  flowName?: string
  /** 状态 */
  status: string
  /** 是否当前激活版本 */
  active: boolean
  /** BPMN XML（差异对比时可能返回） */
  bpmnXml?: string
  /** 部署时间 */
  deployTime?: string
  /** 创建人 */
  createBy?: number
  createTime?: string
}

/** 版本差异对比结果 */
export interface VersionDiffDTO {
  /** 版本 1 */
  v1: number
  /** 版本 2 */
  v2: number
  /** 差异内容（文本格式） */
  diffContent: string
  /** 新增节点 */
  addedNodes?: string[]
  /** 删除节点 */
  removedNodes?: string[]
  /** 修改节点 */
  modifiedNodes?: string[]
}

/** 模拟运行步骤结果 */
export interface SimulateStepDTO {
  /** 步骤序号 */
  stepIndex: number
  /** 节点编码 */
  nodeCode: string
  /** 节点名称 */
  nodeName?: string
  /** 节点类型 */
  nodeType?: string
  /** 处理结果：PASS / REJECT / SKIP / ERROR */
  result: string
  /** 处理人 */
  assignee?: string
  /** 处理说明 */
  comment?: string
  /** 是否通过 */
  passed: boolean
}

/** 模拟运行结果 */
export interface SimulateResultDTO {
  /** 流程编码 */
  flowCode: string
  /** 是否成功完成 */
  success: boolean
  /** 模拟步骤列表 */
  steps: SimulateStepDTO[]
  /** 最终结果 */
  finalResult?: string
  /** 错误信息 */
  errorMessage?: string
}

/** P2-3: 任务评论 */
export interface TaskCommentDTO {
  id: number
  tenantId?: number
  instanceId: number
  taskId?: number
  nodeCode?: string
  userId: number
  userName?: string
  content: string
  /** 评论类型：COMMENT / QUESTION / REPLY */
  type: string
  /** 父评论 ID（楼中楼回复） */
  parentId?: number
  createdAt?: string
  updatedAt?: string
}

/** P2-6: 流程模板 */
export interface FlowTemplateDTO {
  templateCode: string
  templateName: string
  category?: string
  description?: string
  icon?: string
  useCount?: number
  formPath?: string
  bpmnXml?: string
  createdAt?: string
  updatedAt?: string
}

/** P3-1: 通知通道配置 */
export interface NotifyChannelDTO {
  id?: number
  tenantId?: number
  /** 通道类型：INAPP / EMAIL / SMS / WEBHOOK / DINGTALK / WECHAT */
  channelType: string
  channelName: string
  /** JSON 配置（webhook URL / SMS 模板编码等） */
  config: string
  enabled: boolean
  createdAt?: string
  updatedAt?: string
}

/** P3-3: 实例迁移入参 */
export interface InstanceMigrationDTO {
  sourceDefinitionId: number
  targetDefinitionId: number
  tenantId?: number
  /** 旧节点编码 → 新节点编码 映射 */
  nodeMapping?: Record<string, string>
  /** true=仅预览，false=执行迁移 */
  dryRun?: boolean
}

/** P3-3: 迁移明细 */
export interface MigrationDetail {
  instanceId: number
  instanceTitle?: string
  oldNodeCode?: string
  newNodeCode?: string
  /** MIGRATED / SKIPPED / FAILED */
  status: string
  reason?: string
}

/** P3-3: 实例迁移结果 */
export interface InstanceMigrationResultDTO {
  totalInstances: number
  migratedCount: number
  skippedCount: number
  failedCount: number
  details: MigrationDetail[]
  nodeMappingApplied?: Record<string, string>
}
