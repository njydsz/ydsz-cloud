/**
 * @file 前端权限码常量
 * @description 与后端 com.njydsz.pmis.common.permission.PermissionCodes 一一对应
 * @module constants/permissionCodes
 *
 * 统一规范: <module>:<resource>:<action> 三段式。
 *
 * 任何前端页面、组件中涉及权限判断时, 必须从本常量引用, 禁止硬编码字符串。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */

/**
 * 权限码常量集合
 *
 * 命名规则：`{MODULE}_{RESOURCE}_{ACTION}`，与后端 PermissionCodes 字段名保持一致。
 * 取值规则：`{module}:{resource}:{action}`，三段式权限码。
 *
 * 使用示例：
 * ```ts
 * import { PC } from '@/constants/permissionCodes'
 * if (userStore.hasPermission(PC.AUTH_USER_LIST)) { ... }
 * ```
 */
export const PC = {
  // ============= 认证授权(auth) =============
  AUTH_USER_LIST: 'auth:user:list',
  AUTH_USER_CREATE: 'auth:user:create',
  AUTH_USER_UPDATE: 'auth:user:update',
  AUTH_USER_DELETE: 'auth:user:delete',
  AUTH_USER_RESET_PWD: 'auth:user:reset-password',
  AUTH_USER_TOGGLE: 'auth:user:toggle',
  AUTH_USER_ASSIGN: 'auth:user:assign',

  AUTH_ROLE_LIST: 'auth:role:list',
  AUTH_ROLE_CREATE: 'auth:role:create',
  AUTH_ROLE_UPDATE: 'auth:role:update',
  AUTH_ROLE_DELETE: 'auth:role:delete',
  AUTH_ROLE_ASSIGN: 'auth:role:assign',

  AUTH_PERM_CREATE: 'auth:perm:create',
  AUTH_PERM_UPDATE: 'auth:perm:update',
  AUTH_PERM_DELETE: 'auth:perm:delete',

  // ============= 组织架构(org) =============
  ORG_DEPT_CREATE: 'org:dept:create',
  ORG_DEPT_UPDATE: 'org:dept:update',
  ORG_DEPT_DELETE: 'org:dept:delete',

  // ============= 系统配置(sys) =============
  SYS_CONFIG_LIST: 'sys:config:list',
  SYS_CONFIG_CREATE: 'sys:config:create',
  SYS_CONFIG_UPDATE: 'sys:config:update',
  SYS_CONFIG_DELETE: 'sys:config:delete',
  SYS_CONFIG_REFRESH: 'sys:config:refresh',

  // 特性开关
  SYS_FEATURE_FLAG_VIEW: 'sys:feature-flag:view',
  SYS_FEATURE_FLAG_UPDATE: 'sys:feature-flag:update',
  SYS_FEATURE_FLAG_CHECK: 'sys:feature-flag:check',

  // 混沌工程
  SYS_CHAOS_VIEW: 'sys:chaos:view',
  SYS_CHAOS_CREATE: 'sys:chaos:create',
  SYS_CHAOS_DELETE: 'sys:chaos:delete',
  SYS_CHAOS_TRIGGER: 'sys:chaos:trigger',

  // ============= 考勤管理(attendance) =============
  ATTENDANCE_RECORD_CREATE: 'attendance:record:create',
  ATTENDANCE_RECORD_LIST: 'attendance:record:list',
  ATTENDANCE_OVERTIME_CREATE: 'attendance:overtime:create',
  ATTENDANCE_OVERTIME_APPROVE: 'attendance:overtime:approve',
  ATTENDANCE_OVERTIME_LIST: 'attendance:overtime:list',
  ATTENDANCE_LEAVE_CREATE: 'attendance:leave:create',
  ATTENDANCE_LEAVE_APPROVE: 'attendance:leave:approve',
  ATTENDANCE_LEAVE_LIST: 'attendance:leave:list',

  // ============= 资源管理(resource) =============
  RESOURCE_POOL_CREATE: 'resource:pool:create',
  RESOURCE_POOL_UPDATE: 'resource:pool:update',
  RESOURCE_POOL_DELETE: 'resource:pool:delete',

  RESOURCE_TAG_CREATE: 'resource:tag:create',
  RESOURCE_TAG_UPDATE: 'resource:tag:update',
  RESOURCE_TAG_DELETE: 'resource:tag:delete',

  RESOURCE_ASSIGN_ACT: 'resource:assign:act',
  RESOURCE_BENCH_ACT: 'resource:bench:act',
  RESOURCE_BENCH_LIST: 'resource:bench:list',
  RESOURCE_BENCH_INTO: 'resource:bench:into',
  RESOURCE_BENCH_OUT: 'resource:bench:out',
  RESOURCE_UTILIZATION_LIST: 'resource:utilization:list',

  // ============= 个人安全与 2FA =============
  AUTH_USER_2FA_BIND: 'auth:user:bind-2fa',
  AUTH_USER_2FA_VERIFY: 'auth:user:verify-2fa',
  AUTH_USER_SESSION_LIST: 'auth:user:session-list',
  AUTH_USER_SESSION_KICK: 'auth:user:session-kick',
  AUTH_USER_CHANGE_PWD: 'auth:user:change-password',

  // ============= 审计日志(audit) =============
  AUDIT_LOG_VIEW: 'audit:log:view',
  AUDIT_LOGIN_VIEW: 'audit:login:view',
  AUDIT_EXPORT_VIEW: 'audit:export:view',
  AUDIT_SENSITIVE_VIEW: 'audit:sensitive:view',

  // ============= 敏感操作二次认证(sensitive) =============
  SENSITIVE_REAUTH: 'sensitive:reauth:confirm',

  // ============= 定时任务管理(cronjob) =============
  CRONJOB_JOB_CREATE: 'cronjob:job:create',
  CRONJOB_JOB_UPDATE: 'cronjob:job:update',
  CRONJOB_JOB_DELETE: 'cronjob:job:delete',
  CRONJOB_JOB_TRIGGER: 'cronjob:job:trigger',
  CRONJOB_JOB_RELOAD: 'cronjob:job:reload',

  // ============= 通知中心(notif) =============
  NOTIF_MESSAGE_SEND: 'notif:message:send',

  // ============= 文件存储(file) =============
  FILE_STORAGE_UPLOAD: 'file:storage:upload',
  FILE_STORAGE_DELETE: 'file:storage:delete',

  // ============= 项目模块(project) =============
  // 商机管理
  PROJECT_OPPORTUNITY_LIST: 'project:opportunity:list',
  PROJECT_OPPORTUNITY_CREATE: 'project:opportunity:create',
  PROJECT_OPPORTUNITY_UPDATE: 'project:opportunity:update',
  PROJECT_OPPORTUNITY_DELETE: 'project:opportunity:delete',
  PROJECT_OPPORTUNITY_EVALUATE: 'project:opportunity:evaluate',
  PROJECT_OPPORTUNITY_CONVERT: 'project:opportunity:convert',

  // 立项管理
  PROJECT_INITIATION_LIST: 'project:initiation:list',
  PROJECT_INITIATION_CREATE: 'project:initiation:create',
  PROJECT_INITIATION_DELETE: 'project:initiation:delete',
  PROJECT_INITIATION_BUDGET: 'project:initiation:budget',
  PROJECT_INITIATION_GATE: 'project:initiation:gate',
  PROJECT_INITIATION_START_PROCESS: 'project:initiation:start-process',

  // 合同管理
  PROJECT_CONTRACT_LIST: 'project:contract:list',
  PROJECT_CONTRACT_CREATE: 'project:contract:create',
  PROJECT_CONTRACT_UPDATE: 'project:contract:update',
  PROJECT_CONTRACT_DELETE: 'project:contract:delete',
  PROJECT_CONTRACT_STATUS: 'project:contract:status',

  PROJECT_CONTRACT_TEMPLATE_LIST: 'project:contract-template:list',
  PROJECT_CONTRACT_TEMPLATE_CREATE: 'project:contract-template:create',
  PROJECT_CONTRACT_TEMPLATE_PUBLISH: 'project:contract-template:publish',

  PROJECT_CONTRACT_CHANGE_LIST: 'project:contract-change:list',
  PROJECT_CONTRACT_CHANGE_CREATE: 'project:contract-change:create',
  PROJECT_CONTRACT_CHANGE_APPROVE: 'project:contract-change:approve',

  // ============= 执行模块(execution) =============
  // WBS 任务
  EXECUTION_WBS_LIST: 'execution:wbs:list',
  EXECUTION_WBS_CREATE: 'execution:wbs:create',
  EXECUTION_WBS_UPDATE: 'execution:wbs:update',
  EXECUTION_WBS_STATUS: 'execution:wbs:status',
  EXECUTION_WBS_DELETE: 'execution:wbs:delete',

  // 工时管理
  EXECUTION_TIME_LIST: 'execution:time:list',
  EXECUTION_TIME_CREATE: 'execution:time:create',
  EXECUTION_TIME_APPROVE: 'execution:time:approve',
  EXECUTION_TIME_REJECT: 'execution:time:reject',

  // 采购管理
  EXECUTION_PURCHASE_LIST: 'execution:purchase:list',
  EXECUTION_PURCHASE_CREATE: 'execution:purchase:create',
  EXECUTION_PURCHASE_STATUS: 'execution:purchase:status',
  EXECUTION_PURCHASE_DELETE: 'execution:purchase:delete',

  // 费用管理
  EXECUTION_EXPENSE_LIST: 'execution:expense:list',
  EXECUTION_EXPENSE_CREATE: 'execution:expense:create',
  EXECUTION_EXPENSE_STATUS: 'execution:expense:status',

  // 风险管理
  EXECUTION_RISK_LIST: 'execution:risk:list',
  EXECUTION_RISK_CREATE: 'execution:risk:create',
  EXECUTION_RISK_STATUS: 'execution:risk:status',

  // 收入管理
  EXECUTION_REVENUE_LIST: 'execution:revenue:list',
  EXECUTION_REVENUE_CREATE: 'execution:revenue:create',

  // 利润管理
  EXECUTION_PROFIT_LIST: 'execution:profit:list',
  EXECUTION_PROFIT_SNAPSHOT: 'execution:profit:snapshot',

  // 交付物管理
  EXECUTION_DELIVERY_LIST: 'execution:delivery:list',
  EXECUTION_DELIVERY_CREATE: 'execution:delivery:create',
  EXECUTION_DELIVERY_REVIEW: 'execution:delivery:review',

  // EVM 挣值管理
  EXECUTION_EVM_LIST: 'execution:evm:list',
  EXECUTION_EVM_SAVE: 'execution:evm:save',
  EXECUTION_EVM_DASHBOARD: 'execution:evm:dashboard',

  // 双费率 / 利润模拟
  EXECUTION_RATE_LIST: 'execution:rate:list',
  EXECUTION_RATE_CARD_CREATE: 'execution:rate-card:create',
  EXECUTION_RATE_INTERNAL_CREATE: 'execution:rate-internal:create',
  EXECUTION_SIMULATION_LIST: 'execution:simulation:list',
  EXECUTION_SIMULATION_CREATE: 'execution:simulation:create',
  EXECUTION_SIMULATION_APPROVE: 'execution:simulation:approve',

  // 每日对账
  EXECUTION_RECONCILE_VIEW: 'execution:reconcile:view',
  EXECUTION_RECONCILE_RUN: 'execution:reconcile:run',

  // 可计费利用率
  EXECUTION_UTILIZATION_VIEW: 'execution:utilization:view',
  EXECUTION_UTILIZATION_RECOMPUTE: 'execution:utilization:recompute',

  // 预算管理
  EXECUTION_BUDGET_VIEW: 'execution:budget:view',

  // ============= 售后管理(aftersales) =============
  // 质保期
  AFTERSALES_WARRANTY_LIST: 'aftersales:warranty:list',
  AFTERSALES_WARRANTY_CREATE: 'aftersales:warranty:create',
  AFTERSALES_WARRANTY_TERMINATE: 'aftersales:warranty:terminate',
  AFTERSALES_WARRANTY_SCAN: 'aftersales:warranty:scan',

  // 运维工单
  AFTERSALES_OPS_TICKET_LIST: 'aftersales:ops-ticket:list',
  AFTERSALES_OPS_TICKET_CREATE: 'aftersales:ops-ticket:create',
  AFTERSALES_OPS_TICKET_ASSIGN: 'aftersales:ops-ticket:assign',
  AFTERSALES_OPS_TICKET_STATUS: 'aftersales:ops-ticket:status',
  AFTERSALES_OPS_TICKET_EVALUATE: 'aftersales:ops-ticket:evaluate',
  AFTERSALES_OPS_TICKET_SCAN: 'aftersales:ops-ticket:scan',

  // 满意度评价
  AFTERSALES_SATISFACTION_LIST: 'aftersales:satisfaction:list',
  AFTERSALES_SATISFACTION_SUBMIT: 'aftersales:satisfaction:submit',
  AFTERSALES_SATISFACTION_FOLLOWUP: 'aftersales:satisfaction:follow-up',

  // ============= 财务模块(finance) =============
  // 开票管理
  FINANCE_INVOICE_LIST: 'finance:invoice:list',
  FINANCE_INVOICE_CREATE: 'finance:invoice:create',
  FINANCE_INVOICE_APPROVE: 'finance:invoice:approve',
  FINANCE_INVOICE_ISSUE: 'finance:invoice:issue',
  FINANCE_INVOICE_REVERSE: 'finance:invoice:reverse',

  // 回款管理
  FINANCE_PAYMENT_LIST: 'finance:payment:list',
  FINANCE_PAYMENT_CREATE: 'finance:payment:create',
  FINANCE_PAYMENT_ALLOCATE: 'finance:payment:allocate',

  // 客户信用
  FINANCE_CREDIT_LIST: 'finance:credit:list',
  FINANCE_CREDIT_ASSESS: 'finance:credit:assess',

  // ============= 报表与驾驶舱(report/cockpit) =============
  REPORT_PROFIT_VIEW: 'report:profit:view',
  REPORT_COST_VIEW: 'report:cost:view',
  REPORT_PAYMENT_LEDGER_VIEW: 'report:payment-ledger:view',
  REPORT_LIFECYCLE_VIEW: 'report:lifecycle:view',
  REPORT_ADVANCED_VIEW: 'report:advanced:view',
  REPORT_EXECUTIVE_VIEW: 'report:executive:view',
  COCKPIT_OVERVIEW_VIEW: 'cockpit:overview:view',
  COCKPIT_DRILLDOWN_VIEW: 'cockpit:drilldown:view',
  COCKPIT_ALERT_VIEW: 'cockpit:alert:view',

  // ============= 项目结项与变更(closure/change) =============
  CLOSURE_LIST: 'closure:project:list',
  CLOSURE_CREATE: 'closure:project:create',
  CLOSURE_STATUS: 'closure:project:status',
  PROJECT_CHANGE_LIST: 'project:change:list',
  PROJECT_CHANGE_CREATE: 'project:change:create',
  PROJECT_CHANGE_STATUS: 'project:change:status',

  // ============= AI Agent(agent) =============
  AGENT_RUN: 'agent:task:run',
  AGENT_HISTORY: 'agent:task:list',
  AGENT_VIEW: 'agent:task:view',
  AGENT_ORCHESTRATION_RUN: 'agent:orchestration:run',
  AGENT_ORCHESTRATION_VIEW: 'agent:orchestration:view',
  AGENT_PREDICTION_VIEW: 'agent:prediction:view',

  // ============= 工作流(workflow) =============
  WORKFLOW_DEFINITION_LIST: 'workflow:definition:list',
  WORKFLOW_DEFINITION_CREATE: 'workflow:definition:create',
  WORKFLOW_DEFINITION_UPDATE: 'workflow:definition:update',
  WORKFLOW_DEFINITION_DELETE: 'workflow:definition:delete',
  WORKFLOW_DEFINITION_PUBLISH: 'workflow:definition:publish',
  WORKFLOW_DEFINITION_DEPLOY: 'workflow:definition:deploy',
  WORKFLOW_APPROVAL_CENTER: 'workflow:approval-center:view',
  WORKFLOW_CC_VIEW: 'workflow:cc:view',
  WORKFLOW_MONITOR: 'workflow:monitor:view',
  WORKFLOW_STATS: 'workflow:stats:view',
  WORKFLOW_DIAGRAM: 'workflow:diagram:view',
  WORKFLOW_TIMELINE: 'workflow:timeline:view',

  // 委托授权
  WORKFLOW_DELEGATE_AUTH_VIEW: 'workflow:delegate-auth:view',
  WORKFLOW_DELEGATE_AUTH_CREATE: 'workflow:delegate-auth:create',
  WORKFLOW_DELEGATE_AUTH_REVOKE: 'workflow:delegate-auth:revoke',
  WORKFLOW_DELEGATE_AUTH_TOGGLE: 'workflow:delegate-auth:toggle',

  // SLA 管理
  WORKFLOW_SLA_VIEW: 'workflow:sla:view',
  WORKFLOW_SLA_SCAN: 'workflow:sla:scan',
  WORKFLOW_SLA_PROCESS: 'workflow:sla:process',

  // 灰度发布
  WORKFLOW_CANARY_VIEW: 'workflow:canary:view',
  WORKFLOW_CANARY_PUBLISH: 'workflow:canary:publish',
  WORKFLOW_CANARY_ADJUST: 'workflow:canary:adjust',
  WORKFLOW_CANARY_PROMOTE: 'workflow:canary:promote',
  WORKFLOW_CANARY_ROLLBACK: 'workflow:canary:rollback',

  // 版本管理 + 模拟运行
  WORKFLOW_VERSION_VIEW: 'workflow:version:view',
  WORKFLOW_VERSION_SWITCH: 'workflow:version:switch',
  WORKFLOW_VERSION_SIMULATE: 'workflow:version:simulate',

  // P2-8: 历史数据归档管理
  WORKFLOW_HISTORY_ARCHIVE_VIEW: 'workflow:history:archive:view',
  WORKFLOW_HISTORY_ARCHIVE_TRIGGER: 'workflow:history:archive:trigger',
  WORKFLOW_HISTORY_PURGE_TRIGGER: 'workflow:history:purge:trigger',
} as const

/** 权限码字面量联合类型（用于参数类型约束） */
export type PermissionCode = (typeof PC)[keyof typeof PC]

/** 全部权限码列表（用于初始化角色权限选择树等场景） */
export const ALL_PERMISSION_CODES: readonly string[] = Object.values(PC)
