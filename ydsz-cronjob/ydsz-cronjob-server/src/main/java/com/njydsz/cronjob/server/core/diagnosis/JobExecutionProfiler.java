package com.njydsz.cronjob.server.core.diagnosis;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.domain.vo.JobLogVO;

/**
 * P0-3: 任务执行画像服务。
 *
 * <p>基于历史执行数据，为每个任务生成执行画像，包括：
 *
 * <ul>
 *   <li>耗时分布（P50/P90/P99）
 *   <li>执行稳定性评分
 *   <li>异常模式识别
 *   <li>资源消耗趋势
 * </ul>
 *
 * <p>画像数据可用于：
 *
 * <ul>
 *   <li>任务健康度评估
 *   <li>超时配置优化建议
 *   <li>慢任务自动识别
 *   <li>执行趋势预测
 * </ul>
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobExecutionProfiler {

  /** 画像分析默认时间窗口：7 天 */
  private static final int PROFILE_WINDOW_DAYS = 7;

  /** 最少样本数 */
  private static final int MIN_SAMPLES_FOR_PROFILE = 10;

  /** P99 耗时警告阈值（秒）：300s */
  private static final long P99_DURATION_WARN_SECONDS = 300;

  /** 稳定性评分阈值 */
  private static final double STABILITY_THRESHOLD_WARNING = 0.3;

  /** P50 百分位常量 */
  private static final int PERCENTILE_P50 = 50;

  /** P90 百分位常量 */
  private static final int PERCENTILE_P90 = 90;

  /** P99 百分位常量 */
  private static final int PERCENTILE_P99 = 99;

  /** P90/P50 比值阈值：P90 超过 P50 的 5 倍时判定为长尾 */
  private static final int P90_P50_RATIO_THRESHOLD = 5;

  /** 超时率阈值（%）：超过 5% 时提示 */
  private static final double TIMEOUT_RATE_THRESHOLD = 5.0;

  /** 成功率阈值（%）：低于 90% 时提示 */
  private static final double SUCCESS_RATE_THRESHOLD = 90.0;

  private final JobLogRepository jobLogRepository;

  /**
   * 生成任务执行画像。
   *
   * @param jobId 任务 ID
   * @return 执行画像，数据不足时返回 {@link Profile#insufficientData(String)}
   */
  public Profile profile(String jobId) {
    return profile(jobId, PROFILE_WINDOW_DAYS);
  }

  /**
   * 生成任务执行画像（指定时间窗口）。
   *
   * @param jobId 任务 ID
   * @param days 时间窗口（天）
   * @return 执行画像
   */
  public Profile profile(String jobId, int days) {
    LocalDateTime since = LocalDateTime.now().minusDays(days);
    List<JobLogVO> logs = jobLogRepository.findByJobIdSince(jobId, since);

    if (logs.size() < MIN_SAMPLES_FOR_PROFILE) {
      return Profile.insufficientData(jobId, logs.size());
    }

    return buildProfile(jobId, logs, days);
  }

  /**
   * 批量生成任务执行画像。
   *
   * @param jobIds 任务 ID 列表
   * @return 执行画像列表
   */
  public List<Profile> profileBatch(List<String> jobIds) {
    if (jobIds == null || jobIds.isEmpty()) {
      return Collections.emptyList();
    }
    List<Profile> profiles = new ArrayList<>(jobIds.size());
    for (String jobId : jobIds) {
      try {
        profiles.add(profile(jobId));
      } catch (Exception e) {
        log.warn("[Profiler] 生成任务画像异常: jobId={} reason={}", jobId, e.getMessage());
      }
    }
    return profiles;
  }

  /**
   * 构建执行画像。
   *
   * @param jobId 任务 ID
   * @param logs 执行日志列表
   * @param days 时间窗口（天）
   * @return 执行画像
   */
  private Profile buildProfile(String jobId, List<JobLogVO> logs, int days) {
    List<Long> successDurations = new ArrayList<>(logs.size());
    int totalCount = logs.size();
    int successCount = 0;
    int failCount = 0;
    int timeoutCount = 0;

    for (JobLogVO log : logs) {
      String status = log.getStatus();
      if ("SUCCESS".equals(status)) {
        successCount++;
        if (log.getDurationMs() != null && log.getDurationMs() > 0) {
          successDurations.add(log.getDurationMs());
        }
      } else if ("FAILED".equals(status)) {
        failCount++;
      } else if ("TIMEOUT".equals(status)) {
        timeoutCount++;
      }
    }

    // 计算耗时百分位
    Collections.sort(successDurations);
    long p50 = percentile(successDurations, PERCENTILE_P50);
    long p90 = percentile(successDurations, PERCENTILE_P90);
    long p99 = percentile(successDurations, PERCENTILE_P99);
    long avg = average(successDurations);
    long min = successDurations.isEmpty() ? 0 : successDurations.get(0);
    long max = successDurations.isEmpty() ? 0 : successDurations.get(successDurations.size() - 1);

    // 计算稳定性评分（基于变异系数）
    double stabilityScore = calculateStabilityScore(successDurations, avg);

    // 计算成功率
    double successRate = totalCount > 0 ? (successCount * 100.0) / totalCount : 0;

    // 生成优化建议
    List<String> suggestions = generateOptimizationSuggestions(
        p50, p90, p99, avg, stabilityScore, successRate, timeoutCount, totalCount);

    return new Profile(
        jobId,
        totalCount,
        successCount,
        failCount,
        timeoutCount,
        successRate,
        new DurationStats(p50, p90, p99, avg, min, max),
        stabilityScore,
        days,
        LocalDateTime.now(),
        suggestions);
  }

  /**
   * 计算百分位值。
   *
   * @param sortedValues 已排序的值列表
   * @param percentile 百分位（0-100）
   * @return 百分位值
   */
  private long percentile(List<Long> sortedValues, int percentile) {
    if (sortedValues.isEmpty()) {
      return 0;
    }
    int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
    index = Math.max(0, Math.min(index, sortedValues.size() - 1));
    return sortedValues.get(index);
  }

  /**
   * 计算平均值。
   *
   * @param values 值列表
   * @return 平均值
   */
  private long average(List<Long> values) {
    if (values.isEmpty()) {
      return 0;
    }
    return values.stream().mapToLong(Long::longValue).sum() / values.size();
  }

  /**
   * 计算稳定性评分（0-100，越高越稳定）。
   *
   * <p>基于变异系数（CV = 标准差 / 均值），CV 越小越稳定。
   *
   * @param values 耗时列表
   * @param mean 平均值
   * @return 稳定性评分
   */
  private double calculateStabilityScore(List<Long> values, long mean) {
    if (values.isEmpty() || mean <= 0) {
      return 100.0;
    }
    double variance = values.stream()
        .mapToDouble(v -> Math.pow(v - mean, 2))
        .average()
        .orElse(0);
    double stdDev = Math.sqrt(variance);
    double cv = stdDev / mean;
    // CV 为 0 时评分 100，CV >= 1 时评分 0
    return Math.max(0, Math.min(100, 100 * (1 - cv)));
  }

  /**
   * 生成优化建议。
   *
   * @param p50 P50 耗时
   * @param p90 P90 耗时
   * @param p99 P99 耗时
   * @param avg 平均耗时
   * @param stabilityScore 稳定性评分
   * @param successRate 成功率
   * @param timeoutCount 超时次数
   * @param totalCount 总次数
   * @return 优化建议列表
   */
  private List<String> generateOptimizationSuggestions(
      long p50, long p90, long p99, long avg, double stabilityScore,
      double successRate, int timeoutCount, int totalCount) {
    List<String> suggestions = new ArrayList<>();

    // P99 耗时过长
    if (p99 > P99_DURATION_WARN_SECONDS * 1000) {
      suggestions.add(String.format(
          "P99 耗时 %ds 过长，建议优化 handler 逻辑或拆分任务", p99 / 1000));
    }

    // P90 与 P50 差距大（长尾明显）
    if (p50 > 0 && p90 > p50 * P90_P50_RATIO_THRESHOLD) {
      suggestions.add(String.format(
          "P90(%ds) 远大于 P50(%ds)，存在明显长尾，建议排查慢执行原因",
          p90 / 1000, p50 / 1000));
    }

    // 稳定性差
    if (stabilityScore < STABILITY_THRESHOLD_WARNING * 100) {
      suggestions.add(String.format(
          "执行耗时稳定性评分 %.1f 较低，建议检查外部依赖或资源争抢情况", stabilityScore));
    }

    // 超时率过高
    if (totalCount > 0) {
      double timeoutRate = (timeoutCount * 100.0) / totalCount;
      if (timeoutRate > TIMEOUT_RATE_THRESHOLD) {
        suggestions.add(String.format(
            "超时率 %.1f%% 较高，建议增大任务超时时间或优化执行逻辑", timeoutRate));
      }
    }

    // 成功率偏低
    if (successRate < SUCCESS_RATE_THRESHOLD) {
      suggestions.add(String.format(
          "成功率 %.1f%% 偏低，建议检查 handler 逻辑或外部依赖健康状态", successRate));
    }

    return suggestions;
  }

  /**
   * 耗时统计。
   *
   * @param p50 P50 耗时（毫秒）
   * @param p90 P90 耗时（毫秒）
   * @param p99 P99 耗时（毫秒）
   * @param avg 平均耗时（毫秒）
   * @param min 最小耗时（毫秒）
   * @param max 最大耗时（毫秒）
   */
  public record DurationStats(long p50, long p90, long p99, long avg, long min, long max) {
  }

  /**
   * 任务执行画像。
   *
   * @param jobId 任务 ID
   * @param totalExecutions 总执行次数
   * @param successCount 成功次数
   * @param failCount 失败次数
   * @param timeoutCount 超时次数
   * @param successRate 成功率（%）
   * @param durationStats 耗时统计
   * @param stabilityScore 稳定性评分（0-100）
   * @param windowDays 分析时间窗口（天）
   * @param profileTime 画像生成时间
   * @param suggestions 优化建议列表
   */
  public record Profile(
      String jobId,
      int totalExecutions,
      int successCount,
      int failCount,
      int timeoutCount,
      double successRate,
      DurationStats durationStats,
      double stabilityScore,
      int windowDays,
      LocalDateTime profileTime,
      List<String> suggestions) {

    static Profile insufficientData(String jobId, int sampleSize) {
      return new Profile(
          jobId, sampleSize, 0, 0, 0, 0,
          new DurationStats(0, 0, 0, 0, 0, 0),
          100.0, PROFILE_WINDOW_DAYS, LocalDateTime.now(),
          List.of("数据不足，至少需要 " + MIN_SAMPLES_FOR_PROFILE + " 条记录，当前 " + sampleSize + " 条"));
    }
  }
}
