/**
 * @file 规则引擎 API 接口封装
 * @description 提供规则定义的增删改查、启停切换、版本管理与回滚、Dry-run 仿真、
 *              表达式校验、执行统计、模板市场导入及 AI 辅助生成等能力，
 *              对应后端规则引擎 Controller（/execution/rules）。
 *              URL 前缀使用 /execution 是因为后端 execution 服务的 gateway 路由前缀。
 * @module api/execution/rule-engine
 */
import { request } from '@/utils/request'

/**
 * 规则定义
 */
export interface RuleDefinition {
  /** 规则编码（唯一标识） */
  code: string
  /** 规则名称 */
  name: string
  /** 规则类别，如 BUDGET / RISK / EVM */
  category: string
  /** 分类路径（P1-9 规则目录树），如 "finance/credit/loan" */
  categoryPath?: string
  /** 责任人（P1-9），工号/用户名 */
  owner?: string
  /** 规则描述 */
  description?: string
  /** 条件表达式（Aviator），返回 boolean 决定是否触发 */
  conditionExpression: string
  /** 严重度表达式（Aviator），返回 RED/YELLOW/NORMAL */
  severityExpression?: string
  /** 默认严重度（当 severityExpression 为空或求值失败时使用） */
  defaultSeverity: string
  /** 标题模板（支持 ${var} 变量占位） */
  titleTemplate?: string
  /** 描述模板（支持 ${var} 变量占位） */
  descriptionTemplate?: string
  /** 优先级（数值越小越先执行） */
  priority: number
  /** 是否启用 */
  enabled: boolean
  /** 作用域（可选，限定规则生效范围） */
  scope?: string
  /** 版本号 */
  version: number
}

/**
 * 规则目录树节点（P1-9）
 */
export interface CategoryNode {
  /** 节点名称（最后一段，如 "credit"） */
  name: string
  /** 完整路径（如 "/finance/credit"） */
  path: string
  /** 节点深度（ROOT=0，一级=1） */
  depth: number
  /** 是否根节点（虚拟 ROOT） */
  root: boolean
  /** 当前节点及子节点下规则数 */
  ruleCount: number
  /** 责任人列表（去重） */
  owners: string[]
  /** 子节点 */
  children: CategoryNode[]
}

/**
 * 规则执行结果（Dry-run / 真实执行）
 */
export interface RuleResult {
  /** 规则编码 */
  ruleCode: string
  /** 规则名称 */
  ruleName: string
  /** 规则类别 */
  category: string
  /** 是否触发 */
  triggered: boolean
  /** 严重度（触发后求值得到） */
  severity?: string
  /** 预警标题（模板渲染后） */
  title?: string
  /** 预警描述（模板渲染后） */
  description?: string
  /** 触发时间 */
  triggeredAt?: string
  /** 单条规则执行耗时（毫秒） */
  elapsedMs?: number
}

/**
 * 规则版本历史记录
 */
export interface RuleVersion {
  /** 版本记录 ID */
  id: number
  /** 规则编码 */
  ruleCode: string
  /** 版本号 */
  version: number
  /** 变更说明 */
  changeDesc?: string
  /** 操作人 */
  operator: string
  /** 创建时间 */
  createdAt: string
}

/**
 * 规则模板（模板市场）
 */
export interface RuleTemplate {
  /** 模板 ID */
  id: number
  /** 模板编码 */
  templateCode: string
  /** 模板名称 */
  templateName: string
  /** 模板类别 */
  category: string
  /** 模板描述 */
  description?: string
  /** 条件表达式 */
  conditionExpression: string
  /** 严重度表达式 */
  severityExpression?: string
  /** 默认严重度 */
  defaultSeverity: string
  /** 适用行业 */
  industry?: string
  /** 标签（逗号分隔） */
  tags?: string
}

/**
 * 规则引擎执行统计
 */
export interface RuleEngineStats {
  /** 总评估次数 */
  totalEvaluations: number
  /** 总触发次数 */
  totalTriggered: number
  /** 总错误次数 */
  totalErrors: number
  /** 总耗时（毫秒） */
  totalElapsedMs: number
  /** 按规则编码聚合的统计 */
  perRuleStats: Record<
    string,
    { executions: number; triggered: number; errors: number; totalElapsedMs: number }
  >
}

// ==================== 规则 CRUD ====================

/**
 * 查询全部规则列表
 * @returns 规则定义列表
 */
export const listRules = () =>
  request<RuleDefinition[]>({ url: '/execution/rules', method: 'GET' })

/**
 * 查询单条规则详情
 * @param ruleCode 规则编码
 * @returns 规则定义详情
 */
export const getRule = (ruleCode: string) =>
  request<RuleDefinition>({ url: `/execution/rules/${ruleCode}`, method: 'GET' })

/**
 * 新建 / 更新规则（按 code 幂等），同时记录版本变更
 * @param data 规则定义
 * @param changeDesc 变更说明（可选，记录到版本历史）
 * @returns 保存后的规则定义
 */
export const saveRule = (data: RuleDefinition, changeDesc?: string) =>
  request<RuleDefinition>({
    url: '/execution/rules',
    method: 'POST',
    data,
    params: { changeDesc },
  })

/**
 * 切换规则启停状态
 * @param ruleCode 规则编码
 * @param enabled 是否启用
 */
export const toggleRule = (ruleCode: string, enabled: boolean) =>
  request<void>({
    url: `/execution/rules/${ruleCode}/toggle`,
    method: 'PUT',
    params: { enabled },
  })

// ==================== 版本管理 ====================

/**
 * 查询指定规则的版本历史
 * @param ruleCode 规则编码
 * @returns 版本记录列表（按版本号倒序）
 */
export const listVersions = (ruleCode: string) =>
  request<RuleVersion[]>({
    url: `/execution/rules/${ruleCode}/versions`,
    method: 'GET',
  })

/**
 * 回滚规则到指定版本
 * @param ruleCode 规则编码
 * @param version 目标版本号
 * @returns 回滚后的规则定义
 */
export const rollbackRule = (ruleCode: string, version: number) =>
  request<RuleDefinition>({
    url: `/execution/rules/${ruleCode}/rollback`,
    method: 'POST',
    params: { version },
  })

// ==================== Dry-run 仿真 ====================

/**
 * Dry-run 仿真执行（不产生真实预警）
 * @param ruleCode 指定规则编码；传 null 则对全部启用规则求值
 * @param facts 事实数据（键值对）
 * @returns 各规则的触发结果列表
 */
export const dryRun = (ruleCode: string | null, facts: Record<string, unknown>) =>
  request<RuleResult[]>({
    url: '/execution/rules/dry-run',
    method: 'POST',
    params: { ruleCode },
    data: facts,
  })

// ==================== 表达式校验 ====================

/**
 * 校验表达式语法是否合法
 * @param expression Aviator 表达式
 * @returns 是否合法
 */
export const validateExpression = (expression: string) =>
  request<boolean>({
    url: '/execution/rules/validate',
    method: 'GET',
    params: { expression },
  })

/**
 * 表达式求值预览结果
 */
export interface ExpressionPreviewResult {
  expression: string
  value: string
  javaType: string
  boolean?: boolean | null
  elapsedMs: number
  error?: string
  isSuccess: boolean
}

/**
 * 表达式求值预览（P2-8）
 * @param expression 表达式
 * @param facts 样例事实数据
 * @returns 求值结果
 */
export const previewExpression = (expression: string, facts: Record<string, unknown>) =>
  request<ExpressionPreviewResult>({
    url: '/execution/rules/expression-preview',
    method: 'POST',
    params: { expression },
    data: facts,
  })

// ==================== 执行统计 ====================

/**
 * 查询规则引擎执行统计
 * @returns 全局 + 按规则的执行统计
 */
export const getStats = () =>
  request<RuleEngineStats>({ url: '/execution/rules/stats', method: 'GET' })

// ==================== 模板市场 ====================

/**
 * 查询全部规则模板
 * @returns 模板列表
 */
export const listTemplates = () =>
  request<RuleTemplate[]>({ url: '/execution/rules/templates', method: 'GET' })

/**
 * 按类别查询规则模板
 * @param category 模板类别
 * @returns 模板列表
 */
export const listTemplatesByCategory = (category: string) =>
  request<RuleTemplate[]>({
    url: `/execution/rules/templates/category/${category}`,
    method: 'GET',
  })

/**
 * 从模板市场一键导入规则
 * @param templateCode 模板编码
 * @returns 导入生成的规则定义
 */
export const importTemplate = (templateCode: string) =>
  request<RuleDefinition>({
    url: `/execution/rules/templates/${templateCode}/import`,
    method: 'POST',
  })

// ==================== AI 辅助生成 ====================

/**
 * AI 辅助生成规则（仅预览，不保存）
 * @param description 自然语言规则描述
 * @param availableFields 可用字段列表（辅助 AI 生成合法表达式）
 * @returns AI 生成的规则定义
 */
export const aiGenerate = (description: string, availableFields?: string[]) =>
  request<RuleDefinition>({
    url: '/execution/rules/ai-generate',
    method: 'POST',
    data: { description, availableFields },
  })

/**
 * AI 辅助生成规则并保存
 * @param description 自然语言规则描述
 * @param availableFields 可用字段列表
 * @returns 生成并保存后的规则定义
 */
export const aiGenerateAndSave = (description: string, availableFields?: string[]) =>
  request<RuleDefinition>({
    url: '/execution/rules/ai-generate-and-save',
    method: 'POST',
    data: { description, availableFields },
  })

// ==================== 冲突检测 ====================

/** 规则冲突信息 */
export interface RuleConflict {
  ruleA: string
  ruleAName: string
  ruleB: string
  ruleBName: string
  overlapFields: string[]
  severity: 'high' | 'medium' | 'low'
}

/**
 * 检测规则冲突
 * @returns 冲突规则对列表
 */
export const detectConflicts = () =>
  request<RuleConflict[]>({ url: '/execution/rules/conflicts', method: 'GET' })

// ==================== 测试用例管理 ====================

/** 测试用例 */
export interface RuleTestCase {
  id?: number
  /** 测试用例名称 */
  name: string
  /** 关联规则编码（可选，null 表示通用测试用例） */
  ruleCode?: string
  /** 事实数据 JSON */
  factsData: Record<string, unknown>
  /** 预期触发规则编码列表 */
  expectedTriggered?: string[]
  /** 描述 */
  description?: string
  createdAt?: string
  updatedAt?: string
}

/**
 * 查询所有测试用例
 */
export const listTestCases = (ruleCode?: string) =>
  request<RuleTestCase[]>({
    url: '/execution/rules/test-cases',
    method: 'GET',
    params: { ruleCode },
  })

/**
 * 保存测试用例
 */
export const saveTestCase = (data: RuleTestCase) =>
  request<RuleTestCase>({
    url: '/execution/rules/test-cases',
    method: 'POST',
    data,
  })

/**
 * 删除测试用例
 */
export const deleteTestCase = (id: number) =>
  request<void>({
    url: `/execution/rules/test-cases/${id}`,
    method: 'DELETE',
  })

/** 回归测试单个用例结果 */
export interface RegressionCaseResult {
  testCaseId: number
  testCaseName: string
  ruleCode: string
  pass: boolean
  expectedTriggered: string[]
  actualTriggered: string[]
  missing: string[]
  unexpected: string[]
  results: RuleResult[]
}

/** 回归测试报告 */
export interface RegressionReport {
  total: number
  passed: number
  failed: number
  passRate: string
  allPassed: boolean
  caseResults: RegressionCaseResult[]
}

/**
 * 批量执行测试用例（回归测试）
 * @param ids 测试用例 ID 列表，为空则执行全部
 * @returns 回归测试报告（含通过率、缺失/意外触发等）
 */
export const batchRunTestCases = (ids: number[] = []) =>
  request<RegressionReport>({
    url: '/execution/rules/test-cases/batch-run',
    method: 'POST',
    data: { ids },
  })

// ==================== 生命周期管理 ====================

/**
 * 变更规则状态
 */
export const changeRuleStatus = (ruleCode: string, targetStatus: string, comment?: string) =>
  request<RuleDefinition>({
    url: `/execution/rules/${ruleCode}/status`,
    method: 'PUT',
    data: { targetStatus, comment },
  })

// ==================== 执行链路追踪 ====================

/** 执行链路追踪记录 */
export interface ExecutionTrace {
  id: number
  traceId: string
  ruleCode: string
  ruleName: string
  scenario: string
  triggered: boolean
  severity: string
  conditionResult: string
  elapsedMs: number
  factsSnapshot: Record<string, unknown>
  resultSnapshot: Record<string, unknown>
  errorMessage: string
  createdAt: string
}

/**
 * 按 traceId 查询执行链路
 */
export const getTrace = (traceId: string) =>
  request<ExecutionTrace[]>({
    url: `/execution/rules/traces/${traceId}`,
    method: 'GET',
  })

/**
 * 按规则编码查询最近链路
 */
export const getTracesByRule = (ruleCode: string, limit = 20) =>
  request<ExecutionTrace[]>({
    url: `/execution/rules/traces/rule/${ruleCode}`,
    method: 'GET',
    params: { limit },
  })

/**
 * 查询最近执行链路（按时间倒序）
 * @param limit 返回条数（默认 50）
 */
export const listRecentTraces = (limit = 50) =>
  request<ExecutionTrace[]>({
    url: '/execution/rules/traces',
    method: 'GET',
    params: { limit },
  })

/** 执行回放差异分析 */
export interface ReplayDiff {
  added: string[]
  removed: string[]
  unchanged: string[]
  summary: string
}

/** 执行回放结果 */
export interface ReplayResult {
  traceId: string
  factsSnapshot: Record<string, unknown>
  historicalTraces: ExecutionTrace[]
  currentResults: RuleResult[]
  diff: ReplayDiff
}

/**
 * 执行回放：基于 traceId 重放历史执行链路
 * @param traceId 追踪 ID
 * @returns 回放结果（含历史快照 + 当前评估 + 差异分析）
 */
export const replayTrace = (traceId: string) =>
  request<ReplayResult>({
    url: `/execution/rules/traces/${traceId}/replay`,
    method: 'POST',
  })

/** A/B 测试差异详情 */
export interface ABTestDiff {
  triggeredChanged: boolean
  severityChanged: boolean
  titleChanged: boolean
  descriptionChanged: boolean
  hasDiff: boolean
  triggeredBefore?: boolean
  triggeredAfter?: boolean
  severityBefore?: string
  severityAfter?: string
}

/** A/B 测试报告 */
export interface ABTestReport {
  ruleCode: string
  currentVersion: number
  candidateVersion: number
  currentResult: RuleResult
  candidateResult: RuleResult
  diff: ABTestDiff
  summary: string
}

/**
 * 规则 A/B 测试
 * @param ruleCode 规则编码
 * @param candidate 候选规则定义
 * @param facts 事实数据
 * @returns A/B 测试报告
 */
export const abTest = (ruleCode: string, candidate: Partial<RuleDefinition>, facts: Record<string, unknown>) =>
  request<ABTestReport>({
    url: `/execution/rules/${ruleCode}/ab-test`,
    method: 'POST',
    data: { candidate, facts },
  })

// ==================== 决策表管理 ====================

/** 决策表 */
export interface DecisionTable {
  id?: number
  tableCode: string
  tableName: string
  description?: string
  category?: string
  conditionColumns: Record<string, unknown>[]
  actionColumns: Record<string, unknown>[]
  rows: Record<string, unknown>[]
  defaultActions?: Record<string, unknown>
  enabled: boolean
  priority: number
  version: number
}

/**
 * 查询全部决策表
 */
export const listDecisionTables = () =>
  request<DecisionTable[]>({ url: '/execution/rules/decision-tables', method: 'GET' })

/**
 * 查询单条决策表
 */
export const getDecisionTable = (tableCode: string) =>
  request<DecisionTable>({ url: `/execution/rules/decision-tables/${tableCode}`, method: 'GET' })

/**
 * 保存决策表
 */
export const saveDecisionTable = (data: DecisionTable) =>
  request<DecisionTable>({ url: '/execution/rules/decision-tables', method: 'POST', data })

/**
 * 删除决策表
 */
export const deleteDecisionTable = (id: number) =>
  request<void>({ url: `/execution/rules/decision-tables/${id}`, method: 'DELETE' })

// ==================== 导入导出 ====================

/**
 * 导出全部规则
 */
export const exportRules = () =>
  request<{ exportTime: string; ruleCount: number; rules: Record<string, unknown>[] }>({
    url: '/execution/rules/export',
    method: 'GET',
  })

/**
 * 导入规则
 */
export const importRules = (rules: Record<string, unknown>[]) =>
  request<{ imported: number; skipped: number }>({
    url: '/execution/rules/import',
    method: 'POST',
    data: { rules },
  })

// ==================== 规则删除（P0-4） ====================

/**
 * 软删除规则（status 置为 ARCHIVED）
 * @param ruleCode 规则编码
 */
export const deleteRule = (ruleCode: string) =>
  request<void>({
    url: `/execution/rules/${ruleCode}`,
    method: 'DELETE',
    headers: { 'X-Operator': 'admin' },
  })

// ==================== 批量操作（P0-5） ====================

/**
 * 批量启停规则
 * @param ruleCodes 规则编码列表
 * @param enabled true=启用, false=停用
 */
export const batchToggle = (ruleCodes: string[], enabled: boolean) =>
  request<{ success: number; failed: string[] }>({
    url: '/execution/rules/batch-toggle',
    method: 'POST',
    data: { ruleCodes, enabled },
    headers: { 'X-Operator': 'admin' },
  })

/**
 * 批量调整优先级
 * @param ruleCodes 规则编码列表
 * @param delta 优先级变化量（可为负）
 */
export const batchPriority = (ruleCodes: string[], delta: number) =>
  request<{ success: number; failed: string[] }>({
    url: '/execution/rules/batch-priority',
    method: 'POST',
    data: { ruleCodes, delta },
    headers: { 'X-Operator': 'admin' },
  })

/**
 * 批量调整分类
 */
export const batchCategory = (ruleCodes: string[], category: string) =>
  request<{ success: number; failed: string[] }>({
    url: '/execution/rules/batch-category',
    method: 'POST',
    data: { ruleCodes, category },
    headers: { 'X-Operator': 'admin' },
  })

// ==================== 规则链画布（P0-1） ====================

/** 链节点位置 */
export interface ChainNodePosition {
  x: number
  y: number
}

/** 链节点 DTO */
export interface ChainNodeDTO {
  nodeId: string
  nodeType: 'CHAIN' | 'SINGLE' | 'GROUP'
  chainType?: 'THEN' | 'WHEN' | 'IF' | 'ELIF' | 'SWITCH' | 'FOR' | 'WHILE' | 'BREAK'
  label?: string
  ruleCode?: string
  parentNodeId?: string
  position?: ChainNodePosition
  metadata?: Record<string, unknown>
}

/** 链边 DTO */
export interface ChainEdgeDTO {
  edgeId: string
  sourceNodeId: string
  targetNodeId: string
  edgeType: 'THEN' | 'IF_BRANCH' | 'ELIF_BRANCH' | 'SWITCH_BRANCH' | 'FOR_ITER' | 'WHILE_ITER' | 'DEFAULT_BRANCH' | 'GROUP_MEMBER' | 'BREAK'
  condition?: string
  branchValue?: string
  label?: string
}

/** 画布视口 */
export interface ChainViewport {
  x: number
  y: number
  zoom: number
}

/** 规则链画布 */
export interface RuleChainGraph {
  graphId?: string
  name?: string
  ruleCode?: string
  description?: string
  scenario?: string
  tenantId?: string
  version?: string
  status?: string
  nodes: ChainNodeDTO[]
  edges: ChainEdgeDTO[]
  viewport?: ChainViewport
  metadata?: Record<string, unknown>
  createdAt?: string
  updatedAt?: string
  createdBy?: string
  updatedBy?: string
}

/** 画布问题（前端展示） */
export interface RuleChainGraphViewIssue {
  level: 'ERROR' | 'WARN'
  code: string
  message: string
}

/** 画布保存结果 */
export interface SaveGraphResult {
  valid: boolean
  issues: RuleChainGraphViewIssue[]
  graph?: RuleChainGraph
  message?: string
}

/**
 * 查询规则的画布
 * @param ruleCode 规则编码
 */
export const getChainGraph = (ruleCode: string) =>
  request<RuleChainGraph>({
    url: `/execution/rules/${ruleCode}/graph`,
    method: 'GET',
  })

/**
 * 保存或更新画布
 * @param ruleCode 规则编码
 * @param graph 画布
 */
export const saveChainGraph = (ruleCode: string, graph: RuleChainGraph) =>
  request<SaveGraphResult>({
    url: `/execution/rules/${ruleCode}/graph`,
    method: 'POST',
    data: graph,
    headers: { 'X-Operator': 'admin' },
  })

/**
 * 校验画布结构（不保存）
 * @param graph 画布
 */
export const validateChainGraph = (graph: RuleChainGraph) =>
  request<RuleChainGraphViewIssue[]>({
    url: `/execution/rules/_/graph/validate`,
    method: 'POST',
    data: graph,
  })

/**
 * 删除画布
 */
export const deleteChainGraph = (ruleCode: string) =>
  request<void>({
    url: `/execution/rules/${ruleCode}/graph`,
    method: 'DELETE',
  })

/**
 * 画布 Dry-run 仿真（P0-1 执行闭环）
 * @param ruleCode 规则编码
 * @param facts 事实数据
 * @returns 已触发的规则结果列表
 */
export const dryRunGraph = (ruleCode: string, facts: Record<string, unknown>) =>
  request<RuleResult[]>({
    url: `/execution/rules/${ruleCode}/graph/dry-run`,
    method: 'POST',
    data: facts,
  })

/**
 * 检查画布中失效的规则引用
 * @param ruleCode 规则编码
 * @returns 失效规则编码列表
 */
export const invalidGraphRefs = (ruleCode: string) =>
  request<string[]>({
    url: `/execution/rules/${ruleCode}/graph/invalid-refs`,
    method: 'GET',
  })

// ==================== 函数市场（P1-7） ====================

/** 表达式函数定义 */
export interface ExpressionFunctionDef {
  name: string
  signature: string
  description: string
  sample: string
  category: string
  supportedEngines: string
}

/**
 * 获取已注册表达式函数列表
 * @param engine 引擎类型 aviator/qlexpress/all
 */
export const expressionFunctions = (engine: 'aviator' | 'qlexpress' | 'all' = 'all') =>
  request<ExpressionFunctionDef[]>({
    url: '/execution/rules/expression-functions',
    method: 'GET',
    params: { engine },
  })

// ==================== 规则目录树 + 责任人（P1-9） ====================

/**
 * 获取规则目录树
 */
export const getCategoryTree = () =>
  request<CategoryNode>({
    url: '/execution/rules/category-tree',
    method: 'GET',
  })

/**
 * 按分类路径前缀查询规则
 * @param path 分类路径前缀（必传）
 */
export const listByCategoryPath = (path: string) =>
  request<RuleDefinition[]>({
    url: '/execution/rules/by-category-path',
    method: 'GET',
    params: { path },
  })

/**
 * 按 Owner 查询规则
 * @param owner 责任人工号
 */
export const listByOwner = (owner: string) =>
  request<RuleDefinition[]>({
    url: '/execution/rules/by-owner',
    method: 'GET',
    params: { owner },
  })

/**
 * 设置规则责任人
 */
export const setRuleOwner = (ruleCode: string, owner: string) =>
  request<void>({
    url: `/execution/rules/${ruleCode}/owner`,
    method: 'PUT',
    params: { owner },
  })

/**
 * 设置规则分类路径
 */
export const setRuleCategoryPath = (ruleCode: string, path: string) =>
  request<void>({
    url: `/execution/rules/${ruleCode}/category-path`,
    method: 'PUT',
    params: { path },
  })

// ==================== 规则集市场（P2-14） ====================

/** 规则集（RulePack） */
export interface RulePack {
  packCode: string
  packVersion: string
  packName: string
  industry?: string
  tags?: string[]
  ruleCodes: string[]
  description?: string
  author?: string
  downloadCount: number
  rating: number
}

/** 安装结果 */
export interface RulePackInstallResult {
  packCode: string
  version: string
  total: number
  success: number
  failed: number
  failedCodes: string[]
}

/**
 * 列出市场全部规则集
 */
export const listPacks = () =>
  request<RulePack[]>({
    url: '/execution/rules/packs',
    method: 'GET',
  })

/**
 * 搜索规则集
 */
export const searchPacks = (keyword: string) =>
  request<RulePack[]>({
    url: '/execution/rules/packs/search',
    method: 'GET',
    params: { keyword },
  })

/**
 * 查询规则集最新版本
 */
export const getLatestPack = (packCode: string) =>
  request<RulePack>({
    url: `/execution/rules/packs/${packCode}/latest`,
    method: 'GET',
  })

/**
 * 安装规则集
 */
export const installPack = (packCode: string, version?: string) =>
  request<RulePackInstallResult>({
    url: `/execution/rules/packs/${packCode}/install`,
    method: 'POST',
    params: version ? { version } : {},
  })

// ==================== 决策树管理（P1-4） ====================

/** 决策树节点（内部条件节点 / 叶子决策节点） */
export interface DecisionNode {
  /** 条件表达式（仅条件节点使用，Aviator 返回 boolean） */
  conditionExpression?: string
  /** true 分支子节点 */
  trueBranch?: DecisionNode
  /** false 分支子节点 */
  falseBranch?: DecisionNode
  /** 严重度字符串（仅叶子节点使用，RED/YELLOW/INFO） */
  severity?: string
  /** 标题（仅叶子节点使用） */
  title?: string
  /** 描述（仅叶子节点使用） */
  description?: string
  /** 是否为叶子节点 */
  leaf?: boolean
}

/** 决策树规则定义 */
export interface DecisionTreeDefinition {
  /** 规则编码（唯一） */
  ruleCode: string
  /** 规则名称 */
  ruleName: string
  /** 类别（如 RISK / GENERAL） */
  category?: string
  /** 描述 */
  description?: string
  /** 根节点 */
  root?: DecisionNode
  /** 是否启用 */
  enabled?: boolean
  /** 优先级（数值越小越先执行） */
  priority?: number
  /** 影响范围 */
  scope?: string
  /** 当前版本号 */
  version?: number
}

/** 决策树校验问题 */
export interface DecisionTreeIssue {
  level: 'ERROR' | 'WARN'
  code: string
  message: string
  nodePath?: string
}

/** 决策树校验结果 */
export interface DecisionTreeValidateResult {
  valid: boolean
  issues: DecisionTreeIssue[]
}

/**
 * 查询决策树定义
 * @param ruleCode 规则编码
 */
export const getDecisionTree = (ruleCode: string) =>
  request<DecisionTreeDefinition>({
    url: `/execution/rules/decision-trees/${ruleCode}`,
    method: 'GET',
  })

/**
 * 保存决策树定义
 * @param data 决策树定义
 */
export const saveDecisionTree = (data: DecisionTreeDefinition) =>
  request<DecisionTreeDefinition>({
    url: '/execution/rules/decision-trees',
    method: 'POST',
    data,
    headers: { 'X-Operator': 'admin' },
  })

/**
 * 校验决策树结构（不保存）
 * @param data 决策树定义
 */
export const validateDecisionTree = (data: DecisionTreeDefinition) =>
  request<DecisionTreeValidateResult>({
    url: '/execution/rules/decision-trees/validate',
    method: 'POST',
    data,
  })

// ==================== 评分卡管理（P1-4） ====================

/** 评分方向：DESCENDING 分数越低风险越高；ASCENDING 分数越高风险越高 */
export type ScoreDirection = 'DESCENDING' | 'ASCENDING'

/** 评分因子 */
export interface ScoreFactor {
  /** 条件表达式（Aviator，返回 boolean） */
  conditionExpression: string
  /** 命中时的固定得分（正分加分，负分扣分） */
  score?: number
  /** 动态分值表达式（与 score 二选一，优先使用 scoreExpression） */
  scoreExpression?: string
  /** 权重（实际得分 = 分值 × 权重，默认 1.0） */
  weight?: number
  /** 因子描述 */
  description?: string
}

/** 评分评级映射 */
export interface ScoreGrade {
  /** 评级名称（如 A/B/C/D） */
  label: string
  /** 区间下界（含） */
  minScore: number
  /** 区间上界（不含） */
  maxScore: number
  /** 对应的严重度编码（RED/YELLOW/INFO，可选） */
  severity?: string
}

/** 评分卡规则定义 */
export interface ScorecardDefinition {
  /** 规则编码（唯一） */
  ruleCode: string
  /** 规则名称 */
  ruleName: string
  /** 类别 */
  category?: string
  /** 描述 */
  description?: string
  /** 基础分（默认 100） */
  baseScore?: number
  /** 红色阈值 */
  redThreshold?: number
  /** 黄色阈值 */
  yellowThreshold?: number
  /** 评分方向（默认 DESCENDING） */
  scoreDirection?: ScoreDirection
  /** 最低分（钳制下界，默认 0） */
  minScore?: number
  /** 最高分（钳制上界，默认 100） */
  maxScore?: number
  /** 评分因子列表 */
  factors?: ScoreFactor[]
  /** 自定义评级映射（可选） */
  grades?: ScoreGrade[]
  /** 是否启用 */
  enabled?: boolean
  /** 优先级 */
  priority?: number
  /** 影响范围 */
  scope?: string
  /** 当前版本号 */
  version?: number
}

/** 评分卡校验问题 */
export interface ScorecardIssue {
  level: 'ERROR' | 'WARN'
  code: string
  message: string
  factorIndex?: number
}

/** 评分卡校验结果 */
export interface ScorecardValidateResult {
  valid: boolean
  issues: ScorecardIssue[]
}

/**
 * 查询评分卡定义
 * @param ruleCode 规则编码
 */
export const getScorecard = (ruleCode: string) =>
  request<ScorecardDefinition>({
    url: `/execution/rules/scorecards/${ruleCode}`,
    method: 'GET',
  })

/**
 * 保存评分卡定义
 * @param data 评分卡定义
 */
export const saveScorecard = (data: ScorecardDefinition) =>
  request<ScorecardDefinition>({
    url: '/execution/rules/scorecards',
    method: 'POST',
    data,
    headers: { 'X-Operator': 'admin' },
  })

/**
 * 校验评分卡结构（不保存）
 * @param data 评分卡定义
 */
export const validateScorecard = (data: ScorecardDefinition) =>
  request<ScorecardValidateResult>({
    url: '/execution/rules/scorecards/validate',
    method: 'POST',
    data,
  })

// ==================== 监控大盘（P1-6） ====================

/** 监控大盘时间范围 */
export type DashboardTimeRange = '24h' | '7d' | '30d'

/** Top 规则排序类型 */
export type DashboardTopType = 'triggered' | 'slowest' | 'errorRate'

/** 监控大盘 - 概览指标 */
export interface DashboardOverview {
  /** 规则总数 */
  totalRules: number
  /** 启用规则数 */
  enabledRules: number
  /** 按状态分组：DRAFT/REVIEW/PUBLISHED/DISABLED/ARCHIVED → 数量 */
  statusDistribution: Record<string, number>
  /** 按类别分组：category → 数量 */
  categoryDistribution: Record<string, number>
  /** 今日评估次数 */
  todayEvaluations: number
  /** 今日触发次数 */
  todayTriggered: number
  /** 今日触发率（0~1） */
  todayTriggerRate: number
  /** 今日错误次数 */
  todayErrors: number
  /** 今日错误率（0~1） */
  todayErrorRate: number
  /** 今日活跃规则数 */
  todayActiveRules: number
  /** P50 耗时（毫秒） */
  p50ElapsedMs: number
  /** P95 耗时（毫秒） */
  p95ElapsedMs: number
  /** P99 耗时（毫秒） */
  p99ElapsedMs: number
  /** 平均耗时（毫秒） */
  avgElapsedMs: number
  /** 统计时间窗口起始 */
  since: string
  /** 统计时间窗口结束 */
  until: string
}

/** 监控大盘 - 趋势指标 */
export interface DashboardTrend {
  /** 时间范围 */
  timeRange: DashboardTimeRange
  /** 时间点标签列表（X 轴） */
  timeLabels: string[]
  /** 评估次数序列 */
  evaluationSeries: number[]
  /** 触发次数序列 */
  triggeredSeries: number[]
  /** 错误次数序列 */
  errorSeries: number[]
  /** P99 耗时序列（毫秒） */
  p99ElapsedSeries: number[]
  /** P50 耗时序列（毫秒） */
  p50ElapsedSeries: number[]
  /** 错误率序列（0~1） */
  errorRateSeries: number[]
  /** 触发率序列（0~1） */
  triggerRateSeries: number[]
  /** 起始时间 */
  since: string
  /** 结束时间 */
  until: string
}

/** 饼图条目 */
export interface DashboardPieItem {
  /** 名称 */
  name: string
  /** 数量 */
  value: number
}

/** 监控大盘 - 分布指标 */
export interface DashboardDistribution {
  /** 按状态分布 */
  byStatus: Record<string, number>
  /** 按类别分布 */
  byCategory: Record<string, number>
  /** 按严重度分布 */
  bySeverity: Record<string, number>
  /** 按场景分布 */
  byScenario: Record<string, number>
  /** 按租户分布 */
  byTenant: Record<string, number>
  /** 按责任人分布 */
  byOwner: Record<string, number>
  /** 状态饼图 */
  statusPie: DashboardPieItem[]
  /** 类别饼图 */
  categoryPie: DashboardPieItem[]
  /** 严重度饼图 */
  severityPie: DashboardPieItem[]
  /** 场景饼图 */
  scenarioPie: DashboardPieItem[]
}

/** 监控大盘 - Top 规则条目 */
export interface DashboardTopRule {
  /** 规则编码 */
  ruleCode: string
  /** 规则名称 */
  ruleName: string
  /** 规则类别 */
  category?: string
  /** 责任人 */
  owner?: string
  /** 是否启用 */
  enabled?: boolean
  /** 默认严重度 */
  defaultSeverity?: string
  /** 评估次数 */
  evaluations: number
  /** 触发次数 */
  triggered: number
  /** 错误次数 */
  errors: number
  /** 触发率（0~1） */
  triggerRate: number
  /** 错误率（0~1） */
  errorRate: number
  /** 平均耗时（毫秒） */
  avgElapsedMs: number
  /** P99 耗时（毫秒） */
  p99ElapsedMs: number
  /** 总耗时（毫秒） */
  totalElapsedMs: number
}

/** 监控大盘 - 实时指标 */
export interface DashboardRealtime {
  /** 当前注册规则数 */
  registeredRules: number
  /** 最近一次评估遍历的规则数 */
  lastEvaluatedRules: number
  /** 最近 1 分钟评估次数 */
  recentEvaluations: number
  /** 最近 1 分钟触发次数 */
  recentTriggered: number
  /** 最近 1 分钟错误次数 */
  recentErrors: number
  /** 当前 QPS（次/秒） */
  currentQps: number
  /** 当前活跃规则数 */
  activeRules: number
  /** Trace 队列积压 */
  traceQueueSize: number
  /** 服务器时间戳 */
  timestamp: number
}

/**
 * 概览指标
 * @returns 概览指标（首屏卡片）
 */
export const getDashboardOverview = () =>
  request<DashboardOverview>({
    url: '/execution/rules/dashboard/overview',
    method: 'GET',
  })

/**
 * 趋势指标
 * @param timeRange 时间范围：24h / 7d / 30d
 * @returns 趋势数据（时间序列）
 */
export const getDashboardTrends = (timeRange: DashboardTimeRange = '24h') =>
  request<DashboardTrend>({
    url: '/execution/rules/dashboard/trends',
    method: 'GET',
    params: { timeRange },
  })

/**
 * 分布指标
 * @returns 分布数据（饼图）
 */
export const getDashboardDistribution = () =>
  request<DashboardDistribution>({
    url: '/execution/rules/dashboard/distribution',
    method: 'GET',
  })

/**
 * Top 规则列表
 * @param type  排序类型：triggered / slowest / errorRate
 * @param limit 返回条数（默认 10）
 */
export const getDashboardTopRules = (type: DashboardTopType = 'triggered', limit = 10) =>
  request<DashboardTopRule[]>({
    url: '/execution/rules/dashboard/top-rules',
    method: 'GET',
    params: { type, limit },
  })

/**
 * 实时指标
 * @returns 实时指标（当前 QPS、活跃规则数）
 */
export const getDashboardRealtime = () =>
  request<DashboardRealtime>({
    url: '/execution/rules/dashboard/realtime',
    method: 'GET',
  })

// ==================== 规则依赖关系（P2-6） ====================

/** 规则依赖关系记录 */
export interface RuleDependency {
  /** 记录 ID */
  id: string
  /** 主规则编码（依赖方） */
  ruleCode: string
  /** 被依赖的规则编码 */
  dependsOnRuleCode: string
  /** 依赖类型：EXECUTE / READ_RESULT / SOFT */
  dependencyType: string
  /** 被依赖规则被禁用时是否级联禁用本规则 */
  cascadeOnDisable: boolean
  /** 依赖说明 */
  description?: string
  /** 租户 ID */
  tenantId?: string
  /** 创建人 */
  createdBy?: string
  /** 创建时间 */
  createdAt?: string
}

/**
 * 查询规则的正向依赖（依赖了哪些规则）
 * @param ruleCode 规则编码
 * @returns 依赖记录列表
 */
export const listDependencies = (ruleCode: string) =>
  request<RuleDependency[]>({
    url: `/execution/rules/${ruleCode}/dependencies`,
    method: 'GET',
  })

/**
 * 查询规则的反向依赖（被哪些规则依赖）
 * @param ruleCode 规则编码
 * @returns 被依赖记录列表
 */
export const listDependents = (ruleCode: string) =>
  request<RuleDependency[]>({
    url: `/execution/rules/${ruleCode}/dependents`,
    method: 'GET',
  })

// ==================== CEP 模式管理（P2-7） ====================

/** CEP 模式类型 */
export type CEPPatternType = 'TIME_WINDOW' | 'SEQUENCE' | 'AGGREGATE' | 'ABSENCE'

/** CEP 聚合函数 */
export type CEPAggregateFunction = 'COUNT' | 'SUM' | 'AVG' | 'MIN' | 'MAX'

/** CEP 序列步骤 */
export interface CEPSequenceStep {
  /** 步骤序号（从 1 开始） */
  order: number
  /** 该步骤匹配的事件类型 */
  eventType: string
  /** 该步骤的事件过滤条件（Aviator 表达式） */
  filter?: string
  /** 该步骤与下一步的最小间隔（毫秒，null 表示无限制） */
  minGapMs?: number
  /** 该步骤与下一步的最大间隔（毫秒，null 表示无限制） */
  maxGapMs?: number
}

/** CEP 模式定义 */
export interface CEPPattern {
  /** 模式唯一标识 */
  id: string
  /** 模式类型 */
  type: CEPPatternType
  /** 关联的规则编码（命中模式时触发的规则） */
  ruleCode?: string
  /** 模式名称 */
  name?: string
  /** 时间窗口长度（毫秒） */
  windowMs?: number
  /** 滑动步长（毫秒，仅滑动窗口；null 表示滚动窗口） */
  slideMs?: number
  /** 触发阈值（TIME_WINDOW 模式下为次数，AGGREGATE 模式下为数值阈值） */
  threshold?: number
  /** 事件类型（单事件类型匹配） */
  eventType?: string
  /** 事件类型列表（多类型 OR 匹配） */
  eventTypes?: string[]
  /** 事件过滤条件（Aviator 表达式） */
  filter?: string
  /** 聚合函数（AGGREGATE 模式使用） */
  aggregateFunction?: CEPAggregateFunction
  /** 聚合字段（AGGREGATE 模式使用） */
  aggregateField?: string
  /** 序列步骤（SEQUENCE 模式使用） */
  sequence?: CEPSequenceStep[]
  /** 描述 */
  description?: string
}

/** CEP 命中记录 */
export interface CEPHit {
  /** 命中模式 ID */
  patternId: string
  /** 关联规则编码 */
  ruleCode?: string
  /** 命中时间戳（ISO 字符串） */
  hitAt: string
  /** 触发事件列表 */
  events?: Record<string, unknown>[]
  /** 命中时的属性快照 */
  attributes?: Record<string, unknown>
}

/**
 * 列出已注册的 CEP 模式
 * @returns 模式列表
 */
export const listCepPatterns = () =>
  request<CEPPattern[]>({
    url: '/execution/rules/cep/patterns',
    method: 'GET',
  })

/**
 * 注册 / 更新 CEP 模式
 * @param pattern 模式定义
 */
export const saveCepPattern = (pattern: CEPPattern) =>
  request<void>({
    url: '/execution/rules/cep/patterns',
    method: 'POST',
    data: pattern,
  })

/**
 * 注销 CEP 模式
 * @param patternId 模式 ID
 */
export const deleteCepPattern = (patternId: string) =>
  request<void>({
    url: `/execution/rules/cep/patterns/${patternId}`,
    method: 'DELETE',
  })

/** CEP 模式测试事件 */
export interface CEPTestEvent {
  type: string
  partitionKey?: string
  timestamp?: string
  attributes?: Record<string, unknown>
}

/** CEP 模式测试结果 */
export interface CEPTestResult {
  patternId: string
  fedEvents: number
  triggeredHits: number
  hits: CEPHit[]
}

/**
 * 测试 CEP 模式（注册临时模式 → 投递事件 → 收集命中 → 注销）
 * @param pattern 模式定义
 * @param events 测试事件列表
 * @returns 测试结果
 */
export const testCepPattern = (pattern: CEPPattern, events: CEPTestEvent[]) =>
  request<CEPTestResult>({
    url: '/execution/rules/cep/patterns/test',
    method: 'POST',
    data: { pattern, events },
  })

// ==================== 规则压测（P2-9） ====================

/** 压测直方图分桶 */
export interface StressTestHistogramBucket {
  bucketLabel: string
  count: number
}

/** 压测结果 */
export interface StressTestResult {
  /** 总执行次数 */
  totalExecutions: number
  /** 总耗时（毫秒） */
  totalTimeMs: number
  /** QPS（次/秒） */
  qps: number
  /** P50 耗时（毫秒） */
  p50Ms: number
  /** P95 耗时（毫秒） */
  p95Ms: number
  /** P99 耗时（毫秒） */
  p99Ms: number
  /** 错误率（0~1） */
  errorRate: number
  /** 错误次数 */
  errorCount: number
  /** 错误详情（最多 10 条） */
  errors: string[]
  /** 耗时分布直方图 */
  histogram: StressTestHistogramBucket[]
}

/** 压测请求参数 */
export interface StressTestParams {
  /** 目标规则编码 */
  ruleCode: string
  /** 事实数据列表（多条用于并发投递） */
  factsList: Record<string, unknown>[]
  /** 并发线程数 */
  threads: number
  /** 每线程迭代次数 */
  iterations: number
  /** 预热迭代次数（不统计） */
  warmupIterations: number
}

/**
 * 执行规则压测
 * @param params 压测参数
 * @returns 压测结果（含 QPS、P50/P95/P99、直方图）
 */
export const stressTest = (params: StressTestParams) =>
  request<StressTestResult>({
    url: '/execution/rules/stress-test',
    method: 'POST',
    data: params,
  })

// ==================== 知识包更新检查（P2-10） ====================

/** 知识包更新信息 */
export interface PackUpdateInfo {
  /** 知识包编码 */
  packCode: string
  /** 知识包名称 */
  packName: string
  /** 已安装版本 */
  installedVersion: string
  /** 市场最新版本 */
  latestVersion: string
  /** 是否有更新 */
  hasUpdate: boolean
  /** 安装时间（ISO 字符串） */
  installedAt?: string
  /** 行业 */
  industry?: string
  /** 描述 */
  description?: string
}

/**
 * 检查已安装知识包的更新（P2-10）
 * @returns 全部已安装包的更新信息（含无更新的，调用方可通过 hasUpdate=true 过滤）
 */
export const checkPackUpdates = () =>
  request<PackUpdateInfo[]>({
    url: '/execution/rules/packs/update-check',
    method: 'GET',
  })

/**
 * 批量更新知识包
 * @param packCodes 知识包编码列表
 * @returns 每个知识包的安装结果
 */
export const batchUpdatePacks = (packCodes: string[]) =>
  request<RulePackInstallResult[]>({
    url: '/execution/rules/packs/batch-update',
    method: 'POST',
    data: packCodes,
    headers: { 'X-Operator': 'admin' },
  })
