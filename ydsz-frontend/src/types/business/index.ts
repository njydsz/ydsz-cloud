/**
 * @fileoverview 业务实体类型统一出口 (P2-2 类型中心化)
 * @description 收口跨模块共享的业务 VO 类型，避免跨模块直接引用 api/*/types 造成循环依赖。
 *
 * 使用规范：
 * - 仅在本模块内使用的 DTO/Query 类型 → 保留在 api/{module}/types.ts
 * - 被 2 个及以上模块引用的 VO 类型 → 集中到本文件
 * - 新增跨模块共享 VO 时，必须在此处 re-export
 *
 * @module types/business
 * @author ydsz-team
 * @since 1.0.0
 */

// ===========================================
// system 模块 — 用户 / 部门 / 角色 / 菜单 / 字典
// ===========================================

export type { UserVO, UserQuery, UserCreateDTO } from '@/api/system/user/types'
export type { DeptVO, DeptFormDTO } from '@/api/system/dept/types'
export type { RoleVO, RoleQuery, RoleFormDTO } from '@/api/system/role/types'
export type { MenuTreeVO, PermissionFormDTO } from '@/api/system/menu/types'
export type {
  DictTypeVO,
  DictItemVO,
  DictTypeFormDTO,
  DictItemFormDTO,
  DictItemQuery,
} from '@/api/system/dict/types'
export type { ConfigVO, ConfigFormDTO, ConfigQuery } from '@/api/system/config/types'

// ===========================================
// resource 模块 — 资源池 / 员工 / 职级 / 费率卡
// ===========================================

export type { EmployeeVO, EmployeeCreateDTO, EmployeeUpdateDTO } from '@/api/resource/employee/types'
export type { ResourcePoolVO, ResourcePoolCreateDTO } from '@/api/resource/pool/types'
export type { RankVO, RankRateVO } from '@/api/resource/rank/types'
export type { RateCardVO, RateCardCreateDTO } from '@/api/resource/rate-card/types'
export type { RateInternalVO, RateInternalCreateDTO } from '@/api/resource/rate-internal/types'
export type { BenchRecordVO, BenchRecordCreateDTO, BenchDashboardVO } from '@/api/resource/bench/types'
export type { ResourceAssignmentVO, ResourceAssignmentCreateDTO } from '@/api/resource/assignment/types'
export type { EmployeeTagVO, EmployeeTagCreateDTO } from '@/api/resource/employee-tag/types'

// ===========================================
// contract 模块 — 合同
// ===========================================

export type { ContractVO, ContractCreateDTO, ContractStatusDTO } from '@/api/contract/types'

// ===========================================
// finance 模块 — 财务
// ===========================================

export type { PaymentVO, PaymentCreateDTO, PaymentAllocationDTO, PaymentAllocationVO } from '@/api/finance/payment/types'
export type { InvoiceVO, InvoiceCreateDTO, InvoiceApprovalDTO } from '@/api/finance/invoice/types'
export type { ExpenseVO, ExpenseCreateDTO } from '@/api/finance/expense/types'
export type { DailyReconcileVO, DailyReconcileAggregateVO } from '@/api/finance/reconcile/types'
export type { RevenueVO, RevenueCreateDTO, ProfitSnapshotVO } from '@/api/finance/profit/types'

// ===========================================
// execution 模块 — 项目执行
// ===========================================

export type { WbsTaskVO, WbsTaskCreateDTO, WbsTaskStatusDTO } from '@/api/execution/wbs-task/types'
export type { TimeEntryVO, TimeEntryCreateDTO, TimeEntryApprovalDTO } from '@/api/execution/time-entry/types'
export type { RiskVO, RiskCreateDTO, RiskStatusDTO } from '@/api/execution/risk/types'
export type { EvmMeasureVO, EvmMeasureCreateDTO, EvmDashboardVO } from '@/api/execution/evm/types'
export type { CustomerCreditVO, CreditAssessmentDTO } from '@/api/execution/customer-credit/types'
export type { PurchaseVO, PurchaseCreateDTO } from '@/api/execution/purchase/types'
export type { DeliveryItemVO, DeliveryItemCreateDTO, DeliveryItemStatusDTO } from '@/api/execution/delivery/types'

// ===========================================
// workflow 模块 — 工作流
// ===========================================

export type { FlowDefinitionDTO, FlowInstanceDTO, FlowTaskDTO } from '@/api/workflow/types'

// ===========================================
// message / notification 模块
// ===========================================

export type { MessageLogVO, MessageStatus, MessageChannel, MessagePriority } from '@/api/message/types'
export type { NotificationVO, NotificationSendDTO } from '@/api/notification/types'

// ===========================================
// cronjob 模块
// ===========================================

export type { JobVO, JobStatus, ScheduleType, JobType } from '@/api/cronjob/types'
