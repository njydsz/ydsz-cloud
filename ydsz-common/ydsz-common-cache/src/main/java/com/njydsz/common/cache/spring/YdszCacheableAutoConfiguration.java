package com.njydsz.common.cache.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * {@link YdszCacheable} 注解的 Spring Boot 自动配置。
 *
 * <p>注册 {@link YdszCacheableAspect} 切面 Bean，要求以下条件同时满足：
 *
 * <ul>
 *   <li>Spring AOP 位于 classpath（{@code @ConditionalOnClass}）
 *   <li>Spring 容器中存在 {@link YdszCacheManager} Bean（{@code @ConditionalOnBean}）
 *   <li>{@code ydzsz.cache.aop-enabled} 配置为 true（默认开启，可配置关闭）
 * </ul>
 *
 * <p>当上述条件不满足时（如纯 JDK 环境或使用 Spring 原生 {@code @Cacheable}），自动配置退避—— 不影响 {@link
 * YdszCacheManager} 作为 Spring Cache CacheManager 的能力。
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see YdszCacheable
 * @see YdszCacheableAspect
 */
@AutoConfiguration(after = YdszCacheAutoConfiguration.class)
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
@ConditionalOnBean(YdszCacheManager.class)
@ConditionalOnProperty(prefix = "ydsz.cache", name = "aopEnabled", havingValue = "true", matchIfMissing = true)
public class YdszCacheableAutoConfiguration {

  /**
   * 创建 {@link YdszCacheableAspect} Bean。
   *
   * @return 缓存注解切面实例
   */
  @Bean
  @ConditionalOnMissingBean(YdszCacheableAspect.class)
  public YdszCacheableAspect ydszCacheableAspect() {
    return new YdszCacheableAspect();
  }
}
