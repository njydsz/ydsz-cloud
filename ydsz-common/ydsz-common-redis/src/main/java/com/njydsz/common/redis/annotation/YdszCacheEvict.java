package com.njydsz.common.redis.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 缓存淘汰注解
 *
 * <p>标注在方法上，方法执行成功后自动删除对应的 Redis 缓存。 支持 SpEL 表达式动态指定缓存 Key，与 {@link YdszCacheable} 配合使用。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @YdszCacheEvict(key = "'user:' + #id")
 * public void deleteUser(Long id) {
 *     userMapper.deleteById(id);
 * }
 *
 * @YdszCacheEvict(key = "'users'", allEntries = true)
 * public void deleteAllUsers() {
 *     userMapper.deleteAll();
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface YdszCacheEvict {

  /**
   * 缓存 Key（支持 SpEL 表达式）
   *
   * @return SpEL Key 表达式
   */
  String key();

  /**
   * 是否清除所有缓存
   *
   * <p>{@code true} 时忽略 {@link #key()}，清除前缀下所有匹配的缓存。 使用 SCAN 命令扫描并批量删除，避免 KEYS 命令阻塞 Redis。
   *
   * @return 是否清除所有缓存
   */
  boolean allEntries() default false;
}
