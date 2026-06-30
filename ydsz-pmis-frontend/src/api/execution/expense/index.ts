import { request } from '@/utils/request'
import type { ExpenseVO, ExpenseCreateDTO, ApprovalDTO } from './types'

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

export const getExpense = (id: number) =>
  request<ExpenseVO>({ url: `/execution/expense/${id}`, method: 'GET' })

export const createExpense = (data: ExpenseCreateDTO) =>
  request<number>({ url: '/execution/expense', method: 'POST', data })

export const changeExpenseStatus = (data: ApprovalDTO) =>
  request<void>({ url: '/execution/expense/status', method: 'PUT', data })

export const deleteExpense = (id: number) =>
  request<void>({ url: `/execution/expense/${id}`, method: 'DELETE' })
