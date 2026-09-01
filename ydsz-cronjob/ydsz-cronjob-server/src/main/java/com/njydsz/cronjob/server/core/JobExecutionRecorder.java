package com.njydsz.cronjob.server.core;

import java.time.Duration;
import java.time.LocalDateTime;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.cronjob.domain.vo.JobLogVO;

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
 * @since 26.09.01
 */
@Slf4j
public class JobExecutionRecorder {

  /** 任务执行状态：待执行 */
  public static final String STATUS_PENDING = "PENDING";
  /** 任务执行状态：执行中 */
  public static final String STATUS_RUNNING = "RUNNING";
  /** 任务执行状态：成功 */
  public static final String STATUS_SUCCESS = "SUCCESS";
  /** 任务执行状态：失败 */
  public static final String STATUS_FAILED = "FAILED";
  /** 任务执行状态：超时 */
  public static final String STATUS_TIMEOUT = "TIMEOUT";

  /** 超时错误消息 */
  private static final String TIMEOUT_ERROR_MESSAGE = "任务执行超时";

  /**
   * 创建执行日志记录。
   *
   * @param jobId 任务 ID
   * @param jobKey 任务 KEY
   * @param triggerType 触发类型
   * @return 初始化的 JobLogVO（status=PENDING，待持久化）
   */
  public JobLogVO createLog(String jobId, String jobKey, String triggerType) {
    JobLogVO logVO = new JobLogVO();
    logVO.setJobId(jobId);
    logVO.setJobKey(jobKey);
    logVO.setStatus(STATUS_PENDING);
    logVO.setTriggerType(triggerType);
    logVO.setCreatedAt(LocalDateTime.now());
    logVO.setDeleted(0);
    return logVO;
  }

  /**
   * 标记日志为 RUNNING 状态。
   *
   * @param logDO 执行日志 VO
   * @param lockHolder 持锁者标识
   * @param execNodeId 执行节点 ID
   * @param execThreadId 执行线程 ID
   */
  public void markRunning(JobLogVO logDO, String lockHolder, String execNodeId, Long execThreadId) {
    LocalDateTime now = LocalDateTime.now();
    logDO.setStatus(STATUS_RUNNING);
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
   * @param logDO 执行日志 VO
   * @param resultJson 执行结果 JSON
   * @param handlerEndTime Handler 结束时间（P1-2 执行轨迹）
   */
  public void markSuccess(JobLogVO logDO, String resultJson, LocalDateTime handlerEndTime) {
    LocalDateTime now = LocalDateTime.now();
    logDO.setStatus(STATUS_SUCCESS);
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
   * @param logDO 执行日志 VO
   * @param errorMessage 错误信息
   * @param handlerEndTime Handler 结束时间（P1-2 执行轨迹）
   */
  public void markFailed(JobLogVO logDO, String errorMessage, LocalDateTime handlerEndTime) {
    LocalDateTime now = LocalDateTime.now();
    logDO.setStatus(STATUS_FAILED);
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
   * @param logDO 执行日志 VO
   */
  public void markTimeout(JobLogVO logDO) {
    LocalDateTime now = LocalDateTime.now();
    logDO.setStatus(STATUS_TIMEOUT);
    logDO.setEndTime(now);
    logDO.setErrorMessage(TIMEOUT_ERROR_MESSAGE);
    if (logDO.getStartTime() != null) {
      logDO.setDurationMs(Duration.between(logDO.getStartTime(), now).toMillis());
    }
    logDO.setUpdatedAt(now);
  }
}
