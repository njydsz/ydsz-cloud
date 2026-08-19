package com.njydsz.cronjob.server.core.diagnosis;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.domain.entity.job.Job;
import com.njydsz.cronjob.domain.entity.log.JobLog;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;

/**
 * 任务诊断服务（P3-1：任务诊断 API + 智能运维）。
 *
 * <p>基于任务执行历史，提供智能诊断能力：
 *
 * <ul>
 *   <li><b>健康评分</b>：综合成功率、耗时稳定性、超时率等计算任务健康度
 *   <li><b>异常检测</b>：识别执行耗时突增、失败率异常、连续失败等异常模式
 *   <li><b>优化建议</b>：根据诊断结果给出配置优化建议（调整超时、增加重试、降低并发）
 *   <li><b>风险预警</b>：提前发现潜在问题（如执行耗时持续增长趋势）
 * </ul>
 *
 * <h3>诊断维度</h3>
 *
 * <ul>
 *   <li><b>成功率</b>：近 N 次执行的成功率，低于阈值触发警告
 *   <li><b>耗时稳定性</b>：执行耗时的变异系数（CV），过高说明执行时间不稳定
 *   <li><b>超时率</b>：执行超时的比例，高超时率可能需要调整超时配置
 *   <li><b>连续失败</b>：连续失败次数，频繁连续失败可能是配置错误
 * </ul>
 *
 * <h3>对标</h3>
 *
 * <p>对标 Prometheus AlertManager、Grafana ML、Datadog Anomaly Detection。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TaskDiagnosisService {

  private final JobMapper jobMapper;
  private final JobLogMapper jobLogMapper;

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
    Job job = jobMapper.selectByJobKey(jobKey);
    if (job == null) {
      return TaskDiagnosis.notFound(jobKey);
    }
    List<JobLog> recentLogs = selectRecentLogs(job.getId(), DIAGNOSIS_WINDOW_HOURS);
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
    List<Job> normalJobs = jobMapper.selectList(
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Job>()
            .eq(Job::getStatus, "NORMAL"));
    if (normalJobs.isEmpty()) {
      return Collections.emptyList();
    }
    List<TaskDiagnosis> results = new ArrayList<>();
    for (Job job : normalJobs) {
      try {
        List<JobLog> recentLogs = selectRecentLogs(job.getId(), DIAGNOSIS_WINDOW_HOURS);
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
   *
   * @param jobKey 任务 KEY
   * @param logs   最近执行日志
   * @return 诊断结果
   */
  private TaskDiagnosis analyze(String jobKey, List<JobLog> logs) {
    long total = logs.size();
    long successCount = logs.stream().filter(l -> "SUCCESS".equals(l.getStatus())).count();
    long failedCount = logs.stream().filter(l -> "FAILED".equals(l.getStatus())).count();
    long timeoutCount = logs.stream().filter(l -> "TIMEOUT".equals(l.getStatus())).count();
    double successRate = (successCount * 100.0) / total;

    // 计算耗时统计
    List<Long> durations = logs.stream()
        .filter(l -> l.getDurationMs() != null && l.getDurationMs() > 0)
        .map(JobLog::getDurationMs)
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
    List<String> suggestions = generateSuggestions(successRate, cvDuration, timeoutCount, total, consecutiveFailures, avgDuration);

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

  /**
   * 计算变异系数（Coefficient of Variation），衡量耗时稳定性。
   *
   * <p>CV = 标准差 / 均值，CV > 0.5 说明耗时波动较大。
   */
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

  /**
   * 计算最大连续失败次数。
   */
  private int calculateConsecutiveFailures(List<JobLog> logs) {
    int maxConsecutive = 0;
    int currentConsecutive = 0;
    for (JobLog log : logs) {
      if ("FAILED".equals(log.getStatus()) || "TIMEOUT".equals(log.getStatus())) {
        currentConsecutive++;
        maxConsecutive = Math.max(maxConsecutive, currentConsecutive);
      } else {
        currentConsecutive = 0;
      }
    }
    return maxConsecutive;
  }

  /**
   * 计算健康评分 (0-100)。
   *
   * <p>基于以下因素加权：
   * <ul>
   *   <li>成功率（权重 50%）</li>
   *   <li>耗时稳定性（权重 20%）</li>
   *   <li>超时率（权重 15%）</li>
   *   <li>连续失败惩罚（权重 15%）</li>
   * </ul>
   */
  private double calculateHealthScore(double successRate, double cvDuration, long timeoutCount, long total, int consecutiveFailures) {
    // 成功率得分 (0-50)
    double successScore = successRate * 0.5;

    // 耗时稳定性得分 (0-20)，CV 越小得分越高
    double stabilityScore = Math.max(0, 20 - cvDuration * 20);

    // 超时率得分 (0-15)
    double timeoutRate = total > 0 ? (timeoutCount * 100.0) / total : 0;
    double timeoutScore = Math.max(0, 15 - timeoutRate * 0.3);

    // 连续失败惩罚 (0-15)
    double consecutivePenalty = Math.min(15, consecutiveFailures * 3);

    return Math.max(0, Math.min(100, successScore + stabilityScore + timeoutScore + (15 - consecutivePenalty)));
  }

  /**
   * 生成优化建议。
   */
  private List<String> generateSuggestions(double successRate, double cvDuration, long timeoutCount, long total, int consecutiveFailures, double avgDuration) {
    List<String> suggestions = new ArrayList<>();

    if (successRate < 80) {
      suggestions.add(String.format("成功率 %.1f%% 偏低，建议检查 handler 逻辑或外部依赖健康状态", successRate));
    }
    if (consecutiveFailures >= 3) {
      suggestions.add(String.format("连续失败 %d 次，建议暂停任务并排查配置错误", consecutiveFailures));
    }
    if (cvDuration > 0.5) {
      suggestions.add(String.format("执行耗时变异系数 %.2f 过高，建议优化 handler 逻辑或增加超时时间", cvDuration));
    }
    if (timeoutCount > 0) {
      double timeoutRate = (timeoutCount * 100.0) / total;
      if (timeoutRate > 10) {
        suggestions.add(String.format("超时率 %.1f%% 过高，建议增大任务超时时间或优化执行逻辑", timeoutRate));
      }
    }
    if (avgDuration > 60000) {
      suggestions.add(String.format("平均执行耗时 %.1fs 较长，建议优化 handler 逻辑或拆分任务", avgDuration / 1000));
    }
    return suggestions;
  }

  /**
   * 查询最近 N 小时的执行日志。
   */
  private List<JobLog> selectRecentLogs(String jobId, int hours) {
    LocalDateTime since = LocalDateTime.now().minusHours(hours);
    return jobLogMapper.selectList(
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<JobLog>()
            .eq(JobLog::getJobId, jobId)
            .ge(JobLog::getCreatedAt, since)
            .orderByDesc(JobLog::getCreatedAt));
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
   * @param jobKey              任务 KEY
   * @param level               健康级别
   * @param healthScore         健康评分 (0-100)
   * @param successRate         成功率 (%)
   * @param avgDurationMs       平均执行耗时 (ms)
   * @param cvDuration          耗时变异系数
   * @param consecutiveFailures 最大连续失败次数
   * @param sampleSize          样本数量
   * @param diagnosisTime       诊断时间
   * @param suggestions         优化建议列表
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

    /** 任务不存在时的诊断结果。 */
    static TaskDiagnosis notFound(String jobKey) {
      return new TaskDiagnosis(jobKey, HealthLevel.CRITICAL, 0, 0, 0, 0, 0, 0,
          LocalDateTime.now(), List.of("任务不存在: " + jobKey));
    }

    /** 数据不足时的诊断结果。 */
    static TaskDiagnosis insufficientData(String jobKey, int sampleSize) {
      return new TaskDiagnosis(jobKey, HealthLevel.HEALTHY, 100, 0, 0, 0, 0, sampleSize,
          LocalDateTime.now(), List.of("数据不足，至少需要 " + MIN_SAMPLE_SIZE + " 条记录，当前 " + sampleSize + " 条"));
    }
  }
}
