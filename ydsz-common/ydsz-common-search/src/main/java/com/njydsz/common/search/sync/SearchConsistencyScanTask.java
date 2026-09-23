package com.njydsz.common.search.sync;

import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.common.search.config.SearchConsistencyProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 搜索索引一致性巡检任务（P1-4 调度层）。
 *
 * <p>定时扫描所有 {@code SearchProvider} 注册的数据类型，对比数据库实际数量与索引文档数量，
 * 检测索引丢失或冗余。不一致时根据 {@link SearchConsistencyProperties#isAutoRepair()}
 * 配置决定是否自动修复。
 *
 * <p>通过 {@link DistributedScheduled} 确保多实例部署时同一时刻只有一个节点执行，
 * 避免并发刷新导致的索引抖动。获取不到分布式锁的节点自动跳过本次执行。
 *
 * <p><b>启用方式</b>：在业务模块 application.yml 中配置 {@code ydsz.search.consistency.enabled=true}。
 *
 * <p><b>配置示例</b>：
 * <pre>{@code
 * ydsz:
 *   search:
 *     consistency:
 *       enabled: true
 *       interval-ms: 3600000    # 1 小时
 *       auto-repair: true       # 自动修复不一致
 *       max-load-size: 10000    # 单类型最大加载数
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.23
 * @see IndexConsistencyChecker
 * @see SearchConsistencyProperties
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnClass(name = "com.njydsz.common.lock.annotation.DistributedScheduled")
@ConditionalOnProperty(
    prefix = "ydsz.search.consistency",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SearchConsistencyScanTask {

  private final IndexConsistencyChecker consistencyChecker;
  private final SearchConsistencyProperties properties;

  /**
   * 定时巡检搜索索引一致性。
   *
   * <p>使用 {@code fixedDelay} 确保上次执行完毕后再等待 intervalMs，避免任务堆积。
   * {@link DistributedScheduled} 提供分布式锁保障，多节点仅一个执行。
   */
  @Scheduled(fixedDelayString = "${ydsz.search.consistency.interval-ms:3600000}")
  @DistributedScheduled(
      lockKey = "search:consistency:scan",
      leaseTime = 300,
      timeUnit = java.util.concurrent.TimeUnit.SECONDS)
  public void scanConsistency() {
    log.info("[SearchConsistency] 开始索引一致性巡检: autoRepair={}", properties.isAutoRepair());

    IndexConsistencyChecker.ConsistencyReport report = consistencyChecker.check(null);

    if (report.isConsistent()) {
      log.info("[SearchConsistency] 索引一致性巡检完成，所有类型对齐");
      return;
    }

    log.warn(
        "[SearchConsistency] 索引不一致: missing={}, orphan={}",
        report.missingFromIndex(),
        report.orphanInIndex());

    if (properties.isAutoRepair()) {
      int repaired = consistencyChecker.autoRepair(null);
      log.info("[SearchConsistency] 自动修复完成: repaired={}", repaired);
    }
  }
}
