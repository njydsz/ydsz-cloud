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
