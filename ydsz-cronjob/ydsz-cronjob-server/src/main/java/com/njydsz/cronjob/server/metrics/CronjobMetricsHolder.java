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
 *   <li>{@code cronjob.shard_success_total{job_name,shard_index}} — 分片成功计数
 *   <li>{@code cronjob.shard_failure_total{job_name,shard_index}} — 分片失败计数
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class CronjobMetricsHolder extends AbstractMetricsHolder {

  /** 模块指标前缀 */
  private static final String METRIC_PREFIX = "cronjob.";

  private CronjobMetricsHolder() {
    throw new UnsupportedOperationException("utility class");
  }

  // ======================== 任务执行计数 ========================

  /**
   * 递增任务执行计数（{@code cronjob.execution_total}）。
   *
   * @param jobName 任务名称（job_name 标签）
   */
  public static void incrementExecution(String jobName) {
    registerCounter(METRIC_PREFIX, "execution_total", "job_name", safe(jobName)).increment();
  }

  // ======================== 任务执行耗时 ========================

  /**
   * 记录任务执行耗时（{@code cronjob.execution_duration}）。
   *
   * @param jobName 任务名称
   * @param millis 执行耗时（毫秒）
   */
  public static void recordExecutionDuration(String jobName, long millis) {
    recordDuration(METRIC_PREFIX, "execution_duration", millis, "job_name", safe(jobName));
  }

  // ======================== 分片成功计数 ========================

  /**
   * 递增分片成功计数（{@code cronjob.shard_success_total}）。
   *
   * @param jobName 任务名称
   * @param shardIndex 分片索引
   */
  public static void incrementShardSuccess(String jobName, int shardIndex) {
    String si = String.valueOf(shardIndex);
    registerCounter(
            METRIC_PREFIX, "shard_success_total", "job_name", safe(jobName), "shard_index", si)
        .increment();
  }

  // ======================== 分片失败计数 ========================

  /**
   * 递增分片失败计数（{@code cronjob.shard_failure_total}）。
   *
   * @param jobName 任务名称
   * @param shardIndex 分片索引
   */
  public static void incrementShardFailure(String jobName, int shardIndex) {
    String si = String.valueOf(shardIndex);
    registerCounter(
            METRIC_PREFIX, "shard_failure_total", "job_name", safe(jobName), "shard_index", si)
        .increment();
  }
}
