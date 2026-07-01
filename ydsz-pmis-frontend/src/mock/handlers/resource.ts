/**
 * @file 资源模块 Mock 数据处理器
 * @description 为资源池、资源分配、Bench 闲置等资源管理类接口提供 Mock 数据。
 * @module mock/handlers/resource
 */
import type { MockHandler } from './types'

/**
 * 资源模块 Mock 处理器集合
 *
 * 覆盖端点:
 * - GET /user/resource/pool/page        资源池分页查询
 * - GET /user/resource/assignment/page  资源分配分页查询
 * - GET /user/resource/bench/page       Bench 闲置资源分页查询
 *
 * @returns 资源模块所有 Mock 处理器数组
 */
export const resourceHandlers: MockHandler[] = [
  {
    method: 'GET',
    path: '/user/resource/pool/page',
    handler: ({ query }) => ({
      list: Array.from({ length: Number(query.size || 10) }, (_, i) => ({
        id: i + 1,
        employeeId: 1000 + i,
        employeeName: `员工${i + 1}`,
        department: `部门${(i % 5) + 1}`,
        level: `L${(i % 12) + 1}`,
        poolType: ['RESERVE', 'DIVISION', 'HQ'][i % 3],
        utilization: 50 + (i * 3) % 50,
      })),
      total: 100,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 10,
    }),
  },
  {
    method: 'GET',
    path: '/user/resource/assignment/page',
    handler: ({ query }) => ({
      list: Array.from({ length: Number(query.size || 10) }, (_, i) => ({
        id: i + 1,
        employeeName: `员工${i + 1}`,
        projectName: `项目${(i % 5) + 1}`,
        startDate: '2026-06-01',
        endDate: '2026-12-31',
        status: ['RESERVED', 'STARTED', 'TRANSFERRED', 'RELEASED'][i % 4],
      })),
      total: 50,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 5,
    }),
  },
  {
    method: 'GET',
    path: '/user/resource/bench/page',
    handler: ({ query }) => ({
      list: Array.from({ length: Number(query.size || 10) }, (_, i) => ({
        id: i + 1,
        employeeName: `员工${i + 1}`,
        benchDate: '2026-05-01',
        exitDate: i % 3 === 0 ? '2026-06-15' : null,
        idleDays: 15 + i,
        totalIdleCost: 30000 + i * 1000,
      })),
      total: 20,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 2,
    }),
  },
]
