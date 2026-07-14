package com.njydsz.pmis.common.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 缓存刷新注解 — 声明式自动刷新
 *
 * <p>标注在方法上，配合 {@link Cached} 使用，表示缓存条目应定期自动刷新。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * @Cached(name = "config", key = "#configKey")
 * @CacheRefresh(refreshAfterWrite = 5, timeUnit = TimeUnit.MINUTES)
 * public Config getConfig(String configKey) {
 *   return configDao.findByKey(configKey);
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @version 4.1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheRefresh {

  /** 刷新间隔 */
  long refreshAfterWrite() default -1;

  /** 时间单位 */
  TimeUnit timeUnit() default TimeUnit.SECONDS;

  /** 是否启用 SWR 模式（先返回旧值，异步刷新） */
  boolean staleWhileRevalidate() default false;

  /** SWR 陈旧期（仅在 staleWhileRevalidate=true 时有效） */
  long stalePeriod() default 0;
}
