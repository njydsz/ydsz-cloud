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

/** 规则定义 */
export interface RuleDefinition {
  /** 规则编码 */
  code: string
  /** 规则名称 */
  name: string
  /** 分类 */
  category?: string
  /** 分类路径 */
  categoryPath?: string
  /** 负责人 */
  owner?: string
  /** 规则描述 */
  description?: string
  /** 条件表达式 */
  conditionExpression?: string
  /** 严重度表达式 */
  severityExpression?: string
  /** 默认严重度 */
  defaultSeverity?: string
  /** 告警标题模板 */
  titleTemplate?: string
  /** 告警描述模板 */
  descriptionTemplate?: string
  /** 优先级 */
  priority?: number
  /** 是否启用 */
  enabled?: boolean
  /** 作用域 */
  scope?: string
  /** 互斥组 */
  mutexGroup?: string
  /** 版本号 */
  version?: number
  /** 租户 ID */
  tenantId?: string
  /** 环境标识 */
  environment?: string
  /** 状态 */
  status?: string
  /** 生效起始时间 */
  effectiveFrom?: string
  /** 生效结束时间 */
  effectiveTo?: string
  /** 审核人 */
  reviewedBy?: string
  /** 审核时间 */
  reviewedAt?: string
  /** 审核意见 */
  reviewComment?: string
  /** 灰度比例 */
  canaryRatio?: number
  /** 灰度条件列表 */
  canaryConditions?: string[]
  /** 灰度条件表达式 */
  canaryConditionExpression?: string
  /** 灰度严重度表达式 */
  canarySeverityExpression?: string
}

/** 规则执行结果 */
export interface RuleResult {
  /** 结果 ID */
  resultId?: string
  /** 规则编码 */
  ruleCode: string
  /** 规则名称 */
  ruleName?: string
  /** 分类 */
  category?: string
  /** 是否触发 */
  triggered: boolean
  /** 严重度 */
  severity?: string
  /** 告警标题 */
  title?: string
  /** 告警描述 */
  description?: string
  /** 当前值 */
  currentValue?: string
  /** 阈值 */
  threshold?: string
  /** 作用域 */
  scope?: string
  /** 触发时间 */
  triggeredAt?: string
  /** 是否可下钻 */
  drilldownAvailable?: boolean
  /** 执行耗时（毫秒） */
  elapsedMs?: number
  /** 是否灰度命中 */
  canary?: boolean
  /** 灰度分桶 */
  canaryBucket?: string
}

/** 规则版本 */
export interface RuleVersion {
  /** 版本 ID */
  id: string
  /** 规则编码 */
  ruleCode: string
  /** 版本号 */
  version: number
  /** 定义 JSON */
  definitionJson: string
  /** 变更说明 */
  changeDesc?: string
  /** 操作人 */
  operator?: string
  /** 创建时间 */
  createdAt?: string
}

/** 规则模板 */
export interface RuleTemplate {
  /** 模板 ID */
  id: string
  /** 模板名称 */
  name: string
  /** 分类 */
  category: string
  /** 模板描述 */
  description: string
  /** 条件模板 */
  conditionTemplate: string
  /** 严重度模板 */
  severityTemplate?: string
  /** 标签列表 */
  tags?: string[]
}

/** 规则引擎统计信息 */
export interface RuleEngineStats {
  /** 规则总数 */
  totalRules: number
  /** 活跃规则数 */
  activeRules: number
  /** 总评估次数 */
  totalEvaluations: number
  /** 总触发次数 */
  totalTriggered: number
  /** 总错误次数 */
  totalErrors: number
  /** 平均执行耗时（毫秒） */
  avgElapsedMs: number
  /** 触发次数最多的规则 */
  topTriggered?: Array<{ ruleCode: string; count: number }>
  /** 错误次数最多的规则 */
  topErrors?: Array<{ ruleCode: string; count: number }>
}

/** 执行轨迹 */
export interface ExecutionTrace {
  /** 轨迹 ID */
  traceId: string
  /** 规则编码 */
  ruleCode: string
  /** 是否触发 */
  triggered: boolean
  /** 条件结果 */
  conditionResult?: boolean
  /** 严重度 */
  severity?: string
  /** 执行耗时（毫秒） */
  elapsedMs: number
  /** 错误信息 */
  error?: string
  /** 时间戳 */
  timestamp: string
  /** 轨迹树 */
  traceTree?: any
}

/** 单条回放结果 */
export interface ReplayResult {
  /** 轨迹 ID */
  traceId: string
  /** 事实快照 */
  factsSnapshot?: Record<string, any>
  /** 历史执行轨迹列表 */
  historicalTraces?: ExecutionTrace[]
  /** 当前执行结果列表 */
  currentResults?: RuleResult[]
  /** 对比差异 */
  diff?: {
    /** 新增触发的规则 */
    added: string[]
    /** 不再触发的规则 */
    removed: string[]
    /** 无变化的规则 */
    unchanged: string[]
    /** 差异摘要 */
    summary: string
  }
  /** 错误信息 */
  errorMessage?: string
  /** 回放时间 */
  replayedAt?: string
}

/** 批量回放结果 */
export interface BatchReplayResult {
  /** 总回放次数 */
  totalReplayed: number
  /** 一致次数 */
  consistentCount: number
  /** 差异次数 */
  diffCount: number
  /** 跳过的次数 */
  skippedCount?: number
  /** 差异列表 */
  diffs: Array<{
    /** 轨迹 ID */
    traceId: string
    /** 规则编码 */
    ruleCode: string
    /** 规则名称 */
    ruleName?: string
    /** 历史是否触发 */
    historicalTriggered: boolean
    /** 当前是否触发 */
    currentTriggered: boolean
    /** 历史严重度 */
    historicalSeverity?: string
    /** 当前严重度 */
    currentSeverity?: string
    /** 差异类型 */
    diffType: string
  }>
  /** 汇总说明 */
  summary: string
  /** 回放时间 */
  replayedAt?: string
}

/** 影响分析预览结果 */
export interface ImpactPreviewResult {
  /** 规则编码 */
  ruleCode: string
  /** 条件表达式 */
  conditionExpression: string
  /** 总轨迹数 */
  totalTraces: number
  /** 历史触发次数 */
  historicalTriggeredCount: number
  /** 新触发次数 */
  newTriggeredCount: number
  /** 新增触发次数 */
  addedTriggeredCount: number
  /** 移除触发次数 */
  removedTriggeredCount: number
  /** 受影响的轨迹列表 */
  affectedTraces: Array<{
    /** 轨迹 ID */
    traceId: string
    /** 历史是否触发 */
    historicalTriggered: boolean
    /** 新是否触发 */
    newTriggered: boolean
    /** 历史严重度 */
    historicalSeverity?: string
    /** 新严重度 */
    newSeverity?: string
    /** 影响类型 */
    impactType: string
    /** 创建时间 */
    createdAt?: string
  }>
  /** 汇总说明 */
  summary: string
}

/** 审计日志条目 */
export interface AuditLogEntry {
  /** 日志 ID */
  id?: string
  /** 规则编码 */
  ruleCode?: string
  /** 规则名称 */
  ruleName?: string
  /** 操作动作 */
  action: string
  /** 操作人 */
  operator?: string
  /** 操作来源 */
  source?: string
  /** 变更说明 */
  changeDesc?: string
  /** 变更前快照 */
  beforeSnapshot?: Record<string, any>
  /** 变更后快照 */
  afterSnapshot?: Record<string, any>
  /** 字段级变更对比 */
  fieldDiffs?: Record<string, { field: string; oldValue?: string; newValue?: string }>
  /** 操作结果 */
  result?: string
  /** 错误信息 */
  errorMessage?: string
  /** 创建时间 */
  createdAt?: string
}

/** DSL 校验结果 */
export interface DslValidateResult {
  /** 是否合法 */
  valid: boolean
  /** 错误列表 */
  errors: string[]
  /** 规则数量 */
  ruleCount: number
  /** 规则链数量 */
  chainCount?: number
}

/** DSL 导出结果 */
export interface DslExportResult {
  /** 导出格式 */
  format: string
  /** 规则数量 */
  ruleCount?: number
  /** 规则编码 */
  ruleCode?: string
  /** 导出内容 */
  content: string
}

/** DSL 导入结果 */
export interface DslImportResult {
  /** 总规则数 */
  totalRules: number
  /** 成功数 */
  successCount: number
  /** 失败数 */
  failCount: number
  /** 已导入的规则编码列表 */
  importedCodes: string[]
  /** 错误列表 */
  errors: string[]
  /** 汇总说明 */
  summary: string
}

/** DSL 预览结果 */
export interface DslPreviewResult {
  /** 规则编码 */
  ruleCode: string
  /** 是否触发 */
  triggered: boolean
  /** 严重度 */
  severity?: string
  /** 告警标题 */
  title?: string
  /** 告警描述 */
  description?: string
  /** 错误信息 */
  error?: string
}

/** 归因分析报告 */
export interface AttributionReport {
  /** 规则编码 */
  ruleCode: string
  /** 规则名称 */
  ruleName?: string
  /** 是否触发 */
  triggered?: boolean
  /** 严重度 */
  severity?: string
  /** 分析摘要 */
  summary?: string
  /** 影响因素列表 */
  factors?: Array<{ name: string; value: any; contribution: string }>
  /** LLM 分析结果 */
  llmAnalysis?: string
  /** 优化建议 */
  recommendation?: string
}

/** 规则健康度评分 */
export interface RuleHealthScore {
  /** 规则编码 */
  ruleCode: string
  /** 规则名称 */
  ruleName?: string
  /** 总分 */
  totalScore: number
  /** 健康等级 */
  level: string
  /** 各维度评分 */
  dimensions?: Array<{ name: string; score: number; weight: number; desc: string }>
  /** 优化建议列表 */
  suggestions?: string[]
}

/** 阈值分析结果 */
export interface ThresholdAnalysis {
  /** 规则编码 */
  ruleCode: string
  /** 当前阈值 */
  currentThreshold?: number
  /** 建议阈值 */
  suggestedThreshold?: number
  /** 调整策略 */
  strategy?: string
  /** 调整原因 */
  reason?: string
  /** 置信度 */
  confidence?: number
  /** 预期提升 */
  improvement?: string
}

/** 回归测试报告 */
export interface RegressionReport {
  /** 总用例数 */
  total: number
  /** 通过数 */
  passed: number
  /** 失败数 */
  failed: number
  /** 通过率 */
  passRate: string
  /** 各用例结果 */
  caseResults?: Array<{
    /** 用例 ID */
    caseId: string
    /** 用例名称 */
    caseName: string
    /** 是否通过 */
    passed: boolean
    /** 实际触发的规则 */
    actualTriggered?: string[]
    /** 预期触发的规则 */
    expectedTriggered?: string[]
    /** 误报规则列表 */
    falsePositives?: string[]
    /** 漏报规则列表 */
    falseNegatives?: string[]
    /** 失败原因 */
    failureReason?: string
  }>
}

/** AB 测试报告 */
export interface ABTestReport {
  /** 策略 ID */
  policyId: string
  /** 规则编码 */
  ruleCode: string
  /** 主版本触发率 */
  mainTriggerRate: number
  /** 灰度版本触发率 */
  canaryTriggerRate: number
  /** 主版本平均耗时 */
  mainAvgElapsed: number
  /** 灰度版本平均耗时 */
  canaryAvgElapsed: number
  /** 主版本错误率 */
  mainErrorRate: number
  /** 灰度版本错误率 */
  canaryErrorRate: number
  /** 样本量 */
  sampleSize: number
  /** 推荐操作 */
  recommendation?: string
}

/** 规则测试用例 */
export interface RuleTestCase {
  /** 用例 ID */
  id: string
  /** 用例名称 */
  name: string
  /** 规则编码 */
  ruleCode?: string
  /** 事实数据 */
  factsData: Record<string, any>
  /** 预期触发规则 */
  expectedTriggered?: string[]
  /** 用例描述 */
  description?: string
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
}

/** 压测结果 */
export interface StressTestResult {
  /** 总请求数 */
  totalRequests: number
  /** 成功数 */
  successCount: number
  /** 错误数 */
  errorCount: number
  /** 平均延迟（毫秒） */
  avgLatencyMs: number
  /** P50 延迟（毫秒） */
  p50LatencyMs: number
  /** P95 延迟（毫秒） */
  p95LatencyMs: number
  /** P99 延迟（毫秒） */
  p99LatencyMs: number
  /** QPS */
  qps: number
}

/** 压测参数 */
export interface StressTestParams {
  /** 规则编码 */
  ruleCode?: string
  /** 事实模板 */
  factsTemplate: Record<string, any>
  /** 并发数 */
  concurrency: number
  /** 总请求数 */
  totalRequests: number
  /** 持续时间（秒） */
  duration: number
}

/** 变量定义 */
export interface VariableDefinition {
  /** 变量名 */
  name: string
  /** 变量类型 */
  type: string
  /** 变量描述 */
  description?: string
  /** 默认值 */
  defaultValue?: any
}

/** 评分卡定义 */
export interface ScorecardDefinition {
  /** 评分卡名称 */
  name: string
  /** 评分卡描述 */
  description?: string
  /** 评分维度列表 */
  dimensions: Array<{
    /** 维度名称 */
    name: string
    /** 维度标签 */
    label?: string
    /** 维度类型 */
    type: string
    /** 权重 */
    weight?: number
    /** 最小值 */
    min?: number
    /** 最大值 */
    max?: number
    /** 步长 */
    step?: number
    /** 默认值 */
    defaultValue?: any
    /** 选项列表 */
    options?: Array<{ label: string; value: any }>
    /** 分桶规则 */
    buckets: Array<{ condition: string; label?: string; score: number }>
  }>
  /** 等级划分 */
  gradeBands: Array<{ minScore: number; maxScore: number; label: string; grade: string }>
}

/** 审批记录 */
export interface ApprovalRecord {
  /** 审批记录 ID */
  id: string
  /** 规则编码 */
  ruleCode: string
  /** 规则名称 */
  ruleName: string
  /** 提交人 */
  submitter: string
  /** 提交时间 */
  submittedAt: string
  /** 变更类型 */
  changeType: string
  /** 审批状态 */
  status: string
  /** 变更说明 */
  changeDesc?: string
  /** 审批人 */
  approver?: string
  /** 审批时间 */
  approvedAt?: string
  /** 审批意见 */
  comment?: string
}

// ===== 规则 CRUD =====

/**
 * 分页查询规则列表
 *
 * 按分类、关键字、状态等条件分页查询规则定义。
 *
 * @param params 查询参数（分类 / 关键字 / 状态 / 页码 / 每页条数）
 * @returns 规则分页数据
 */
export const listRules = (params?: { category?: string; keyword?: string; status?: string; page?: number; size?: number }) =>
  request<PageData<RuleDefinition>>({ url: '/ruleEngine/rules', method: 'GET', params })

/**
 * 查询单个规则详情
 *
 * 根据规则编码获取规则完整定义。
 *
 * @param code 规则编码
 * @returns 规则定义
 */
export const getRule = (code: string) =>
  request<RuleDefinition>({ url: `/ruleEngine/rules/${code}`, method: 'GET' })

/**
 * 保存规则（新增或更新）
 *
 * 规则编码已存在时执行全量更新，不存在时新增。
 *
 * @param data 规则定义
 * @returns 保存后的规则定义
 */
export const saveRule = (data: RuleDefinition) =>
  request<RuleDefinition>({ url: '/ruleEngine/rules', method: 'POST', data })

/**
 * 删除规则
 *
 * 根据规则编码物理删除规则。
 *
 * @param code 规则编码
 * @returns void
 */
export const deleteRule = (code: string) =>
  request<void>({ url: `/ruleEngine/rules/${code}`, method: 'DELETE' })

/**
 * 启停规则
 *
 * 切换规则的启用/禁用状态。
 *
 * @param code 规则编码
 * @param enabled 是否启用
 * @returns void
 */
export const toggleRule = (code: string, enabled: boolean) =>
  request<void>({ url: `/ruleEngine/rules/${code}/toggle`, method: 'PUT', params: { enabled } })

/**
 * 批量启停规则
 *
 * 同时对多条规则执行启用/禁用操作。
 *
 * @param codes 规则编码列表
 * @param enabled 是否启用
 * @returns void
 */
export const batchToggle = (codes: string[], enabled: boolean) =>
  request<void>({ url: '/ruleEngine/rules/batchToggle', method: 'POST', data: { codes, enabled } })

/**
 * 批量调整优先级
 *
 * 同时对多条规则设置优先级。
 *
 * @param items 规则编码与优先级列表
 * @returns void
 */
export const batchPriority = (items: Array<{ code: string; priority: number }>) =>
  request<void>({ url: '/ruleEngine/rules/batchPriority', method: 'POST', data: { items } })

/**
 * 批量修改分类
 *
 * 将指定规则批量移至新分类。
 *
 * @param codes 规则编码列表
 * @param category 新分类
 * @returns void
 */
export const batchCategory = (codes: string[], category: string) =>
  request<void>({ url: '/ruleEngine/rules/batchCategory', method: 'POST', data: { codes, category } })

// ===== 版本管理 =====

/**
 * 查询规则版本列表
 *
 * 返回指定规则的全部历史版本。
 *
 * @param ruleCode 规则编码
 * @returns 版本列表
 */
export const listVersions = (ruleCode: string) =>
  request<RuleVersion[]>({ url: `/ruleEngine/rules/${ruleCode}/versions`, method: 'GET' })

/**
 * 回滚规则到指定版本
 *
 * 将规则定义回滚至历史版本，并生成新版本记录。
 *
 * @param ruleCode 规则编码
 * @param version 目标版本号
 * @param operator 操作人
 * @returns 回滚后的规则定义
 */
export const rollback = (ruleCode: string, version: number, operator?: string) =>
  request<RuleDefinition>({ url: `/ruleEngine/rules/${ruleCode}/rollback`, method: 'POST', params: { version }, headers: { 'X-Operator': operator || 'SYSTEM' } })

/**
 * 对比两个版本的差异
 *
 * 返回指定规则两个版本之间的字段级差异。
 *
 * @param ruleCode 规则编码
 * @param oldVersion 旧版本号
 * @param newVersion 新版本号
 * @returns 版本差异对象
 */
export const versionDiff = (ruleCode: string, oldVersion: number, newVersion: number) =>
  request<any>({ url: `/ruleEngine/rules/${ruleCode}/versionDiff`, method: 'GET', params: { oldVersion, newVersion } })

// ===== Dry-run =====

/**
 * 执行 Dry-run
 *
 * 使用给定事实数据模拟规则执行，不落库、不产生告警。
 *
 * @param ruleCode 规则编码（为空时执行所有规则）
 * @param facts 事实数据
 * @returns 规则执行结果列表
 */
export const dryRun = (ruleCode: string | null, facts: Record<string, any>) =>
  request<RuleResult[]>({ url: '/ruleEngine/rules/dryRun', method: 'POST', params: { ruleCode }, data: facts })

// ===== 表达式校验 =====

/**
 * 校验表达式合法性
 *
 * 对条件表达式或严重度表达式进行语法校验。
 *
 * @param expression 表达式
 * @returns 校验结果
 */
export const validateExpression = (expression: string) =>
  request<any>({ url: '/ruleEngine/rules/validateExpression', method: 'POST', params: { expression } })

/**
 * 预览表达式执行结果
 *
 * 使用给定事实数据预览表达式的计算结果。
 *
 * @param expression 表达式
 * @param facts 事实数据
 * @returns 表达式计算结果
 */
export const previewExpression = (expression: string, facts: Record<string, any>) =>
  request<any>({ url: '/ruleEngine/rules/previewExpression', method: 'POST', params: { expression }, data: facts })

/**
 * 获取表达式可用函数列表
 *
 * 返回规则引擎支持的全部内置函数。
 *
 * @returns 函数列表
 */
export const listFunctions = () =>
  request<any[]>({ url: '/ruleEngine/rules/functions', method: 'GET' })

// ===== 测试用例 =====

/**
 * 查询测试用例列表
 *
 * 按规则编码筛选测试用例。
 *
 * @param ruleCode 规则编码（可选）
 * @returns 测试用例列表
 */
export const getTestCases = (ruleCode?: string) =>
  request<RuleTestCase[]>({ url: '/ruleEngine/rules/testCases', method: 'GET', params: { ruleCode } })

/**
 * 保存测试用例
 *
 * 新增或更新测试用例。
 *
 * @param data 测试用例数据
 * @returns 保存后的测试用例
 */
export const saveTestCase = (data: Partial<RuleTestCase>) =>
  request<RuleTestCase>({ url: '/ruleEngine/rules/testCases', method: 'POST', data })

/**
 * 删除测试用例
 *
 * 根据 ID 物理删除测试用例。
 *
 * @param id 用例 ID
 * @returns void
 */
export const deleteTestCase = (id: string) =>
  request<void>({ url: `/ruleEngine/rules/testCases/${id}`, method: 'DELETE' })

/**
 * 批量运行测试用例
 *
 * 对指定测试用例执行批量回归测试。
 *
 * @param ids 用例 ID 列表
 * @returns 回归测试报告
 */
export const batchRunTestCases = (ids: number[]) =>
  request<RegressionReport>({ url: '/ruleEngine/rules/testCases/batchRun', method: 'POST', data: { ids } })

// ===== 执行轨迹 =====

/**
 * 查询执行轨迹
 *
 * 按规则编码和时间范围分页查询执行轨迹。
 *
 * @param params 查询参数（规则编码 / 页码 / 每页条数 / 起始时间 / 结束时间）
 * @returns 执行轨迹分页数据
 */
export const getTraces = (params: { ruleCode?: string; page?: number; size?: number; startTime?: string; endTime?: string }) =>
  request<PageData<ExecutionTrace>>({ url: '/ruleEngine/rules/traces', method: 'GET', params })

/**
 * 回放单条执行轨迹
 *
 * 使用轨迹中的历史事实数据重新执行规则，比对结果差异。
 *
 * @param traceId 轨迹 ID
 * @returns 回放结果
 */
export const replayTrace = (traceId: string) =>
  request<ReplayResult>({ url: `/ruleEngine/rules/traces/${traceId}/replay`, method: 'POST' })

/**
 * 批量回放执行轨迹
 *
 * 按时间范围批量回放历史执行轨迹，统计一致性。
 *
 * @param data 回放参数（起始时间 / 结束时间 / 规则编码 / 限制数量）
 * @returns 批量回放结果
 */
export const batchReplayTraces = (data: { startTime: string; endTime: string; ruleCode?: string; limit?: number }) =>
  request<BatchReplayResult>({ url: '/ruleEngine/rules/traces/batchReplay', method: 'POST', data })

// ===== 影响分析 =====

/**
 * 影响分析预览
 *
 * 模拟修改规则条件表达式后对历史轨迹的影响范围。
 *
 * @param ruleCode 规则编码
 * @param data 影响分析参数（条件表达式 / 严重度表达式 / 默认严重度 / 限制数量）
 * @returns 影响分析预览结果
 */
export const impactPreview = (ruleCode: string, data: {
  conditionExpression: string
  severityExpression?: string
  defaultSeverity?: string
  limit?: number
}) =>
  request<ImpactPreviewResult>({ url: `/ruleEngine/rules/${ruleCode}/impactPreview`, method: 'POST', data })

// ===== 审批 =====

/**
 * 提交审批
 *
 * 将规则变更提交审批，触发审批流程。
 *
 * @param ruleCode 规则编码
 * @param data 提交参数（变更说明 / 审批人）
 * @returns void
 */
export const submitForReview = (ruleCode: string, data: { changeDesc: string; reviewer?: string }) =>
  request<void>({ url: `/ruleEngine/rules/${ruleCode}/submitReview`, method: 'POST', data })

/**
 * 通过审批
 *
 * 审批通过指定审批记录。
 *
 * @param recordId 审批记录 ID
 * @param comment 审批意见
 * @returns void
 */
export const approveRule = (recordId: string, comment: string) =>
  request<void>({ url: `/ruleEngine/rules/approve/${recordId}`, method: 'POST', params: { comment } })

/**
 * 驳回审批
 *
 * 驳回指定审批记录。
 *
 * @param recordId 审批记录 ID
 * @param comment 驳回意见
 * @returns void
 */
export const rejectRule = (recordId: string, comment: string) =>
  request<void>({ url: `/ruleEngine/rules/reject/${recordId}`, method: 'POST', params: { comment } })

/**
 * 转办审批
 *
 * 将审批任务转交给他人处理。
 *
 * @param recordId 审批记录 ID
 * @param delegateTo 被转办人
 * @param comment 转办说明
 * @returns void
 */
export const delegateRule = (recordId: string, delegateTo: string, comment: string) =>
  request<void>({ url: `/ruleEngine/rules/delegate/${recordId}`, method: 'POST', params: { delegateTo, comment } })

/**
 * 查询审批记录列表
 *
 * 按审批状态筛选审批记录。
 *
 * @param status 审批状态（可选）
 * @returns 审批记录列表
 */
export const getApprovalRecords = (status?: string) =>
  request<ApprovalRecord[]>({ url: '/ruleEngine/rules/approvalRecords', method: 'GET', params: { status } })

// ===== 监控大盘 =====

/**
 * 获取大盘概览
 *
 * 返回规则引擎整体运行概览数据。
 *
 * @returns 概览数据
 */
export const getDashboardOverview = () =>
  request<any>({ url: '/ruleEngine/dashboard/overview', method: 'GET' })

/**
 * 获取 Top 规则
 *
 * 返回触发次数最多的规则排行。
 *
 * @returns Top 规则列表
 */
export const getDashboardTopRules = () =>
  request<any[]>({ url: '/ruleEngine/dashboard/topRules', method: 'GET' })

/**
 * 获取趋势数据
 *
 * 返回规则评估、触发、错误数的时间趋势。
 *
 * @returns 趋势数据数组
 */
export const getDashboardTrend = () =>
  request<Array<{ time: string; evals: number; triggered: number; errors: number }>>({ url: '/ruleEngine/dashboard/trend', method: 'GET' })

/**
 * 获取分布数据
 *
 * 返回规则按分类的分布统计。
 *
 * @returns 分布数据列表
 */
export const getDashboardDistribution = () =>
  request<any[]>({ url: '/ruleEngine/dashboard/distribution', method: 'GET' })

/**
 * 获取实时数据
 *
 * 返回规则引擎实时运行状态数据。
 *
 * @returns 实时数据
 */
export const getDashboardRealtime = () =>
  request<any>({ url: '/ruleEngine/dashboard/realtime', method: 'GET' })

// ===== 压测 =====

/**
 * 执行压测
 *
 * 对规则执行压力测试，评估性能指标。
 *
 * @param params 压测参数
 * @returns 压测结果
 */
export const stressTest = (params: StressTestParams) =>
  request<StressTestResult>({ url: '/ruleEngine/rules/stressTest', method: 'POST', data: params })

// ===== AB 测试 =====

/**
 * 获取 AB 测试报告
 *
 * 查询指定 AB 测试策略的对比报告。
 *
 * @param policyId 策略 ID
 * @returns AB 测试报告
 */
export const getABTestReport = (policyId: string) =>
  request<ABTestReport>({ url: `/ruleEngine/rules/abTest/${policyId}/report`, method: 'GET' })

/**
 * 回滚 AB 测试
 *
 * 将 AB 测试策略回滚至主版本。
 *
 * @param policyId 策略 ID
 * @param reason 回滚原因
 * @returns void
 */
export const rollbackABTest = (policyId: string, reason: string) =>
  request<void>({ url: `/ruleEngine/rules/abTest/${policyId}/rollback`, method: 'POST', params: { reason } })

// ===== 模板市场 =====

/**
 * 查询模板列表
 *
 * 按分类筛选规则模板。
 *
 * @param category 分类（可选）
 * @returns 模板列表
 */
export const listTemplates = (category?: string) =>
  request<RuleTemplate[]>({ url: '/ruleEngine/rules/templates', method: 'GET', params: { category } })

/**
 * 导入模板
 *
 * 从模板市场导入规则模板生成新规则。
 *
 * @param templateId 模板 ID
 * @param ruleCode 目标规则编码
 * @returns 生成的规则定义
 */
export const importTemplate = (templateId: string, ruleCode: string) =>
  request<RuleDefinition>({ url: '/ruleEngine/rules/templates/import', method: 'POST', params: { templateId, ruleCode } })

// ===== 规则包 =====

/**
 * 查询规则包列表
 *
 * 返回全部已安装的规则包。
 *
 * @returns 规则包列表
 */
export const listPacks = () =>
  request<any[]>({ url: '/ruleEngine/rules/packs', method: 'GET' })

/**
 * 安装规则包
 *
 * 安装指定规则包到当前环境。
 *
 * @param packId 规则包 ID
 * @returns 安装结果
 */
export const installPack = (packId: string) =>
  request<any>({ url: `/ruleEngine/rules/packs/${packId}/install`, method: 'POST' })

// ===== 变量管理 =====

/**
 * 查询变量列表
 *
 * 返回规则引擎中全部自定义变量。
 *
 * @returns 变量定义列表
 */
export const listVariables = () =>
  request<VariableDefinition[]>({ url: '/ruleEngine/variables', method: 'GET' })

/**
 * 保存变量
 *
 * 新增或更新自定义变量。
 *
 * @param data 变量定义
 * @returns 保存后的变量定义
 */
export const saveVariable = (data: VariableDefinition) =>
  request<VariableDefinition>({ url: '/ruleEngine/variables', method: 'POST', data })

/**
 * 删除变量
 *
 * 根据变量名物理删除自定义变量。
 *
 * @param name 变量名
 * @returns void
 */
export const deleteVariable = (name: string) =>
  request<void>({ url: `/ruleEngine/variables/${name}`, method: 'DELETE' })

// ===== DSL 管理（P3-6） =====

/**
 * 校验 DSL
 *
 * 对 DSL 内容进行语法和语义校验。
 *
 * @param data 校验参数（DSL 内容 / 格式）
 * @returns 校验结果
 */
export const validateDsl = (data: { content: string; format?: string }) =>
  request<DslValidateResult>({ url: '/ruleEngine/dsl/validate', method: 'POST', data })

/**
 * 解析 DSL
 *
 * 将 DSL 内容解析为结构化数据。
 *
 * @param data 解析参数（DSL 内容 / 格式）
 * @returns 解析结果
 */
export const parseDsl = (data: { content: string; format?: string }) =>
  request<any>({ url: '/ruleEngine/dsl/parse', method: 'POST', data })

/**
 * 导入 DSL
 *
 * 将 DSL 内容导入为规则定义。
 *
 * @param data 导入参数（DSL 内容 / 格式 / 操作人）
 * @returns 导入结果
 */
export const importDsl = (data: { content: string; format?: string; operator?: string }) =>
  request<DslImportResult>({ url: '/ruleEngine/dsl/import', method: 'POST', data })

/**
 * 导出全部 DSL
 *
 * 按分类导出全部规则为 DSL 格式。
 *
 * @param category 分类（可选）
 * @returns 导出结果
 */
export const exportAllDsl = (category?: string) =>
  request<DslExportResult>({ url: '/ruleEngine/dsl/export', method: 'GET', params: { category } })

/**
 * 导出单个 DSL
 *
 * 将指定规则导出为 DSL 格式。
 *
 * @param ruleCode 规则编码
 * @returns 导出结果
 */
export const exportSingleDsl = (ruleCode: string) =>
  request<DslExportResult>({ url: `/ruleEngine/dsl/export/${ruleCode}`, method: 'GET' })

/**
 * 预览 DSL
 *
 * 使用给定事实数据预览 DSL 规则的执行结果。
 *
 * @param data 预览参数（DSL 内容 / 格式 / 事实数据）
 * @returns 预览结果列表
 */
export const previewDsl = (data: { content: string; format?: string; facts?: Record<string, any> }) =>
  request<DslPreviewResult[]>({ url: '/ruleEngine/dsl/preview', method: 'POST', data })

// ===== 审计日志（P3-5） =====

/**
 * 查询最近审计日志
 *
 * 返回最近的审计日志条目。
 *
 * @param limit 返回条数（可选）
 * @returns 审计日志列表
 */
export const getRecentAuditLogs = (limit?: number) =>
  request<AuditLogEntry[]>({ url: '/ruleEngine/audit/recent', method: 'GET', params: { limit } })

/**
 * 按规则查询审计日志
 *
 * 查询指定规则的变更审计日志。
 *
 * @param ruleCode 规则编码
 * @param limit 返回条数（可选）
 * @returns 审计日志列表
 */
export const getAuditLogsByRule = (ruleCode: string, limit?: number) =>
  request<AuditLogEntry[]>({ url: `/ruleEngine/audit/byRule/${ruleCode}`, method: 'GET', params: { limit } })

/**
 * 按操作人查询审计日志
 *
 * 查询指定操作人的审计日志。
 *
 * @param operator 操作人
 * @param limit 返回条数（可选）
 * @returns 审计日志列表
 */
export const getAuditLogsByOperator = (operator: string, limit?: number) =>
  request<AuditLogEntry[]>({ url: '/ruleEngine/audit/byOperator', method: 'GET', params: { operator, limit } })

/**
 * 按操作查询审计日志
 *
 * 查询指定操作类型的审计日志。
 *
 * @param action 操作类型
 * @param limit 返回条数（可选）
 * @returns 审计日志列表
 */
export const getAuditLogsByAction = (action: string, limit?: number) =>
  request<AuditLogEntry[]>({ url: '/ruleEngine/audit/byAction', method: 'GET', params: { action, limit } })

/**
 * 按时间范围查询审计日志
 *
 * 查询指定时间范围内的审计日志。
 *
 * @param startTime 起始时间
 * @param endTime 结束时间
 * @param limit 返回条数（可选）
 * @returns 审计日志列表
 */
export const getAuditLogsByTimeRange = (startTime: string, endTime: string, limit?: number) =>
  request<AuditLogEntry[]>({ url: '/ruleEngine/audit/byTimeRange', method: 'GET', params: { startTime, endTime, limit } })

// ===== 归因分析（P3-3） =====

/**
 * 执行归因分析
 *
 * 对指定规则和事实数据进行归因分析，找出关键影响因素。
 *
 * @param ruleCode 规则编码
 * @param facts 事实数据
 * @returns 归因分析报告
 */
export const attribution = (ruleCode: string, facts: Record<string, any>) =>
  request<AttributionReport>({ url: `/ruleEngine/rules/${ruleCode}/attribution`, method: 'POST', data: facts })

/**
 * 批量归因分析
 *
 * 对多条执行轨迹进行批量归因分析。
 *
 * @param traceIds 轨迹 ID 列表
 * @returns 归因分析报告列表
 */
export const batchAttribution = (traceIds: string[]) =>
  request<AttributionReport[]>({ url: '/ruleEngine/rules/attribution/batch', method: 'POST', data: traceIds })

/**
 * 轨迹归因分析
 *
 * 对单条执行轨迹进行归因追溯分析。
 *
 * @param traceId 轨迹 ID
 * @returns 归因分析报告列表
 */
export const traceAttribution = (traceId: string) =>
  request<AttributionReport[]>({ url: `/ruleEngine/rules/traces/${traceId}/attribution`, method: 'GET' })

// ===== 自适应阈值（P3-4） =====

/**
 * 分析规则阈值
 *
 * 基于历史数据对指定规则进行阈值分析和建议。
 *
 * @param ruleCode 规则编码
 * @param days 分析天数（可选）
 * @returns 阈值分析结果列表
 */
export const analyzeThreshold = (ruleCode: string, days?: number) =>
  request<ThresholdAnalysis[]>({ url: `/ruleEngine/rules/${ruleCode}/thresholdAnalysis`, method: 'POST', params: { days } })

/**
 * 分析全部规则阈值
 *
 * 对所有规则进行批量阈值分析。
 *
 * @param days 分析天数（可选）
 * @returns 阈值分析结果列表
 */
export const analyzeAllThresholds = (days?: number) =>
  request<ThresholdAnalysis[]>({ url: '/ruleEngine/rules/thresholdAnalysis/all', method: 'POST', params: { days } })

/**
 * 应用阈值建议
 *
 * 将阈值分析建议应用到规则配置。
 *
 * @param ruleCode 规则编码
 * @param analysis 阈值分析结果
 * @returns 是否成功
 */
export const applyThreshold = (ruleCode: string, analysis: ThresholdAnalysis) =>
  request<boolean>({ url: `/ruleEngine/rules/${ruleCode}/applyThreshold`, method: 'POST', data: analysis })

/**
 * 获取阈值建议
 *
 * 查询指定规则的阈值优化建议。
 *
 * @param ruleCode 规则编码
 * @returns 阈值建议列表
 */
export const getThresholdSuggestions = (ruleCode: string) =>
  request<ThresholdAnalysis[]>({ url: `/ruleEngine/rules/${ruleCode}/thresholdSuggestions`, method: 'GET' })

// ===== 规则依赖 =====

/**
 * 查询规则的依赖项
 *
 * 返回指定规则依赖的其他规则列表。
 *
 * @param ruleCode 规则编码
 * @returns 依赖规则列表
 */
export const listDependencies = (ruleCode: string) =>
  request<any[]>({ url: `/ruleEngine/rules/${ruleCode}/dependencies`, method: 'GET' })

/**
 * 查询规则的被依赖项
 *
 * 返回依赖指定规则的其他规则列表。
 *
 * @param ruleCode 规则编码
 * @returns 被依赖规则列表
 */
export const listDependents = (ruleCode: string) =>
  request<any[]>({ url: `/ruleEngine/rules/${ruleCode}/dependents`, method: 'GET' })

/**
 * 级联禁用
 *
 * 禁用指定规则及其所有下游依赖规则。
 *
 * @param ruleCode 规则编码
 * @returns 被级联禁用的规则编码列表
 */
export const cascadingDisable = (ruleCode: string) =>
  request<string[]>({ url: `/ruleEngine/rules/${ruleCode}/cascadingDisable`, method: 'GET' })

// ===== 规则目录树 =====

/**
 * 获取规则分类树
 *
 * 返回规则分类的树形结构。
 *
 * @returns 分类树数据
 */
export const getCategoryTree = () =>
  request<any>({ url: '/ruleEngine/rules/categoryTree', method: 'GET' })

/**
 * 按分类路径查询规则
 *
 * 根据分类路径列出该分类下的所有规则。
 *
 * @param path 分类路径（可选）
 * @returns 规则定义列表
 */
export const listByCategoryPath = (path?: string) =>
  request<RuleDefinition[]>({ url: '/ruleEngine/rules/byCategoryPath', method: 'GET', params: { path } })

/**
 * 按负责人查询规则
 *
 * 列出指定负责人名下的所有规则。
 *
 * @param owner 负责人
 * @returns 规则定义列表
 */
export const listByOwner = (owner: string) =>
  request<RuleDefinition[]>({ url: '/ruleEngine/rules/byOwner', method: 'GET', params: { owner } })

// ===== 规则链画布 =====

/**
 * 获取规则链图
 *
 * 查询指定规则的规则链图数据。
 *
 * @param ruleCode 规则编码
 * @returns 规则链图数据
 */
export const getChainGraph = (ruleCode: string) =>
  request<any>({ url: `/ruleEngine/rules/${ruleCode}/graph`, method: 'GET' })

/**
 * 保存规则链图
 *
 * 保存或更新指定规则的规则链图配置。
 *
 * @param ruleCode 规则编码
 * @param graph 规则链图数据
 * @returns 保存后的规则链图数据
 */
export const saveChainGraph = (ruleCode: string, graph: any) =>
  request<any>({ url: `/ruleEngine/rules/${ruleCode}/graph`, method: 'POST', data: graph })

/**
 * 删除规则链图
 *
 * 删除指定规则的规则链图配置。
 *
 * @param ruleCode 规则编码
 * @returns void
 */
export const deleteChainGraph = (ruleCode: string) =>
  request<void>({ url: `/ruleEngine/rules/${ruleCode}/graph`, method: 'DELETE' })

/**
 * 校验规则链图
 *
 * 对规则链图进行合法性校验。
 *
 * @param graph 规则链图数据
 * @returns 校验错误列表
 */
export const validateChainGraph = (graph: any) =>
  request<any[]>({ url: '/ruleEngine/rules/graph/validate', method: 'POST', data: graph })

/**
 * Dry-run 规则链图
 *
 * 使用给定事实数据对规则链图进行模拟执行。
 *
 * @param ruleCode 规则编码
 * @param facts 事实数据
 * @returns 规则执行结果列表
 */
export const dryRunGraph = (ruleCode: string, facts: Record<string, any>) =>
  request<RuleResult[]>({ url: `/ruleEngine/rules/${ruleCode}/graph/dryRun`, method: 'POST', data: facts })

// ===== 表达式函数市场 =====

/**
 * 获取表达式函数列表
 *
 * 查询规则引擎支持的表达式函数市场。
 *
 * @param engine 引擎类型（可选）
 * @returns 函数列表
 */
export const getExpressionFunctions = (engine?: string) =>
  request<any[]>({ url: '/ruleEngine/rules/expressionFunctions', method: 'GET', params: { engine } })

// ===== 导入导出 =====

/**
 * 导出规则（JSON）
 *
 * 将所有规则导出为 JSON 格式。
 *
 * @returns 导出数据
 */
export const exportRules = () =>
  request<any>({ url: '/ruleEngine/rules/export', method: 'GET' })

/**
 * 导出规则（YAML）
 *
 * 将所有规则导出为 YAML 格式。
 *
 * @returns YAML 字符串
 */
export const exportRulesAsYaml = () =>
  request<string>({ url: '/ruleEngine/rules/export.yaml', method: 'GET' })

/**
 * 导入规则
 *
 * 从 JSON 数据批量导入规则。
 *
 * @param data 导入数据（规则数组）
 * @returns 导入结果
 */
export const importRules = (data: { rules: any[] }) =>
  request<any>({ url: '/ruleEngine/rules/import', method: 'POST', data })

// ===== 规则包管理（完整） =====

/**
 * 搜索规则包
 *
 * 按关键字搜索可用规则包。
 *
 * @param keyword 搜索关键字（可选）
 * @returns 规则包列表
 */
export const searchPacks = (keyword?: string) =>
  request<any[]>({ url: '/ruleEngine/rules/packs/search', method: 'GET', params: { keyword } })

/**
 * 获取最新规则包
 *
 * 查询指定规则包的最新版本。
 *
 * @param packCode 规则包编码
 * @returns 规则包最新版本
 */
export const getLatestPack = (packCode: string) =>
  request<any>({ url: `/ruleEngine/rules/packs/${packCode}/latest`, method: 'GET' })

/**
 * 查询规则包版本列表
 *
 * 返回指定规则包的全部历史版本。
 *
 * @param packCode 规则包编码
 * @returns 版本列表
 */
export const listPackVersions = (packCode: string) =>
  request<any[]>({ url: `/ruleEngine/rules/packs/${packCode}/versions`, method: 'GET' })

/**
 * 回滚规则包
 *
 * 将规则包回滚至指定历史版本。
 *
 * @param packCode 规则包编码
 * @param version 目标版本
 * @returns 回滚后的规则包
 */
export const rollbackPack = (packCode: string, version: string) =>
  request<any>({ url: `/ruleEngine/rules/packs/${packCode}/rollback`, method: 'POST', params: { version } })

/**
 * 对比规则包版本差异
 *
 * 对比指定规则包两个版本之间的差异。
 *
 * @param packCode 规则包编码
 * @param fromVersion 源版本
 * @param toVersion 目标版本
 * @returns 版本差异
 */
export const diffPack = (packCode: string, fromVersion: string, toVersion: string) =>
  request<any>({ url: `/ruleEngine/rules/packs/${packCode}/diff`, method: 'GET', params: { from: fromVersion, to: toVersion } })

/**
 * 检查规则包更新
 *
 * 检查已安装的规则包是否有可用更新。
 *
 * @returns 可更新的规则包列表
 */
export const checkPackUpdates = () =>
  request<any[]>({ url: '/ruleEngine/rules/packs/updateCheck', method: 'GET' })

/**
 * 批量更新规则包
 *
 * 对指定规则包执行批量更新操作。
 *
 * @param packCodes 待更新的规则包编码列表
 * @returns 更新结果列表
 */
export const batchUpdatePacks = (packCodes: string[]) =>
  request<any[]>({ url: '/ruleEngine/rules/packs/batchUpdate', method: 'POST', data: packCodes })