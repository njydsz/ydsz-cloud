/**
 * 项目模块 mock (商机/立项/合同/变更)
 */
import type { MockHandler } from './types'

const list = (n: number, factory: (i: number) => Record<string, unknown>) =>
  Array.from({ length: n }, (_, i) => factory(i + 1))

/**
 * 简易 in-memory 存储, 让 E2E 测试能验证创建/状态变更结果
 */
const initiationStore: Record<string, Record<string, unknown>> = {}
const contractStore: Record<string, Record<string, unknown>> = {}
const riskStore: Record<string, Record<string, unknown>> = {}
const alertStore: Record<string, Record<string, unknown>> = {}
let nextSeq = 1000

export const projectHandlers: MockHandler[] = [
  {
    method: 'GET',
    path: '/opportunity/page',
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
    path: '/initiation/page',
    handler: ({ query }) => {
      const stored = Object.values(initiationStore)
      const fixtureList = list(Number(query.size || 10), (i) => ({
        id: i,
        initiationCode: `INIT-${String(i).padStart(4, '0')}`,
        initiationName: `项目${i}`,
        pmName: `PM${i % 5}`,
        customerName: `客户${i % 8}`,
        budgetTotal: 500000 * i,
        status: ['DRAFT', 'APPROVED', 'EXECUTING', 'CLOSED'][i % 4],
        createdAt: '2026-06-15 10:00:00',
      }))
      // 真实创建的优先返回 (供 E2E 校验)
      const merged = [...stored.map((s) => ({ ...s })), ...fixtureList].slice(0, Number(query.size || 10))
      return {
        list: merged,
        total: stored.length + 80,
        page: Number(query.page || 1),
        size: Number(query.size || 10),
        pages: Math.ceil((stored.length + 80) / Number(query.size || 10)),
      }
    },
  },
  {
    method: 'POST',
    path: '/initiation',
    handler: ({ body }) => {
      const b = (body || {}) as Record<string, unknown>
      const id = ++nextSeq
      const code = (b.initiationCode as string) || `INIT-${String(id).padStart(4, '0')}`
      const rec = {
        id,
        initiationCode: code,
        initiationName: b.initiationName || b.name || `项目${id}`,
        pmName: 'PM-E2E',
        customerName: b.customerName || '客户',
        budgetTotal: b.budgetTotal || b.amount || 0,
        status: 'DRAFT',
        createdAt: new Date().toISOString(),
      }
      initiationStore[code] = rec
      return rec
    },
  },
  {
    method: 'POST',
    path: '/initiation/{id}/submit',
    handler: ({ body }) => {
      const id = (body as { id?: number })?.id || 0
      // 更新 store
      const obj = Object.values(initiationStore).find((s) => s.id === id)
      if (obj) obj.status = 'SUBMITTED'
      return { success: true, id, status: 'SUBMITTED' }
    },
  },
  {
    method: 'POST',
    path: '/initiation/{id}/approve',
    handler: ({ body }) => {
      const id = (body as { id?: number })?.id || 0
      const obj = Object.values(initiationStore).find((s) => s.id === id)
      if (obj) obj.status = 'APPROVED'
      return { success: true, id, status: 'APPROVED' }
    },
  },
  {
    method: 'GET',
    path: '/contract/page',
    handler: ({ query }) => {
      const stored = Object.values(contractStore)
      const fixtureList = list(Number(query.size || 10), (i) => ({
        id: i,
        contractCode: `CT-${String(i).padStart(4, '0')}`,
        contractName: `合同${i}`,
        customerName: `客户${i % 8}`,
        amount: 800000 * i,
        status: ['DRAFT', 'SIGNED', 'EXECUTING', 'COMPLETED'][i % 4],
        signDate: '2026-06-20',
      }))
      const merged = [...stored.map((s) => ({ ...s })), ...fixtureList].slice(0, Number(query.size || 10))
      return {
        list: merged,
        total: stored.length + 60,
        page: Number(query.page || 1),
        size: Number(query.size || 10),
        pages: Math.ceil((stored.length + 60) / Number(query.size || 10)),
      }
    },
  },
  {
    method: 'POST',
    path: '/contract',
    handler: ({ body }) => {
      const b = (body || {}) as Record<string, unknown>
      const id = ++nextSeq
      const code = (b.contractCode as string) || `CT-${String(id).padStart(4, '0')}`
      const rec = {
        id,
        contractCode: code,
        contractName: b.contractName || b.name || `合同${id}`,
        customerName: b.customerName || '客户',
        amount: b.amount || 0,
        status: 'DRAFT',
        signDate: '2026-06-25',
      }
      contractStore[code] = rec
      return rec
    },
  },
  {
    method: 'GET',
    path: '/initiation/change/page',
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

/**
 * 暴露 stores 给其他 handler 模块复用 (风险/预警联动)
 */
export { initiationStore, contractStore, riskStore, alertStore }
