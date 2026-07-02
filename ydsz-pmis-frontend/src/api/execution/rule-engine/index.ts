/**
 * @file 规则引擎 API 接口封装
 * @description 提供规则定义的增删改查、启停切换、版本管理与回滚、Dry-run 仿真、
 *              表达式校验、执行统计、模板市场导入及 AI 辅助生成等能力，
 *              对应后端规则引擎 Controller（/execution/api/v1/rules）。
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
  /** 规则描述 */
  description?: string
  /** 条件表达式（SpEL），返回 boolean 决定是否触发 */
  conditionExpression: string
  /** 严重度表达式（SpEL），返回 RED/YELLOW/NORMAL */
  severityExpression?: string
  /** 默认严重度（当 severityExpression 为空或求值失败时使用） */
  defaultSeverity: string
  /** 标题模板（支持 SpEL 变量占位） */
  titleTemplate?: string
  /** 描述模板（支持 SpEL 变量占位） */
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
  request<RuleDefinition[]>({ url: '/execution/api/v1/rules', method: 'GET' })

/**
 * 查询单条规则详情
 * @param ruleCode 规则编码
 * @returns 规则定义详情
 */
export const getRule = (ruleCode: string) =>
  request<RuleDefinition>({ url: `/execution/api/v1/rules/${ruleCode}`, method: 'GET' })

/**
 * 新建 / 更新规则（按 code 幂等），同时记录版本变更
 * @param data 规则定义
 * @param changeDesc 变更说明（可选，记录到版本历史）
 * @returns 保存后的规则定义
 */
export const saveRule = (data: RuleDefinition, changeDesc?: string) =>
  request<RuleDefinition>({
    url: '/execution/api/v1/rules',
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
    url: `/execution/api/v1/rules/${ruleCode}/toggle`,
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
    url: `/execution/api/v1/rules/${ruleCode}/versions`,
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
    url: `/execution/api/v1/rules/${ruleCode}/rollback`,
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
    url: '/execution/api/v1/rules/dry-run',
    method: 'POST',
    params: { ruleCode },
    data: facts,
  })

// ==================== 表达式校验 ====================

/**
 * 校验表达式语法是否合法
 * @param expression SpEL 表达式
 * @returns 是否合法
 */
export const validateExpression = (expression: string) =>
  request<boolean>({
    url: '/execution/api/v1/rules/validate',
    method: 'GET',
    params: { expression },
  })

// ==================== 执行统计 ====================

/**
 * 查询规则引擎执行统计
 * @returns 全局 + 按规则的执行统计
 */
export const getStats = () =>
  request<RuleEngineStats>({ url: '/execution/api/v1/rules/stats', method: 'GET' })

// ==================== 模板市场 ====================

/**
 * 查询全部规则模板
 * @returns 模板列表
 */
export const listTemplates = () =>
  request<RuleTemplate[]>({ url: '/execution/api/v1/rules/templates', method: 'GET' })

/**
 * 按类别查询规则模板
 * @param category 模板类别
 * @returns 模板列表
 */
export const listTemplatesByCategory = (category: string) =>
  request<RuleTemplate[]>({
    url: `/execution/api/v1/rules/templates/category/${category}`,
    method: 'GET',
  })

/**
 * 从模板市场一键导入规则
 * @param templateCode 模板编码
 * @returns 导入生成的规则定义
 */
export const importTemplate = (templateCode: string) =>
  request<RuleDefinition>({
    url: `/execution/api/v1/rules/templates/${templateCode}/import`,
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
    url: '/execution/api/v1/rules/ai-generate',
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
    url: '/execution/api/v1/rules/ai-generate-and-save',
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
  request<RuleConflict[]>({ url: '/execution/api/v1/rules/conflicts', method: 'GET' })

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
    url: '/execution/api/v1/rules/test-cases',
    method: 'GET',
    params: { ruleCode },
  })

/**
 * 保存测试用例
 */
export const saveTestCase = (data: RuleTestCase) =>
  request<RuleTestCase>({
    url: '/execution/api/v1/rules/test-cases',
    method: 'POST',
    data,
  })

/**
 * 删除测试用例
 */
export const deleteTestCase = (id: number) =>
  request<void>({
    url: `/execution/api/v1/rules/test-cases/${id}`,
    method: 'DELETE',
  })

/**
 * 批量执行测试用例
 */
export const batchRunTestCases = (ids: number[]) =>
  request<Record<string, RuleResult[]>>({
    url: '/execution/api/v1/rules/test-cases/batch-run',
    method: 'POST',
    data: { ids },
  })
