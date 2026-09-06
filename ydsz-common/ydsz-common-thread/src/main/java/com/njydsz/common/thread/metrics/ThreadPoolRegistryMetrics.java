package com.njydsz.common.thread.metrics;

import java.util.concurrent.ThreadPoolExecutor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.thread.registry.ThreadPoolRegistry;

/**
 * 线程池注册中心 Micrometer 指标绑定器。
 *
 * <p>将 {@link ThreadPoolRegistry} 中所有已注册线程池的实时指标绑定到 Micrometer，
 * 使得 Prometheus / Observability 平台可以采集到线程池运行状态。
 *
 * <p>暴露的 Gauge 指标（每个线程池 6 项）：
 *
 * <ul>
 *   <li>{@code ydsz.registry.executor.core} - 核心线程数
 *   <li>{@code ydsz.registry.executor.max} - 最大线程数
 *   <li>{@code ydsz.registry.executor.active} - 当前活跃线程数
 *   <li>{@code ydsz.registry.executor.pool.size} - 线程池当前大小
 *   <li>{@code ydsz.registry.executor.queue.size} - 工作队列当前长度
 *   <li>{@code ydsz.registry.executor.queue.capacity} - 工作队列总容量
 *   <li>{@code ydsz.registry.executor.completed} - 累计完成任务数
 *   <li>{@code ydsz.registry.executor.largest} - 历史最大线程数
 *   <li>{@code ydsz.registry.executor.task.count} - 累计任务总数
 * </ul>
 *
 * <p>每个指标带 {@code pool.name} Tag，便于按线程池名称区分和聚合。
 *
 * <p>此外，还注册一条汇总 Gauge {@code ydsz.registry.executor.count} 表示当前注册中心管理的线程池总数。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class ThreadPoolRegistryMetrics implements MeterBinder {

  /** 注册中心指标前缀。 */
  public static final String METRIC_PREFIX = "ydsz.registry.executor";

  /** 注册中心指标汇总前缀。 */
  public static final String METRIC_COUNT_NAME = "ydsz.registry.executor.count";

  @Override
  public void bindTo(MeterRegistry registry) {
    // 汇总指标：当前注册中心管理的线程池数量
    Gauge.builder(METRIC_COUNT_NAME, ThreadPoolExecutor.class, e -> ThreadPoolRegistry.size())
        .description("当前 ThreadPoolRegistry 管理的线程池总数")
        .register(registry);

    // 为每个已注册线程池注册 Gauge（延迟绑定，按需创建）
    // 由于线程池是运行时动态注册的，我们使用动态 Gauge 注册器
    registerDynamicGauges(registry);

    log.info(
        "[ThreadPoolRegistryMetrics] Micrometer 指标绑定完成，当前注册线程池 {} 个",
        ThreadPoolRegistry.size());
  }

  /**
   * 动态注册所有已注册线程池的 Gauge 指标。
   *
   * <p>从 {@link ThreadPoolRegistry#getAll()} 遍历所有线程池，为每个创建一组 Gauge。
   * 后续新注册的线程池需通过 {@link #refreshMetrics(MeterRegistry)} 方法重新绑定。
   */
  private void registerDynamicGauges(MeterRegistry registry) {
    ThreadPoolRegistry.getAll().forEach(this::registerPoolGauges);
  }

  /**
   * 为单个线程池注册所有 Gauge 指标。
   *
   * @param poolName 线程池名称
   * @param executor 线程池执行器
   */
  private void registerPoolGauges(String poolName, ThreadPoolExecutor executor) {
    Tags tags = Tags.of("pool.name", poolName);

    Gauge.builder(METRIC_PREFIX + ".core", executor, ThreadPoolExecutor::getCorePoolSize)
        .tags(tags)
        .description("线程池核心线程数")
        .register(registry);

    Gauge.builder(METRIC_PREFIX + ".max", executor, ThreadPoolExecutor::getMaximumPoolSize)
        .tags(tags)
        .description("线程池最大线程数")
        .register(registry);

    Gauge.builder(METRIC_PREFIX + ".active", executor, ThreadPoolExecutor::getActiveCount)
        .tags(tags)
        .description("当前活跃线程数")
        .register(registry);

    Gauge.builder(METRIC_PREFIX + ".pool.size", executor, ThreadPoolExecutor::getPoolSize)
        .tags(tags)
        .description("线程池当前大小")
        .register(registry);

    Gauge.builder(
            METRIC_PREFIX + ".queue.size",
            executor,
            e -> e.getQueue() != null ? e.getQueue().size() : 0)
        .tags(tags)
        .description("工作队列当前长度")
        .register(registry);

    Gauge.builder(
            METRIC_PREFIX + ".queue.capacity",
            executor,
            e -> {
              if (e.getQueue() == null) {
                return 0;
              }
              return e.getQueue().size() + e.getQueue().remainingCapacity();
            })
        .tags(tags)
        .description("工作队列总容量")
        .register(registry);

    Gauge.builder(
            METRIC_PREFIX + ".completed", executor, ThreadPoolExecutor::getCompletedTaskCount)
        .tags(tags)
        .description("累计完成任务数")
        .register(registry);

    Gauge.builder(METRIC_PREFIX + ".largest", executor, ThreadPoolExecutor::getLargestPoolSize)
        .tags(tags)
        .description("历史最大线程池大小")
        .register(registry);

    Gauge.builder(METRIC_PREFIX + ".task.count", executor, ThreadPoolExecutor::getTaskCount)
        .tags(tags)
        .description("累计任务总数（含队列中待执行）")
        .register(registry);
  }
}
