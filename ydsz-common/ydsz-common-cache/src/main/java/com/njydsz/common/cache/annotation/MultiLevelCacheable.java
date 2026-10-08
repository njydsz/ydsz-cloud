package com.njydsz.common.cache.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 多级缓存（L1 本地 YdszCache + L2 Redis）声明式注解。
 *
 * <p>由 {@link com.njydsz.common.cache.aspect.MultiLevelCacheAspect} 提供切面实现。
 * 与编程式 {@link com.njydsz.common.cache.support.MultiLevelCacheTemplate} 对标，
 * 但无需在每个 Service 中样板式注入和调用，直接在方法上声明即可获得多级缓存能力。
 *
 * <p>切面执行流程：
 *
 * <ol>
 *   <li>解析 {@link #key()} SpEL 表达式 → 生成缓存键（格式：{@code mlevel:{declaringClass.simpleName}.{methodName}:{spelResult}}）</li>
 *   <li>L1（{@code YdszCache}本地缓存）命中 → 直接返回</li>
 *   <li>L2（Redis StringOps）命中 → 回填 L1，返回</li>
 *   <li>未命中 → 执行目标方法体，结果写入 L1 + L2</li>
 * </ol>
 *
 * <p>防穿透保护由 {@link
 * com.njydsz.common.cache.api.CacheProtectionGuard} 实现（{@link #useProtection()} = true 时启用）。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 配置 SpEL key，使用默认 TTL
 * @MultiLevelCacheable(key = "#userId")
 * public UserProfile getProfile(Long userId) {
 *     return userRepository.findById(userId).orElse(null);
 * }
 *
 * // 自定义本地/远程 TTL，关闭防穿透
 * @MultiLevelCacheable(
 *     key = "#query.code",
 *     localTTL = 30000,
 *     remoteTTL = 600000,
 *     localMaxSize = 2048,
 *     useProtection = false
 * )
 * public List<DictItemVO> listByCode(DictQuery query) {
 *     return dictService.findByCode(query.getCode());
 * }
 * }</pre>
 *
 * <p><b>前置条件：</b>Spring 容器中需存在 {@link
 * org.springframework.data.redis.core.StringRedisTemplate} Bean
 * （由装配方引入 {@code spring-data-redis} 后自动配置）。若 Redis 不可用，切面降级为纯 L1 模式。
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see com.njydsz.common.cache.aspect.MultiLevelCacheAspect
 * @see com.njydsz.common.cache.support.MultiLevelCacheTemplate
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MultiLevelCacheable {

  /**
   * SpEL 表达式，基于方法参数引用生成缓存键的子段。
   *
   * <p>SpEL 上下文变量（对标 Spring Cache）：
   *
   * <ul>
   *   <li>{@code #root.methodName} — 方法名
   *   <li>{@code #root.target} — 目标对象
   *   <li>{@code #paramName} — 方法参数名（如 {@code #userId}、{@code #query.code}）
   *   <li>{@code #p0}, {@code #a0} — 参数索引（如 {@code #p0} 为第一个参数）
   * </ul>
   *
   * <p>最终 L1 + L2 使用的完整缓存键格式为：{@code mlevel:{declaringClass.simpleName}.{methodName}:{spelResult}}。
   *
   * @return SpEL 表达式字符串；为空时使用默认 key 生成规则（方法名 + 参数拼接）
   */
  String key() default "";

  /**
   * L1 本地缓存（{@code YdszCache}）写入后过期时间（毫秒）。
   *
   * @return 过期时间，毫秒；默认 60000（60 秒）
   */
  long localTTL() default 60_000L;

  /**
   * L2 远程缓存（Redis StringOps）过期时间（毫秒）。
   *
   * <p>注意：该值同时作为 L2 空结果占位 TTL 的基准（空占位 TTL 取 {@code remoteTTL / 3}，至少 1 秒）。
   *
   * @return 过期时间，毫秒；默认 300000（5 分钟）
   */
  long remoteTTL() default 300_000L;

  /**
   * L1 本地缓存最大条目数。
   *
   * @return 最大条目数；默认 1000
   */
  long localMaxSize() default 1_000L;

  /**
   * 是否启用防穿透保护（{@code CacheProtectionGuard}），默认 {@code true}。
   *
   * <p>开启后，L1 + L2 均未命中时执行方法体；若方法返回空（null），通过保护守卫注册带抖动的空值占位符，
   * 避免击穿 DB。
   *
   * @return {@code true} 启用防穿透/防击穿保护
   */
  boolean useProtection() default true;
}
