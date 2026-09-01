package com.njydsz.system.server.config;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.njydsz.common.cache.spring.YdszCacheManager;
import com.njydsz.common.cache.spring.YdszCacheProperties;



/**
 * 系统模块 Spring Cache 配置。
 *
 * <p>启用 Spring Cache 注解驱动（{@code @Cacheable} / {@code @CacheEvict}），以 ydsz-common-cache 的 {@link
 * YdszCacheManager} 作为缓存管理器：
 *
 * <ul>
 *   <li>统一缓存编程模型，消除各 ServiceImpl 中重复的 cache-aside 样板代码
 *   <li>利用 ydsz-common-cache 内置的防穿透能力，无需手动维护空值哨兵
 *   <li>写方法通过 {@code @CacheEvict} 精准失效本地缓存，保证本实例一致性
 *   <li>跨实例一致性通过 TTL 自然过期实现最终一致；如需实时一致性，开启 {@code ydsz.system.cache.cross-instance-enabled=true}
 *       启用 Redis Pub/Sub 失效总线
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.common.cache.spring.YdszCacheProperties
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(YdszCacheProperties.class)
public class CacheConfig {

  /**
   * 注册 ydsz-common-cache 缓存管理器为 {@code @Primary}。
   *
   * <p>当 classpath 同时存在 spring-data-redis 和 ydsz-common-cache 时， 显式声明 {@code @Primary} 的 {@link
   * YdszCacheManager} 确保 Spring Cache 注解使用 ydsz-common-cache（本地缓存）而非 RedisCacheManager。
   *
   * @param ydszCacheProperties ydsz-common-cache 全局配置
   * @return ydsz-common-cache 缓存管理器
   */
  @Bean
  @Primary
  public YdszCacheManager cacheManager(YdszCacheProperties ydszCacheProperties) {
    YdszCacheManager manager = new YdszCacheManager();
    manager.setCacheType(ydszCacheProperties.getType());
    manager.setCacheNames(ydszCacheProperties.getCacheNames());
    manager.setMaximumSize(ydszCacheProperties.getMaximumSize());
    manager.setExpireAfterWrite(
        ydszCacheProperties.getExpireAfterWrite(), ydszCacheProperties.getExpireTimeUnit());
    manager.setAllowNullValues(ydszCacheProperties.isAllowNullValues());
    manager.setInitialCapacity(ydszCacheProperties.getInitialCapacity());
    manager.setExpireAfterAccess(
        ydszCacheProperties.getExpireAfterAccess(), ydszCacheProperties.getExpireTimeUnit());
    manager.setRefreshAfterWrite(
        ydszCacheProperties.getRefreshAfterWrite(), ydszCacheProperties.getExpireTimeUnit());
    manager.setRecordStats(ydszCacheProperties.isRecordStats());
    manager.setPerCacheConfigs(ydszCacheProperties.getCaches());
    return manager;
  }
}
