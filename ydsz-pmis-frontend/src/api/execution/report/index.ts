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

/** 项目利润表 */
export const getProjectProfitReport = (initiationId: number, period?: string) =>
  request<Record<string, unknown>>({
    url: '/execution/report/profit',
    method: 'GET',
    params: { initiationId, period },
  })

/** 项目成本归集明细 */
export const getCostDetailReport = (initiationId: number, period?: string) =>
  request<Record<string, unknown>>({
    url: '/execution/report/cost',
    method: 'GET',
    params: { initiationId, period },
  })

/** 项目回款台账 */
export const getPaymentLedger = (initiationId: number) =>
  request<Record<string, unknown>>({
    url: '/execution/report/payment-ledger',
    method: 'GET',
    params: { initiationId },
  })

/** 项目全生命周期台账 */
export const getLifecycleReport = (initiationId: number) =>
  request<Record<string, unknown>>({
    url: '/execution/report/lifecycle',
    method: 'GET',
    params: { initiationId },
  })

/** 跨项目利润汇总 */
export const getProfitSummary = () =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/report/profit-summary',
    method: 'GET',
  })

/** 项目利润排行榜 (P2-1) */
export const getProfitRank = (top = 10, sortBy?: string, period?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/report/profit-rank',
    method: 'GET',
    params: { top, sortBy, period },
  })

// ============= 高级报表 =============

/**
 * EVM 挣值管理报表
 * 后端: GET /api/v1/execution/advanced-report/evm?initiationId=...
 */
export const getEvmReport = (initiationId: number) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/advanced-report/evm',
    method: 'GET',
    params: { initiationId },
  })

/**
 * 人效排行榜 (默认近 3 个月)
 * 后端: GET /api/v1/execution/advanced-report/utilization-rank
 */
export const getUtilizationRank = (top = 20) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/advanced-report/utilization-rank',
    method: 'GET',
    params: { top },
  })

/** 利用率报表 (别名, 兼容旧调用) */
export const getUtilizationReport = (top = 20) => getUtilizationRank(top)

/**
 * Bench 成本报表 (默认近 30 天, 后端为 List)
 * 后端: GET /api/v1/execution/advanced-report/bench-cost
 */
export const getBenchCostReport = () =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/advanced-report/bench-cost',
    method: 'GET',
  })

/**
 * 双费率利润对比 (后端按 period 全局聚合)
 * 后端: GET /api/v1/execution/advanced-report/dual-rate?period=YYYY-MM
 */
export const getDualRateComparison = (period?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/advanced-report/dual-rate',
    method: 'GET',
    params: { period },
  })

/**
 * 资源甘特图 (必传 initiationId)
 * 后端: GET /api/v1/execution/advanced-report/gantt?initiationId=...
 */
export const getResourceGantt = (initiationId: number) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/advanced-report/gantt',
    method: 'GET',
    params: { initiationId },
  })

/**
 * 项目风险预警看板 (后端为 List)
 * 后端: GET /api/v1/execution/advanced-report/risk-dashboard
 */
export const getRiskDashboard = () =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/advanced-report/risk-dashboard',
    method: 'GET',
  })
