import { request } from '@/utils/request'

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

// ============= 高级报表 =============

/** EVM 报表 */
export const getEvmReport = (initiationId: number, period?: string) =>
  request<Record<string, unknown>>({
    url: '/execution/advanced-report/evm',
    method: 'GET',
    params: { initiationId, period },
  })

/** 利用率报表 */
export const getUtilizationReport = (period?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/advanced-report/utilization',
    method: 'GET',
    params: { period },
  })

/** Bench 成本报表 */
export const getBenchCostReport = (period?: string) =>
  request<Record<string, unknown>>({
    url: '/execution/advanced-report/bench-cost',
    method: 'GET',
    params: { period },
  })

/** 双费率利润对比 */
export const getDualRateComparison = (initiationId: number) =>
  request<Record<string, unknown>>({
    url: '/execution/advanced-report/dual-rate',
    method: 'GET',
    params: { initiationId },
  })

/** 资源甘特图 */
export const getResourceGantt = (period?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/advanced-report/resource-gantt',
    method: 'GET',
    params: { period },
  })

/** 项目风险预警看板 */
export const getRiskDashboard = (period?: string) =>
  request<Record<string, unknown>>({
    url: '/execution/advanced-report/risk-dashboard',
    method: 'GET',
    params: { period },
  })
