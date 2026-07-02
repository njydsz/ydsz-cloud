/**
 * @file 工作流 API 客户端
 * @module api/workflow
 * @description 流程引擎 HTTP 接口封装（对标钉钉/飞书审批），含：
 *   - 流程定义 CRUD + 部署/发布/停用
 *   - 流程实例启动/查询/终止/挂起/激活/撤回
 *   - 待办/已办/我发起的/抄送我的查询
 *   - 任务操作：通过/驳回/转办/委派/加签/催办/签收/撤回/自由跳转/批量审批
 *   - 流程图、审批轨迹
 *   - 运营统计：节点耗时、超期任务、流程监控
 */
import http from '@/utils/request'
import type { ApiResponse, PageResult } from '@/utils/request'
import type {
  FlowDefinitionDTO,
  FlowInstanceDTO,
  FlowTaskDTO,
  FlowCcDTO,
  FlowCcQuery,
  FlowTaskQuery,
  FlowDiagramDTO,
  FlowTimelineDTO,
  FlowReplayStepDTO,
  FlowStartProcessDTO,
  FlowTaskOperateDTO,
  FlowDeployDTO,
  FlowNodeDurationStatDTO,
} from './types'

/** 引擎信息 */
export function engineInfo() {
  return http.get<ApiResponse<{ engineType: string; available: boolean }>>(
    '/workflow/engine/info',
  )
}

// ===========================================
// 流程定义
// ===========================================

/** 部署流程定义（XML/JSON） */
export function deployDefinition(payload: FlowDeployDTO) {
  return http.post<ApiResponse<number>>('/workflow/engine/definition/deploy', payload)
}

/** 发布流程定义（启用） */
export function publishDefinition(id: number) {
  return http.post<ApiResponse<null>>(`/workflow/engine/definition/${id}/publish`)
}

/** 停用流程定义 */
export function deactivateDefinition(id: number) {
  return http.post<ApiResponse<null>>(`/workflow/engine/definition/${id}/deactivate`)
}

/** 分页查询流程定义 */
export function pageDefinitions(params: {
  flowCode?: string
  flowName?: string
  category?: string
  status?: string
  pageNum?: number
  pageSize?: number
}) {
  return http.get<ApiResponse<PageResult<FlowDefinitionDTO>>>(
    '/workflow/engine/definition/page',
    { params },
  )
}

/** 获取流程定义详情 */
export function getDefinition(id: number) {
  return http.get<ApiResponse<FlowDefinitionDTO>>(
    `/workflow/engine/definition/${id}`,
  )
}

// ===========================================
// 流程实例
// ===========================================

/** 启动流程实例 */
export function startInstance(payload: FlowStartProcessDTO) {
  return http.post<ApiResponse<number>>('/workflow/engine/instance/start', payload)
}

/** 分页查询"我发起的" */
export function pageMyInstances(params: {
  flowCode?: string
  flowName?: string
  status?: string
  pageNum?: number
  pageSize?: number
}) {
  return http.get<ApiResponse<PageResult<FlowInstanceDTO>>>(
    '/workflow/engine/instance/my',
    { params },
  )
}

/** 流程实例详情 */
export function getInstance(id: number) {
  return http.get<ApiResponse<FlowInstanceDTO>>(
    `/workflow/engine/instance/${id}`,
  )
}

/** 流程实例列表（管理员监控） */
export function pageInstances(params: {
  flowCode?: string
  flowName?: string
  status?: string
  initiatorId?: number
  startTime?: string
  endTime?: string
  pageNum?: number
  pageSize?: number
}) {
  return http.get<ApiResponse<PageResult<FlowInstanceDTO>>>(
    '/workflow/engine/instance/page',
    { params },
  )
}

/** 终止流程实例 */
export function terminateInstance(id: number, reason: string) {
  return http.post<ApiResponse<null>>(
    `/workflow/engine/instance/${id}/terminate`,
    { reason },
  )
}

/** 挂起流程实例 */
export function suspendInstance(id: number) {
  return http.post<ApiResponse<null>>(`/workflow/engine/instance/${id}/suspend`)
}

/** 激活流程实例 */
export function activateInstance(id: number) {
  return http.post<ApiResponse<null>>(`/workflow/engine/instance/${id}/activate`)
}

/** 撤回流程实例 */
export function recallInstance(id: number) {
  return http.post<ApiResponse<null>>(`/workflow/engine/instance/${id}/recall`)
}

// ===========================================
// 任务操作
// ===========================================

/** 通过任务 */
export function passTask(payload: FlowTaskOperateDTO) {
  return http.post<ApiResponse<null>>('/workflow/engine/task/pass', payload)
}

/** 驳回任务 */
export function rejectTask(payload: FlowTaskOperateDTO) {
  return http.post<ApiResponse<null>>('/workflow/engine/task/reject', payload)
}

/**
 * P1-1: 查询任务所属实例经过的历史节点（驳回候选目标）
 */
export function rejectableNodes(taskId: number) {
  return http.get<ApiResponse<Array<{
    nodeCode: string
    nodeName: string
    firstFinishAt?: string
    visitCount?: number
  }>>>(`/workflow/engine/task/${taskId}/rejectable-nodes`)
}

/** 转办任务 */
export function transferTask(payload: FlowTaskOperateDTO) {
  return http.post<ApiResponse<null>>('/workflow/engine/task/transfer', payload)
}

/** 委派任务 */
export function delegateTask(payload: FlowTaskOperateDTO) {
  return http.post<ApiResponse<null>>('/workflow/engine/task/delegate', payload)
}

/** 前加签 */
export function countersignBefore(payload: FlowTaskOperateDTO) {
  return http.post<ApiResponse<null>>(
    '/workflow/engine/task/countersignBefore',
    payload,
  )
}

/** 后加签 */
export function countersignAfter(payload: FlowTaskOperateDTO) {
  return http.post<ApiResponse<null>>(
    '/workflow/engine/task/countersignAfter',
    payload,
  )
}

/** 减签 */
export function countersignRemove(payload: FlowTaskOperateDTO) {
  return http.post<ApiResponse<null>>(
    '/workflow/engine/task/countersignRemove',
    payload,
  )
}

/** 签收任务 */
export function claimTask(payload: FlowTaskOperateDTO) {
  return http.post<ApiResponse<null>>('/workflow/engine/task/claim', payload)
}

/** 取消签收 */
export function unclaimTask(payload: FlowTaskOperateDTO) {
  return http.post<ApiResponse<null>>('/workflow/engine/task/unclaim', payload)
}

/** 自由跳转 */
export function jumpTask(payload: FlowTaskOperateDTO) {
  return http.post<ApiResponse<null>>('/workflow/engine/task/jump', payload)
}

/** 催办 */
export function urgeTask(payload: {
  instanceId: number
  comment?: string
}) {
  return http.post<ApiResponse<string[]>>('/workflow/engine/task/urge', payload)
}

/** 批量审批 */
export function batchPass(payload: {
  taskIds: number[]
  comment?: string
}) {
  return http.post<ApiResponse<number>>('/workflow/engine/task/batchPass', payload)
}

/** GAP-P0: 暂存待审 */
export function saveDraft(payload: FlowTaskOperateDTO) {
  return http.post<ApiResponse<null>>('/workflow/engine/task/saveDraft', payload)
}

/** GAP-P0: 追加处理人 */
export function addApprover(payload: FlowTaskOperateDTO) {
  return http.post<ApiResponse<null>>('/workflow/engine/task/addApprover', payload)
}

/** GAP-P0: 已阅 */
export function markReadTask(payload: { taskId: number; userId: number }) {
  return http.post<ApiResponse<null>>(`/workflow/engine/task/${payload.taskId}/read`, payload)
}

/** GAP-P0: 沟通 */
export function communicateTask(payload: FlowTaskOperateDTO) {
  return http.post<ApiResponse<null>>('/workflow/engine/task/communicate', payload)
}

// ===========================================
// 待办/已办/我发起的/抄送我的
// ===========================================

/** 我的待办分页 */
export function pageTodoTasks(params: FlowTaskQuery) {
  return http.get<ApiResponse<PageResult<FlowTaskDTO>>>(
    '/workflow/engine/task/todo/search',
    { params },
  )
}

/** 我的已办分页 */
export function pageDoneTasks(params: FlowTaskQuery) {
  return http.get<ApiResponse<PageResult<FlowTaskDTO>>>(
    '/workflow/engine/task/done/search',
    { params },
  )
}

/** 我的待办（不分页，前 100 条，导航栏用） */
export function listTodoTop(limit = 100) {
  return http.get<ApiResponse<FlowTaskDTO[]>>(
    '/workflow/engine/task/todo/top',
    { params: { limit } },
  )
}

/** 抄送我的分页 */
export function pageCc(payload: FlowCcQuery) {
  return http.post<ApiResponse<PageResult<FlowCcDTO>>>(
    '/workflow/engine/cc/page',
    payload,
  )
}

/** 抄送未读数（导航栏徽标） */
export function ccUnreadCount() {
  return http.get<ApiResponse<number>>('/workflow/engine/cc/unread-count')
}

/** 抄送标记已读 */
export function ccMarkRead(id: number) {
  return http.post<ApiResponse<boolean>>(`/workflow/engine/cc/${id}/read`)
}

/** 抄送全部已读 */
export function ccMarkAllRead() {
  return http.post<ApiResponse<number>>('/workflow/engine/cc/read-all')
}

// ===========================================
// 流程图 / 审批轨迹
// ===========================================

/** 流程图（高亮当前节点） */
export function getDiagram(instanceId: number) {
  return http.get<ApiResponse<FlowDiagramDTO>>(
    `/workflow/engine/instance/${instanceId}/diagram`,
  )
}

/** 审批轨迹时间线 */
export function getTimeline(instanceId: number) {
  return http.get<ApiResponse<FlowTimelineDTO>>(
    `/workflow/engine/instance/${instanceId}/timeline`,
  )
}

/** P2-4: 流程回放步骤序列 */
export function getReplaySteps(instanceId: number) {
  return http.get<ApiResponse<FlowReplayStepDTO[]>>(
    `/workflow/engine/instance/${instanceId}/replay`,
  )
}

// ===========================================
// 运营统计 / 监控
// ===========================================

/** 节点平均耗时统计 */
export function nodeDurationStats(params: {
  flowCode?: string
  nodeCode?: string
  startTime?: string
  endTime?: string
}) {
  return http.get<ApiResponse<FlowNodeDurationStatDTO[]>>(
    '/workflow/engine/stats/node-duration',
    { params },
  )
}

/** 超期任务列表 */
export function listOverdueTasks(params: {
  assigneeId?: number
  pageNum?: number
  pageSize?: number
}) {
  return http.get<ApiResponse<PageResult<FlowTaskDTO>>>(
    '/workflow/engine/stats/overdue',
    { params },
  )
}

// ===========================================
// P2-1: 智能审批辅助
// ===========================================

/** P2-1: 推荐审批人（调用 Agent 服务） */
export function recommendApprovers(payload: {
  flowCode?: string
  nodeCode?: string
  businessType?: string
  businessId?: number
  businessTitle?: string
  requiredLevel?: string
  requiredRole?: string
  requiredDepartment?: string
  topN?: number
  candidates: Array<{
    userId: number
    name?: string
    department?: string
    level?: string
    role?: string
    activeTasks?: number
    avgApprovalMs?: number
  }>
}) {
  return http.post<ApiResponse<Array<Record<string, unknown>>>>(
    '/workflow/engine/ai/recommend-approvers',
    payload,
  )
}

/** P2-1: 起草审批意见（调用 Agent 服务） */
export function draftComment(payload: {
  action: 'PASS' | 'REJECT' | 'TRANSFER' | 'DELEGATE' | 'URGE'
  taskId?: number
  flowCode?: string
  flowName?: string
  nodeCode?: string
  nodeName?: string
  title?: string
  riskLevel?: 'RED' | 'YELLOW' | 'GREEN'
  overdueDays?: number
  tone?: 'FORMAL' | 'FRIENDLY'
  maxLength?: number
  historicalComments?: string[]
}) {
  return http.post<ApiResponse<{
    primary: string
    alternatives: string[]
    reasons: string[]
    action: string
    tone: string
  }>>(
    '/workflow/engine/ai/draft-comment',
    payload,
  )
}

/** P2-1: 检查 AI Agent 服务是否可用 */
export function aiStatus() {
  return http.get<ApiResponse<{ available: boolean; agents: string[] }>>(
    '/workflow/engine/ai/status',
  )
}

// ===========================================
// P2-2: 嵌入式审批（业务页内嵌审批面板）
// ===========================================

/** 嵌入式审批面板视图 */
export interface EmbeddedApprovalView {
  businessType: string
  businessId: string
  instance: Record<string, unknown> | null
  diagram: Record<string, unknown> | null
  currentTasks: EmbeddedCurrentTask[]
  history: EmbeddedHistoryItem[]
  myRole: 'INITIATOR' | 'APPROVER' | 'OBSERVER'
  actions: string[]
  aiAvailable: boolean
  canRecall: boolean
  finished: boolean
  message: string
}

export interface EmbeddedCurrentTask {
  taskId: number
  nodeCode: string
  nodeName: string
  nodeType: number
  assigneeType: string
  assigneeId: string
  assigneeName: string
  performType: string
  taskStatus: string
  createAt?: string
  dueAt?: string
  mine: boolean
}

export interface EmbeddedHistoryItem {
  type: string
  taskId?: number
  nodeCode?: string
  nodeName?: string
  assigneeId?: string
  assigneeName?: string
  action?: string
  comment?: string
  timestamp?: string
  taskStatus?: string
}

/** P2-2: 加载嵌入式审批面板 */
export function loadEmbeddedPanel(params: {
  businessType: string
  businessId: string | number
  userId?: number
}) {
  return http.get<ApiResponse<EmbeddedApprovalView>>(
    '/workflow/embedded/panel',
    { params },
  )
}

/** P2-2: 嵌入式快捷操作 */
export function embeddedQuickAction(payload: {
  businessType: string
  businessId: string | number
  action: 'PASS' | 'REJECT' | 'TRANSFER' | 'DELEGATE' | 'URGE' | 'WITHDRAW'
  userId?: number
  userName?: string
  comment?: string
  commentType?: string
  targetUserId?: number
  targetUserName?: string
  variables?: Record<string, unknown>
}) {
  return http.post<ApiResponse<null>>('/workflow/embedded/action', payload)
}

