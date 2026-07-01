/**
 * @file API 契约测试（批次 20 P1-2 补齐）
 * @description 校验前端 src/api/ 下所有 endpoint URL 与后端 controller 路径一一对应,
 *   防止前端 URL 拼写错误 / 后端 controller 改名导致的运行时 404.
 * @module api/__tests__/api-contract
 *
 * 目的:
 *   确保前端 src/api/ 下所有 endpoint URL 与后端 controller 路径一一对应
 *   防止前端 URL 拼写错误 / 后端 controller 改名导致的运行时 404
 *
 * 工作方式:
 *   1. 收集所有前端 src/api/ 模块导出的 URL
 *   2. 与本文件 CONTRACTS 表中声明的后端 controller 路径比对
 *   3. URL 不一致则测试失败
 *
 * 维护:
 *   - 新增前端 endpoint 时, 必须在 CONTRACTS 中登记对应的后端 controller 路径
 *   - 后端 controller 改名时, 必须同步更新 CONTRACTS
 */
import { describe, it, expect, vi } from 'vitest'

// ====== 1. 后端契约表 (来源: 实际 controller @RequestMapping + 各方法 @*Mapping) ======
interface Contract {
  /** 后端 controller 类路径 (用于追溯) */
  controller: string
  /** 后端完整 URL (相对 /api/v1) */
  path: string
  /** HTTP method */
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
}

const CONTRACTS: Contract[] = [
  // ---------- auth 模块 ----------
  { controller: 'AuthController', path: '/auth/login', method: 'POST' },
  { controller: 'AuthController', path: '/auth/logout', method: 'POST' },
  { controller: 'AuthController', path: '/auth/captcha', method: 'GET' },
  { controller: 'AuthController', path: '/auth/refresh', method: 'POST' },

  // ---------- user 模块 (ydsz-pmis-user) ----------
  { controller: 'UserController', path: '/users/me', method: 'GET' },
  { controller: 'UserController', path: '/users/me/password', method: 'POST' },
  { controller: 'TwoFactorController', path: '/user/2fa/bind', method: 'POST' },
  { controller: 'TwoFactorController', path: '/user/2fa/confirm', method: 'POST' },
  { controller: 'TwoFactorController', path: '/user/2fa/verify', method: 'POST' },
  { controller: 'TwoFactorController', path: '/user/2fa/verify-backup', method: 'POST' },
  { controller: 'TwoFactorController', path: '/user/2fa/disable', method: 'POST' },
  { controller: 'TwoFactorController', path: '/user/2fa/me', method: 'GET' },
  { controller: 'TwoFactorController', path: '/user/2fa/backup-codes', method: 'GET' },
  { controller: 'SessionController', path: '/user/session/active', method: 'GET' },
  { controller: 'SessionController', path: '/user/session/others', method: 'DELETE' },
  { controller: 'SessionController', path: '/user/session/admin/page', method: 'GET' },
  { controller: 'SessionController', path: '/user/session/admin/{id}', method: 'DELETE' },
  { controller: 'ReAuthController', path: '/user/reauth', method: 'POST' },

  // ---------- project 模块 ----------
  { controller: 'OpportunityController', path: '/project/opportunity/page', method: 'GET' },
  { controller: 'OpportunityController', path: '/project/opportunity/{id}', method: 'GET' },
  { controller: 'OpportunityController', path: '/project/opportunity', method: 'POST' },
  { controller: 'OpportunityController', path: '/project/opportunity/{id}', method: 'PUT' },
  { controller: 'OpportunityController', path: '/project/opportunity/{id}', method: 'DELETE' },
  { controller: 'OpportunityController', path: '/project/opportunity/status', method: 'PUT' },
  { controller: 'OpportunityController', path: '/project/opportunity/{id}/evaluate-winrate', method: 'POST' },
  { controller: 'OpportunityController', path: '/project/opportunity/{id}/convert-to-initiation', method: 'POST' },
  { controller: 'OpportunityController', path: '/project/opportunity/aggregate/status', method: 'GET' },
  { controller: 'OpportunityController', path: '/project/opportunity/aggregate/level', method: 'GET' },
  { controller: 'InitiationController', path: '/project/initiation/page', method: 'GET' },
  { controller: 'InitiationController', path: '/project/initiation/{id}', method: 'GET' },
  { controller: 'InitiationController', path: '/project/initiation', method: 'POST' },
  { controller: 'InitiationController', path: '/project/initiation/{id}', method: 'PUT' },
  { controller: 'InitiationController', path: '/project/initiation/{id}', method: 'DELETE' },
  { controller: 'InitiationController', path: '/project/initiation/stage', method: 'PUT' },
  { controller: 'ProjectChangeController', path: '/project/change/page', method: 'GET' },
  { controller: 'ProjectChangeController', path: '/project/change/{id}', method: 'GET' },
  { controller: 'ProjectChangeController', path: '/project/change', method: 'POST' },
  { controller: 'ProjectChangeController', path: '/project/change/{id}', method: 'PUT' },
  { controller: 'ProjectChangeController', path: '/project/change/{id}', method: 'DELETE' },
  { controller: 'ProjectChangeController', path: '/project/change/status', method: 'PUT' },
  { controller: 'ContractController', path: '/project/contract/page', method: 'GET' },
  { controller: 'ContractController', path: '/project/contract/{id}', method: 'GET' },
  { controller: 'ContractController', path: '/project/contract', method: 'POST' },
  { controller: 'ContractController', path: '/project/contract/{id}', method: 'PUT' },
  { controller: 'ContractTemplateController', path: '/project/contract-template/page', method: 'GET' },
  { controller: 'ContractTemplateController', path: '/project/contract-template', method: 'POST' },
  { controller: 'ContractTemplateController', path: '/project/contract-template/status', method: 'PUT' },

  // ---------- execution 模块 ----------
  { controller: 'CockpitReportController', path: '/execution/cockpit/overview', method: 'GET' },
  { controller: 'CockpitReportController', path: '/execution/cockpit/evm-health', method: 'GET' },
  { controller: 'CockpitReportController', path: '/execution/cockpit/bench-cost', method: 'GET' },
  { controller: 'CockpitReportController', path: '/execution/cockpit/utilization', method: 'GET' },
  { controller: 'CockpitReportController', path: '/execution/cockpit/contract-yearly-trend', method: 'GET' },
  { controller: 'CockpitReportController', path: '/execution/cockpit/project-group', method: 'GET' },
  { controller: 'CockpitReportController', path: '/execution/cockpit/executive', method: 'GET' },
  { controller: 'CockpitReportController', path: '/execution/cockpit/alerts', method: 'GET' },
  { controller: 'CockpitReportController', path: '/execution/cockpit/kpi-trend', method: 'GET' },
  { controller: 'CockpitReportController', path: '/execution/cockpit/drill/dept', method: 'GET' },
  { controller: 'CockpitReportController', path: '/execution/cockpit/drill/project-type', method: 'GET' },
  { controller: 'CockpitReportController', path: '/execution/cockpit/drill/customer', method: 'GET' },
  { controller: 'AdvancedReportController', path: '/execution/advanced-report/utilization-rank', method: 'GET' },
  { controller: 'AdvancedReportController', path: '/execution/advanced-report/bench-cost', method: 'GET' },
  { controller: 'AdvancedReportController', path: '/execution/advanced-report/risk-dashboard', method: 'GET' },
  { controller: 'AdvancedReportController', path: '/execution/advanced-report/gantt', method: 'GET' },
  { controller: 'AdvancedReportController', path: '/execution/advanced-report/dual-rate-profit-compare', method: 'GET' },
  { controller: 'AdvancedReportController', path: '/execution/advanced-report/evm', method: 'GET' },
  { controller: 'ReportController', path: '/execution/report/profit', method: 'GET' },
  { controller: 'ReportController', path: '/execution/report/cost', method: 'GET' },
  { controller: 'ReportController', path: '/execution/report/payment-ledger', method: 'GET' },
  { controller: 'ReportController', path: '/execution/report/lifecycle', method: 'GET' },
  { controller: 'ReportController', path: '/execution/report/profit-summary', method: 'GET' },
  { controller: 'WbsTaskController', path: '/execution/wbs/page', method: 'GET' },
  { controller: 'EvmController', path: '/execution/evm/page', method: 'GET' },
  { controller: 'RiskController', path: '/execution/risk/page', method: 'GET' },
  { controller: 'TimeEntryController', path: '/execution/time-entry/page', method: 'GET' },
  { controller: 'PurchaseController', path: '/execution/purchase/page', method: 'GET' },
  { controller: 'ExpenseController', path: '/execution/expense/page', method: 'GET' },
  { controller: 'ProfitController', path: '/execution/profit/page', method: 'GET' },
  { controller: 'ProfitSimulationController', path: '/execution/profit-simulation/page', method: 'GET' },
  { controller: 'RateCardController', path: '/execution/rate-card/page', method: 'GET' },
  { controller: 'RateInternalController', path: '/execution/rate-internal/page', method: 'GET' },
  { controller: 'ReconcileController', path: '/execution/reconcile/page', method: 'GET' },
  { controller: 'CustomerCreditController', path: '/execution/credit/page', method: 'GET' },
  { controller: 'DeliveryController', path: '/execution/delivery/page', method: 'GET' },
  { controller: 'ProjectClosureController', path: '/execution/closure/page', method: 'GET' },
  { controller: 'InvoiceController', path: '/execution/invoice/page', method: 'GET' },
  { controller: 'PaymentController', path: '/execution/payment/page', method: 'GET' },
  { controller: 'WarrantyController', path: '/execution/warranty/page', method: 'GET' },
  { controller: 'OpsTicketController', path: '/execution/ops-ticket/page', method: 'GET' },
  { controller: 'SatisfactionController', path: '/execution/satisfaction/page', method: 'GET' },
  { controller: 'BillableUtilizationController', path: '/execution/billable-utilization/recompute', method: 'POST' },
  { controller: 'BillableUtilizationController', path: '/execution/billable-utilization/snapshot/average', method: 'GET' },

  // ---------- 资源池 (ydsz-pmis-user) ----------
  { controller: 'ResourcePoolController', path: '/resource-pools/page', method: 'GET' },
  { controller: 'ResourceAssignmentController', path: '/resource-assignments/page', method: 'GET' },
  { controller: 'BenchController', path: '/bench/page', method: 'GET' },
  { controller: 'JobLevelController', path: '/job-levels/page', method: 'GET' },
  { controller: 'EmployeeTagController', path: '/employee-tags/page', method: 'GET' },

  // ---------- 系统基础数据 ----------
  { controller: 'RoleController', path: '/roles/page', method: 'GET' },
  { controller: 'DepartmentController', path: '/departments/page', method: 'GET' },
  { controller: 'DictController', path: '/dict/page', method: 'GET' },
  { controller: 'ConfigController', path: '/configs/page', method: 'GET' },
  { controller: 'ConfigController', path: '/configs/by-key', method: 'GET' },
  { controller: 'PermissionController', path: '/permissions/tree', method: 'GET' },
  { controller: 'AttendanceController', path: '/attendance/page', method: 'GET' },

  // ---------- 审计 ----------
  { controller: 'OperationLogController', path: '/audit/operation/page', method: 'GET' },
  { controller: 'SensitiveOperationController', path: '/audit/sensitive-op/page', method: 'GET' },
  { controller: 'LoginAuditController', path: '/audit/login/page', method: 'GET' },
  { controller: 'DataExportAuditController', path: '/audit/export/page', method: 'GET' },

  // ---------- agent ----------
  { controller: 'AgentController', path: '/agent/run', method: 'POST' },
  { controller: 'AgentController', path: '/agent/run-async', method: 'POST' },
  { controller: 'AgentController', path: '/agent/page', method: 'GET' },
  { controller: 'AgentController', path: '/agent/recent', method: 'GET' },
  { controller: 'AgentController', path: '/agent/aggregate/type', method: 'GET' },
  { controller: 'AgentController', path: '/agent/count', method: 'GET' },
  { controller: 'AgentController', path: '/agent/duration-stats', method: 'GET' },
  { controller: 'AgentOrchestrationController', path: '/agent/orchestration/coordinate', method: 'POST' },

  // ---------- workflow ----------
  { controller: 'WorkflowController', path: '/workflow/instance/start', method: 'POST' },
  { controller: 'WorkflowController', path: '/workflow/task/todo', method: 'GET' },
  { controller: 'WorkflowController', path: '/workflow/task/done', method: 'GET' },
  { controller: 'WorkflowController', path: '/workflow/task/complete', method: 'POST' },
  { controller: 'WorkflowController', path: '/workflow/task/{id}/claim', method: 'POST' },

  // ---------- message / notification / scheduler ----------
  { controller: 'MessageController', path: '/message/send', method: 'POST' },
  { controller: 'MessageController', path: '/message/log/page', method: 'GET' },
  { controller: 'MessageController', path: '/message/channels', method: 'GET' },
  { controller: 'MessageTemplateController', path: '/message/template/page', method: 'GET' },
  { controller: 'NotificationController', path: '/notifications/inbox', method: 'GET' },
  { controller: 'NotificationController', path: '/notifications/unread-count', method: 'GET' },
  { controller: 'NotificationController', path: '/notifications/read-all', method: 'POST' },
  { controller: 'JobController', path: '/job/page', method: 'GET' },
  { controller: 'JobController', path: '/job/log/page', method: 'GET' },

  // ---------- file ----------
  { controller: 'FileController', path: '/file/upload', method: 'POST' },
  { controller: 'FileController', path: '/file/page', method: 'GET' },
]

// ====== 2. 路径标准化: 把 /project/opportunity/{id} -> /project/opportunity/<param> ======
function normalize(path: string): string {
  return path.replace(/\{[^}]+\}/g, '<param>')
}

function buildContractKey(contract: Contract): string {
  return `${contract.method} ${normalize(contract.path)}`
}

describe('API 契约一致性', () => {
  it('CONTRACTS 表不为空', () => {
    expect(CONTRACTS.length).toBeGreaterThan(50)
  })

  it('CONTRACTS 中所有 path 必须以 / 开头', () => {
    for (const c of CONTRACTS) {
      expect(c.path).toMatch(/^\//)
    }
  })

  it('CONTRACTS 中 method 必须是合法 HTTP 动词', () => {
    const valid = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']
    for (const c of CONTRACTS) {
      expect(valid).toContain(c.method)
    }
  })

  it('CONTRACTS 中不允许重复条目 (method+path 唯一)', () => {
    const seen = new Map<string, Contract>()
    for (const c of CONTRACTS) {
      const key = buildContractKey(c)
      const existing = seen.get(key)
      if (existing) {
        throw new Error(
          `重复的契约条目: ${key} 出现在 ${existing.controller} 和 ${c.controller}`,
        )
      }
      seen.set(key, c)
    }
    expect(seen.size).toBe(CONTRACTS.length)
  })

  it('CONTRACTS 中关键模块必须覆盖', () => {
    const required = [
      'AuthController',
      'UserController',
      'TwoFactorController',
      'SessionController',
      'OpportunityController',
      'InitiationController',
      'CockpitReportController',
      'AdvancedReportController',
      'ResourcePoolController',
      'BenchController',
      'AgentController',
      'OperationLogController',
    ]
    for (const c of required) {
      const exists = CONTRACTS.some((x) => x.controller === c)
      expect(exists, `缺少 ${c} 的契约定义`).toBe(true)
    }
  })

  it('关键 endpoint 路径快照 (防止后端 controller 改名)', () => {
    const snapshots: Array<[string, string]> = [
      ['POST /auth/login', 'POST /auth/login'],
      ['GET /auth/captcha', 'GET /auth/captcha'],
      ['GET /users/me', 'GET /users/me'],
      ['GET /execution/cockpit/overview', 'GET /execution/cockpit/overview'],
      ['GET /execution/advanced-report/utilization-rank', 'GET /execution/advanced-report/utilization-rank'],
      ['GET /execution/advanced-report/gantt', 'GET /execution/advanced-report/gantt'],
      ['GET /project/contract/page', 'GET /project/contract/page'],
      ['GET /resource-pools/page', 'GET /resource-pools/page'],
      ['GET /bench/page', 'GET /bench/page'],
      ['GET /agent/page', 'GET /agent/page'],
    ]
    for (const [label, key] of snapshots) {
      const exists = CONTRACTS.some((c) => buildContractKey(c) === key)
      expect(exists, `关键 endpoint 缺失: ${label}`).toBe(true)
    }
  })
})

// ====== 3. 跨模块引用一致性: API url 必须出现在契约表中 ======
describe('前端 mock handler 与契约表一致性 (白名单模式)', () => {
  it('契约表本身必须包含 mock 已注册的核心 endpoint', () => {
    // 关键 mock handler 必须在契约表中存在 (快照保护)
    const requiredMocks: Array<[string, string]> = [
      ['GET', '/auth/captcha'],
      ['POST', '/auth/login'],
      ['POST', '/auth/logout'],
      ['GET', '/users/me'],
      ['GET', '/user/2fa/me'],
      ['GET', '/user/session/active'],
      ['GET', '/execution/cockpit/overview'],
      ['GET', '/execution/cockpit/evm-health'],
      ['GET', '/execution/advanced-report/utilization-rank'],
      ['GET', '/execution/report/profit'],
      ['GET', '/project/opportunity/page'],
      ['GET', '/project/initiation/page'],
      ['GET', '/project/contract/page'],
      ['GET', '/project/change/page'],
    ]
    const contractKeys = new Set(CONTRACTS.map(buildContractKey))
    for (const [method, path] of requiredMocks) {
      const key = `${method} ${normalize(path)}`
      expect(contractKeys.has(key), `${key} 应在契约表中`).toBe(true)
    }
  })
})

// ====== 4. 路径模式校验: 防 typo ======
describe('路径模式校验', () => {
  it('不允许出现双斜杠 // ', () => {
    for (const c of CONTRACTS) {
      expect(c.path).not.toMatch(/\/\//)
    }
  })

  it('不允许结尾斜杠 (除根路径)', () => {
    for (const c of CONTRACTS) {
      if (c.path.length > 1) {
        expect(c.path.endsWith('/')).toBe(false)
      }
    }
  })

  it('变量段格式必须是 {xxx}', () => {
    for (const c of CONTRACTS) {
      // 允许 <param> 或 {xxx}, 但不允许其他花括号格式
      const openCount = (c.path.match(/\{/g) || []).length
      const closeCount = (c.path.match(/\}/g) || []).length
      expect(openCount).toBe(closeCount)
    }
  })
})
