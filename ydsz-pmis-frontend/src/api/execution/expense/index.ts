/**
 * @file 费用报销管理 API
 * @description 提供项目执行阶段员工费用报销的增删改查及状态变更（审批）能力，
 *              对应后端 ExpenseController（/execution/expense）。
 * @module api/execution/expense
 */
import { request } from '@/utils/request'
import type { ExpenseVO, ExpenseCreateDTO, ApprovalDTO } from './types'

/**
 * 分页查询费用报销记录
 * @param page 当前页码（从 1 开始）
 * @param size 每页条数
 * @param params 可选查询条件：关键字、状态、费用类型、员工 ID、立项 ID
 * @returns 费用报销分页结果
 */
export const pageExpenses = (
  page: number,
  size: number,
  params?: {
    keyword?: string
    status?: string
    expenseType?: string
    employeeId?: number
    initiationId?: number
  },
) =>
  request<PageResult<ExpenseVO>>({
    url: '/execution/expense/page',
    method: 'GET',
    params: { page, size, ...(params || {}) },
  })

/**
 * 根据 ID 获取费用报销详情
 * @param id 费用报销 ID
 * @returns 费用报销详情
 */
export const getExpense = (id: number) =>
  request<ExpenseVO>({ url: `/execution/expense/${id}`, method: 'GET' })

/**
 * 新建费用报销记录
 * @param data 费用报销创建参数
 * @returns 新建费用报销的 ID
 */
export const createExpense = (data: ExpenseCreateDTO) =>
  request<number>({ url: '/execution/expense', method: 'POST', data })

/**
 * 变更费用报销状态（审批/驳回/付款等）
 * @param data 审批状态变更参数
 * @returns 无返回值
 */
export const changeExpenseStatus = (data: ApprovalDTO) =>
  request<void>({ url: '/execution/expense/status', method: 'PUT', data })

/**
 * 根据 ID 删除费用报销记录
 * @param id 费用报销 ID
 * @returns 无返回值
 */
export const deleteExpense = (id: number) =>
  request<void>({ url: `/execution/expense/${id}`, method: 'DELETE' })
