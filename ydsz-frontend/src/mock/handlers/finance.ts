/**
 * @file 财务模块 Mock 数据处理器
 * @description 为财务模块的发票、回款、客户信用等 API 路径提供 Mock 数据
 * @module mock/handlers/finance
 */
import type { MockHandler } from './types'

/**
 * 财务模块 Mock 处理器集合
 * @returns {MockHandler[]} 覆盖发票分页、回款分页、客户信用分页等接口的 Mock 处理器
 */
export const financeHandlers: MockHandler[] = [
  // ===== 发票分页查询（含发票号、金额、状态、开票日期、客户） =====
  {
    method: 'GET',
    path: '/api/project/finance/invoice/page',
    handler: ({ query }) => ({
      list: Array.from({ length: Number(query.size || 10) }, (_, i) => ({
        id: i + 1,
        invoiceCode: `INV-${String(i + 1).padStart(4, '0')}`,
        invoiceNo: i % 2 === 0 ? `12345678${String(i).padStart(4, '0')}` : null,
        amount: 50000 * (i + 1),
        status: ['DRAFT', 'ISSUED', 'CANCELLED', 'RED_REVERSED'][i % 4],
        issueDate: '2026-06-15',
        customerName: `客户${(i % 8) + 1}`,
      })),
      total: 60,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 6,
    }),
  },
  // ===== 回款分页查询（含回款编码、金额、状态、到账日期、客户） =====
  {
    method: 'GET',
    path: '/api/project/finance/payment/page',
    handler: ({ query }) => ({
      list: Array.from({ length: Number(query.size || 10) }, (_, i) => ({
        id: i + 1,
        paymentCode: `PAY-${String(i + 1).padStart(4, '0')}`,
        amount: 30000 * (i + 1),
        status: ['PENDING', 'RECEIVED', 'ALLOCATED'][i % 3],
        receiveDate: '2026-06-20',
        customerName: `客户${(i % 8) + 1}`,
      })),
      total: 50,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 5,
    }),
  },
  // ===== 客户信用分页查询（含评分、等级、逾期金额、最近更新时间） =====
  {
    method: 'GET',
    path: '/api/project/finance/customer-credit/page',
    handler: ({ query }) => ({
      list: Array.from({ length: Number(query.size || 10) }, (_, i) => ({
        id: i + 1,
        customerName: `客户${i + 1}`,
        score: 30 + (i * 7) % 70,
        level: ['A', 'B', 'C', 'D'][i % 4],
        overdueAmount: i % 5 === 0 ? 5000 : 0,
        lastUpdated: '2026-06-25',
      })),
      total: 30,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 3,
    }),
  },
]
