package com.njydsz.cronjob.server.core.diagnosis;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.domain.vo.JobVO;

/**
 * P0-3: 任务诊断服务（增强：失败模式识别 + 根因分析 + 自动修复建议）。
 *
 * <p>基于任务执行历史，提供智能诊断能力：
 *
 * <ul>
 *   <li>健康度评分（成功率 + 稳定性 + 超时率 + 连续失败）
 *   <li>失败模式识别（网络抖动 / 权限变更 / 参数错误 / 依赖服务不可用）
 *   <li>根因分析（超时 / 资源不足 / GC 停顿 / 死循环）
 *   <li>自动修复建议（调整超时 / 增加并发 / 降级策略）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TaskDiagnosisService {
  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

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

  /** 网络抖动错误模式 */
  private static final Pattern NETWORK_ERROR_PATTERN = Pattern.compile(
      "(?i)(connection.*refused|connection.*reset|timeout|socket.*timeout|"
          + "connect.*timed?\\s*out|read.*timed?\\s*out|network.*unreachable|"
          + "no.*route.*to.*host|broken.*pipe)");

  /** 权限错误模式 */
  private static final Pattern PERMISSION_ERROR_PATTERN = Pattern.compile(
      "(?i)(access.*denied|permission.*denied|unauthorized|forbidden|403|401|"
          + "invalid.*token|expired.*token|invalid.*credential)");

  /** 参数错误模式 */
  private static final Pattern PARAM_ERROR_PATTERN = Pattern.compile(
      "(?i)(nullpointer|null.*pointer|illegal.*argument|invalid.*parameter|"
          + "missing.*required|bad.*request|400|index.*out.*of.*bounds|"
          + "class.*cast.*exception|number.*format.*exception)");

  /** 依赖服务错误模式 */
  private static final Pattern DEPENDENCY_ERROR_PATTERN = Pattern.compile(
      "(?i)(service.*unavailable|502|503|504|gateway.*timeout|backend.*error|"
          + "upstream.*error|no.*instance|circuit.*breaker.*open)");

  /** 资源不足错误模式 */
  private static final Pattern RESOURCE_ERROR_PATTERN = Pattern.compile(
      "(?i)(out.*of.*memory|oom|disk.*full|too.*many.*open.*files|"
          + "thread.*pool.*exhausted|queue.*full|rate.*limit)");

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
    List<TaskDiagnosis> results = new ArrayList<>(COLLECTION_CAPACITY);
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
    List<String> suggestions = new ArrayList<>(COLLECTION_CAPACITY);

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
   * P0-3: 识别失败模式。
   *
   * <p>分析失败日志的错误信息，识别失败根因类别。
   *
   * @param logs 执行日志列表
   * @return 失败模式识别结果
   */
  public FailurePattern identifyFailurePattern(List<JobLogVO> logs) {
    int networkCount = 0;
    int permissionCount = 0;
    int paramCount = 0;
    int dependencyCount = 0;
    int resourceCount = 0;
    int unknownCount = 0;

    for (JobLogVO log : logs) {
      if (!"FAILED".equals(log.getStatus()) && !"TIMEOUT".equals(log.getStatus())) {
        continue;
      }
      String errorMsg = log.getErrorMessage();
      if (errorMsg == null || errorMsg.isEmpty()) {
        unknownCount++;
        continue;
      }
      if (NETWORK_ERROR_PATTERN.matcher(errorMsg).find()) {
        networkCount++;
      } else if (PERMISSION_ERROR_PATTERN.matcher(errorMsg).find()) {
        permissionCount++;
      } else if (PARAM_ERROR_PATTERN.matcher(errorMsg).find()) {
        paramCount++;
      } else if (DEPENDENCY_ERROR_PATTERN.matcher(errorMsg).find()) {
        dependencyCount++;
      } else if (RESOURCE_ERROR_PATTERN.matcher(errorMsg).find()) {
        resourceCount++;
      } else {
        unknownCount++;
      }
    }

    int totalFailures = networkCount + permissionCount + paramCount
        + dependencyCount + resourceCount + unknownCount;
    if (totalFailures == 0) {
      return new FailurePattern(FailureType.NONE, 0, List.of());
    }

    // 找出主导失败模式
    FailureType dominantType;
    int dominantCount;
    if (networkCount >= permissionCount && networkCount >= paramCount
        && networkCount >= dependencyCount && networkCount >= resourceCount
        && networkCount >= unknownCount) {
      dominantType = FailureType.NETWORK;
      dominantCount = networkCount;
    } else if (permissionCount >= paramCount && permissionCount >= dependencyCount
        && permissionCount >= resourceCount && permissionCount >= unknownCount) {
      dominantType = FailureType.PERMISSION;
      dominantCount = permissionCount;
    } else if (paramCount >= dependencyCount && paramCount >= resourceCount
        && paramCount >= unknownCount) {
      dominantType = FailureType.PARAM_ERROR;
      dominantCount = paramCount;
    } else if (dependencyCount >= resourceCount && dependencyCount >= unknownCount) {
      dominantType = FailureType.DEPENDENCY;
      dominantCount = dependencyCount;
    } else if (resourceCount >= unknownCount) {
      dominantType = FailureType.RESOURCE;
      dominantCount = resourceCount;
    } else {
      dominantType = FailureType.UNKNOWN;
      dominantCount = unknownCount;
    }

    double confidence = (dominantCount * 100.0) / totalFailures;
    List<String> suggestions = generateFixSuggestions(dominantType);

    return new FailurePattern(dominantType, confidence, suggestions);
  }

  /**
   * 根据失败类型生成修复建议。
   *
   * @param type 失败类型
   * @return 修复建议列表
   */
  private List<String> generateFixSuggestions(FailureType type) {
    return switch (type) {
      case NETWORK -> List.of(
          "检测到网络抖动，建议：",
          "  1. 增加重试次数和指数退避策略",
          "  2. 检查目标服务是否正常运行",
          "  3. 考虑增加连接超时时间");
      case PERMISSION -> List.of(
          "检测到权限错误，建议：",
          "  1. 检查 Token/密钥是否过期",
          "  2. 确认服务账号权限配置",
          "  3. 重新授权或更新凭证");
      case PARAM_ERROR -> List.of(
          "检测到参数错误，建议：",
          "  1. 检查任务参数配置是否正确",
          "  2. 确认输入数据格式是否符合预期",
          "  3. 添加参数校验逻辑");
      case DEPENDENCY -> List.of(
          "检测到依赖服务不可用，建议：",
          "  1. 检查下游服务健康状态",
          "  2. 考虑添加熔断降级策略",
          "  3. 增加超时和重试配置");
      case RESOURCE -> List.of(
          "检测到资源不足，建议：",
          "  1. 增加 JVM 内存或线程池大小",
          "  2. 检查是否有资源泄漏",
          "  3. 考虑降低并发执行数");
      case UNKNOWN -> List.of(
          "未能识别具体失败原因，建议：",
          "  1. 查看完整错误堆栈",
          "  2. 检查应用日志获取更多上下文",
          "  3. 联系相关服务负责人排查");
      case NONE -> List.of();
    };
  }

  /**
   * 查询最近 N 小时的执行日志。
   */
  private List<JobLogVO> selectRecentLogs(String jobId, int hours) {
    LocalDateTime since = LocalDateTime.now().minusHours(hours);
    return jobLogRepository.findByJobIdSince(jobId, since);
  }

  /**
   * 失败类型枚举。
   *
   * <p>基于错误信息模式识别的失败根因分类。
   */
  public enum FailureType {
    /** 无失败 */
    NONE,
    /** 网络抖动 */
    NETWORK,
    /** 权限错误 */
    PERMISSION,
    /** 参数错误 */
    PARAM_ERROR,
    /** 依赖服务不可用 */
    DEPENDENCY,
    /** 资源不足 */
    RESOURCE,
    /** 未知原因 */
    UNKNOWN
  }

  /**
   * 失败模式识别结果。
   *
   * @param type 主导失败类型
   * @param confidence 置信度（%）
   * @param suggestions 修复建议列表
   */
  public record FailurePattern(FailureType type, double confidence, List<String> suggestions) {
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
