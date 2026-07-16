/**
 * @file 基础报表与高级报表 API 封装
 * @description 提供项目执行阶段的利润、成本、回款、生命周期等基础报表，
 *              以及 EVM、人效、Bench 成本、双费率对比、资源甘特图、风险看板等高级报表查询能力，
 *              对应后端 ReportController（/report）与
 *              AdvancedReportController（/report/advanced）。
 *              注：批次 20 已修复 P0 契约偏差，详见下方各方法注释。
 * @module api/report
 */
import { request } from '@/utils/request'

/**
 * 基础报表 + 高级报表 API 封装（批次 20 修复 P0 契约偏差）
 *
 * 修复点:
 * 1. getEvmReport - 后端 /evm 仅取 initiationId, period 字段忽略 (无害)
 * 2. getUtilizationReport - URL 修正为 /utilization-rank (无 initiationId)
 * 3. getBenchCostReport - 调整为 benchCostList (后端为 List<Map>)
 * 4. getDualRateComparison - 调整为 period 参数 (后端 /dual-rate 仅取 period)
 * 5. getResourceGantt - URL 修正为 /gantt + 必传 initiationId
 * 6. getRiskDashboard - 调整为 riskList (后端为 List<Map>)
 */

// ============= 基础报表 =============

/**
 * 项目利润表
 * @param initiationId 立项 ID
 * @param period 周期（YYYY-MM，可选）
 * @returns 项目利润报表数据
 */
export const getProjectProfitReport = (initiationId: number, period?: string) =>
  request<Record<string, unknown>>({
    url: '/api/project/report/profit',
    method: 'GET',
    params: { initiationId, period },
  })

/**
 * 项目成本归集明细
 * @param initiationId 立项 ID
 * @param period 周期（YYYY-MM，可选）
 * @returns 项目成本归集明细数据
 */
export const getCostDetailReport = (initiationId: number, period?: string) =>
  request<Record<string, unknown>>({
    url: '/api/project/report/cost',
    method: 'GET',
    params: { initiationId, period },
  })

/**
 * 项目回款台账
 * @param initiationId 立项 ID
 * @returns 项目回款台账数据
 */
export const getPaymentLedger = (initiationId: number) =>
  request<Record<string, unknown>>({
    url: '/api/project/report/payment-ledger',
    method: 'GET',
    params: { initiationId },
  })

/**
 * 项目全生命周期台账
 * @param initiationId 立项 ID
 * @returns 项目全生命周期台账数据
 */
export const getLifecycleReport = (initiationId: number) =>
  request<Record<string, unknown>>({
    url: '/api/project/report/lifecycle',
    method: 'GET',
    params: { initiationId },
  })

/**
 * 跨项目利润汇总
 * @returns 跨项目利润汇总列表
 */
export const getProfitSummary = () =>
  request<Array<Record<string, unknown>>>({
    url: '/api/project/report/profit-summary',
    method: 'GET',
  })

/**
 * 项目利润排行榜 (P2-1)
 * @param top 取前 N 名，默认 10
 * @param sortBy 排序字段（可选）
 * @param period 周期（YYYY-MM，可选）
 * @returns 项目利润排行列表
 */
export const getProfitRank = (top = 10, sortBy?: string, period?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/api/project/report/profit-rank',
    method: 'GET',
    params: { top, sortBy, period },
  })

// ============= 高级报表 =============

/**
 * EVM 挣值管理报表
 * 后端: GET /report/advanced/evm?initiationId=...
 * @param initiationId 立项 ID
 * @returns EVM 挣值管理报表数据列表
 */
export const getEvmReport = (initiationId: number) =>
  request<Array<Record<string, unknown>>>({
    url: '/api/project/report/advanced/evm',
    method: 'GET',
    params: { initiationId },
  })

/**
 * 人效排行榜 (默认近 3 个月)
 * 后端: GET /report/advanced/utilization-rank
 * @param top 取前 N 名，默认 20
 * @returns 人效排行榜列表
 */
export const getUtilizationRank = (top = 20) =>
  request<Array<Record<string, unknown>>>({
    url: '/api/project/report/advanced/utilization-rank',
    method: 'GET',
    params: { top },
  })

/**
 * 利用率报表 (别名, 兼容旧调用)
 * @param top 取前 N 名，默认 20
 * @returns 人效排行榜列表（同 getUtilizationRank 返回）
 */
export const getUtilizationReport = (top = 20) => getUtilizationRank(top)

/**
 * Bench 成本报表 (默认近 30 天, 后端为 List)
 * 后端: GET /report/advanced/bench-cost
 * @returns Bench 成本报表列表
 */
export const getBenchCostReport = () =>
  request<Array<Record<string, unknown>>>({
    url: '/api/project/report/advanced/bench-cost',
    method: 'GET',
  })

/**
 * 双费率利润对比 (后端按 period 全局聚合)
 * 后端: GET /report/advanced/dual-rate?period=YYYY-MM
 * @param period 周期（YYYY-MM，可选）
 * @returns 双费率利润对比列表
 */
export const getDualRateComparison = (period?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/api/project/report/advanced/dual-rate',
    method: 'GET',
    params: { period },
  })

/**
 * 资源甘特图 (必传 initiationId)
 * 后端: GET /report/advanced/gantt?initiationId=...
 * @param initiationId 立项 ID
 * @returns 资源甘特图数据列表
 */
export const getResourceGantt = (initiationId: number) =>
  request<Array<Record<string, unknown>>>({
    url: '/api/project/report/advanced/gantt',
    method: 'GET',
    params: { initiationId },
  })

/**
 * 项目风险预警看板 (后端为 List)
 * 后端: GET /report/advanced/risk-dashboard
 * @returns 项目风险预警看板数据列表
 */
export const getRiskDashboard = () =>
  request<Array<Record<string, unknown>>>({
    url: '/api/project/report/advanced/risk-dashboard',
    method: 'GET',
  })
