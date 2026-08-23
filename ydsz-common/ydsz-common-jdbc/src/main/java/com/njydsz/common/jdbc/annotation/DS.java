package com.njydsz.common.jdbc.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据源切换注解
 *
 * <p>标注在类或方法上，用于动态切换数据源。方法级注解优先级高于类级。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 类级别：整个 Service 使用 slave 数据源
 * @Service
 * @DS("slave")
 * public class UserService {
 *
 *     // 方法级别：覆盖类级别，使用 master 数据源
 *     @DS("master")
 *     public void updateUser(User user) {
 *         // 写操作使用主库
 *     }
 *
 *     public List<User> listUsers() {
 *         // 读操作使用从库（继承类级别）
 *     }
 * }
 * }</pre>
 *
 * <p>支持 SpEL 表达式动态解析数据源名称：
 *
 * <pre>{@code
 * @DS("#tenant.dbSource")
 * public List<Data> queryByTenant() {
 *     // 根据租户动态路由
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DS {

  /**
   * 数据源名称。
   *
   * <p>支持：
   *
   * <ul>
   *   <li>固定名称：{@code "master"}、{@code "slave"}
   *   <li>SpEL 表达式：{@code "#tenant.dbSource"}、{@code "@dsResolver.resolve()"}
   * </ul>
   *
   * @return 数据源名称（固定名或 SpEL 表达式），默认 {@code "master"}
   */
  String value() default "master";
}
