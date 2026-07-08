/**
 * @file 消息通知引擎(message) API 接口封装
 * @description 提供消息日志、模板管理、路由规则、灰度实验、死信管理、统计看板等能力，
 *              对应后端 MessageController / TemplateController / RouteRuleController /
 *              CanaryController / CanaryReportController / DeadLetterController /
 *              MessageStatsController（/message/*）。
 *
 *   - 所有 ID 类型为 string（后端 VARCHAR(20) 雪花算法字符串）
 *   - 分页接口返回 PageData<T>
 *   - GET 请求用 params，POST/PUT 用 data
 *   - 查询类接口加 silent: true，不触发全局 loading
 * @module api/message
 */
import { request } from '@/utils/request'
import type { PageData } from '@/types/api'
import type {
  MessageLogVO,
  MessageLogPageQuery,
  BatchProgressVO,
  MsgTemplateVO,
  TemplateCreateDTO,
  TemplateQueryDTO,
  TemplateAuditDTO,
  MsgRouteRuleVO,
  RouteRuleUpsertDTO,
  MsgCanaryVO,
  CanaryUpsertDTO,
  CanaryHitQuery,
  CanaryReportVO,
  CanaryReportQuery,
  MessageStatsVO,
  ChannelStatsVO,
  ReceiptStatsVO,
  FunnelStatsVO,
  FunnelQuery,
  StatsQuery,
} from './types'

/** message 接口路径前缀（baseURL 由 VITE_API_BASE_URL 注入） */
const BASE = '/message'

// ==================== 消息日志 ====================

/**
 * 分页查询消息日志
 * @param params 分页与筛选条件
 * @returns 消息日志分页数据
 */
export const getMessageLogPage = (params: MessageLogPageQuery) =>
  request<PageData<MessageLogVO>>({ url: `${BASE}/log/page`, method: 'GET', params, silent: true })

/**
 * 查询批次发送进度
 * @param batchId 批次 ID
 * @param page 页码
 * @param size 每页条数
 * @returns 批次进度
 */
export const getBatchProgress = (batchId: string, page = 1, size = 10) =>
  request<PageData<BatchProgressVO>>({
    url: `${BASE}/batch/${batchId}/progress`,
    method: 'GET',
    params: { page, size },
    silent: true,
  })

/**
 * 同步发送消息
 * @param data 消息参数
 */
export const sendMessage = (data: unknown) =>
  request<void>({ url: `${BASE}/send`, method: 'POST', data })

/**
 * 直接发送消息（跳过聚合/异步队列）
 * @param data 消息参数
 */
export const sendDirectMessage = (data: unknown) =>
  request<void>({ url: `${BASE}/send-direct`, method: 'POST', data })

/**
 * 异步发送消息
 * @param data 消息参数
 */
export const sendAsyncMessage = (data: unknown) =>
  request<void>({ url: `${BASE}/send-async`, method: 'POST', data })

/**
 * 批量发送消息
 * @param batchId 批次 ID
 * @param data 消息列表
 */
export const batchSendMessage = (batchId: string, data: unknown) =>
  request<void>({ url: `${BASE}/batch-send`, method: 'POST', params: { batchId }, data })

// ==================== 模板管理 ====================

/**
 * 分页查询消息模板
 * @param params 模板分页与筛选条件
 * @returns 模板分页数据
 */
export const getTemplatePage = (params: TemplateQueryDTO) =>
  request<PageData<MsgTemplateVO>>({ url: `${BASE}/template/page`, method: 'GET', params, silent: true })

/**
 * 根据 ID 获取模板详情
 * @param id 模板 ID
 * @returns 模板详情
 */
export const getTemplateById = (id: string) =>
  request<MsgTemplateVO>({ url: `${BASE}/template/${id}`, method: 'GET', silent: true })

/**
 * 创建模板
 * @param data 模板参数
 */
export const createTemplate = (data: TemplateCreateDTO) =>
  request<void>({ url: `${BASE}/template`, method: 'POST', data })

/**
 * 更新模板
 * @param id 模板 ID
 * @param data 模板参数
 */
export const updateTemplate = (id: string, data: TemplateCreateDTO) =>
  request<void>({ url: `${BASE}/template/${id}`, method: 'PUT', data })

/**
 * 删除模板
 * @param id 模板 ID
 */
export const deleteTemplate = (id: string) =>
  request<void>({ url: `${BASE}/template/${id}`, method: 'DELETE' })

/**
 * 审核模板
 * @param id 模板 ID
 * @param data 审核参数
 */
export const auditTemplate = (id: string, data: TemplateAuditDTO) =>
  request<void>({ url: `${BASE}/template/${id}/audit`, method: 'POST', data })

// ==================== 路由规则 ====================

/**
 * 分页查询路由规则
 * @param params 分页条件
 * @returns 路由规则分页数据
 */
export const getRouteRulePage = (params: { page: number; size: number; bizType?: string; channel?: string; status?: string }) =>
  request<PageData<MsgRouteRuleVO>>({ url: `${BASE}/route-rule/page`, method: 'GET', params, silent: true })

/**
 * 根据 ID 获取路由规则详情
 * @param id 路由规则 ID
 * @returns 路由规则详情
 */
export const getRouteRuleById = (id: string) =>
  request<MsgRouteRuleVO>({ url: `${BASE}/route-rule/${id}`, method: 'GET', silent: true })

/**
 * 查询已启用的路由规则列表
 * @returns 已启用的路由规则列表
 */
export const getEnabledRouteRules = () =>
  request<MsgRouteRuleVO[]>({ url: `${BASE}/route-rule/enabled`, method: 'GET', silent: true })

/**
 * 创建路由规则
 * @param data 路由规则参数
 */
export const createRouteRule = (data: RouteRuleUpsertDTO) =>
  request<void>({ url: `${BASE}/route-rule`, method: 'POST', data })

/**
 * 更新路由规则
 * @param id 路由规则 ID
 * @param data 路由规则参数
 */
export const updateRouteRule = (id: string, data: RouteRuleUpsertDTO) =>
  request<void>({ url: `${BASE}/route-rule/${id}`, method: 'PUT', data })

/**
 * 删除路由规则
 * @param id 路由规则 ID
 */
export const deleteRouteRule = (id: string) =>
  request<void>({ url: `${BASE}/route-rule/${id}`, method: 'DELETE' })

// ==================== 灰度管理 ====================

/**
 * 分页查询灰度桶
 * @param params 分页条件
 * @returns 灰度桶分页数据
 */
export const getCanaryPage = (params: { page: number; size: number; canaryKey?: string; status?: string }) =>
  request<PageData<MsgCanaryVO>>({ url: `${BASE}/canary/page`, method: 'GET', params, silent: true })

/**
 * 根据灰度键获取灰度桶详情
 * @param canaryKey 灰度键
 * @returns 灰度桶详情
 */
export const getCanaryByKey = (canaryKey: string) =>
  request<MsgCanaryVO>({ url: `${BASE}/canary/${canaryKey}`, method: 'GET', silent: true })

/**
 * 新增/更新灰度桶（Upsert）
 * @param data 灰度桶参数
 */
export const upsertCanary = (data: CanaryUpsertDTO) =>
  request<void>({ url: `${BASE}/canary`, method: 'POST', data })

/**
 * 灰度命中检查
 * @param params 命中检查参数
 * @returns 是否命中
 */
export const checkCanaryHit = (params: CanaryHitQuery) =>
  request<boolean>({ url: `${BASE}/canary/hit`, method: 'GET', params, silent: true })

// ==================== 灰度 A/B 报表 ====================

/**
 * 查询灰度 A/B 实验报表
 * @param params 报表查询参数
 * @returns A/B 实验报表
 */
export const getCanaryReport = (params: CanaryReportQuery) =>
  request<CanaryReportVO>({ url: `${BASE}/canary/report`, method: 'GET', params, silent: true })

// ==================== 死信管理 ====================

/**
 * 分页查询死信（status 强制为 DEAD）
 * @param params 分页与筛选条件
 * @returns 死信分页数据
 */
export const getDeadLetterPage = (params: MessageLogPageQuery) =>
  request<PageData<MessageLogVO>>({ url: `${BASE}/dead-letter/page`, method: 'GET', params, silent: true })

/**
 * 重发死信
 * @param logId 日志 ID
 */
export const resendDeadLetter = (logId: string) =>
  request<void>({ url: `${BASE}/dead-letter/${logId}/resend`, method: 'POST' })

// ==================== 统计看板 ====================

/**
 * 查询消息发送总览统计
 * @param params 统计查询参数
 * @returns 总览统计
 */
export const getMessageStatsOverview = (params: StatsQuery) =>
  request<MessageStatsVO>({ url: `${BASE}/stats/overview`, method: 'GET', params, silent: true })

/**
 * 查询按通道维度的发送统计
 * @param params 统计查询参数
 * @returns 通道统计列表
 */
export const getChannelStats = (params: StatsQuery) =>
  request<ChannelStatsVO[]>({ url: `${BASE}/stats/channel`, method: 'GET', params, silent: true })

/**
 * 查询回执统计
 * @param params 统计查询参数
 * @returns 回执统计
 */
export const getReceiptStats = (params: StatsQuery) =>
  request<ReceiptStatsVO>({ url: `${BASE}/stats/receipt`, method: 'GET', params, silent: true })

/**
 * 查询消息转化漏斗（P2-2）
 * @param params 漏斗查询参数（含可选 channel/templateCode 过滤）
 * @returns 漏斗统计
 */
export const getFunnelStats = (params: FunnelQuery) =>
  request<FunnelStatsVO>({ url: `${BASE}/stats/funnel`, method: 'GET', params, silent: true })
