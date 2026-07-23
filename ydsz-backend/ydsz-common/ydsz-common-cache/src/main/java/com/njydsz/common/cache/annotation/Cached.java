package com.njydsz.common.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 缓存注解 — 声明式缓存写入
 *
 * <p>标注在方法上，表示方法的返回值应被缓存。 类似 Spring Cache 的 @Cacheable 但支持更多
 * YdszCache 特有配置。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * @Cached(name = "users", key = "#userId", expireAfterWrite = 30, timeUnit = TimeUnit.MINUTES)
 * public User getUser(String userId) {
 *   return userDao.findById(userId);
 * }
 * }</pre>
 *
 * 
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cached {

  /** 缓存名称 */
  String name();

  /** SpEL key 表达式（默认使用方法参数的 hashCode） */
  String key() default "";

  /** 是否条件缓存（SpEL 表达式，true 才缓存） */
  String condition() default "";

  /** 是否排除 null 值（默认 true） */
  boolean unlessNull() default true;

  /** 写入后过期时间（-1 表示不过期） */
  long expireAfterWrite() default -1;

  /** 访问后过期时间（-1 表示不过期） */
  long expireAfterAccess() default -1;

  /** 时间单位 */
  TimeUnit timeUnit() default TimeUnit.SECONDS;
}
