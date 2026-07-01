/**
 * @file 项目变更管理 API 接口封装
 * @description 提供项目变更（ProjectChange）模块的增删改查、状态迁移、按立项查询、聚合统计及合法状态迁移查询等接口；
 *              对应后端 ProjectChangeController（/project/change）。批次 19 补全。
 * @module api/project/change
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

/**
 * 分页查询项目变更列表
 * @param page 当前页码（从 1 开始）
 * @param size 每页条数
 * @param params 查询条件：keyword 关键字、changeType 变更类型、status 状态、initiationId 立项 ID
 * @returns 项目变更分页结果
 */
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

/**
 * 查询变更详情
 * @param id 变更记录 ID
 * @returns 变更详情对象
 */
export const getProjectChange = (id: number) =>
  request<ProjectChangeVO>({ url: `/project/change/${id}`, method: 'GET' })

/**
 * 创建变更（后端自动调用 ChangeImpactEvaluator 评估影响等级 + 重大变更标识）
 * @param data 变更创建数据
 * @returns 新建变更记录 ID
 */
export const createProjectChange = (data: ProjectChangeCreateDTO) =>
  request<number>({ url: '/project/change', method: 'POST', data })

/**
 * 状态迁移（后端校验 canTransitTo 合法性）
 * @param data 状态迁移入参（包含 id、目标状态）
 * @returns 无返回值
 */
export const changeProjectChangeStatus = (data: ProjectChangeStatusDTO) =>
  request<void>({ url: '/project/change/status', method: 'PUT', data })

/**
 * 删除变更（仅 DRAFT/REJECTED/CANCELLED 可删）
 * @param id 变更记录 ID
 * @returns 无返回值
 */
export const deleteProjectChange = (id: number) =>
  request<void>({ url: `/project/change/${id}`, method: 'DELETE' })

/**
 * 按立项查询变更列表
 * @param initiationId 立项 ID
 * @returns 该立项下的变更记录列表
 */
export const listProjectChangesByInitiation = (initiationId: number) =>
  request<ProjectChangeVO[]>({
    url: `/project/change/list-by-initiation/${initiationId}`,
    method: 'GET',
  })

/**
 * 按变更类型聚合
 * @param tenantId 租户 ID（可选）
 * @returns 按变更类型分组的聚合结果列表
 */
export const aggregateProjectChangeByType = (tenantId?: number) =>
  request<ProjectChangeAggregateRow[]>({
    url: '/project/change/aggregate/type',
    method: 'GET',
    params: { tenantId },
  })

/**
 * 按状态聚合
 * @param tenantId 租户 ID（可选）
 * @returns 按状态分组的聚合结果列表
 */
export const aggregateProjectChangeByStatus = (tenantId?: number) =>
  request<ProjectChangeStatusAggregateRow[]>({
    url: '/project/change/aggregate/status',
    method: 'GET',
    params: { tenantId },
  })

/**
 * 统计项目重大变更数
 * @param initiationId 立项 ID
 * @returns 重大变更记录数
 */
export const countMajorProjectChange = (initiationId: number) =>
  request<number>({
    url: `/project/change/major-count/${initiationId}`,
    method: 'GET',
  })

/**
 * 获取某条变更的合法状态迁移列表 (基于 canTransitTo 服务端计算)
 * @param id 变更记录 ID
 * @returns 合法的下一个状态代码列表
 */
export const getAllowedTransitions = (id: number) =>
  request<string[]>({
    url: `/project/change/${id}/allowed-transitions`,
    method: 'GET',
  })
