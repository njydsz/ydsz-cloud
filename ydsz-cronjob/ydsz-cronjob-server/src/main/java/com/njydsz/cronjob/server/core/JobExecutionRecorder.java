package com.njydsz.cronjob.server.core;

import java.time.Duration;
import java.time.LocalDateTime;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.cronjob.domain.entity.log.JobLog;

/**
 * P2-1: 任务执行记录器（从 DefaultTaskDispatcher 提取）。
 *
 * <p>封装任务执行日志（JobLog）的创建、状态更新、执行轨迹记录等逻辑， 消除 DefaultTaskDispatcher 中分散的日志记录代码。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>{@link #createLog}：创建执行日志记录（PENDING 状态）
 *   <li>{@link #markRunning}：标记为 RUNNING + 记录派发时间（P1-2 执行轨迹）
 *   <li>{@link #markSuccess}：标记为 SUCCESS + 记录结束时间 + 耗时
 *   <li>{@link #markFailed}：标记为 FAILED + 记录错误信息
 *   <li>{@link #markTimeout}：标记为 TIMEOUT（超时终止）
 * </ul>
 *
 * <h3>提取动机</h3>
 *
 * <p>DefaultTaskDispatcher 1592 行代码中约 200 行涉及日志记录， 提取后可统一日志格式和执行轨迹字段管理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class JobExecutionRecorder {

  /**
   * 创建执行日志记录。
   *
   * @param jobId 任务 ID
   * @param jobKey 任务 KEY
   * @param triggerType 触发类型
   * @return 初始化的 JobLog（status=PENDING，待持久化）
   */
  public JobLog createLog(String jobId, String jobKey, String triggerType) {
    JobLog logDO = new JobLog();
    logDO.setJobId(jobId);
    logDO.setJobKey(jobKey);
    logDO.setStatus("PENDING");
    logDO.setTriggerType(triggerType);
    logDO.setCreatedAt(LocalDateTime.now());
    logDO.setDeleted(0);
    return logDO;
  }

  /**
   * 标记日志为 RUNNING 状态。
   *
   * @param logDO 执行日志
   * @param lockHolder 持锁者标识
   * @param execNodeId 执行节点 ID
   * @param execThreadId 执行线程 ID
   */
  public void markRunning(JobLog logDO, String lockHolder, String execNodeId, Long execThreadId) {
    LocalDateTime now = LocalDateTime.now();
    logDO.setStatus("RUNNING");
    logDO.setStartTime(now);
    logDO.setLockHolder(lockHolder);
    logDO.setExecNodeId(execNodeId);
    logDO.setExecThreadId(execThreadId);
    // P1-2: 执行轨迹 — 派发时间
    logDO.setDispatchTime(now);
    logDO.setUpdatedAt(now);
  }

  /**
   * 标记日志为 SUCCESS 状态。
   *
   * @param logDO 执行日志
   * @param resultJson 执行结果 JSON
   * @param handlerEndTime Handler 结束时间（P1-2 执行轨迹）
   */
  public void markSuccess(JobLog logDO, String resultJson, LocalDateTime handlerEndTime) {
    LocalDateTime now = LocalDateTime.now();
    logDO.setStatus("SUCCESS");
    logDO.setEndTime(now);
    logDO.setResultJson(resultJson);
    // P1-2: 执行轨迹 — Handler 结束时间
    logDO.setHandlerEndTime(handlerEndTime);
    if (logDO.getStartTime() != null) {
      logDO.setDurationMs(Duration.between(logDO.getStartTime(), now).toMillis());
    }
    logDO.setUpdatedAt(now);
  }

  /**
   * 标记日志为 FAILED 状态。
   *
   * @param logDO 执行日志
   * @param errorMessage 错误信息
   * @param handlerEndTime Handler 结束时间（P1-2 执行轨迹）
   */
  public void markFailed(JobLog logDO, String errorMessage, LocalDateTime handlerEndTime) {
    LocalDateTime now = LocalDateTime.now();
    logDO.setStatus("FAILED");
    logDO.setEndTime(now);
    logDO.setErrorMessage(errorMessage);
    logDO.setHandlerEndTime(handlerEndTime);
    if (logDO.getStartTime() != null) {
      logDO.setDurationMs(Duration.between(logDO.getStartTime(), now).toMillis());
    }
    logDO.setUpdatedAt(now);
  }

  /**
   * 标记日志为 TIMEOUT 状态。
   *
   * @param logDO 执行日志
   */
  public void markTimeout(JobLog logDO) {
    LocalDateTime now = LocalDateTime.now();
    logDO.setStatus("TIMEOUT");
    logDO.setEndTime(now);
    logDO.setErrorMessage("任务执行超时");
    if (logDO.getStartTime() != null) {
      logDO.setDurationMs(Duration.between(logDO.getStartTime(), now).toMillis());
    }
    logDO.setUpdatedAt(now);
  }
}
