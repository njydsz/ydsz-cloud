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
 * <p>启用 Spring Cache 注解驱动（{@code @Cacheable} / {@code @CacheEvict}）， 以 ydsz-common-cache 的 {@link
 * YdszCacheManager} 作为缓存管理器， 替代原有的 {@code RedisStringOps} 手动缓存管理模式：
 *
 * <ul>
 *   <li>统一缓存编程模型，消除各 ServiceImpl 中重复的 cache-aside 样板代码
 *   <li>利用 ydsz-common-cache 内置的防穿透能力，无需手动维护空值哨兵
 *   <li>跨实例缓存一致性由 {@code ConfigServiceImpl} OutboxService 事件广播 + {@code CrossModuleEventListener}
 *       本地缓存失效机制保证
 * </ul>
 *
 * <p><b>本地缓存 vs Redis：</b>ydsz-common-cache 为进程内本地缓存（无网络 IO）， TTL 和容量通过 {@code ydsz.cache.caches}
 * YAML 配置。跨实例一致性通过 OutboxService 事件实现：写操作发布 {@code CONFIG_CHANGED} 事件， 各实例的 {@code
 * CrossModuleEventListener} 接收到事件后清除本地缓存。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.common.cache.spring.YdszCacheProperties
 * @see com.njydsz.system.server.listener.CrossModuleEventListener
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
