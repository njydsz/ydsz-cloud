package com.njydsz.pmis.common.permission;

/**
 * 权限码权威常量
 *
 * <p>统一规范: {@code <module>:<resource>:<action>} 三段式:
 * <ul>
 *   <li>{@code module} - 业务域缩写(小写)</li>
 *   <li>{@code resource} - 资源/对象(小写)</li>
 *   <li>{@code action} - 动作(小写, 常用: list/create/update/delete/approve/refresh/act/view/send/upload/trigger/reload)</li>
 * </ul>
 *
 * <p>前后端权限码必须一致,统一从本常量引用。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class PermissionCodes {

    private PermissionCodes() {}

    // ==================== 认证授权 ====================

    public static final String AUTH_USER_LIST = "auth:user:list";
    public static final String AUTH_USER_CREATE = "auth:user:create";
    public static final String AUTH_USER_UPDATE = "auth:user:update";
    public static final String AUTH_USER_DELETE = "auth:user:delete";
    public static final String AUTH_USER_RESET_PWD = "auth:user:reset-password";
    public static final String AUTH_USER_TOGGLE = "auth:user:toggle";
    public static final String AUTH_USER_ASSIGN = "auth:user:assign";

    public static final String AUTH_ROLE_LIST = "auth:role:list";
    public static final String AUTH_ROLE_CREATE = "auth:role:create";
    public static final String AUTH_ROLE_UPDATE = "auth:role:update";
    public static final String AUTH_ROLE_DELETE = "auth:role:delete";
    public static final String AUTH_ROLE_ASSIGN = "auth:role:assign";

    public static final String AUTH_PERM_CREATE = "auth:perm:create";
    public static final String AUTH_PERM_UPDATE = "auth:perm:update";
    public static final String AUTH_PERM_DELETE = "auth:perm:delete";

    // ==================== 组织架构 ====================

    public static final String ORG_DEPT_CREATE = "org:dept:create";
    public static final String ORG_DEPT_UPDATE = "org:dept:update";
    public static final String ORG_DEPT_DELETE = "org:dept:delete";

    // ==================== 系统配置 ====================

    public static final String SYS_CONFIG_LIST = "sys:config:list";
    public static final String SYS_CONFIG_CREATE = "sys:config:create";
    public static final String SYS_CONFIG_UPDATE = "sys:config:update";
    public static final String SYS_CONFIG_DELETE = "sys:config:delete";
    public static final String SYS_CONFIG_REFRESH = "sys:config:refresh";

    // ==================== 特性开关 (批次 20 P2-3) ====================

    public static final String SYS_FEATURE_FLAG_VIEW = "sys:feature-flag:view";
    public static final String SYS_FEATURE_FLAG_UPDATE = "sys:feature-flag:update";
    public static final String SYS_FEATURE_FLAG_CHECK = "sys:feature-flag:check";

    // ==================== 混沌工程 ====================

    public static final String SYS_CHAOS_VIEW = "sys:chaos:view";
    public static final String SYS_CHAOS_CREATE = "sys:chaos:create";
    public static final String SYS_CHAOS_DELETE = "sys:chaos:delete";
    public static final String SYS_CHAOS_TRIGGER = "sys:chaos:trigger";

    // ==================== 考勤 ====================

    public static final String ATTENDANCE_RECORD_CREATE = "attendance:record:create";
    public static final String ATTENDANCE_RECORD_LIST = "attendance:record:list";
    public static final String ATTENDANCE_OVERTIME_CREATE = "attendance:overtime:create";
    public static final String ATTENDANCE_OVERTIME_APPROVE = "attendance:overtime:approve";
    public static final String ATTENDANCE_OVERTIME_LIST = "attendance:overtime:list";
    public static final String ATTENDANCE_LEAVE_CREATE = "attendance:leave:create";
    public static final String ATTENDANCE_LEAVE_APPROVE = "attendance:leave:approve";
    public static final String ATTENDANCE_LEAVE_LIST = "attendance:leave:list";

    // ==================== 资源 ====================

    public static final String RESOURCE_POOL_CREATE = "resource:pool:create";
    public static final String RESOURCE_POOL_UPDATE = "resource:pool:update";
    public static final String RESOURCE_POOL_DELETE = "resource:pool:delete";

    public static final String RESOURCE_TAG_CREATE = "resource:tag:create";
    public static final String RESOURCE_TAG_UPDATE = "resource:tag:update";
    public static final String RESOURCE_TAG_DELETE = "resource:tag:delete";

    public static final String RESOURCE_ASSIGN_ACT = "resource:assign:act";
    public static final String RESOURCE_BENCH_ACT = "resource:bench:act";

    // ==================== 调度 ====================

    public static final String SCHEDULER_JOB_CREATE = "scheduler:job:create";
    public static final String SCHEDULER_JOB_UPDATE = "scheduler:job:update";
    public static final String SCHEDULER_JOB_DELETE = "scheduler:job:delete";
    public static final String SCHEDULER_JOB_TRIGGER = "scheduler:job:trigger";
    public static final String SCHEDULER_JOB_RELOAD = "scheduler:job:reload";

    // ==================== 通知 ====================

    public static final String NOTIF_MESSAGE_SEND = "notif:message:send";

    // ==================== 文件 ====================

    public static final String FILE_STORAGE_UPLOAD = "file:storage:upload";
    public static final String FILE_STORAGE_DELETE = "file:storage:delete";

    // ==================== 执行 ====================

    public static final String EXECUTION_RECONCILE_VIEW = "execution:reconcile:view";
    public static final String EXECUTION_UTILIZATION_VIEW = "execution:utilization:view";
    public static final String EXECUTION_UTILIZATION_RECOMPUTE = "execution:utilization:recompute";

    // ==================== 售后管理 ====================

    public static final String AFTERSALES_WARRANTY_LIST = "aftersales:warranty:list";
    public static final String AFTERSALES_WARRANTY_CREATE = "aftersales:warranty:create";
    public static final String AFTERSALES_WARRANTY_TERMINATE = "aftersales:warranty:terminate";
    public static final String AFTERSALES_WARRANTY_SCAN = "aftersales:warranty:scan";

    public static final String AFTERSALES_OPS_TICKET_LIST = "aftersales:ops-ticket:list";
    public static final String AFTERSALES_OPS_TICKET_CREATE = "aftersales:ops-ticket:create";
    public static final String AFTERSALES_OPS_TICKET_ASSIGN = "aftersales:ops-ticket:assign";
    public static final String AFTERSALES_OPS_TICKET_STATUS = "aftersales:ops-ticket:status";
    public static final String AFTERSALES_OPS_TICKET_EVALUATE = "aftersales:ops-ticket:evaluate";
    public static final String AFTERSALES_OPS_TICKET_SCAN = "aftersales:ops-ticket:scan";

    public static final String AFTERSALES_SATISFACTION_LIST = "aftersales:satisfaction:list";
    public static final String AFTERSALES_SATISFACTION_SUBMIT = "aftersales:satisfaction:submit";
    public static final String AFTERSALES_SATISFACTION_FOLLOWUP = "aftersales:satisfaction:follow-up";

    // ==================== 资源(补充) ====================

    public static final String RESOURCE_BENCH_LIST = "resource:bench:list";
    public static final String RESOURCE_BENCH_INTO = "resource:bench:into";
    public static final String RESOURCE_BENCH_OUT = "resource:bench:out";
    public static final String RESOURCE_UTILIZATION_LIST = "resource:utilization:list";

    // ==================== 个人安全与 2FA ====================

    public static final String AUTH_USER_2FA_BIND = "auth:user:2fa-bind";
    public static final String AUTH_USER_2FA_VERIFY = "auth:user:2fa-verify";
    public static final String AUTH_USER_SESSION_LIST = "auth:user:session-list";
    public static final String AUTH_USER_SESSION_KICK = "auth:user:session-kick";
    public static final String AUTH_USER_CHANGE_PWD = "auth:user:change-password";

    // ==================== 审计日志 ====================

    public static final String AUDIT_LOG_VIEW = "audit:log:view";
    public static final String AUDIT_LOGIN_VIEW = "audit:login:view";
    public static final String AUDIT_EXPORT_VIEW = "audit:export:view";
    public static final String AUDIT_SENSITIVE_VIEW = "audit:sensitive:view";

    // ==================== 敏感操作二次认证 ====================

    public static final String SENSITIVE_REAUTH = "sensitive:reauth:confirm";

    // ==================== 项目模块 ====================

    // 商机
    public static final String PROJECT_OPPORTUNITY_LIST = "project:opportunity:list";
    public static final String PROJECT_OPPORTUNITY_CREATE = "project:opportunity:create";
    public static final String PROJECT_OPPORTUNITY_UPDATE = "project:opportunity:update";
    public static final String PROJECT_OPPORTUNITY_DELETE = "project:opportunity:delete";
    public static final String PROJECT_OPPORTUNITY_EVALUATE = "project:opportunity:evaluate";
    public static final String PROJECT_OPPORTUNITY_CONVERT = "project:opportunity:convert";

    // 立项
    public static final String PROJECT_INITIATION_LIST = "project:initiation:list";
    public static final String PROJECT_INITIATION_CREATE = "project:initiation:create";
    public static final String PROJECT_INITIATION_DELETE = "project:initiation:delete";
    public static final String PROJECT_INITIATION_BUDGET = "project:initiation:budget";
    public static final String PROJECT_INITIATION_GATE = "project:initiation:gate";
    public static final String PROJECT_INITIATION_START_PROCESS = "project:initiation:start-process";

    // 合同
    public static final String PROJECT_CONTRACT_LIST = "project:contract:list";
    public static final String PROJECT_CONTRACT_CREATE = "project:contract:create";
    public static final String PROJECT_CONTRACT_UPDATE = "project:contract:update";
    public static final String PROJECT_CONTRACT_DELETE = "project:contract:delete";
    public static final String PROJECT_CONTRACT_STATUS = "project:contract:status";

    // 合同模板
    public static final String PROJECT_CONTRACT_TEMPLATE_LIST = "project:contract-template:list";
    public static final String PROJECT_CONTRACT_TEMPLATE_CREATE = "project:contract-template:create";
    public static final String PROJECT_CONTRACT_TEMPLATE_PUBLISH = "project:contract-template:publish";

    // 合同变更
    public static final String PROJECT_CONTRACT_CHANGE_LIST = "project:contract-change:list";
    public static final String PROJECT_CONTRACT_CHANGE_CREATE = "project:contract-change:create";
    public static final String PROJECT_CONTRACT_CHANGE_APPROVE = "project:contract-change:approve";

    // ==================== 执行模块(补充) ====================

    // WBS 任务
    public static final String EXECUTION_WBS_LIST = "execution:wbs:list";
    public static final String EXECUTION_WBS_CREATE = "execution:wbs:create";
    public static final String EXECUTION_WBS_UPDATE = "execution:wbs:update";
    public static final String EXECUTION_WBS_STATUS = "execution:wbs:status";
    public static final String EXECUTION_WBS_DELETE = "execution:wbs:delete";

    // 工时管理
    public static final String EXECUTION_TIME_LIST = "execution:time:list";
    public static final String EXECUTION_TIME_CREATE = "execution:time:create";
    public static final String EXECUTION_TIME_APPROVE = "execution:time:approve";
    public static final String EXECUTION_TIME_REJECT = "execution:time:reject";

    // 采购管理
    public static final String EXECUTION_PURCHASE_LIST = "execution:purchase:list";
    public static final String EXECUTION_PURCHASE_CREATE = "execution:purchase:create";
    public static final String EXECUTION_PURCHASE_STATUS = "execution:purchase:status";
    public static final String EXECUTION_PURCHASE_DELETE = "execution:purchase:delete";

    // 费用管理
    public static final String EXECUTION_EXPENSE_LIST = "execution:expense:list";
    public static final String EXECUTION_EXPENSE_CREATE = "execution:expense:create";
    public static final String EXECUTION_EXPENSE_STATUS = "execution:expense:status";

    // 风险管理
    public static final String EXECUTION_RISK_LIST = "execution:risk:list";
    public static final String EXECUTION_RISK_CREATE = "execution:risk:create";
    public static final String EXECUTION_RISK_STATUS = "execution:risk:status";

    // 收入管理
    public static final String EXECUTION_REVENUE_LIST = "execution:revenue:list";
    public static final String EXECUTION_REVENUE_CREATE = "execution:revenue:create";

    // 利润管理
    public static final String EXECUTION_PROFIT_LIST = "execution:profit:list";
    public static final String EXECUTION_PROFIT_SNAPSHOT = "execution:profit:snapshot";

    // 交付物管理
    public static final String EXECUTION_DELIVERY_LIST = "execution:delivery:list";
    public static final String EXECUTION_DELIVERY_CREATE = "execution:delivery:create";
    public static final String EXECUTION_DELIVERY_REVIEW = "execution:delivery:review";

    // EVM 挣值管理
    public static final String EXECUTION_EVM_LIST = "execution:evm:list";
    public static final String EXECUTION_EVM_SAVE = "execution:evm:save";
    public static final String EXECUTION_EVM_DASHBOARD = "execution:evm:dashboard";

    // 双费率 / 利润模拟
    public static final String EXECUTION_RATE_LIST = "execution:rate:list";
    public static final String EXECUTION_RATE_CARD_CREATE = "execution:rate-card:create";
    public static final String EXECUTION_RATE_INTERNAL_CREATE = "execution:rate-internal:create";
    public static final String EXECUTION_SIMULATION_LIST = "execution:simulation:list";
    public static final String EXECUTION_SIMULATION_CREATE = "execution:simulation:create";
    public static final String EXECUTION_SIMULATION_APPROVE = "execution:simulation:approve";

    // 每日对账
    public static final String EXECUTION_RECONCILE_RUN = "execution:reconcile:run";

    // 预算管理
    public static final String EXECUTION_BUDGET_VIEW = "execution:budget:view";

    // ==================== 财务模块 ====================

    // 开票管理
    public static final String FINANCE_INVOICE_LIST = "finance:invoice:list";
    public static final String FINANCE_INVOICE_CREATE = "finance:invoice:create";
    public static final String FINANCE_INVOICE_APPROVE = "finance:invoice:approve";
    public static final String FINANCE_INVOICE_ISSUE = "finance:invoice:issue";
    public static final String FINANCE_INVOICE_REVERSE = "finance:invoice:reverse";

    // 回款管理
    public static final String FINANCE_PAYMENT_LIST = "finance:payment:list";
    public static final String FINANCE_PAYMENT_CREATE = "finance:payment:create";
    public static final String FINANCE_PAYMENT_ALLOCATE = "finance:payment:allocate";

    // 客户信用
    public static final String FINANCE_CREDIT_LIST = "finance:credit:list";
    public static final String FINANCE_CREDIT_ASSESS = "finance:credit:assess";

    // ==================== 报表与驾驶舱 ====================

    public static final String REPORT_PROFIT_VIEW = "report:profit:view";
    public static final String REPORT_COST_VIEW = "report:cost:view";
    public static final String REPORT_PAYMENT_LEDGER_VIEW = "report:payment-ledger:view";
    public static final String REPORT_LIFECYCLE_VIEW = "report:lifecycle:view";
    public static final String REPORT_ADVANCED_VIEW = "report:advanced:view";
    public static final String REPORT_EXECUTIVE_VIEW = "report:executive:view";
    public static final String COCKPIT_OVERVIEW_VIEW = "cockpit:overview:view";
    public static final String COCKPIT_DRILLDOWN_VIEW = "cockpit:drilldown:view";
    public static final String COCKPIT_ALERT_VIEW = "cockpit:alert:view";

    // ==================== 项目结项与变更 ====================

    public static final String CLOSURE_LIST = "closure:project:list";
    public static final String CLOSURE_CREATE = "closure:project:create";
    public static final String CLOSURE_STATUS = "closure:project:status";
    public static final String PROJECT_CHANGE_LIST = "project:change:list";
    public static final String PROJECT_CHANGE_CREATE = "project:change:create";
    public static final String PROJECT_CHANGE_STATUS = "project:change:status";

    // ==================== AI Agent ====================

    public static final String AGENT_RUN = "agent:task:run";
    public static final String AGENT_HISTORY = "agent:task:list";
    public static final String AGENT_VIEW = "agent:task:view";
    public static final String AGENT_ORCHESTRATION_RUN = "agent:orchestration:run";
    public static final String AGENT_ORCHESTRATION_VIEW = "agent:orchestration:view";
    public static final String AGENT_PREDICTION_VIEW = "agent:prediction:view";

    // ==================== 兼容旧码(将废弃,仅用于数据迁移) ====================

    /** @deprecated use {@link #SCHEDULER_JOB_CREATE} */
    @Deprecated
    public static final String LEGACY_JOB_ADD = "job:add";
    /** @deprecated use {@link #SCHEDULER_JOB_UPDATE} */
    @Deprecated
    public static final String LEGACY_JOB_EDIT = "job:edit";
    /** @deprecated use {@link #NOTIF_MESSAGE_SEND} */
    @Deprecated
    public static final String LEGACY_NOTIF_SEND = "notif:send";
    /** @deprecated use {@link #FILE_STORAGE_UPLOAD} */
    @Deprecated
    public static final String LEGACY_FILE_UPLOAD = "file:upload";
    /** @deprecated use {@link #FILE_STORAGE_DELETE} */
    @Deprecated
    public static final String LEGACY_FILE_DELETE = "file:delete";
    /** @deprecated use {@link #RESOURCE_TAG_CREATE} */
    @Deprecated
    public static final String LEGACY_TAG_ADD = "resource:tag:add";
    /** @deprecated use {@link #RESOURCE_TAG_DELETE} */
    @Deprecated
    public static final String LEGACY_TAG_REMOVE = "resource:tag:remove";
    /** @deprecated use {@link #RESOURCE_TAG_UPDATE} */
    @Deprecated
    public static final String LEGACY_TAG_REPLACE = "resource:tag:replace";
}
