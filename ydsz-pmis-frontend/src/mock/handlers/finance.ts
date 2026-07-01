/**
 * 财务模块 mock (发票/回款/客户信用)
 */
import type { MockHandler } from './types'

export const financeHandlers: MockHandler[] = [
  {
    method: 'GET',
    path: '/finance/invoice/page',
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
  {
    method: 'GET',
    path: '/finance/payment/page',
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
  {
    method: 'GET',
    path: '/finance/customer-credit/page',
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
