package com.njydsz.common.cache.spring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import com.njydsz.common.cache.builder.CacheType;

/**
 * @YdszCacheable 集成测试的最小 Spring 应用配置。
 *
 * <p>使用 {@link Configuration}（非 {@code @SpringBootApplication}，避免拉取 {@code YdszCacheAutoConfiguration}
 * 导致前缀绑定失败），显式定义 {@link YdszCacheManager} 和测试所需的 {@link YdszCacheableAspect} Bean。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class YdszCacheableTestConfiguration {

  /**
   * 提供测试专用的 CacheManager Bean。
   *
   * @return 配置好的 YdszCacheManager
   */
  @Bean
  public YdszCacheManager cacheManager() {
    YdszCacheManager manager = new YdszCacheManager();
    manager.setCacheType(CacheType.TINYLFU);
    manager.setMaximumSize(100);
    manager.setExpireAfterWrite(30, java.util.concurrent.TimeUnit.MINUTES);
    return manager;
  }

  /**
   * 提供测试用 Service Bean。
   *
   * @return YdszCacheableTestService 实例
   */
  @Bean
  public YdszCacheableTestService ydszCacheableTestService() {
    return new YdszCacheableTestService();
  }

  /**
   * 显式注册 {@link YdszCacheableAspect} Bean。
   *
   * @param manager 已配置的 CacheManager
   * @return 缓存注解切面实例
   */
  @Bean
  public YdszCacheableAspect ydszCacheableAspect(YdszCacheManager manager) {
    YdszCacheableAspect aspect = new YdszCacheableAspect();
    // YdszCacheableAspect 通过 @Autowired(required = false) 字段注入 cacheManager；
    // 使用反射直接注入以便在没有 @AutowiredAnnotationBeanPostProcessor 的场景下也能工作。
    try {
      java.lang.reflect.Field field = YdszCacheableAspect.class.getDeclaredField("cacheManager");
      field.setAccessible(true);
      field.set(aspect, manager);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException("Failed to inject cacheManager into YdszCacheableAspect", e);
    }
    return aspect;
  }
}
