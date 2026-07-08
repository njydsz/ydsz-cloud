/**
 * @file DAG 编排引擎类型定义
 * @module api/agent/dag
 * @author ydsz-pmis-team
 * @since 1.0.0
 */

/** DAG 失败策略 */
export type DagFailureStrategy = 'ABORT' | 'CONTINUE' | 'RETRY'

/** DAG 实例状态 */
export type DagInstanceStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'PARTIAL'

/** DAG 节点状态 */
export type DagNodeStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SKIPPED'

/** DAG 节点定义 */
export interface DagNode {
  name: string
  agentType?: string
  dependencies?: string[]
  condition?: string
  inputs?: Record<string, unknown>
  failureStrategy?: DagFailureStrategy
  maxRetries?: number
  timeoutMs?: number
}

/** DAG 定义（前端构造） */
export interface DagDefinition {
  id?: string
  name: string
  description?: string
  nodes: DagNode[]
  failureStrategy?: DagFailureStrategy
  defaultTimeoutMs?: number
  maxRetries?: number
}

/** DAG 定义 DO（后端返回） */
export interface DagDefinitionDO extends DagDefinition {
  id: string
  tenantId?: string
  createdAt: string
  updatedAt: string
  createdBy?: string
}

/** DAG 执行 trace 条目 */
export interface DagTraceEntry {
  nodeName?: string | null
  event: string
  message: string
  detail?: unknown
  timestamp?: string
}

/** DAG 执行结果 */
export interface DagExecutionResult {
  instanceId: string
  definitionId: string
  dagName: string
  status: DagInstanceStatus
  nodeStatuses: Record<string, DagNodeStatus>
  nodeOutputs: Record<string, unknown>
  nodeErrors: Record<string, string>
  nodeRetryCounts: Record<string, number>
  traces: DagTraceEntry[]
  totalCostMs: number
  successCount: number
  failedCount: number
  skippedCount: number
  totalNodes: number
  note?: string
}

/** DAG 实例 DO */
export interface DagInstanceDO {
  id: string
  definitionId: string
  status: DagInstanceStatus
  totalCostMs?: number
  successCount?: number
  failedCount?: number
  skippedCount?: number
  totalNodes?: number
  inputs?: string
  outputs?: string
  errorMsg?: string
  createdAt: string
  updatedAt: string
}

/** DAG 节点实例 DO */
export interface DagNodeInstanceDO {
  id: string
  instanceId: string
  nodeName: string
  agentType?: string
  status: DagNodeStatus
  inputs?: string
  outputs?: string
  errorMsg?: string
  retryCount?: number
  costMs?: number
  startedAt?: string
  finishedAt?: string
}
