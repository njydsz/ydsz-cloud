/**
 * @file 执行模块 Mock 数据处理器
 * @description 为执行模块的 WBS、EVM、利用率排名、闲置成本、风险仪表盘、风险/预警/结项等 API 路径提供 Mock 数据
 * @module mock/handlers/execution
 */
import type { MockHandler } from './types'

/**
 * 执行模块 Mock 处理器集合
 * @returns {MockHandler[]} 覆盖 WBS 分页、EVM 分页、高级报表、风险/预警/结项流程等接口的 Mock 处理器
 */
export const executionHandlers: MockHandler[] = [
  // ===== WBS 任务分页查询（含编码、名称、父级、进度、负责人、状态） =====
  {
    method: 'GET',
    path: '/execution/wbs/page',
    handler: ({ query }) => ({
      list: Array.from({ length: Number(query.size || 10) }, (_, i) => ({
        id: i + 1,
        wbsCode: `WBS-${String(i + 1).padStart(4, '0')}`,
        wbsName: `WBS 任务 ${i + 1}`,
        parentId: i % 3 === 0 ? null : Math.floor(i / 3) + 1,
        progress: Math.floor((i * 13) % 100),
        assigneeName: `员工${(i % 5) + 1}`,
        status: ['PENDING', 'IN_PROGRESS', 'DONE'][i % 3],
      })),
      total: 200,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 20,
    }),
  },
  // ===== EVM 指标分页查询（含周期、CPI、SPI、健康度） =====
  {
    method: 'GET',
    path: '/execution/evm/page',
    handler: ({ query }) => ({
      list: Array.from({ length: Number(query.size || 10) }, (_, i) => ({
        id: i + 1,
        period: `2026-${String((i % 12) + 1).padStart(2, '0')}`,
        cpi: 0.85 + (i % 5) * 0.05,
        spi: 0.9 + (i % 4) * 0.03,
        health: ['NORMAL', 'YELLOW', 'RED'][i % 3],
      })),
      total: 24,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 3,
    }),
  },
  // ===== 利用率排名报表（员工维度） =====
  {
    method: 'GET',
    path: '/execution/advanced-report/utilization-rank',
    handler: () =>
      Array.from({ length: 20 }, (_, i) => ({
        employeeId: i + 1,
        employeeName: `员工${i + 1}`,
        department: `部门${(i % 5) + 1}`,
        utilization: 60 + (i * 2) % 40,
      })),
  },
  // ===== 闲置成本月度报表 =====
  {
    method: 'GET',
    path: '/execution/advanced-report/bench-cost',
    handler: () =>
      Array.from({ length: 12 }, (_, i) => ({
        period: `2026-${String(i + 1).padStart(2, '0')}`,
        totalCost: 50000 + i * 3000,
        headcount: 3 + (i % 4),
      })),
  },
  // ===== 风险仪表盘报表（项目维度的风险/告警计数） =====
  {
    method: 'GET',
    path: '/execution/advanced-report/risk-dashboard',
    handler: () =>
      Array.from({ length: 5 }, (_, i) => ({
        projectId: i + 1,
        projectName: `项目${i + 1}`,
        highRiskCount: i % 2,
        mediumRiskCount: (i + 1) % 3,
        lowRiskCount: (i + 2) % 4,
        highAlerts: i % 2,
        mediumAlerts: (i + 1) % 3,
        lowAlerts: (i + 2) % 4,
        alertCount: i + 3,
      })),
  },
  // ====== E2E P1-6 风险预警流程 mock (批次 25) ======
  // 风险分页查询（含标题、等级、类别、状态、影响金额）
  {
    method: 'GET',
    path: '/execution/risk/page',
    handler: ({ query }) => {
      const fixture = Array.from({ length: Number(query.size || 10) }, (_, i) => ({
        id: i + 1,
        title: `示例风险 ${i + 1}`,
        level: ['LOW', 'MEDIUM', 'HIGH'][i % 3],
        category: ['SCOPE', 'BUDGET', 'SCHEDULE', 'QUALITY'][i % 4],
        status: ['OPEN', 'CLOSED'][i % 2],
        impactAmount: 100000 * (i + 1),
        createdAt: '2026-06-15 10:00:00',
      }))
      return {
        list: fixture,
        total: 50,
        page: Number(query.page || 1),
        size: Number(query.size || 10),
        pages: 5,
      }
    },
  },
  // 新建风险（随机生成 ID，回填默认值）
  {
    method: 'POST',
    path: '/execution/risk',
    handler: ({ body }) => {
      const b = (body || {}) as Record<string, unknown>
      const id = 2000 + Math.floor(Math.random() * 1000)
      return {
        id,
        title: b.title || b.name || 'E2E 风险',
        level: b.level || 'MEDIUM',
        category: b.category || 'SCOPE',
        status: 'OPEN',
        impactAmount: Number(b.impactAmount) || 0,
        description: b.description || '',
        createdAt: new Date().toISOString(),
      }
    },
  },
  // 风险评估（返回等级、评分、命中规则）
  {
    method: 'POST',
    path: '/execution/risk/{id}/evaluate',
    handler: () => ({
      success: true,
      level: 'HIGH',
      score: 0.75,
      matchedRules: ['CPI<0.95', '高风险事件≥2'],
      evaluatedAt: new Date().toISOString(),
    }),
  },
  // 预警分页查询（含编码、标题、等级、状态）
  {
    method: 'GET',
    path: '/execution/alert/page',
    handler: ({ query }) => {
      const list = Array.from({ length: Number(query.size || 10) }, (_, i) => ({
        id: i + 1,
        alertCode: `ALT-${String(i + 1).padStart(4, '0')}`,
        title: `预警 ${i + 1}`,
        level: ['INFO', 'YELLOW', 'RED'][i % 3],
        status: i % 2 === 0 ? 'OPEN' : 'ACKED',
        createdAt: '2026-06-20 10:00:00',
      }))
      return {
        list,
        total: 30,
        page: Number(query.page || 1),
        size: Number(query.size || 10),
        pages: 3,
      }
    },
  },
  // 预警确认（ack）
  {
    method: 'POST',
    path: '/execution/alert/{id}/ack',
    handler: () => ({ success: true, status: 'ACKED' }),
  },
  // ====== E2E P1-6 结项流程 mock (批次 25) ======
  // 结项分页查询（含编码、立项、类型、状态、原因）
  {
    method: 'GET',
    path: '/execution/project-closure/page',
    handler: ({ query }) => ({
      list: Array.from({ length: Number(query.size || 10) }, (_, i) => ({
        id: i + 1,
        closureCode: `CL-${String(i + 1).padStart(4, '0')}`,
        initiationId: 100 + i,
        initiationName: `项目${i + 1}`,
        type: ['FORMAL', 'PRE_CLOSURE', 'FORCED'][i % 3],
        status: ['DRAFT', 'SUBMITTED', 'ACCEPTED', 'REJECTED'][i % 4],
        reason: `结项原因 ${i + 1}`,
        createdAt: '2026-06-25 10:00:00',
      })),
      total: 20,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 2,
    }),
  },
  // 新建结项（生成编码，回填默认状态）
  {
    method: 'POST',
    path: '/execution/project-closure',
    handler: ({ body }) => {
      const b = (body || {}) as Record<string, unknown>
      return {
        id: 3000 + Math.floor(Math.random() * 1000),
        closureCode: `CL-${Date.now().toString().slice(-6)}`,
        initiationId: b.initiationId,
        initiationName: b.initiationName || `项目${b.initiationId}`,
        type: b.type || 'FORMAL',
        status: 'DRAFT',
        reason: b.reason || '',
        createdAt: new Date().toISOString(),
      }
    },
  },
  // 更新结项状态（提交 / 受理 / 驳回等）
  {
    method: 'PUT',
    path: '/execution/project-closure/status',
    handler: () => ({ success: true }),
  },
  // 结项详情查询
  {
    method: 'GET',
    path: '/execution/project-closure/{id}',
    handler: () => ({
      id: 1,
      closureCode: 'CL-0001',
      initiationId: 100,
      initiationName: '示例项目',
      type: 'FORMAL',
      status: 'ACCEPTED',
      reason: '已完成',
      createdAt: '2026-06-25 10:00:00',
    }),
  },
]
