/**
 * @file 规则引擎 API
 * @description 提供规则 CRUD、版本管理、Dry-run、测试用例、审批、监控、
 *              决策表/树/评分卡/CEP/规则链管理、DSL 管理、审计日志、
 *              执行回放、影响分析、归因分析、健康度评分、自适应阈值、
 *              执行等接口；
 *              对应后端 RuleAdminController（/ruleEngine/rules）、
 *              RuleDslController（/ruleEngine/dsl）、
 *              RuleAuditLogController（/ruleEngine/audit）、
 *              RuleDashboardController（/ruleEngine/dashboard）。
 * @module api/rule-engine
 */

import { request } from '@/utils/request'
import type { PageData } from '@/types/api'

// ===== 类型定义 =====

export interface RuleDefinition {
  code: string
  name: string
  category?: string
  categoryPath?: string
  owner?: string
  description?: string
  conditionExpression?: string
  severityExpression?: string
  defaultSeverity?: string
  titleTemplate?: string
  descriptionTemplate?: string
  priority?: number
  enabled?: boolean
  scope?: string
  mutexGroup?: string
  version?: number
  tenantId?: string
  environment?: string
  status?: string
  effectiveFrom?: string
  effectiveTo?: string
  reviewedBy?: string
  reviewedAt?: string
  reviewComment?: string
  canaryRatio?: number
  canaryConditions?: string[]
  canaryConditionExpression?: string
  canarySeverityExpression?: string
}

export interface RuleResult {
  resultId?: string
  ruleCode: string
  ruleName?: string
  category?: string
  triggered: boolean
  severity?: string
  title?: string
  description?: string
  currentValue?: string
  threshold?: string
  scope?: string
  triggeredAt?: string
  drilldownAvailable?: boolean
  elapsedMs?: number
  canary?: boolean
  canaryBucket?: string
}

export interface RuleVersion {
  id: string
  ruleCode: string
  version: number
  definitionJson: string
  changeDesc?: string
  operator?: string
  createdAt?: string
}

export interface RuleTemplate {
  id: string
  name: string
  category: string
  description: string
  conditionTemplate: string
  severityTemplate?: string
  tags?: string[]
}

export interface RuleEngineStats {
  totalRules: number
  activeRules: number
  totalEvaluations: number
  totalTriggered: number
  totalErrors: number
  avgElapsedMs: number
  topTriggered?: Array<{ ruleCode: string; count: number }>
  topErrors?: Array<{ ruleCode: string; count: number }>
}

export interface ExecutionTrace {
  traceId: string
  ruleCode: string
  triggered: boolean
  conditionResult?: boolean
  severity?: string
  elapsedMs: number
  error?: string
  timestamp: string
  traceTree?: any
}

export interface ReplayResult {
  traceId: string
  factsSnapshot?: Record<string, any>
  historicalTraces?: ExecutionTrace[]
  currentResults?: RuleResult[]
  diff?: {
    added: string[]
    removed: string[]
    unchanged: string[]
    summary: string
  }
  errorMessage?: string
  replayedAt?: string
}

export interface BatchReplayResult {
  totalReplayed: number
  consistentCount: number
  diffCount: number
  skippedCount?: number
  diffs: Array<{
    traceId: string
    ruleCode: string
    ruleName?: string
    historicalTriggered: boolean
    currentTriggered: boolean
    historicalSeverity?: string
    currentSeverity?: string
    diffType: string
  }>
  summary: string
  replayedAt?: string
}

export interface ImpactPreviewResult {
  ruleCode: string
  conditionExpression: string
  totalTraces: number
  historicalTriggeredCount: number
  newTriggeredCount: number
  addedTriggeredCount: number
  removedTriggeredCount: number
  affectedTraces: Array<{
    traceId: string
    historicalTriggered: boolean
    newTriggered: boolean
    historicalSeverity?: string
    newSeverity?: string
    impactType: string
    createdAt?: string
  }>
  summary: string
}

export interface AuditLogEntry {
  id?: string
  ruleCode?: string
  ruleName?: string
  action: string
  operator?: string
  source?: string
  changeDesc?: string
  beforeSnapshot?: Record<string, any>
  afterSnapshot?: Record<string, any>
  fieldDiffs?: Record<string, { field: string; oldValue?: string; newValue?: string }>
  result?: string
  errorMessage?: string
  createdAt?: string
}

export interface DslValidateResult {
  valid: boolean
  errors: string[]
  ruleCount: number
  chainCount?: number
}

export interface DslExportResult {
  format: string
  ruleCount?: number
  ruleCode?: string
  content: string
}

export interface DslImportResult {
  totalRules: number
  successCount: number
  failCount: number
  importedCodes: string[]
  errors: string[]
  summary: string
}

export interface DslPreviewResult {
  ruleCode: string
  triggered: boolean
  severity?: string
  title?: string
  description?: string
  error?: string
}

export interface AttributionReport {
  ruleCode: string
  ruleName?: string
  triggered?: boolean
  severity?: string
  summary?: string
  factors?: Array<{ name: string; value: any; contribution: string }>
  llmAnalysis?: string
  recommendation?: string
}

export interface RuleHealthScore {
  ruleCode: string
  ruleName?: string
  totalScore: number
  level: string
  dimensions?: Array<{ name: string; score: number; weight: number; desc: string }>
  suggestions?: string[]
}

export interface ThresholdAnalysis {
  ruleCode: string
  currentThreshold?: number
  suggestedThreshold?: number
  strategy?: string
  reason?: string
  confidence?: number
  improvement?: string
}

export interface RegressionReport {
  total: number
  passed: number
  failed: number
  passRate: string
  caseResults?: Array<{
    caseId: string
    caseName: string
    passed: boolean
    actualTriggered?: string[]
    expectedTriggered?: string[]
    falsePositives?: string[]
    falseNegatives?: string[]
    failureReason?: string
  }>
}

export interface ABTestReport {
  policyId: string
  ruleCode: string
  mainTriggerRate: number
  canaryTriggerRate: number
  mainAvgElapsed: number
  canaryAvgElapsed: number
  mainErrorRate: number
  canaryErrorRate: number
  sampleSize: number
  recommendation?: string
}

export interface RuleTestCase {
  id: string
  name: string
  ruleCode?: string
  factsData: Record<string, any>
  expectedTriggered?: string[]
  description?: string
  createdAt?: string
  updatedAt?: string
}

export interface StressTestResult {
  totalRequests: number
  successCount: number
  errorCount: number
  avgLatencyMs: number
  p50LatencyMs: number
  p95LatencyMs: number
  p99LatencyMs: number
  qps: number
}

export interface StressTestParams {
  ruleCode?: string
  factsTemplate: Record<string, any>
  concurrency: number
  totalRequests: number
  duration: number
}

export interface VariableDefinition {
  name: string
  type: string
  description?: string
  defaultValue?: any
}

export interface ScorecardDefinition {
  name: string
  description?: string
  dimensions: Array<{
    name: string
    label?: string
    type: string
    weight?: number
    min?: number
    max?: number
    step?: number
    defaultValue?: any
    options?: Array<{ label: string; value: any }>
    buckets: Array<{ condition: string; label?: string; score: number }>
  }>
  gradeBands: Array<{ minScore: number; maxScore: number; label: string; grade: string }>
}

export interface ApprovalRecord {
  id: string
  ruleCode: string
  ruleName: string
  submitter: string
  submittedAt: string
  changeType: string
  status: string
  changeDesc?: string
  approver?: string
  approvedAt?: string
  comment?: string
}

// ===== 规则 CRUD =====

export const listRules = (params?: { category?: string; keyword?: string; status?: string; page?: number; size?: number }) =>
  request<PageData<RuleDefinition>>({ url: '/ruleEngine/rules', method: 'GET', params })

export const getRule = (code: string) =>
  request<RuleDefinition>({ url: `/ruleEngine/rules/${code}`, method: 'GET' })

export const saveRule = (data: RuleDefinition) =>
  request<RuleDefinition>({ url: '/ruleEngine/rules', method: 'POST', data })

export const deleteRule = (code: string) =>
  request<void>({ url: `/ruleEngine/rules/${code}`, method: 'DELETE' })

export const toggleRule = (code: string, enabled: boolean) =>
  request<void>({ url: `/ruleEngine/rules/${code}/toggle`, method: 'PUT', params: { enabled } })

export const batchToggle = (codes: string[], enabled: boolean) =>
  request<void>({ url: '/ruleEngine/rules/batchToggle', method: 'POST', data: { codes, enabled } })

export const batchPriority = (items: Array<{ code: string; priority: number }>) =>
  request<void>({ url: '/ruleEngine/rules/batchPriority', method: 'POST', data: { items } })

export const batchCategory = (codes: string[], category: string) =>
  request<void>({ url: '/ruleEngine/rules/batchCategory', method: 'POST', data: { codes, category } })

// ===== 版本管理 =====

export const listVersions = (ruleCode: string) =>
  request<RuleVersion[]>({ url: `/ruleEngine/rules/${ruleCode}/versions`, method: 'GET' })

export const rollback = (ruleCode: string, version: number, operator?: string) =>
  request<RuleDefinition>({ url: `/ruleEngine/rules/${ruleCode}/rollback`, method: 'POST', params: { version }, headers: { 'X-Operator': operator || 'SYSTEM' } })

export const versionDiff = (ruleCode: string, oldVersion: number, newVersion: number) =>
  request<any>({ url: `/ruleEngine/rules/${ruleCode}/versionDiff`, method: 'GET', params: { oldVersion, newVersion } })

// ===== Dry-run =====

export const dryRun = (ruleCode: string | null, facts: Record<string, any>) =>
  request<RuleResult[]>({ url: '/ruleEngine/rules/dryRun', method: 'POST', params: { ruleCode }, data: facts })

// ===== 表达式校验 =====

export const validateExpression = (expression: string) =>
  request<any>({ url: '/ruleEngine/rules/validateExpression', method: 'POST', params: { expression } })

export const previewExpression = (expression: string, facts: Record<string, any>) =>
  request<any>({ url: '/ruleEngine/rules/previewExpression', method: 'POST', params: { expression }, data: facts })

export const listFunctions = () =>
  request<any[]>({ url: '/ruleEngine/rules/functions', method: 'GET' })

// ===== 测试用例 =====

export const getTestCases = (ruleCode?: string) =>
  request<RuleTestCase[]>({ url: '/ruleEngine/rules/testCases', method: 'GET', params: { ruleCode } })

export const saveTestCase = (data: Partial<RuleTestCase>) =>
  request<RuleTestCase>({ url: '/ruleEngine/rules/testCases', method: 'POST', data })

export const deleteTestCase = (id: string) =>
  request<void>({ url: `/ruleEngine/rules/testCases/${id}`, method: 'DELETE' })

export const batchRunTestCases = (ids: number[]) =>
  request<RegressionReport>({ url: '/ruleEngine/rules/testCases/batchRun', method: 'POST', data: { ids } })

// ===== 执行轨迹 =====

export const getTraces = (params: { ruleCode?: string; page?: number; size?: number; startTime?: string; endTime?: string }) =>
  request<PageData<ExecutionTrace>>({ url: '/ruleEngine/rules/traces', method: 'GET', params })

export const replayTrace = (traceId: string) =>
  request<ReplayResult>({ url: `/ruleEngine/rules/traces/${traceId}/replay`, method: 'POST' })

export const batchReplayTraces = (data: { startTime: string; endTime: string; ruleCode?: string; limit?: number }) =>
  request<BatchReplayResult>({ url: '/ruleEngine/rules/traces/batchReplay', method: 'POST', data })

// ===== 影响分析 =====

export const impactPreview = (ruleCode: string, data: {
  conditionExpression: string
  severityExpression?: string
  defaultSeverity?: string
  limit?: number
}) =>
  request<ImpactPreviewResult>({ url: `/ruleEngine/rules/${ruleCode}/impactPreview`, method: 'POST', data })

// ===== 审批 =====

export const submitForReview = (ruleCode: string, data: { changeDesc: string; reviewer?: string }) =>
  request<void>({ url: `/ruleEngine/rules/${ruleCode}/submitReview`, method: 'POST', data })

export const approveRule = (recordId: string, comment: string) =>
  request<void>({ url: `/ruleEngine/rules/approve/${recordId}`, method: 'POST', params: { comment } })

export const rejectRule = (recordId: string, comment: string) =>
  request<void>({ url: `/ruleEngine/rules/reject/${recordId}`, method: 'POST', params: { comment } })

export const delegateRule = (recordId: string, delegateTo: string, comment: string) =>
  request<void>({ url: `/ruleEngine/rules/delegate/${recordId}`, method: 'POST', params: { delegateTo, comment } })

export const getApprovalRecords = (status?: string) =>
  request<ApprovalRecord[]>({ url: '/ruleEngine/rules/approvalRecords', method: 'GET', params: { status } })

// ===== 监控大盘 =====

export const getDashboardOverview = () =>
  request<any>({ url: '/ruleEngine/dashboard/overview', method: 'GET' })

export const getDashboardTopRules = () =>
  request<any[]>({ url: '/ruleEngine/dashboard/topRules', method: 'GET' })

export const getDashboardTrend = () =>
  request<Array<{ time: string; evals: number; triggered: number; errors: number }>>({ url: '/ruleEngine/dashboard/trend', method: 'GET' })

export const getDashboardDistribution = () =>
  request<any[]>({ url: '/ruleEngine/dashboard/distribution', method: 'GET' })

export const getDashboardRealtime = () =>
  request<any>({ url: '/ruleEngine/dashboard/realtime', method: 'GET' })

// ===== 压测 =====

export const stressTest = (params: StressTestParams) =>
  request<StressTestResult>({ url: '/ruleEngine/rules/stressTest', method: 'POST', data: params })

// ===== AB 测试 =====

export const getABTestReport = (policyId: string) =>
  request<ABTestReport>({ url: `/ruleEngine/rules/abTest/${policyId}/report`, method: 'GET' })

export const rollbackABTest = (policyId: string, reason: string) =>
  request<void>({ url: `/ruleEngine/rules/abTest/${policyId}/rollback`, method: 'POST', params: { reason } })

// ===== 模板市场 =====

export const listTemplates = (category?: string) =>
  request<RuleTemplate[]>({ url: '/ruleEngine/rules/templates', method: 'GET', params: { category } })

export const importTemplate = (templateId: string, ruleCode: string) =>
  request<RuleDefinition>({ url: '/ruleEngine/rules/templates/import', method: 'POST', params: { templateId, ruleCode } })

// ===== 规则包 =====

export const listPacks = () =>
  request<any[]>({ url: '/ruleEngine/rules/packs', method: 'GET' })

export const installPack = (packId: string) =>
  request<any>({ url: `/ruleEngine/rules/packs/${packId}/install`, method: 'POST' })

// ===== 变量管理 =====

export const listVariables = () =>
  request<VariableDefinition[]>({ url: '/ruleEngine/variables', method: 'GET' })

export const saveVariable = (data: VariableDefinition) =>
  request<VariableDefinition>({ url: '/ruleEngine/variables', method: 'POST', data })

export const deleteVariable = (name: string) =>
  request<void>({ url: `/ruleEngine/variables/${name}`, method: 'DELETE' })

// ===== DSL 管理（P3-6） =====

export const validateDsl = (data: { content: string; format?: string }) =>
  request<DslValidateResult>({ url: '/ruleEngine/dsl/validate', method: 'POST', data })

export const parseDsl = (data: { content: string; format?: string }) =>
  request<any>({ url: '/ruleEngine/dsl/parse', method: 'POST', data })

export const importDsl = (data: { content: string; format?: string; operator?: string }) =>
  request<DslImportResult>({ url: '/ruleEngine/dsl/import', method: 'POST', data })

export const exportAllDsl = (category?: string) =>
  request<DslExportResult>({ url: '/ruleEngine/dsl/export', method: 'GET', params: { category } })

export const exportSingleDsl = (ruleCode: string) =>
  request<DslExportResult>({ url: `/ruleEngine/dsl/export/${ruleCode}`, method: 'GET' })

export const previewDsl = (data: { content: string; format?: string; facts?: Record<string, any> }) =>
  request<DslPreviewResult[]>({ url: '/ruleEngine/dsl/preview', method: 'POST', data })

// ===== 审计日志（P3-5） =====

export const getRecentAuditLogs = (limit?: number) =>
  request<AuditLogEntry[]>({ url: '/ruleEngine/audit/recent', method: 'GET', params: { limit } })

export const getAuditLogsByRule = (ruleCode: string, limit?: number) =>
  request<AuditLogEntry[]>({ url: `/ruleEngine/audit/byRule/${ruleCode}`, method: 'GET', params: { limit } })

export const getAuditLogsByOperator = (operator: string, limit?: number) =>
  request<AuditLogEntry[]>({ url: '/ruleEngine/audit/byOperator', method: 'GET', params: { operator, limit } })

export const getAuditLogsByAction = (action: string, limit?: number) =>
  request<AuditLogEntry[]>({ url: '/ruleEngine/audit/byAction', method: 'GET', params: { action, limit } })

export const getAuditLogsByTimeRange = (startTime: string, endTime: string, limit?: number) =>
  request<AuditLogEntry[]>({ url: '/ruleEngine/audit/byTimeRange', method: 'GET', params: { startTime, endTime, limit } })

// ===== 归因分析（P3-3） =====

export const attribution = (ruleCode: string, facts: Record<string, any>) =>
  request<AttributionReport>({ url: `/ruleEngine/rules/${ruleCode}/attribution`, method: 'POST', data: facts })

export const batchAttribution = (traceIds: string[]) =>
  request<AttributionReport[]>({ url: '/ruleEngine/rules/attribution/batch', method: 'POST', data: traceIds })

export const traceAttribution = (traceId: string) =>
  request<AttributionReport[]>({ url: `/ruleEngine/rules/traces/${traceId}/attribution`, method: 'GET' })

// ===== 自适应阈值（P3-4） =====

export const analyzeThreshold = (ruleCode: string, days?: number) =>
  request<ThresholdAnalysis[]>({ url: `/ruleEngine/rules/${ruleCode}/thresholdAnalysis`, method: 'POST', params: { days } })

export const analyzeAllThresholds = (days?: number) =>
  request<ThresholdAnalysis[]>({ url: '/ruleEngine/rules/thresholdAnalysis/all', method: 'POST', params: { days } })

export const applyThreshold = (ruleCode: string, analysis: ThresholdAnalysis) =>
  request<boolean>({ url: `/ruleEngine/rules/${ruleCode}/applyThreshold`, method: 'POST', data: analysis })

export const getThresholdSuggestions = (ruleCode: string) =>
  request<ThresholdAnalysis[]>({ url: `/ruleEngine/rules/${ruleCode}/thresholdSuggestions`, method: 'GET' })

// ===== 规则依赖 =====

export const listDependencies = (ruleCode: string) =>
  request<any[]>({ url: `/ruleEngine/rules/${ruleCode}/dependencies`, method: 'GET' })

export const listDependents = (ruleCode: string) =>
  request<any[]>({ url: `/ruleEngine/rules/${ruleCode}/dependents`, method: 'GET' })

export const cascadingDisable = (ruleCode: string) =>
  request<string[]>({ url: `/ruleEngine/rules/${ruleCode}/cascadingDisable`, method: 'GET' })

// ===== 规则目录树 =====

export const getCategoryTree = () =>
  request<any>({ url: '/ruleEngine/rules/categoryTree', method: 'GET' })

export const listByCategoryPath = (path?: string) =>
  request<RuleDefinition[]>({ url: '/ruleEngine/rules/byCategoryPath', method: 'GET', params: { path } })

export const listByOwner = (owner: string) =>
  request<RuleDefinition[]>({ url: '/ruleEngine/rules/byOwner', method: 'GET', params: { owner } })

// ===== 规则链画布 =====

export const getChainGraph = (ruleCode: string) =>
  request<any>({ url: `/ruleEngine/rules/${ruleCode}/graph`, method: 'GET' })

export const saveChainGraph = (ruleCode: string, graph: any) =>
  request<any>({ url: `/ruleEngine/rules/${ruleCode}/graph`, method: 'POST', data: graph })

export const deleteChainGraph = (ruleCode: string) =>
  request<void>({ url: `/ruleEngine/rules/${ruleCode}/graph`, method: 'DELETE' })

export const validateChainGraph = (graph: any) =>
  request<any[]>({ url: '/ruleEngine/rules/graph/validate', method: 'POST', data: graph })

export const dryRunGraph = (ruleCode: string, facts: Record<string, any>) =>
  request<RuleResult[]>({ url: `/ruleEngine/rules/${ruleCode}/graph/dryRun`, method: 'POST', data: facts })

// ===== 表达式函数市场 =====

export const getExpressionFunctions = (engine?: string) =>
  request<any[]>({ url: '/ruleEngine/rules/expressionFunctions', method: 'GET', params: { engine } })

// ===== 导入导出 =====

export const exportRules = () =>
  request<any>({ url: '/ruleEngine/rules/export', method: 'GET' })

export const exportRulesAsYaml = () =>
  request<string>({ url: '/ruleEngine/rules/export.yaml', method: 'GET' })

export const importRules = (data: { rules: any[] }) =>
  request<any>({ url: '/ruleEngine/rules/import', method: 'POST', data })

// ===== 规则包管理（完整） =====

export const searchPacks = (keyword?: string) =>
  request<any[]>({ url: '/ruleEngine/rules/packs/search', method: 'GET', params: { keyword } })

export const getLatestPack = (packCode: string) =>
  request<any>({ url: `/ruleEngine/rules/packs/${packCode}/latest`, method: 'GET' })

export const listPackVersions = (packCode: string) =>
  request<any[]>({ url: `/ruleEngine/rules/packs/${packCode}/versions`, method: 'GET' })

export const rollbackPack = (packCode: string, version: string) =>
  request<any>({ url: `/ruleEngine/rules/packs/${packCode}/rollback`, method: 'POST', params: { version } })

export const diffPack = (packCode: string, fromVersion: string, toVersion: string) =>
  request<any>({ url: `/ruleEngine/rules/packs/${packCode}/diff`, method: 'GET', params: { from: fromVersion, to: toVersion } })

export const checkPackUpdates = () =>
  request<any[]>({ url: '/ruleEngine/rules/packs/updateCheck', method: 'GET' })

export const batchUpdatePacks = (packCodes: string[]) =>
  request<any[]>({ url: '/ruleEngine/rules/packs/batchUpdate', method: 'POST', data: packCodes })
