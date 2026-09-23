package com.njydsz.system.server.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.system.server.cache.CacheWarmer;
import com.njydsz.system.server.config.SystemProperties;

/**
 * 缓存一致性兜底调度器（P1-B3 跨实例缓存兜底策略）。
 *
 * <p>当 {@code ydsz.system.cache.cross-instance-enabled=false}（默认关闭）时，多实例部署下各实例的本地缓存
 * 可能读到过期数据（其他实例已修改但本实例缓存未失效）。本调度器周期性从 DB 全量刷新本地缓存，提供<b>最终一致性</b>兜底。
 *
 * <p><b>设计策略：</b>
 *
 * <ul>
 *   <li><b>执行条件：</b>仅当 {@code crossInstanceEnabled=false} 时生效，避免与 Redis Pub/Sub 实时失效叠加浪费 DB 资源</li>
 *   <li><b>执行周期：</b>通过 {@code ydzs.system.cache.consistency-refresh-interval-ms} 配置（默认 5 分钟）</li>
 *   <li><b>刷新逻辑：</b>委托 {@link CacheWarmer} 从 DB 加载最新数据覆盖本地缓存</li>
 *   <li><b>失败容错：</b>刷新失败仅记录警告，不抛出异常（下次调度自动重试）</li>
 * </ul>
 *
 * <p><b>性能影响：</b>
 *
 * <ul>
 *   <li>每次刷新产生 2 次 DB 查询（配置全量 + 字典项全量），建议数据量 ≤ 5000 条/租户时启用</li>
 *   <li>刷新过程使用本地缓存的 put 操作，不涉及分布式锁或远程调用</li>
 *   <li>若数据量大或一致性要求高，建议开启 {@code cross-instance-enabled=true} 获得实时一致性</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 * @see CacheWarmer 缓存预热器（本调度器复用其刷新逻辑）
 * @see SystemProperties.Cache#crossInstanceEnabled 跨实例缓存失效开关
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ydsz.system.cache", name = "consistencyRefreshEnabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class CacheConsistencyScheduler {

  /** 缓存预热器（复用其预热逻辑执行全量刷新） */
  private final CacheWarmer cacheWarmer;

  /**
   * 周期性刷新本地缓存以兜底多实例间缓存不一致。
   *
   * <p>默认每 5 分钟执行一次（启动 120s 后首次执行，给首次预热留足时间），从 DB 全量加载配置与字典项数据覆盖本地缓存。
   */
  @Scheduled(
      fixedDelayString = "${ydsz.system.cache.consistency-refresh-interval-ms:300000}",
      initialDelayString = "${ydsz.system.cache.consistency-refresh-initial-delay-ms:120000}")
  public void refreshLocalCache() {
    log.info("[CacheConsistencyScheduler] 开始全量刷新本地缓存...");
    long start = System.currentTimeMillis();
    try {
      cacheWarmer.refreshFromDatabase();
      log.info("[CacheConsistencyScheduler] 本地缓存全量刷新完成，耗时 {}ms",
          System.currentTimeMillis() - start);
    } catch (Exception e) {
      log.warn("[CacheConsistencyScheduler] 本地缓存刷新失败（不影响业务，下次调度自动重试）: {}",
          e.getMessage(), e);
    }
  }
}
