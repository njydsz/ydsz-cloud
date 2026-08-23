package com.njydsz.cronjob.server.core.diagnosis;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.domain.vo.JobVO;

/**
 * 任务诊断服务（P3-1：任务诊断 API + 智能运维）。
 *
 * <p>基于任务执行历史，提供智能诊断能力。
 *
 * <p>P2-修正：使用 JobRepository/JobLogRepository 替换 Mapper，使用 VO 替换 Entity，符合 DDD 分层规范。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TaskDiagnosisService {
  /** 成功率得分权重 */
  private static final double SUCCESS_SCORE_WEIGHT = 0.5;

  /** 稳定性得分上限 */
  private static final double STABILITY_MAX_SCORE = 20;

  /** 稳定性得分权重（CV 系数） */
  private static final double STABILITY_WEIGHT = 20;

  /** 超时得分上限 */
  private static final double TIMEOUT_MAX_SCORE = 15;

  /** 超时得分权重 */
  private static final double TIMEOUT_WEIGHT = 0.3;

  /** 连续失败扣分上限 */
  private static final double CONSECUTIVE_PENALTY_MAX = 15;

  /** 每次连续失败扣分 */
  private static final double CONSECUTIVE_PENALTY_PER_FAIL = 3;

  /** 健康评分上限 */
  private static final double SCORE_MAX = 100;

  /** 成功率警告阈值（%） */
  private static final double SUCCESS_RATE_WARN_THRESHOLD = 80;

  /** 连续失败警告阈值 */
  private static final int CONSECUTIVE_FAIL_WARN_THRESHOLD = 3;

  /** 耗时变异系数警告阈值 */
  private static final double CV_DURATION_WARN_THRESHOLD = 0.5;

  /** 平均耗时警告阈值（毫秒）：60 秒 */
  private static final long AVG_DURATION_WARN_MILLIS = 60000;


  private final JobRepository jobRepository;
  private final JobLogRepository jobLogRepository;

  /** 诊断时间窗口（默认 24h） */
  private static final int DIAGNOSIS_WINDOW_HOURS = 24;

  /** 最少样本数（低于此数量不进行诊断） */
  private static final int MIN_SAMPLE_SIZE = 5;

  /** 健康评分阈值 */
  private static final double HEALTH_THRESHOLD_CRITICAL = 50.0;
  private static final double HEALTH_THRESHOLD_WARNING = 80.0;

  /**
   * 诊断单个任务的健康状况。
   *
   * @param jobKey 任务 KEY
   * @return 诊断结果
   */
  public TaskDiagnosis diagnose(String jobKey) {
    JobVO job = jobRepository.findByJobKey(jobKey).orElse(null);
    if (job == null) {
      return TaskDiagnosis.notFound(jobKey);
    }
    List<JobLogVO> recentLogs = selectRecentLogs(job.getId(), DIAGNOSIS_WINDOW_HOURS);
    if (recentLogs.size() < MIN_SAMPLE_SIZE) {
      return TaskDiagnosis.insufficientData(jobKey, recentLogs.size());
    }
    return analyze(jobKey, recentLogs);
  }

  /**
   * 批量诊断所有 NORMAL 状态的任务。
   *
   * @return 诊断结果列表（按健康分升序，最差的排在前面）
   */
  public List<TaskDiagnosis> diagnoseAll() {
    List<JobVO> normalJobs = jobRepository.findByStatus("NORMAL");
    if (normalJobs.isEmpty()) {
      return Collections.emptyList();
    }
    List<TaskDiagnosis> results = new ArrayList<>();
    for (JobVO job : normalJobs) {
      try {
        List<JobLogVO> recentLogs = selectRecentLogs(job.getId(), DIAGNOSIS_WINDOW_HOURS);
        if (recentLogs.size() >= MIN_SAMPLE_SIZE) {
          results.add(analyze(job.getJobKey(), recentLogs));
        }
      } catch (Exception e) {
        log.warn("[Diagnosis] 诊断任务异常: jobKey={} reason={}", job.getJobKey(), e.getMessage());
      }
    }
    // 按健康分升序排序（最差的排在前面）
    results.sort((a, b) -> Double.compare(a.healthScore(), b.healthScore()));
    return results;
  }

  /**
   * 分析任务执行历史，生成诊断结果。
   */
  private TaskDiagnosis analyze(String jobKey, List<JobLogVO> logs) {
    long total = logs.size();
    long successCount = logs.stream().filter(l -> "SUCCESS".equals(l.getStatus())).count();
    long failedCount = logs.stream().filter(l -> "FAILED".equals(l.getStatus())).count();
    long timeoutCount = logs.stream().filter(l -> "TIMEOUT".equals(l.getStatus())).count();
    double successRate = (successCount * 100.0) / total;

    // 计算耗时统计
    List<Long> durations = logs.stream()
        .filter(l -> l.getDurationMs() != null && l.getDurationMs() > 0)
        .map(JobLogVO::getDurationMs)
        .toList();
    double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0);
    double cvDuration = calculateCV(durations, avgDuration);

    // 计算连续失败
    int consecutiveFailures = calculateConsecutiveFailures(logs);

    // 计算健康评分 (0-100)
    double healthScore = calculateHealthScore(successRate, cvDuration, timeoutCount, total, consecutiveFailures);

    // 确定健康级别
    HealthLevel level;
    if (healthScore < HEALTH_THRESHOLD_CRITICAL) {
      level = HealthLevel.CRITICAL;
    } else if (healthScore < HEALTH_THRESHOLD_WARNING) {
      level = HealthLevel.WARNING;
    } else {
      level = HealthLevel.HEALTHY;
    }

    // 生成建议
    List<String> suggestions =
        generateSuggestions(
            successRate, cvDuration, timeoutCount, total, consecutiveFailures, avgDuration);

    return new TaskDiagnosis(
        jobKey,
        level,
        healthScore,
        successRate,
        avgDuration,
        cvDuration,
        (int) consecutiveFailures,
        (int) total,
        LocalDateTime.now(),
        suggestions);
  }

  private double calculateCV(List<Long> values, double mean) {
    if (values.isEmpty() || mean <= 0) {
      return 0;
    }
    double variance = values.stream()
        .mapToDouble(v -> Math.pow(v - mean, 2))
        .average()
        .orElse(0);
    double stdDev = Math.sqrt(variance);
    return stdDev / mean;
  }

  private int calculateConsecutiveFailures(List<JobLogVO> logs) {
    int maxConsecutive = 0;
    int currentConsecutive = 0;
    for (JobLogVO log : logs) {
      if ("FAILED".equals(log.getStatus()) || "TIMEOUT".equals(log.getStatus())) {
        currentConsecutive++;
        maxConsecutive = Math.max(maxConsecutive, currentConsecutive);
      } else {
        currentConsecutive = 0;
      }
    }
    return maxConsecutive;
  }

  private double calculateHealthScore(
      double successRate, double cvDuration, long timeoutCount, long total,
      int consecutiveFailures) {
    double successScore = successRate * SUCCESS_SCORE_WEIGHT;
    double stabilityScore = Math.max(0, STABILITY_MAX_SCORE - cvDuration * STABILITY_WEIGHT);
    double timeoutRate = total > 0 ? (timeoutCount * 100.0) / total : 0;
    double timeoutScore = Math.max(0, TIMEOUT_MAX_SCORE - timeoutRate * TIMEOUT_WEIGHT);
    double consecutivePenalty =
        Math.min(CONSECUTIVE_PENALTY_MAX, consecutiveFailures * CONSECUTIVE_PENALTY_PER_FAIL);
    double finalScore =
        successScore + stabilityScore + timeoutScore + (TIMEOUT_MAX_SCORE - consecutivePenalty);
    return Math.max(0, Math.min(SCORE_MAX, finalScore));
  }

  private List<String> generateSuggestions(
      double successRate, double cvDuration, long timeoutCount, long total,
      int consecutiveFailures, double avgDuration) {
    List<String> suggestions = new ArrayList<>();

    if (successRate < SUCCESS_RATE_WARN_THRESHOLD) {
      suggestions.add(String.format("成功率 %.1f%% 偏低，建议检查 handler 逻辑或外部依赖健康状态", successRate));
    }
    if (consecutiveFailures >= CONSECUTIVE_FAIL_WARN_THRESHOLD) {
      suggestions.add(String.format("连续失败 %d 次，建议暂停任务并排查配置错误", consecutiveFailures));
    }
    if (cvDuration > CV_DURATION_WARN_THRESHOLD) {
      suggestions.add(String.format("执行耗时变异系数 %.2f 过高，建议优化 handler 逻辑或增加超时时间", cvDuration));
    }
    if (timeoutCount > 0) {
      double timeoutRate = (timeoutCount * 100.0) / total;
      if (timeoutRate > 10) {
        suggestions.add(String.format("超时率 %.1f%% 过高，建议增大任务超时时间或优化执行逻辑", timeoutRate));
      }
    }
    if (avgDuration > AVG_DURATION_WARN_MILLIS) {
      suggestions.add(String.format("平均执行耗时 %.1fs 较长，建议优化 handler 逻辑或拆分任务", avgDuration / 1000));
    }
    return suggestions;
  }

  /**
   * 查询最近 N 小时的执行日志。
   */
  private List<JobLogVO> selectRecentLogs(String jobId, int hours) {
    LocalDateTime since = LocalDateTime.now().minusHours(hours);
    return jobLogRepository.findByJobIdSince(jobId, since);
  }

  /**
   * 健康级别枚举。
   */
  public enum HealthLevel {
    /** 健康（绿色） */
    HEALTHY,
    /** 警告（黄色） */
    WARNING,
    /** 严重（红色） */
    CRITICAL
  }

  /**
   * 任务诊断结果。
   *
   * @param jobKey 任务 KEY
   * @param level 健康级别
   * @param healthScore 健康评分（0-100）
   * @param successRate 成功率（0-1）
   * @param avgDurationMs 平均耗时（毫秒）
   * @param cvDuration 耗时变异系数
   * @param consecutiveFailures 连续失败次数
   * @param sampleSize 样本数量
   * @param diagnosisTime 诊断时间
   * @param suggestions 诊断建议列表
   */
  public record TaskDiagnosis(
      String jobKey,
      HealthLevel level,
      double healthScore,
      double successRate,
      double avgDurationMs,
      double cvDuration,
      int consecutiveFailures,
      int sampleSize,
      LocalDateTime diagnosisTime,
      List<String> suggestions) {

    static TaskDiagnosis notFound(String jobKey) {
      return new TaskDiagnosis(jobKey, HealthLevel.CRITICAL, 0, 0, 0, 0, 0, 0,
          LocalDateTime.now(), List.of("任务不存在: " + jobKey));
    }

    static TaskDiagnosis insufficientData(String jobKey, int sampleSize) {
      return new TaskDiagnosis(jobKey, HealthLevel.HEALTHY, 100, 0, 0, 0, 0, sampleSize,
          LocalDateTime.now(), List.of("数据不足，至少需要 " + MIN_SAMPLE_SIZE + " 条记录，当前 " + sampleSize + " 条"));
    }
  }
}
