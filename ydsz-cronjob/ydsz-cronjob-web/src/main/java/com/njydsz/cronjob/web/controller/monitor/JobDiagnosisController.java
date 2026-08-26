package com.njydsz.cronjob.web.controller.monitor;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.LockKeyUtil;
import com.njydsz.cronjob.server.core.executor.RunningTaskCounter;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

/**
 * 任务诊断端点（P1-4 新增）。
 *
 * <p>聚合任务的多维度状态信息，提供一键诊断能力：
 *
 * <ul>
 *   <li>最近一次执行日志（状态 + 耗时 + 错误信息）
 *   <li>当前 Redis 锁持有者（排查锁冲突）
 *   <li>运行中任务数（Redis 计数器）
 *   <li>系统负载评分（CPU + 内存 + 线程池）
 * </ul>
 *
 * <p>适用于任务卡死、锁冲突、执行异常等场景的快速定位。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "任务诊断", description = "聚合任务多维度状态信息，提供一键诊断能力")
@RestController
@RequestMapping("/api/v1/cronjob/monitor/diagnosis")
@RequiredArgsConstructor
public class JobDiagnosisController {
  /** Map 初始容量：16 */
  private static final int MAP_CAPACITY_16 = 16;

  /** Map 初始容量：8 */
  private static final int MAP_CAPACITY_8 = 8;

  /** 最近日志条数 */
  private static final int RECENT_LOG_LIMIT = 5;

  /** Map 初始容量：4 */
  private static final int MAP_CAPACITY_4 = 4;


  /** 日志 Repository（DDD 分层：Controller 通过 Repository 接口查询日志） */
  private final JobLogRepository jobLogRepository;
  private final CronjobProperties cronjobProperties;
  private final StringRedisTemplate redisTemplate;

  private final ObjectProvider<RunningTaskCounter> runningTaskCounterProvider;

  /**
   * 诊断指定任务的运行状态。
   *
   * <p>聚合以下信息：
   *
   * <ul>
   *   <li>最近一次执行日志（状态、耗时、错误信息、执行节点）
   *   <li>当前 Redis 锁持有者（排查锁冲突）
   *   <li>运行中任务数（Redis 计数器）
   *   <li>系统负载评分
   * </ul>
   *
   * @param jobKey 任务 KEY
   * @return 诊断信息 Map
   */
  @Operation(summary = "诊断指定任务的运行状态")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
  @GetMapping("/{jobKey}")
  public YdszResponse<Map<String, Object>> diagnose(
      @Parameter(description = "任务 KEY", required = true) @PathVariable String jobKey) {
    Map<String, Object> diagnosis = new HashMap<>(MAP_CAPACITY_16);

    // 1. 基本信息
    diagnosis.put("jobKey", jobKey);
    diagnosis.put("diagnosisTime", LocalDateTime.now().toString());
    diagnosis.put("nodeId", getNodeId());

    // 2. 最近一次执行日志（通过 Repository 查询 VO）
    Optional<JobLogVO> lastLogOpt = jobLogRepository.findLatestByJobKey(jobKey);
    if (lastLogOpt.isPresent()) {
      JobLogVO lastLog = lastLogOpt.get();
      Map<String, Object> lastLogInfo = new HashMap<>(MAP_CAPACITY_8);
      lastLogInfo.put("logId", lastLog.getId());
      lastLogInfo.put("triggerType", lastLog.getTriggerType());
      lastLogInfo.put("startTime", String.valueOf(lastLog.getStartTime()));
      lastLogInfo.put("endTime", String.valueOf(lastLog.getEndTime()));
      lastLogInfo.put("durationMs", lastLog.getDurationMs());
      lastLogInfo.put("errorMessage", lastLog.getErrorMessage());
      lastLogInfo.put("execNodeId", lastLog.getExecNodeId());
      lastLogInfo.put("execThreadId", lastLog.getExecThreadId());
      diagnosis.put("lastExecution", lastLogInfo);
    } else {
      diagnosis.put("lastExecution", null);
    }

    // 3. 最近 5 次执行记录数
    List<JobLogVO> recentLogs = jobLogRepository.findByJobKey(jobKey, RECENT_LOG_LIMIT);
    diagnosis.put("recentExecutions", recentLogs.size());

    // 4. 当前 Redis 锁状态
    String lockKey = LockKeyUtil.buildJobLockKey(jobKey);
    String lockHolder = redisTemplate.opsForValue().get(lockKey);
    diagnosis.put("lockKey", lockKey);
    diagnosis.put("lockHolder", lockHolder);
    diagnosis.put("lockAcquired", lockHolder != null);

    // 5. 运行中任务数（Redis 计数器）
    RunningTaskCounter counter = runningTaskCounterProvider.getIfAvailable();
    if (counter != null) {
      diagnosis.put("clusterRunningTasks", counter.getCount());
    }

    // 6. 系统负载评分（通过 CronjobMetrics 暴露的 Gauge 获取）
    Map<String, Object> loadInfo = new HashMap<>(MAP_CAPACITY_4);
    loadInfo.put("systemLoadScore", CronjobMetrics.getSystemLoadScore());
    diagnosis.put("systemLoad", loadInfo);

    // 7. 配置摘要
    Map<String, Object> configInfo = new HashMap<>(MAP_CAPACITY_4);
    configInfo.put("maxConcurrent", cronjobProperties.getExecutor().getMaxConcurrent());
    configInfo.put("leaderEnabled", cronjobProperties.getLeader().isEnabled());
    diagnosis.put("config", configInfo);

    return YdszResponse.success(diagnosis);
  }

  /** 获取当前节点标识 */
  private String getNodeId() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      return "unknown";
    }
  }
}
