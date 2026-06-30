/**
 * 前端权限码常量
 *
 * 与后端 com.njydsz.pmis.common.permission.PermissionCodes 一一对应。
 * 统一规范: <module>:<resource>:<action> 三段式。
 *
 * 任何前端页面、组件中涉及权限判断时, 必须从本常量引用, 禁止硬编码字符串。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */

export const PC = {
  // 认证授权
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

  // 组织架构
  ORG_DEPT_CREATE: 'org:dept:create',
  ORG_DEPT_UPDATE: 'org:dept:update',
  ORG_DEPT_DELETE: 'org:dept:delete',

  // 系统配置
  SYS_CONFIG_LIST: 'sys:config:list',
  SYS_CONFIG_CREATE: 'sys:config:create',
  SYS_CONFIG_UPDATE: 'sys:config:update',
  SYS_CONFIG_DELETE: 'sys:config:delete',
  SYS_CONFIG_REFRESH: 'sys:config:refresh',

  // 考勤
  ATTENDANCE_RECORD_CREATE: 'attendance:record:create',
  ATTENDANCE_RECORD_LIST: 'attendance:record:list',
  ATTENDANCE_OVERTIME_CREATE: 'attendance:overtime:create',
  ATTENDANCE_OVERTIME_APPROVE: 'attendance:overtime:approve',
  ATTENDANCE_OVERTIME_LIST: 'attendance:overtime:list',
  ATTENDANCE_LEAVE_CREATE: 'attendance:leave:create',
  ATTENDANCE_LEAVE_APPROVE: 'attendance:leave:approve',
  ATTENDANCE_LEAVE_LIST: 'attendance:leave:list',

  // 资源
  RESOURCE_POOL_CREATE: 'resource:pool:create',
  RESOURCE_POOL_UPDATE: 'resource:pool:update',
  RESOURCE_POOL_DELETE: 'resource:pool:delete',

  RESOURCE_TAG_CREATE: 'resource:tag:create',
  RESOURCE_TAG_UPDATE: 'resource:tag:update',
  RESOURCE_TAG_DELETE: 'resource:tag:delete',

  RESOURCE_ASSIGN_ACT: 'resource:assign:act',
  RESOURCE_BENCH_ACT: 'resource:bench:act',

  // 调度
  SCHEDULER_JOB_CREATE: 'scheduler:job:create',
  SCHEDULER_JOB_UPDATE: 'scheduler:job:update',
  SCHEDULER_JOB_DELETE: 'scheduler:job:delete',
  SCHEDULER_JOB_TRIGGER: 'scheduler:job:trigger',
  SCHEDULER_JOB_RELOAD: 'scheduler:job:reload',

  // 通知
  NOTIF_MESSAGE_SEND: 'notif:message:send',

  // 文件
  FILE_STORAGE_UPLOAD: 'file:storage:upload',
  FILE_STORAGE_DELETE: 'file:storage:delete',

  // 执行
  EXECUTION_RECONCILE_VIEW: 'execution:reconcile:view',
} as const

export type PermissionCode = (typeof PC)[keyof typeof PC]

/** 全部权限码列表 */
export const ALL_PERMISSION_CODES: readonly string[] = Object.values(PC)
