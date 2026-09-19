package com.njydsz.common.cache.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * YDSZ 声明式缓存注解 — 在 Spring Cache 之上封装 YDSZ 差异化防护能力。
 *
 * <p>对标 Spring 的 {@code @Cacheable}，由 {@link YdszCacheableAspect} 提供切面实现。 与 Spring
 * 原生注解的差异化能力：
 *
 * <ul>
 *   <li><b>nullTtl</b>（空值 TTL 防穿透）：方法返回 null 时自动注册带随机抖动的空值占位符（对标 {@code
 *       CacheProtectionGuard}），占位期内直接返回 null 不回源，过期后自动恢复加载
 *   <li><b>sync</b>（防击穿开关）：开启后同一 key 的并发请求仅一个执行方法体，其余等待结果（对标 {@code
 *       CacheProtectionGuard#getWithProtection} 防击穿语义）
 *   <li><b>tenantKey</b>（租户隔离开关）：开启后缓存 key 自动追加当前租户 ID，无需在 key 表达式中手
 *       动拼接（复用 {@code CacheKeyBuilder} 租户上下文）
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 基本用法
 * @YdszCacheable(value = "system:dict:item", key = "#typeCode")
 * public List<DictItemVO> listByCode(String typeCode) {
 *     return dictRepository.findByCode(typeCode);
 * }
 *
 * // 防穿透 + 防击穿
 * @YdszCacheable(value = "user:profile", key = "#userId", nullTtl = 60, sync = true)
 * public UserProfile getProfile(Long userId) {
 *     return userRepository.findById(userId).orElse(null);
 * }
 *
 * // 带条件的缓存
 * @YdszCacheable(value = "config:value", key = "#configKey", condition = "#configKey != 'secret'")
 * public String getConfigValue(String configKey) {
 *     return configRepository.findValue(configKey);
 * }
 * }</pre>
 *
 * <p><b>自动配置：</b>通过 {@link YdszCacheableAutoConfiguration} 自动注册 {@link
 * YdszCacheableAspect} 切面 Bean。 要求上下文中存在 {@link YdszCacheManager} 类型的 {@code CacheManager}。
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see YdszCacheableAspect
 * @see YdszCacheableAutoConfiguration
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface YdszCacheable {

  /**
   * 缓存名称。
   *
   * <p>对应 {@link YdszCacheManager} 管理的 cache name（亦是 Spring Cache 的 cache name）。 {@code
   * YdszCacheManager} 会按该 name 查找或创建底层 {@code YdszCache} 实例。
   *
   * @return 缓存名称，不可为空
   */
  String value();

  /**
   * 缓存 key SpEL 表达式。
   *
   * <p>SpEL 上下文变量（对标 Spring Cache）：
   *
   * <ul>
   *   <li>{@code #root.methodName} — 方法名
   *   <li>{@code #root.target} — 目标对象
   *   <li>{@code #paramName} — 方法参数名（如 {@code #typeCode}）
   *   <li>{@code #p0}, {@code #a0} — 参数索引（如 {@code #p0} 为第一个参数）
   * </ul>
   *
   * <p>为空时使用默认 key 生成规则（方法名 + 参数拼接），不建议在生产环境使用。
   *
   * @return SpEL key 表达式，可为空
   */
  String key() default "";

  /**
   * 过期时间（秒），默认 1800 秒（30 分钟）。
   *
   * <p>设置后，切面在获取缓存前会使用 {@link
   * com.njydsz.common.cache.internal.decorator.ExpirableCache} 为底层缓存叠加 {@code
   * expireAfterWrite} 过期策略。当底层缓存本身已配置过期策略时，以注解值为准（取较小者）。
   *
   * @return 过期时间（秒），<= 0 时使用底层缓存的过期配置
   */
  long ttl() default -1;

  /**
   * 防击穿开关（并发互斥加载），默认关闭。
   *
   * <p>开启后同一 key 的并发请求仅一个线程执行方法体，其余等待结果——对标 {@link
   * com.njydsz.common.cache.api.CacheProtectionCache#getWithProtection} 防击穿语义。
   * 推荐用于以下场景：
   *
   * <ul>
   *   <li>热点数据首次加载（系统启动期预热完毕前）
   *   <li>缓存失效时的瞬态并发（TTL 到期后的"惊群"场景）
   * </ul>
   *
   * @return true 启用防击穿
   */
  boolean sync() default false;

  /**
   * 空值占位 TTL（秒），防缓存穿透，默认 60 秒。
   *
   * <p>方法返回 null 时，切面使用 {@link
   * com.njydsz.common.cache.api.CacheProtectionGuard#registerNullPlaceholder} 注册带随机抖动的
   * 空值占位符。占位期内对同一 key 的后续调用直接返回 null（不回源）， 过期后自动触发重新加载。
   *
   * <p>设置 <= 0 时禁用空值占位（方法返回 null 时不缓存）。
   *
   * @return 空值占位 TTL（秒），默认 60 秒
   */
  long nullTtl() default 60;

  /**
   * 租户隔离开关，默认开启。
   *
   * <p>开启后缓存 key 构造时自动追加当前租户 ID，通过 {@link
   * com.njydsz.common.cache.support.CacheKeyBuilder} 获取租户上下文。
   * 仅在确认缓存数据全局共享（如系统级配置）时可关闭。
   *
   * @return true = 追加租户 ID
   */
  boolean tenantKey() default true;

  /**
   * 缓存条件（SpEL 表达式），方法调用后评估。
   *
   * <p>表达式返回 true 时使用缓存；返回 false 时跳过缓存直接执行方法体。 对标 Spring Cache 的 {@code
   * unless} 语义——此处为「前置条件」，方法执行前评估。
   *
   * @return SpEL 条件表达式，为空时无条件执行
   */
  String condition() default "";

  /**
   * 不缓存条件（SpEL 表达式），方法调用后评估。
   *
   * <p>表达式返回 true 时不缓存结果（即便缓存命中也跳过缓存直接执行方法体）。 对标 Spring Cache 的 {@code
   * unless} 语义。
   *
   * @return SpEL 条件表达式，为空时默认缓存
   */
  String unless() default "";
}
