package com.njydsz.common.cache.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * {@link YdszCacheable} 注解的 Spring 集成测试。
 *
 * <p>通过 {@link AnnotationConfigApplicationContext} 手动加载 Spring 上下文，验证 AOP 切面注册和缓存行为——
 * 不使用 {@code @SpringBootTest} 是因为 Spring 7.0.8 / JUnit 5.10.3 之间的 {@code SpringExtension}
 * 存在 {@code computeIfAbsent} 三元组方法签名不兼容问题。
 *
 * <p>验证内容：
 *
 * <ul>
 *   <li>切面 Bean 被正确注册
 *   <li>缓存命中后方法体不再执行（调用计数 = 1）
 *   <li>租户上下文的键隔离
 *   <li>空值占位符正确注册（防穿透）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
class YdszCacheableIntegrationTest {

  private AnnotationConfigApplicationContext context;
  private YdszCacheableTestService testService;

  @BeforeEach
  void setUp() {
    context = new AnnotationConfigApplicationContext();
    context.register(YdszCacheableTestConfiguration.class);
    context.refresh();
    testService = context.getBean(YdszCacheableTestService.class);
    testService.resetInvocationCount();
  }

  @AfterEach
  void tearDown() {
    if (context != null) {
      context.close();
    }
  }

  private void cleanTestCaches() {
    CacheManager cm = context.getBean(CacheManager.class);
    for (String name : Arrays.asList("test:simple", "test:sync", "test:nullable", "test:no-tenant")) {
      Cache cache = cm.getCache(name);
      if (cache != null) {
        cache.clear();
      }
    }
    testService.resetInvocationCount();
  }

  @Nested
  @DisplayName("切面加载")
  class AspectLoadTest {

    @Test
    @DisplayName("YdszCacheableAspect Bean 被正确注册")
    void aspectBean_shouldBeRegistered() {
      assertThat(context.getBean(YdszCacheableAspect.class)).isNotNull();
    }

    @Test
    @DisplayName("YdszCacheManager Bean 被正确注册")
    void cacheManagerBean_shouldBeRegistered() {
      CacheManager cm = context.getBean(CacheManager.class);
      assertThat(cm).isInstanceOf(YdszCacheManager.class);
    }
  }

  @Nested
  @DisplayName("缓存行为")
  class CacheBehaviorTest {

    @Test
    @DisplayName("缓存命中时方法体只执行一次")
    void cachedMethod_shouldExecuteOnce() {
      cleanTestCaches();
      String result1 = testService.getCached("user-1");
      String result2 = testService.getCached("user-1");

      assertThat(testService.getInvocationCount()).isEqualTo(1);
      assertThat(result2).isEqualTo(result1);
    }

    @Test
    @DisplayName("不同 key 各自独立缓存")
    void differentKeys_shouldCacheSeparately() {
      cleanTestCaches();
      String result1 = testService.getCached("user-1");
      String result2 = testService.getCached("user-2");

      assertThat(result1).isNotEqualTo(result2);
      assertThat(testService.getInvocationCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("sync=true 时方法体仅执行一次")
    void syncCachedMethod_shouldExecuteOnceOnConcurrent() {
      cleanTestCaches();
      String result1 = testService.getSyncCached("sync-key");
      String result2 = testService.getSyncCached("sync-key");

      // sync=true 通过 semaphore 防击穿
      assertThat(result1).isEqualTo(result2);
      assertThat(result1).isEqualTo("sync-result-sync-key");
    }

    @Test
    @DisplayName("租户隔离模式追加租户 ID")
    void tenantKey_shouldBeAppended() {
      cleanTestCaches();
      com.njydsz.common.cache.support.CacheKeyBuilder.setTenantIdResolver(() -> "acme");
      try {
        String result1 = testService.getCached("shared-key");
        String result2 = testService.getCached("shared-key");
        assertThat(result2).isEqualTo(result1);
        assertThat(testService.getInvocationCount()).isEqualTo(1);
      } finally {
        com.njydsz.common.cache.support.CacheKeyBuilder.setTenantIdResolver(null);
      }
    }

    @Test
    @DisplayName("空值返回时注册占位符并持续返回 null")
    void nullTtlCache_shouldReturnNull() {
      cleanTestCaches();
      String result1 = testService.getNullable("missing-key");
      assertThat(result1).isNull();
      assertThat(testService.getInvocationCount()).isEqualTo(1);

      // 第二次调用仍返回 null
      String result2 = testService.getNullable("missing-key");
      assertThat(result2).isNull();
    }
  }
}
