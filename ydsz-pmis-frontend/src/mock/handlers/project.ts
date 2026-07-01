/**
 * 项目模块 mock (商机/立项/合同/变更)
 */
import type { MockHandler } from './types'

const list = (n: number, factory: (i: number) => Record<string, unknown>) =>
  Array.from({ length: n }, (_, i) => factory(i + 1))

export const projectHandlers: MockHandler[] = [
  {
    method: 'GET',
    path: '/project/opportunity/page',
    handler: ({ query }) => ({
      list: list(Number(query.size || 10), (i) => ({
        id: i,
        opportunityCode: `OPP-${String(i).padStart(4, '0')}`,
        opportunityName: `商机${i}`,
        customerName: `客户${i}`,
        estimatedAmount: 100000 * i,
        status: ['NEW', 'FOLLOWING', 'WON', 'LOST', 'CONVERTED'][i % 5],
        ownerName: `销售${i % 5}`,
        createdAt: '2026-06-01 10:00:00',
      })),
      total: 100,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 10,
    }),
  },
  {
    method: 'GET',
    path: '/project/initiation/page',
    handler: ({ query }) => ({
      list: list(Number(query.size || 10), (i) => ({
        id: i,
        initiationCode: `INIT-${String(i).padStart(4, '0')}`,
        initiationName: `项目${i}`,
        pmName: `PM${i % 5}`,
        customerName: `客户${i % 8}`,
        budgetTotal: 500000 * i,
        status: ['DRAFT', 'APPROVED', 'EXECUTING', 'CLOSED'][i % 4],
        createdAt: '2026-06-15 10:00:00',
      })),
      total: 80,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 8,
    }),
  },
  {
    method: 'GET',
    path: '/project/contract/page',
    handler: ({ query }) => ({
      list: list(Number(query.size || 10), (i) => ({
        id: i,
        contractCode: `CT-${String(i).padStart(4, '0')}`,
        contractName: `合同${i}`,
        customerName: `客户${i % 8}`,
        amount: 800000 * i,
        status: ['DRAFT', 'SIGNED', 'EXECUTING', 'COMPLETED'][i % 4],
        signDate: '2026-06-20',
      })),
      total: 60,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 6,
    }),
  },
  {
    method: 'GET',
    path: '/project/change/page',
    handler: ({ query }) => ({
      list: list(Number(query.size || 10), (i) => ({
        id: i,
        changeCode: `CHG-${String(i).padStart(4, '0')}`,
        changeTitle: `变更${i}`,
        changeType: ['SCOPE', 'BUDGET', 'SCHEDULE'][i % 3],
        status: ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'EXECUTING'][i % 6],
        majorFlag: i % 4 === 0 ? 1 : 0,
      })),
      total: 50,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 5,
    }),
  },
]
