package com.njydsz.pmis.common.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 缓存失效注解 — 声明式缓存清除
 *
 * <p>标注在方法上，表示方法执行后应清除指定的缓存条目。 类似 Spring Cache 的 @CacheEvict。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * @CacheInvalidate(name = "users", key = "#userId")
 * public void updateUser(String userId, UserDTO dto) {
 *   userDao.update(userId, dto);
 * }
 *
 * @CacheInvalidate(name = "users", allEntries = true)
 * public void batchUpdateUsers(List<User> users) {
 *   userDao.batchUpdate(users);
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * 
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheInvalidate {

  /** 缓存名称 */
  String name();

  /** SpEL key 表达式（默认使用方法参数的 hashCode） */
  String key() default "";

  /** 是否清除全部缓存条目 */
  boolean allEntries() default false;

  /** 是否在方法执行前清除（默认 false，方法执行后清除） */
  boolean beforeInvocation() default false;
}
