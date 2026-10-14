package com.njydsz.common.safe.permission;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级权限注解。
 *
 * <p>标注在 Controller 方法上，指定需要管控的字段表达式（SpEL）。
 * 切面 {@link FieldPermissionAspectImpl} 在执行时解析该表达式并与当前用户的字段权限集比对，
 * 无权限时拒绝访问。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @FieldPermission("#request.userId")
 * public YdszResponse<UserVO> getUserDetail(@RequestBody GetUserRequest request) {
 *     // ...
 * }
 * }</pre>
 *
 * <p>表达式返回值将作为字段权限校验的字段标识符，与安全上下文中用户被授权的字段集合进行匹配。
 *
 * @author ydsz
 * @since 26.10.14
 * @see FieldPermissionAspectImpl
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldPermission {

  /**
   * 字段表达式（SpEL），用于提取需要校验的字段标识。
   *
   * <p>示例：
   * <ul>
   *   <li>{@code "#request.userId"} — 从名为 request 的参数中提取 userId</li>
   *   <li>{@code "#userId"} — 直接引用方法参数</li>
   * </ul>
   *
   * @return SpEL 表达式字符串
   */
  String value();
}
