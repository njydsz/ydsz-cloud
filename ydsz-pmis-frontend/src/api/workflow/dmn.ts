/**
 * @file DMN 决策表 API
 * @description P0-4: DMN 决策表引擎 HTTP 接口封装，提供决策表的增删改查、发布与执行能力。
 *              对应后端 Controller：FlowDmnController（/workflow/dmn）。
 * @module api/workflow/dmn
 */
import { request } from '@/utils/request'
import type { PageData } from '@/types/api'

/** 命中策略 */
export type DmnHitPolicy = 'UNIQUE' | 'FIRST' | 'ANY' | 'PRIORITY' | 'COLLECT'

/** COLLECT 聚合运算符 */
export type DmnCollectOperator = 'LIST' | 'SUM' | 'MIN' | 'MAX' | 'COUNT'

/** 决策表状态 */
export type DmnStatus = 'DRAFT' | 'PUBLISHED' | 'DEPRECATED'

/** DMN 输入/输出列定义（存储于 inputsJson / outputsJson） */
export interface DmnColumn {
  /** 列标识 */
  id?: string
  /** 列名称 */
  name: string
  /** 列类型：string/number/boolean/date */
  type: string
  /** 表达式（可选，输入列的 FEEL 表达式） */
  expression?: string
}

/** DMN 规则行定义（存储于 rulesJson） */
export interface DmnRule {
  /** 规则标识 */
  id?: string
  /** 输入条件列表（与输入列顺序对应，FEEL 表达式字符串） */
  inputEntries: string[]
  /** 输出值列表（与输出列顺序对应） */
  outputEntries: string[]
  /** 规则描述 */
  description?: string
}

/**
 * DMN 决策表 DTO（与后端 FlowDmnTableDO 字段对齐）
 *
 * inputsJson / outputsJson / rulesJson 在后端以 JSON 字符串存储，
 * 前端在表单中以结构化对象编辑，保存前序列化为字符串。
 */
export interface FlowDmnTableDTO {
  id?: string
  tenantId?: string
  /** 决策表唯一标识 */
  tableKey: string
  /** 决策表名称 */
  tableName: string
  /** 决策表描述 */
  description?: string
  /** 命中策略: UNIQUE/FIRST/ANY/PRIORITY/COLLECT */
  hitPolicy: DmnHitPolicy
  /** COLLECT 聚合运算符: LIST/SUM/MIN/MAX/COUNT */
  collectOperator?: DmnCollectOperator
  /** 输入列定义(JSON 字符串) */
  inputsJson: string
  /** 输出列定义(JSON 字符串) */
  outputsJson: string
  /** 规则行定义(JSON 字符串) */
  rulesJson: string
  /** 版本号 */
  version?: number
  /** 状态: DRAFT/PUBLISHED/DEPRECATED */
  status?: DmnStatus
  createdAt?: string
  updatedAt?: string
}

/** 执行决策请求体 */
export interface DmnExecuteRequest {
  tableKey: string
  context: Record<string, unknown>
}

/**
 * 分页查询决策表
 * @param params 分页参数：pageNum 页码、pageSize 每页条数、tableName 名称模糊过滤
 */
export function pageDmnTables(params: {
  pageNum?: number
  pageSize?: number
  tableName?: string
}) {
  return request<PageData<FlowDmnTableDTO>>({
    url: '/workflow/dmn/page',
    method: 'POST',
    params,
  })
}

/** 按 ID 获取决策表详情 */
export function getDmnTable(id: number) {
  return request<FlowDmnTableDTO>({
    url: `/workflow/dmn/${id}`,
    method: 'GET',
  })
}

/** 按 tableKey 获取决策表 */
export function getDmnTableByKey(tableKey: string) {
  return request<FlowDmnTableDTO>({
    url: `/workflow/dmn/key/${tableKey}`,
    method: 'GET',
  })
}

/** 新建/更新决策表（body 中包含 id 则更新，否则新建） */
export function saveDmnTable(data: FlowDmnTableDTO) {
  return request<number>({
    url: '/workflow/dmn/save',
    method: 'POST',
    data,
  })
}

/** 发布决策表 */
export function publishDmnTable(id: number) {
  return request<void>({
    url: `/workflow/dmn/${id}/publish`,
    method: 'POST',
  })
}

/** 执行决策表 */
export function executeDmnTable(data: DmnExecuteRequest) {
  return request<Record<string, unknown>[]>({
    url: '/workflow/dmn/execute',
    method: 'POST',
    data,
  })
}

// ==================== 工具函数 ====================

/** 解析 JSON 字符串为列定义数组，解析失败返回空数组 */
export function parseColumns(json?: string | null): DmnColumn[] {
  if (!json) return []
  try {
    const parsed = JSON.parse(json)
    return Array.isArray(parsed) ? (parsed as DmnColumn[]) : []
  } catch {
    return []
  }
}

/** 解析 JSON 字符串为规则数组，解析失败返回空数组 */
export function parseRules(json?: string | null): DmnRule[] {
  if (!json) return []
  try {
    const parsed = JSON.parse(json)
    return Array.isArray(parsed) ? (parsed as DmnRule[]) : []
  } catch {
    return []
  }
}

/** 序列化列定义数组为 JSON 字符串 */
export function stringifyColumns(columns: DmnColumn[]): string {
  return JSON.stringify(columns || [])
}

/** 序列化规则数组为 JSON 字符串 */
export function stringifyRules(rules: DmnRule[]): string {
  return JSON.stringify(rules || [])
}
