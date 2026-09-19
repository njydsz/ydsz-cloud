package com.njydsz.common.cache.spring;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

/**
 * 测试用 Service — 验证 {@link YdszCacheable} 注解行为。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@Service
public class YdszCacheableTestService {

  /** 方法调用计数器（验证缓存命中后不再调用） */
  private final AtomicInteger invocationCount = new AtomicInteger(0);

  /**
   * 使用 @YdszCacheable 缓存方法。
   *
   * @param key 查询 key
   * @return 方法结果（调用次数标记）
   */
  @YdszCacheable(value = "test:simple", key = "#key", ttl = 60)
  public String getCached(String key) {
    invocationCount.incrementAndGet();
    return "result-" + key + "-" + invocationCount.get();
  }

  /**
   * 使用 sync=true 防击穿。
   *
   * @param key 查询 key
   * @return 方法结果
   */
  @YdszCacheable(value = "test:sync", key = "#key", sync = true)
  public String getSyncCached(String key) {
    invocationCount.incrementAndGet();
    return "sync-result-" + key;
  }

  /**
   * 使用 nullTtl 防穿透。
   *
   * @param key 查询 key
   * @return null（模拟查无数据）
   */
  @YdszCacheable(value = "test:nullable", key = "#key", nullTtl = 30)
  public String getNullable(String key) {
    invocationCount.incrementAndGet();
    return null;
  }

  /**
   * 使用 tenantKey = false 关闭租户隔离。
   *
   * @param key 查询 key
   * @return 结果
   */
  @YdszCacheable(value = "test:no-tenant", key = "#key", tenantKey = false)
  public String getWithoutTenant(String key) {
    invocationCount.incrementAndGet();
    return "no-tenant-" + key;
  }

  /**
   * 获取调用计数。
   *
   * @return 总调用次数
   */
  public int getInvocationCount() {
    return invocationCount.get();
  }

  /** 重置计数器 */
  public void resetInvocationCount() {
    invocationCount.set(0);
  }
}
