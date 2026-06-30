import { request } from '@/utils/request'

/** 驾驶舱总览 KPI */
export const getCockpitOverview = (period?: string, drillDown?: Record<string, unknown>) =>
  request<Record<string, unknown>>({
    url: '/execution/cockpit/overview',
    method: 'GET',
    params: { period, ...(drillDown || {}) },
  })

/** EVM 健康度分布 */
export const getEvmHealthDistribution = (period?: string) =>
  request<Record<string, number>>({
    url: '/execution/cockpit/evm-health',
    method: 'GET',
    params: { period },
  })

/** Bench 闲置成本汇总 */
export const getBenchCostSummary = () =>
  request<Record<string, unknown>>({
    url: '/execution/cockpit/bench-cost',
    method: 'GET',
  })

/** 可计费利用率汇总 */
export const getUtilizationSummary = () =>
  request<Record<string, unknown>>({
    url: '/execution/cockpit/utilization',
    method: 'GET',
  })

/** 按事业部下钻 */
export const drillByDept = (period?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/cockpit/drill/dept',
    method: 'GET',
    params: { period },
  })

/** 按项目类型下钻 */
export const drillByProjectType = (period?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/cockpit/drill/project-type',
    method: 'GET',
    params: { period },
  })

/** 按客户下钻 */
export const drillByCustomer = (period?: string) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/cockpit/drill/customer',
    method: 'GET',
    params: { period },
  })
