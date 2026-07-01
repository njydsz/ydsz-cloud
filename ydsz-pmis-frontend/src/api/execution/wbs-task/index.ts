/**
 * @file WBS 任务管理 API
 * @description 提供项目执行阶段 WBS（工作分解结构）任务的增删改查及状态变更能力，
 *              对应后端 WbsTaskController（/execution/wbs-task）。
 * @module api/execution/wbs-task
 */
import { request } from '@/utils/request'
import type { WbsTaskVO, WbsTaskCreateDTO, WbsTaskStatusDTO } from './types'

/**
 * 分页查询 WBS 任务列表
 * @param page 当前页码（从 1 开始）
 * @param size 每页条数
 * @param params 可选查询条件：关键字、状态、立项 ID、责任人 ID
 * @returns WBS 任务分页结果
 */
export const pageWbsTasks = (
  page: number,
  size: number,
  params?: { keyword?: string; status?: string; initiationId?: number; ownerId?: number },
) =>
  request<PageResult<WbsTaskVO>>({
    url: '/execution/wbs-task/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 根据 ID 获取 WBS 任务详情
 * @param id 任务 ID
 * @returns WBS 任务详情
 */
export const getWbsTask = (id: number) =>
  request<WbsTaskVO>({ url: `/execution/wbs-task/${id}`, method: 'GET' })

/**
 * 新建 WBS 任务
 * @param data 任务创建参数
 * @returns 新建任务的 ID
 */
export const createWbsTask = (data: WbsTaskCreateDTO) =>
  request<number>({ url: '/execution/wbs-task', method: 'POST', data })

/**
 * 更新 WBS 任务信息
 * @param data 任务更新参数（必须包含 id）
 * @returns 无返回值
 */
export const updateWbsTask = (data: Partial<WbsTaskVO> & { id: number }) =>
  request<void>({ url: '/execution/wbs-task', method: 'PUT', data })

/**
 * 变更 WBS 任务状态（含进度更新）
 * @param data 状态变更参数
 * @returns 无返回值
 */
export const changeWbsTaskStatus = (data: WbsTaskStatusDTO) =>
  request<void>({ url: '/execution/wbs-task/status', method: 'PUT', data })

/**
 * 根据 ID 删除 WBS 任务
 * @param id 任务 ID
 * @returns 无返回值
 */
export const deleteWbsTask = (id: number) =>
  request<void>({ url: `/execution/wbs-task/${id}`, method: 'DELETE' })
