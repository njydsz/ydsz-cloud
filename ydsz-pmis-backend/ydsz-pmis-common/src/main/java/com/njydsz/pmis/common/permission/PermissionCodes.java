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

    // ==================== 定时任务 ====================

    public static final String CRONJOB_JOB_CREATE = "cronjob:job:create";
    public static final String CRONJOB_JOB_UPDATE = "cronjob:job:update";
    public static final String CRONJOB_JOB_DELETE = "cronjob:job:delete";
    public static final String CRONJOB_JOB_TRIGGER = "cronjob:job:trigger";
    public static final String CRONJOB_JOB_RELOAD = "cronjob:job:reload";
    /** 告警规则管理（P5 告警 + 监控） */
    public static final String CRONJOB_ALERT_CREATE = "cronjob:alert:create";
    public static final String CRONJOB_ALERT_UPDATE = "cronjob:alert:update";
    public static final String CRONJOB_ALERT_DELETE = "cronjob:alert:delete";
    public static final String CRONJOB_ALERT_VIEW = "cronjob:alert:view";

    // ==================== 通知 ====================

    public static final String NOTIF_MESSAGE_SEND = "notif:message:send";
    /** 实时推送消息到指定用户 (内部 Feign 接口, 仅服务账号/管理员可调用) */
    public static final String NOTIF_PUSH = "notif:message:push";
    /** 广播消息到所有在线用户 (内部 Feign 接口, 仅服务账号/管理员可调用) */
    public static final String NOTIF_BROADCAST = "notif:message:broadcast";
    /** 收件箱分页/未读数量 (本人) */
    public static final String NOTIF_MESSAGE_LIST = "notif:message:list";
    /** 标记已读 (本人) */
    public static final String NOTIF_MESSAGE_VIEW = "notif:message:view";
    /** 删除通知 (本人) */
    public static final String NOTIF_MESSAGE_DELETE = "notif:message:delete";
    /** 撤回通知 (本人/管理员) */
    public static final String NOTIF_MESSAGE_RECALL = "notif:message:recall";

    // ==================== 消息模板 ====================

    public static final String MESSAGE_TEMPLATE_CREATE = "message:template:create";
    public static final String MESSAGE_TEMPLATE_UPDATE = "message:template:update";
    public static final String MESSAGE_TEMPLATE_DELETE = "message:template:delete";
    public static final String MESSAGE_TEMPLATE_VIEW = "message:template:view";
    public static final String MESSAGE_TEMPLATE_LIST = "message:template:list";
    /** 模板审核 (管理员) */
    public static final String MESSAGE_TEMPLATE_APPROVE = "message:template:approve";

    // ==================== 消息订阅 ====================

    public static final String MESSAGE_SUBSCRIPTION_UPDATE = "message:subscription:update";
    public static final String MESSAGE_SUBSCRIPTION_LIST = "message:subscription:list";
    public static final String MESSAGE_SUBSCRIPTION_DELETE = "message:subscription:delete";

    // ==================== 消息路由规则 ====================

    public static final String MESSAGE_ROUTE_RULE_CREATE = "message:route-rule:create";
    public static final String MESSAGE_ROUTE_RULE_UPDATE = "message:route-rule:update";
    public static final String MESSAGE_ROUTE_RULE_DELETE = "message:route-rule:delete";
    public static final String MESSAGE_ROUTE_RULE_VIEW = "message:route-rule:view";
    public static final String MESSAGE_ROUTE_RULE_LIST = "message:route-rule:list";

    // ==================== 消息回执 ====================

    /** 回执回调 (服务商 → 系统) */
    public static final String MESSAGE_RECEIPT_CALLBACK = "message:receipt:callback";
    /** 回执查询 */
    public static final String MESSAGE_RECEIPT_VIEW = "message:receipt:view";

    // ==================== 消息撤回 ====================

    /** 撤回站内通知/已发送消息/批量撤回 */
    public static final String MESSAGE_RECALL_ACT = "message:recall:act";

    // ==================== 消息偏好 ====================

    public static final String MESSAGE_PREFERENCE_UPDATE = "message:preference:update";
    public static final String MESSAGE_PREFERENCE_VIEW = "message:preference:view";
    public static final String MESSAGE_PREFERENCE_DELETE = "message:preference:delete";

    // ==================== 消息灰度 ====================

    public static final String MESSAGE_CANARY_UPDATE = "message:canary:update";
    public static final String MESSAGE_CANARY_VIEW = "message:canary:view";

    // ==================== 消息聚合 ====================

    public static final String MESSAGE_AGGREGATE_LIST = "message:aggregate:list";
    public static final String MESSAGE_AGGREGATE_REFRESH = "message:aggregate:refresh";

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

    public static final String AUTH_USER_2FA_BIND = "auth:user:bind-2fa";
    public static final String AUTH_USER_2FA_VERIFY = "auth:user:verify-2fa";
    public static final String AUTH_USER_SESSION_LIST = "auth:user:session-list";
    public static final String AUTH_USER_SESSION_KICK = "auth:user:session-kick";
    public static final String AUTH_USER_CHANGE_PWD = "auth:user:change-password";

    // ==================== 审计日志 ====================

    public static final String AUDIT_LOG_VIEW = "audit:log:view";
    public static final String AUDIT_LOG_CLEAN = "audit:log:clean";
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

    // ==================== 工作流 (P0-3 补齐) ====================

    /** 流程定义部署 (管理员) */
    public static final String WORKFLOW_DEFINITION_DEPLOY = "workflow:definition:deploy";
    /** 流程定义发布/废弃/启停/切换版本 (管理员) */
    public static final String WORKFLOW_DEFINITION_PUBLISH = "workflow:definition:publish";
    /** 流程定义删除 (管理员) */
    public static final String WORKFLOW_DEFINITION_DELETE = "workflow:definition:delete";
    /** 流程定义导入 (管理员) */
    public static final String WORKFLOW_DEFINITION_IMPORT = "workflow:definition:import";
    /** 流程定义设计器配置 (管理员) */
    public static final String WORKFLOW_DEFINITION_DESIGN = "workflow:definition:design";
    /** 流程实例查询/查看 */
    public static final String WORKFLOW_INSTANCE_VIEW = "workflow:instance:view";
    /** 流程实例启动 */
    public static final String WORKFLOW_INSTANCE_START = "workflow:instance:start";
    /** 流程实例终止/挂起/激活 (管理员) */
    public static final String WORKFLOW_INSTANCE_CONTROL = "workflow:instance:control";
    /** 流程实例回滚 (管理员) */
    public static final String WORKFLOW_INSTANCE_ROLLBACK = "workflow:instance:rollback";
    /** 流程实例重审 (发起人/管理员) */
    public static final String WORKFLOW_INSTANCE_RESUBMIT = "workflow:instance:resubmit";
    /** 流程实例迁移 (管理员) */
    public static final String WORKFLOW_INSTANCE_MIGRATE = "workflow:instance:migrate";
    /** 任务查询/详情 (本人) */
    public static final String WORKFLOW_TASK_VIEW = "workflow:task:view";
    /** 任务操作: 通过/驳回/签收/转办/委派/加签/跳转/批量审批 (本人) */
    public static final String WORKFLOW_TASK_OPERATE = "workflow:task:operate";
    /** GAP-P2-9: 自由流跳转 — 运行时动态指定下一节点 + 办理人 (需节点级 freeJump 白名单) */
    public static final String WORKFLOW_TASK_FREE_JUMP = "workflow:task:freeJump";
    /** 灰度发布管理 (管理员) */
    public static final String WORKFLOW_CANARY_MANAGE = "workflow:canary:manage";
    /** 抄送查询 (本人) */
    public static final String WORKFLOW_CC_VIEW = "workflow:cc:view";
    /** 委派授权管理 (本人) */
    public static final String WORKFLOW_DELEGATE_MANAGE = "workflow:delegate:manage";
    /** SLA 配置 (管理员) */
    public static final String WORKFLOW_SLA_CONFIG = "workflow:sla:config";
    /** 通知通道配置 (管理员) */
    public static final String WORKFLOW_NOTIFY_CONFIG = "workflow:notify:config";
    /** 工作流监控看板查看 */
    public static final String WORKFLOW_MONITOR_VIEW = "workflow:monitor:view";
    /** 工作流模板导入 (管理员) */
    public static final String WORKFLOW_TEMPLATE_IMPORT = "workflow:template:import";

    }
