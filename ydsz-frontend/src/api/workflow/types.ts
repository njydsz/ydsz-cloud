/**
 * @file 工作流 DTO 类型定义
 * @module api/workflow/types
 */

/** 流程定义 */
export interface FlowDefinitionDTO {
  /** 流程定义 ID */
  id: string
  /** 流程编码 */
  flowCode: string
  /** 流程名称 */
  flowName: string
  /** 版本号 */
  version: number
  /** 分类 */
  category?: string
  /** DRAFT / PUBLISHED / DEPRECATED / OFFLINE */
  status: 'DRAFT' | 'PUBLISHED' | 'DEPRECATED' | 'OFFLINE'
  /** 表单路径 */
  formPath?: string
  /** BPMN XML */
  bpmnXml?: string
  /** JSON 模型 */
  jsonModel?: string
  /** 创建人 */
  createBy?: string
  /** 创建时间 */
  createTime?: string
  /** 更新人 */
  updateBy?: string
  /** 更新时间 */
  updateTime?: string
}

/** 流程实例 */
export interface FlowInstanceDTO {
  /** 实例 ID */
  id: string
  /** 流程编码 */
  flowCode: string
  /** 流程名称 */
  flowName?: string
  /** 流程定义 ID */
  definitionId?: string
  /** 业务类型 */
  businessType?: string
  /** 业务 Key */
  businessKey?: string
  /** 业务编号 */
  businessNo?: string
  /** 实例标题 */
  title?: string
  /** 发起人 ID */
  initiatorId?: string
  /** 发起人姓名 */
  initiatorName?: string
  /** RUNNING / SUSPENDED / COMPLETED / TERMINATED / REJECTED */
  status: 'RUNNING' | 'SUSPENDED' | 'COMPLETED' | 'TERMINATED' | 'REJECTED'
  /** 当前节点编码 */
  currentNodeCode?: string
  /** 当前节点名称 */
  currentNodeName?: string
  /** 变量 JSON */
  variableJson?: string
  /** 开始时间 */
  startTime?: string
  /** 结束时间 */
  endTime?: string
  /** 耗时（毫秒） */
  durationMs?: number
  /** 租户 ID */
  tenantId?: string
  /** 提供方跟踪 ID */
  providerTraceId?: string
}

/** 任务 */
export interface FlowTaskDTO {
  /** 任务 ID */
  id: string
  /** 实例 ID */
  instanceId: string
  /** 流程编码 */
  flowCode: string
  /** 流程名称 */
  flowName?: string
  /** 节点编码 */
  nodeCode: string
  /** 节点名称 */
  nodeName?: string
  /** 节点类型 */
  nodeType?: number
  /** 业务类型 */
  businessType?: string
  /** 业务 ID */
  businessId?: string
  /** 业务编号 */
  businessNo?: string
  /** 任务标题 */
  title?: string
  /** 指派人 ID */
  assignorId?: string
  /** 指派人姓名 */
  assignorName?: string
  /** 办理人类型 */
  assigneeType?: string
  /** 办理人 ID */
  assigneeId?: string
  /** 办理人姓名 */
  assigneeName?: string
  /** 执行类型 */
  performType?: string
  /** 审批总数 */
  approveCount?: number
  /** 已审批数 */
  approveFinished?: number
  /** PENDING / CLAIMED / COMPLETED / REJECTED / SKIPPED / CANCELLED / TIMEOUT / DELEGATED / FROZEN */
  taskStatus: 'PENDING' | 'CLAIMED' | 'COMPLETED' | 'REJECTED' | 'SKIPPED' | 'CANCELLED' | 'TIMEOUT' | 'DELEGATED' | 'FROZEN'
  /** 审批意见 */
  comment?: string
  /** 签收时间 */
  claimAt?: string
  /** 完成时间 */
  finishAt?: string
  /** 耗时（毫秒） */
  durationMs?: number
  /** 截止时间 */
  dueAt?: string
  /** 任务优先级（1-100，默认 50） */
  priority?: number
  /** 创建时间 */
  createTime?: string
}

/** 抄送 */
export interface FlowCcDTO {
  /** 抄送 ID */
  id: string
  /** 实例 ID */
  instanceId: string
  /** 任务 ID */
  taskId?: string
  /** 节点编码 */
  nodeCode: string
  /** 节点名称 */
  nodeName?: string
  /** 流程编码 */
  flowCode: string
  /** 流程名称 */
  flowName?: string
  /** 业务 Key */
  businessKey?: string
  /** 抄送用户 ID */
  ccUserId: string
  /** 抄送用户姓名 */
  ccUserName?: string
  /** CC_NODE / MANUAL_CC / AUTO_CC */
  ccType: 'CC_NODE' | 'MANUAL_CC' | 'AUTO_CC'
  /** 触发人 ID */
  triggerUserId?: string
  /** 触发人姓名 */
  triggerUserName?: string
  /** 标题 */
  title?: string
  /** 内容 */
  content?: string
  /** UNREAD / READ */
  readStatus: 'UNREAD' | 'READ'
  /** 阅读时间 */
  readAt?: string
  /** 创建时间 */
  createTime?: string
}

/** 抄送查询 */
export interface FlowCcQuery {
  /** UNREAD / READ */
  readStatus?: 'UNREAD' | 'READ'
  /** 流程编码 */
  flowCode?: string
  /** 页码 */
  pageNum?: number
  /** 每页条数 */
  pageSize?: number
}

/** 任务查询 */
export interface FlowTaskQuery {
  /** 办理人 ID */
  assigneeId?: string
  /** 业务类型 */
  businessType?: string
  /** 流程编码 */
  flowCode?: string
  /** 开始时间 */
  startTime?: string
  /** 结束时间 */
  endTime?: string
  /** 页码 */
  pageNum?: number
  /** 每页条数 */
  pageSize?: number
}

/** 启动流程 */
export interface FlowStartProcessDTO {
  /** 流程编码 */
  flowCode: string
  /** 业务类型 */
  businessType?: string
  /** 业务 Key */
  businessKey?: string
  /** 业务编号 */
  businessNo?: string
  /** 流程标题 */
  title?: string
  /** 流程变量 */
  variables?: Record<string, unknown>
  /** 发起人 ID */
  initiatorId?: string
}

/** 任务操作 DTO */
export interface FlowTaskOperateDTO {
  /** 任务 ID */
  taskId: string
  /** 审批意见 */
  comment?: string
  /** 目标用户 ID */
  targetUserId?: string
  /** 目标用户姓名 */
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
  /** 流程变量 */
  variables?: Record<string, unknown>
}

/** 流程部署 */
export interface FlowDeployDTO {
  /** 流程编码 */
  flowCode: string
  /** 流程名称 */
  flowName: string
  /** 分类 */
  category?: string
  /** 版本号 */
  version?: number
  /** BPMN XML */
  bpmnXml?: string
  /** JSON 模型 */
  jsonModel?: string
  /** 表单路径 */
  formPath?: string
}

/** 流程图 DTO */
export interface FlowDiagramDTO {
  /** 实例 ID */
  instanceId: string
  /** 流程编码 */
  flowCode: string
  /** 流程名称 */
  flowName?: string
  /** 状态 */
  status?: string
  /** 节点列表 */
  nodes: FlowDiagramNodeDTO[]
  /** 流转线列表 */
  skips: FlowDiagramSkipDTO[]
  /** 活跃节点编码列表 */
  activeNodeCodes: string[]
  /** 已完成节点编码列表 */
  completedNodeCodes: string[]
}

/** 流程图节点 DTO */
export interface FlowDiagramNodeDTO {
  /** 节点编码 */
  nodeCode: string
  /** 节点名称 */
  nodeName?: string
  /** 节点类型 */
  nodeType: number
  /** X 坐标 */
  x?: number
  /** Y 坐标 */
  y?: number
  /** 宽度 */
  width?: number
  /** 高度 */
  height?: number
  /** 权限标识 */
  permissionFlag?: string
  /** 扩展属性 */
  ext?: string
}

/** 流程图流转线 DTO */
export interface FlowDiagramSkipDTO {
  /** 流转线编码 */
  skipCode?: string
  /** 流转线名称 */
  skipName?: string
  /** 源节点编码 */
  sourceRef: string
  /** 目标节点编码 */
  targetRef: string
  /** 流转条件 */
  condition?: string
  /** 流转线类型 */
  skipType?: string
}

/** 时间线 DTO */
export interface FlowTimelineDTO {
  /** 实例 ID */
  instanceId: string
  /** 事件列表 */
  events: FlowTimelineEventDTO[]
}

/** 时间线事件 DTO */
export interface FlowTimelineEventDTO {
  /** 事件 ID */
  id?: string
  /** START / TASK_CREATED / TASK_COMPLETED / URGE / TRANSFER / DELEGATE / COUNTERSIGN / TIMEOUT / TERMINATE / COMPLETE / REJECT / SUSPEND / ACTIVATE / RECALL / JUMP / CC */
  eventType: 'START' | 'TASK_CREATED' | 'TASK_COMPLETED' | 'URGE' | 'TRANSFER' | 'DELEGATE' | 'COUNTERSIGN' | 'TIMEOUT' | 'TERMINATE' | 'COMPLETE' | 'REJECT' | 'SUSPEND' | 'ACTIVATE' | 'RECALL' | 'JUMP' | 'CC'
  /** 节点编码 */
  nodeCode?: string
  /** 节点名称 */
  nodeName?: string
  /** 用户 ID */
  userId?: string
  /** 用户姓名 */
  userName?: string
  /** 目标用户 ID */
  targetUserId?: string
  /** 目标用户姓名 */
  targetUserName?: string
  /** 备注 */
  comment?: string
  /** 操作 */
  action?: string
  /** 耗时（毫秒） */
  durationMs?: number
  /** 创建时间 */
  createdAt: string
}

/** 节点耗时统计 */
export interface FlowNodeDurationStatDTO {
  /** 流程编码 */
  flowCode: string
  /** 流程名称 */
  flowName?: string
  /** 节点编码 */
  nodeCode: string
  /** 节点名称 */
  nodeName?: string
  /** 实例数 */
  instanceCount: number
  /** 平均耗时（毫秒） */
  avgDurationMs: number
  /** 最大耗时（毫秒） */
  maxDurationMs: number
  /** 最小耗时（毫秒） */
  minDurationMs: number
  /** 超时数 */
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
  /** 实例 ID */
  id: string
  /** 流程编码 */
  flowCode: string
  /** 流程名称 */
  flowName?: string
  /** 标题 */
  title?: string
  /** 发起人姓名 */
  initiatorName?: string
  /** 状态 */
  status: string
  /** 当前节点名称 */
  currentNodeName?: string
  /** TIMEOUT / STUCK / CIRCULAR_APPROVAL / REPEATED_REJECT */
  anomalyType: 'TIMEOUT' | 'STUCK' | 'CIRCULAR_APPROVAL' | 'REPEATED_REJECT'
  /** 异常类型标签 */
  anomalyTypeLabel?: string
  /** 超时天数 */
  overdueDays?: number
  /** 开始时间 */
  startTime?: string
  /** 截止时间 */
  dueAt?: string
  /** RED / YELLOW / ORANGE */
  warnLevel: 'RED' | 'YELLOW' | 'ORANGE'
}

/** 实例趋势数据点 */
export interface InstanceTrendItemDTO {
  /** 日期 */
  date: string
  /** 新增数量 */
  newCount: number
  /** 完成数量 */
  completedCount: number
}

/** 审批人效率统计 */
export interface ApproverEfficiencyDTO {
  /** 用户 ID */
  userId: string
  /** 用户姓名 */
  userName: string
  /** 部门 */
  department?: string
  /** 完成审批数 */
  completedCount: number
  /** 平均耗时（毫秒） */
  avgDurationMs: number
  /** 总耗时（毫秒） */
  totalDurationMs: number
}

/** 流程类型分布 */
export interface FlowTypeDistributionDTO {
  /** 流程编码 */
  flowCode: string
  /** 流程名称 */
  flowName: string
  /** 数量 */
  count: number
  /** 占比 */
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
  definitionId: string
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
  /** 委托 ID */
  id: string
  /** 授权人 ID */
  ownerId: string
  /** 授权人姓名 */
  ownerName?: string
  /** 代理人 ID */
  delegateId: string
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
  /** 创建时间 */
  createTime?: string
  /** 更新时间 */
  updateTime?: string
}

/** 创建委托授权请求 */
export interface CreateDelegateAuthDTO {
  /** 代理人 ID */
  delegateId: string
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
  id: string
  /** 原授权人 ID */
  ownerId: string
  ownerName?: string
  /** 代理人 ID */
  delegateId: string
  delegateName?: string
  /** 任务 ID */
  taskId?: string
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
export type SlaStrategy = 'REMIND' | 'NOTIFY' | 'ESCALATE' | 'AUTO_PASS' | 'AUTO_REJECT'

/** SLA 超时任务 */
export interface SlaOverdueTaskDTO {
  /** 任务 ID */
  taskId: string
  /** 实例 ID */
  instanceId: string
  /** 流程编码 */
  flowCode: string
  /** 流程名称 */
  flowName?: string
  /** 节点编码 */
  nodeCode: string
  /** 节点名称 */
  nodeName?: string
  /** 办理人 ID */
  assigneeId?: string
  /** 办理人姓名 */
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
 *
 * P1-3 闭环语义：每个 action 都有明确终态，不允许"标记超时但流程卡死"。
 * - REMIND     中间态：仅发送催办通知，达 maxReminders 后切换到 finalAction（默认 NOTIFY）
 * - NOTIFY     最终态：通知管理员/升级人介入，任务保持活跃（等人工处理）
 * - ESCALATE   最终态：转办给 escalateUserId，任务保持活跃
 * - AUTO_PASS  最终态：系统自动通过，流程推进到下一节点
 * - AUTO_REJECT 最终态：系统自动驳回，流程终止
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
  escalateUserId?: string | null
  /** 自动操作备注（AUTO_PASS/AUTO_REJECT 时写入审批意见） */
  autoComment?: string
  /** P1-3: NOTIFY 通知目标用户 ID 列表（逗号分隔，action=NOTIFY 时使用，可空则降级到 escalateUserId/默认管理员） */
  notifyUserIds?: string | null
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
  id: string
  /** 流程定义 ID */
  definitionId: string
  /** 流程编码 */
  flowCode: string
  /** 流程名称 */
  flowName?: string
  /** 灰度策略 */
  strategy: CanaryStrategy
  /** 灰度比例（0-100） */
  percentage?: number
  /** 白名单用户 ID 列表 */
  whitelist?: string[]
  /** 灰度状态 */
  status: CanaryStatus
  /** 开始时间 */
  startTime?: string
  /** 结束时间 */
  endTime?: string
  createBy?: string
  createTime?: string
}

/** 灰度发布日志 */
export interface CanaryRolloutLogDTO {
  id: string
  /** 流程编码 */
  flowCode: string
  /** 流程名称 */
  flowName?: string
  /** 操作类型：PUBLISH / ADJUST / PROMOTE / ROLLBACK */
  action: string
  /** 灰度比例 */
  percentage?: number
  /** 操作人 */
  operatorId?: string
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
  whitelist?: string[]
}

// ===========================================
// P1-3: 版本管理 + 模拟运行
// ===========================================

/** 流程定义版本信息 */
export interface FlowVersionDTO {
  /** 版本号 */
  version: number
  /** 流程定义 ID */
  definitionId: string
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
  createBy?: string
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
  id: string
  tenantId?: string
  instanceId: string
  taskId?: string
  nodeCode?: string
  userId: string
  userName?: string
  content: string
  /** 评论类型：COMMENT / QUESTION / REPLY */
  type: string
  /** 父评论 ID（楼中楼回复） */
  parentId?: string
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

/** P3-3: 实例迁移入参 */
export interface InstanceMigrationDTO {
  sourceDefinitionId: string
  targetDefinitionId: string
  tenantId?: string
  /** 旧节点编码 → 新节点编码 映射 */
  nodeMapping?: Record<string, string>
  /** true=仅预览，false=执行迁移 */
  dryRun?: boolean
}

/** P3-3: 迁移明细 */
export interface MigrationDetail {
  instanceId: string
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
