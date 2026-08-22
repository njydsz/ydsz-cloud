package com.njydsz.common.safe.annotation;

/**
 * 敏感操作等级（P1-8）。
 *
 * <p>用于区分敏感操作的校验强度，配合 {@link SensitiveOperation} 注解使用：
 *
 * <ul>
 *   <li>{@link #MEDIUM} — 中等敏感：个人资料更新等，二次认证密码校验即可
 *   <li>{@link #HIGH} — 高敏感（默认）：改密、角色分配、权限变更等，二次认证 + 审计
 *   <li>{@link #CRITICAL} — 极敏感：删除用户、批量禁用、租户级操作等，短时效二次认证 + 强制审计告警
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum SensitiveLevel {

  /** 中等敏感（个人资料等） */
  MEDIUM,

  /** 高敏感（默认，改密/角色分配等） */
  HIGH,

  /** 极敏感（删除用户/批量禁用/租户级操作等） */
  CRITICAL
}
