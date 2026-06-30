/**
 * EVM 挣值管理 API
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type {
  EvmMeasureVO,
  EvmMeasureCreateDTO,
  EvmDashboardVO,
} from './types'

export * from './types'

/** 分页查询 EVM 测量记录 */
export const pageEvm = (
  page: number,
  size: number,
  params?: { initiationId?: number; alertLevel?: string },
) =>
  request<PageResult<EvmMeasureVO>>({
    url: '/execution/evm/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/** 详情 */
export const getEvm = (id: number) =>
  request<EvmMeasureVO>({ url: `/execution/evm/${id}`, method: 'GET' })

/** 录入 / 更新（按 initiation+wbs+period 幂等） */
export const saveEvm = (data: EvmMeasureCreateDTO) =>
  request<number>({ url: '/execution/evm', method: 'POST', data })

/** 删除 */
export const deleteEvm = (id: number) =>
  request<void>({ url: `/execution/evm/${id}`, method: 'DELETE' })

/** 按项目查询 */
export const listEvmByInitiation = (initiationId: number) =>
  request<EvmMeasureVO[]>({
    url: '/execution/evm/by-initiation',
    method: 'GET',
    params: { initiationId },
  })

/** 项目 EVM 健康仪表盘（汇总：CPI/SPI 平均、EAC/VAC、趋势、预警条数） */
export const getEvmDashboard = (initiationId: number) =>
  request<EvmDashboardVO>({
    url: '/execution/evm/dashboard',
    method: 'GET',
    params: { initiationId },
  })
