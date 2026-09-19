package com.njydsz.common.cache.spring;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * @YdszCacheable 集成测试的最小 Spring 应用配置。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@SpringBootApplication
public class YdszCacheableTestConfiguration {

  /**
   * 提供测试专用的 CacheManager Bean。
   *
   * @return 配置好的 YdszCacheManager
   */
  @Bean
  public YdszCacheManager cacheManager() {
    YdszCacheManager manager = new YdszCacheManager();
    manager.setCacheType(com.njydsz.common.cache.builder.CacheType.TINYLFU);
    manager.setMaximumSize(100);
    manager.setExpireAfterWrite(30, java.util.concurrent.TimeUnit.MINUTES);
    return manager;
  }
}
