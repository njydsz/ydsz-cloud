/**
 * 利润测算 API
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { ProfitSimulationVO, ProfitSimulationCreateDTO, SimulationStatusDTO } from './types'

export * from './types'

export const pageProfitSimulations = (
  page: number,
  size: number,
  params?: { initiationId?: number; scenarioType?: string; status?: string },
) =>
  request<PageResult<ProfitSimulationVO>>({
    url: '/execution/profit-simulation/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

export const getProfitSimulation = (id: number) =>
  request<ProfitSimulationVO>({
    url: `/execution/profit-simulation/${id}`,
    method: 'GET',
  })

export const createProfitSimulation = (data: ProfitSimulationCreateDTO) =>
  request<number>({ url: '/execution/profit-simulation', method: 'POST', data })

export const changeSimulationStatus = (data: SimulationStatusDTO) =>
  request<void>({ url: '/execution/profit-simulation/status', method: 'PUT', data })

export const deleteProfitSimulation = (id: number) =>
  request<void>({ url: `/execution/profit-simulation/${id}`, method: 'DELETE' })

export const compareSimulations = (initiationId: number) =>
  request<Array<Record<string, unknown>>>({
    url: '/execution/profit-simulation/compare',
    method: 'GET',
    params: { initiationId },
  })
