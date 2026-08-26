package com.njydsz.cronjob.server.metrics;

import com.njydsz.common.base.metrics.AbstractMetricsHolder;

/**
 * 分布式调度引擎运行态 Metrics 静态持有者。
 *
 * <p>为调度引擎核心路径提供 Micrometer 指标注册与累加能力， 通过静态方法方便业务代码（如 {@code JobScanner}、{@code
 * DefaultTaskDispatcher}、 {@code AverageShardingStrategy}）埋点。
 *
 * <p>继承 {@link AbstractMetricsHolder}，仅保留本模块的业务语义方法， 注册表绑定与缓存去重由父类统一处理。
 *
 * <p>暴露的 Prometheus 指标：
 *
 * <ul>
 *   <li>{@code cronjob.execution_total{job_name}} — 任务执行计数
 *   <li>{@code cronjob.execution_duration{job_name}} — 任务执行耗时分布
 *   <li>{@code cronjob.dispatch_delay{job_name}} — 调度触发延迟分布（next_fire_time 到实际派发，衡量调度精度）
 *   <li>{@code cronjob.shard_success_total{job_name,shard_index}} — 分片成功计数
 *   <li>{@code cronjob.shard_failure_total{job_name,shard_index}} — 分片失败计数
 * </ul>
 *
 * <p><b>P0-6/11 弃用说明</b>：本类已弃用，所有指标方法已统一迁移至 Spring 管理的 {@link CronjobMetrics} Bean（{@code ydsz_cronjob_}
 * 前缀）。 残留静态方法委托给 {@code cronjobMetrics} 静态引用，仅作兼容过渡，请新代码直接注入 {@link CronjobMetrics}。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 使用 {@link CronjobMetrics} 替代，Spring Bean 方式注入
 */
@Deprecated
public final class CronjobMetricsHolder extends AbstractMetricsHolder {

  /** 模块指标前缀 */
  private static final String METRIC_PREFIX = "cronjob.";

  /** P0-6/11: 静态引用，委托给 Spring 管理的 CronjobMetrics Bean */
  private static volatile CronjobMetrics cronjobMetrics;

  private CronjobMetricsHolder() {
    throw new UnsupportedOperationException("utility class");
  }

  /**
   * P0-6/11: 设置 CronjobMetrics 引用（由 CronjobMetrics 初始化时回调）。
   *
   * @param metrics CronjobMetrics 实例
   */
  static void setCronjobMetrics(CronjobMetrics metrics) {
    cronjobMetrics = metrics;
  }

  // ======================== 任务执行计数 ========================

  /**
   * 递增任务执行计数（{@code cronjob.execution_total}）。
   *
   * @param jobName 任务名称（job_name 标签）
   * @deprecated 使用 {@link CronjobMetrics#incJobExecution(String)} 替代
   */
  @Deprecated
  public static void incrementExecution(String jobName) {
    if (cronjobMetrics != null) {
      cronjobMetrics.incJobExecution(jobName);
    } else {
      // 降级：保留原静态注册能力
      registerCounter(METRIC_PREFIX, "execution_total", "job_name", safe(jobName)).increment();
    }
  }

  // ======================== 任务执行耗时 ========================

  /**
   * 记录任务执行耗时（{@code cronjob.execution_duration}）。
   *
   * @param jobName 任务名称
   * @param millis 执行耗时（毫秒）
   * @deprecated 使用 {@link CronjobMetrics#recordExecutionDuration(String, long)} 替代
   */
  @Deprecated
  public static void recordExecutionDuration(String jobName, long millis) {
    if (cronjobMetrics != null) {
      cronjobMetrics.recordExecutionDuration(jobName, millis);
    } else {
      recordDuration(METRIC_PREFIX, "execution_duration", millis, "job_name", safe(jobName));
    }
  }

  // ======================== 调度触发延迟 ========================

  /**
   * 记录调度触发延迟（{@code cronjob.dispatch_delay}）。
   *
   * <p>延迟 = 实际派发时刻 - next_fire_time（毫秒），用于衡量调度精度：
   * 5s 扫描模式下 P99 接近扫描周期，启用秒级预读（preload）后显著下降，
   * 为 preload/扫描间隔调优提供数据依据。
   *
   * @param jobName 任务名称
   * @param delayMillis 触发延迟（毫秒，>= 0）
   * @deprecated 使用 {@link CronjobMetrics#recordDispatchDelay(String, long)} 替代
   */
  @Deprecated
  public static void recordDispatchDelay(String jobName, long delayMillis) {
    if (cronjobMetrics != null) {
      cronjobMetrics.recordDispatchDelay(jobName, Math.max(delayMillis, 0L));
    } else {
      recordDuration(
          METRIC_PREFIX, "dispatch_delay", Math.max(delayMillis, 0L), "job_name", safe(jobName));
    }
  }

  // ======================== 分片成功计数 ========================

  /**
   * 递增分片成功计数（{@code cronjob.shard_success_total}）。
   *
   * @param jobName 任务名称
   * @param shardIndex 分片索引
   * @deprecated 使用 {@link CronjobMetrics#incShardSuccess(String, int)} 替代
   */
  @Deprecated
  public static void incrementShardSuccess(String jobName, int shardIndex) {
    if (cronjobMetrics != null) {
      cronjobMetrics.incShardSuccess(jobName, shardIndex);
    } else {
      String si = String.valueOf(shardIndex);
      registerCounter(
              METRIC_PREFIX, "shard_success_total", "job_name", safe(jobName), "shard_index", si)
          .increment();
    }
  }

  // ======================== 分片失败计数 ========================

  /**
   * 递增分片失败计数（{@code cronjob.shard_failure_total}）。
   *
   * @param jobName 任务名称
   * @param shardIndex 分片索引
   * @deprecated 使用 {@link CronjobMetrics#incShardFailure(String, int)} 替代
   */
  @Deprecated
  public static void incrementShardFailure(String jobName, int shardIndex) {
    if (cronjobMetrics != null) {
      cronjobMetrics.incShardFailure(jobName, shardIndex);
    } else {
      String si = String.valueOf(shardIndex);
      registerCounter(
              METRIC_PREFIX, "shard_failure_total", "job_name", safe(jobName), "shard_index", si)
          .increment();
    }
  }
}
