/**
 * 项目变更管理 API 封装（批次 19 补全）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { request } from '@/utils/request'
import type {
  ProjectChangeVO,
  ProjectChangeCreateDTO,
  ProjectChangeStatusDTO,
  ProjectChangeAggregateRow,
  ProjectChangeStatusAggregateRow,
} from './types'

/** 分页查询 */
export const pageProjectChanges = (
  page: number,
  size: number,
  params?: {
    keyword?: string
    changeType?: string
    status?: string
    initiationId?: number
  },
) =>
  request<PageResult<ProjectChangeVO>>({
    url: '/project/change/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/** 变更详情 */
export const getProjectChange = (id: number) =>
  request<ProjectChangeVO>({ url: `/project/change/${id}`, method: 'GET' })

/** 创建变更（后端自动调用 ChangeImpactEvaluator 评估影响等级 + 重大变更标识） */
export const createProjectChange = (data: ProjectChangeCreateDTO) =>
  request<number>({ url: '/project/change', method: 'POST', data })

/** 状态迁移（后端校验 canTransitTo 合法性） */
export const changeProjectChangeStatus = (data: ProjectChangeStatusDTO) =>
  request<void>({ url: '/project/change/status', method: 'PUT', data })

/** 删除变更（仅 DRAFT/REJECTED/CANCELLED 可删） */
export const deleteProjectChange = (id: number) =>
  request<void>({ url: `/project/change/${id}`, method: 'DELETE' })

/** 按立项查询变更列表 */
export const listProjectChangesByInitiation = (initiationId: number) =>
  request<ProjectChangeVO[]>({
    url: `/project/change/list-by-initiation/${initiationId}`,
    method: 'GET',
  })

/** 按变更类型聚合 */
export const aggregateProjectChangeByType = (tenantId?: number) =>
  request<ProjectChangeAggregateRow[]>({
    url: '/project/change/aggregate/type',
    method: 'GET',
    params: { tenantId },
  })

/** 按状态聚合 */
export const aggregateProjectChangeByStatus = (tenantId?: number) =>
  request<ProjectChangeStatusAggregateRow[]>({
    url: '/project/change/aggregate/status',
    method: 'GET',
    params: { tenantId },
  })

/** 统计项目重大变更数 */
export const countMajorProjectChange = (initiationId: number) =>
  request<number>({
    url: `/project/change/major-count/${initiationId}`,
    method: 'GET',
  })

/** 获取某条变更的合法状态迁移列表 (基于 canTransitTo 服务端计算) */
export const getAllowedTransitions = (id: number) =>
  request<string[]>({
    url: `/project/change/${id}/allowed-transitions`,
    method: 'GET',
  })
