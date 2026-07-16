/**
 * @file 采购申请管理 API
 * @description 提供项目执行阶段采购申请的增删改查及状态变更（审批）能力，
 *              对应后端 PurchaseController（/execution/purchase）。
 * @module api/execution/purchase
 */
import { request } from '@/utils/request'
import type { PurchaseVO, PurchaseCreateDTO, ApprovalDTO } from './types'

/**
 * 分页查询采购申请列表
 * @param page 当前页码（从 1 开始）
 * @param size 每页条数
 * @param params 可选查询条件：关键字、状态、立项 ID
 * @returns 采购申请分页结果
 */
export const pagePurchases = (
  page: number,
  size: number,
  params?: { keyword?: string; status?: string; initiationId?: number },
) =>
  request<PageResult<PurchaseVO>>({
    url: '/api/project/execution/purchase/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 根据 ID 获取采购申请详情
 * @param id 采购申请 ID
 * @returns 采购申请详情
 */
export const getPurchase = (id: number) =>
  request<PurchaseVO>({ url: `/api/project/execution/purchase/${id}`, method: 'GET' })

/**
 * 新建采购申请
 * @param data 采购申请创建参数
 * @returns 新建采购申请的 ID
 */
export const createPurchase = (data: PurchaseCreateDTO) =>
  request<number>({ url: '/api/project/execution/purchase', method: 'POST', data })

/**
 * 变更采购申请状态（审批/驳回/收货/付款等）
 * @param data 审批状态变更参数
 * @returns 无返回值
 */
export const changePurchaseStatus = (data: ApprovalDTO) =>
  request<void>({ url: '/api/project/execution/purchase/status', method: 'PUT', data })

/**
 * 根据 ID 删除采购申请
 * @param id 采购申请 ID
 * @returns 无返回值
 */
export const deletePurchase = (id: number) =>
  request<void>({ url: `/api/project/execution/purchase/${id}`, method: 'DELETE' })
