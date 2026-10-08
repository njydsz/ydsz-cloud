package com.njydsz.common.cache.aspect;

import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link MultiLevelCacheable} / {@link MultiLevelCacheAspect} 的 Spring Boot 自动配置。
 *
 * <p>在以下条件同时满足时注册 {@link MultiLevelCacheAspect} 切面 Bean：
 *
 * <ul>
 *   <li>AspectJ 注解类位于 classpath（{@code @ConditionalOnClass(Aspect.class)}）</li>
 *   <li>{@link StringRedisTemplate} 类位于 classpath（{@code @ConditionalOnClass(StringRedisTemplate.class)}）—
 *       注：类可用只决定是否加载本自动配置；运行时由切面通过 {@code ObjectProvider}
 *       优雅降级（Redis Bean 缺失 → L1-only）</li>
 *   <li>{@code ydsz.cache.multilevel.enabled} 不为 {@code false}（默认开启，可配置关闭）</li>
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 1. 在 application.yml 启用（默认开启）
 * // ydsz:
 * //   cache:
 * //     multilevel:
 * //       enabled: true
 *
 * // 2. 在 Service 方法上使用
 * @Service
 * public class DictService {
 *
 *     @MultiLevelCacheable(
 *         key = "#code",
 *         localTTL = 60_000,
 *         remoteTTL = 300_000,
 *         localMaxSize = 1000,
 *         useProtection = true
 *     )
 *     public List<DictItemVO> findByCode(String code) {
 *         return dictRepository.findByCode(code);
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see MultiLevelCacheable
 * @see MultiLevelCacheAspect
 */
@AutoConfiguration(after = com.njydsz.common.cache.spring.YdszCacheAutoConfiguration.class)
@ConditionalOnClass({Aspect.class, StringRedisTemplate.class})
@ConditionalOnProperty(prefix = "ydsz.cache.multilevel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MultiLevelCacheAutoConfiguration {

  /**
   * 注册 {@link MultiLevelCacheAspect} Bean（仅当实例不存在时）。
   *
   * <p>构造参数所需的 {@link StringRedisTemplate} / {@link L2ValueSerializer} 通过 {@link ObjectProvider} 注入，
   * 任一不可用时由切面自行降级处理。
   *
   * @param stringRedisTemplateProvider Redis 模板提供器（可为空）
   * @param l2ValueSerializerProvider   L2 序列化器提供器（可为空，空时使用默认实现）
   * @return 多级缓存切面实例
   */
  @Bean
  @ConditionalOnMissingBean(MultiLevelCacheAspect.class)
  public MultiLevelCacheAspect multiLevelCacheAspect(
      ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
      ObjectProvider<L2ValueSerializer> l2ValueSerializerProvider) {
    return new MultiLevelCacheAspect(stringRedisTemplateProvider, l2ValueSerializerProvider);
  }
}
