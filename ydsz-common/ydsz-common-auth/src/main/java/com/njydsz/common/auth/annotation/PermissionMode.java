package com.njydsz.common.auth.annotation;

/**
 * 权限校验模式枚举。
 *
 * <p>统一用于 {@link AuthMenuPermission} 和 {@link AuthApiPermission} 注解的多权限码校验模式。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum PermissionMode {
  /** AND 模式：用户必须同时拥有所有指定的权限码才能通过校验。 */
  AND,

  /** OR 模式：用户只需拥有任意一个指定的权限码即可通过校验。 */
  OR
}
