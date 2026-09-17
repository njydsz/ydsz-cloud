package com.njydsz.agent.domain.asynctask;

import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 异步任务实体
 *
 * <p>封装一次异步执行请求的完整生命周期数据，包括任务类型、输入参数、状态、进度和结果。
 *
 * <p>对应 agents-flex 的 async-task 模块概念，支持长任务的持久化、恢复和配额管理。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@TableName("ydsz_agt_async_task")
public class AsyncTask extends MpBaseEntity<Long> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 任务类型编码（REPORT_GENERATE / DOC_INGEST / BATCH_CHAT 等） */
  private String taskType;

  /** 任务状态 */
  private String status;

  /** 租户编码（多租户隔离） */
  private String tenantCode;

  /** 触发用户 ID */
  private String userId;

  /** 任务输入参数（JSON 字符串） */
  private String inputPayload;

  /** 任务执行结果（JSON 字符串，完成后非空） */
  private String outputPayload;

  /** 失败原因（状态为 FAILED 时非空） */
  private String errorMessage;

  /** 当前进度百分比（0-100） */
  private Integer progressPercent;

  /** 已重试次数 */
  private Integer retryCount;

  /** 最大重试次数 */
  private Integer maxRetry;

  /** 下次重试时间 */
  private LocalDateTime nextRetryAt;

  /** 执行超时时间（秒） */
  private Long timeoutSeconds;

  /** Worker 节点标识（执行此任务的服务实例） */
  private String workerId;

  /** 任务开始执行时间 */
  private LocalDateTime startedAt;

  /** 任务完成时间 */
  private LocalDateTime completedAt;

  /** 任务过期时间（超时未完成则自动取消） */
  private LocalDateTime expireAt;

  /**
   * 创建异步任务。
   *
   * @param taskType    任务类型
   * @param tenantCode  租户编码
   * @param userId      用户 ID
   * @param inputPayload 输入参数 JSON
   */
  public AsyncTask(String taskType, String tenantCode, String userId, String inputPayload) {
    this.taskType = Objects.requireNonNull(taskType, "taskType 不能为 null");
    this.tenantCode = tenantCode;
    this.userId = userId;
    this.inputPayload = inputPayload;
    this.status = AsyncTaskStatus.PENDING.getCode();
    this.progressPercent = 0;
    this.retryCount = 0;
    this.maxRetry = 3;
  }

  /** 默认构造器（MyBatis Plus 需要）。 */
  public AsyncTask() {
  }

  public String getTaskType() {
    return taskType;
  }

  public void setTaskType(String taskType) {
    this.taskType = taskType;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getTenantCode() {
    return tenantCode;
  }

  public void setTenantCode(String tenantCode) {
    this.tenantCode = tenantCode;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getInputPayload() {
    return inputPayload;
  }

  public void setInputPayload(String inputPayload) {
    this.inputPayload = inputPayload;
  }

  public String getOutputPayload() {
    return outputPayload;
  }

  public void setOutputPayload(String outputPayload) {
    this.outputPayload = outputPayload;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Integer getProgressPercent() {
    return progressPercent;
  }

  public void setProgressPercent(Integer progressPercent) {
    this.progressPercent = progressPercent;
  }

  public Integer getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(Integer retryCount) {
    this.retryCount = retryCount;
  }

  public Integer getMaxRetry() {
    return maxRetry;
  }

  public void setMaxRetry(Integer maxRetry) {
    this.maxRetry = maxRetry;
  }

  public LocalDateTime getNextRetryAt() {
    return nextRetryAt;
  }

  public void setNextRetryAt(LocalDateTime nextRetryAt) {
    this.nextRetryAt = nextRetryAt;
  }

  public Long getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public void setTimeoutSeconds(Long timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
  }

  public String getWorkerId() {
    return workerId;
  }

  public void setWorkerId(String workerId) {
    this.workerId = workerId;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(LocalDateTime startedAt) {
    this.startedAt = startedAt;
  }

  public LocalDateTime getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(LocalDateTime completedAt) {
    this.completedAt = completedAt;
  }

  public LocalDateTime getExpireAt() {
    return expireAt;
  }

  public void setExpireAt(LocalDateTime expireAt) {
    this.expireAt = expireAt;
  }

  /**
   * 判断任务是否处于终态（SUCCEEDED / FAILED / CANCELED / EXPIRED）。
   *
   * @return true=已到达终态
   */
  public boolean isTerminal() {
    return AsyncTaskStatus.isTerminal(this.status);
  }

  /**
   * 判断任务是否可重试。
   *
   * @return true=可重试
   */
  public boolean isRetryable() {
    return AsyncTaskStatus.isRetryable(this.status)
        && retryCount != null
        && maxRetry != null
        && retryCount < maxRetry;
  }

  /**
   * 递增重试计数。
   */
  public void incrementRetry() {
    this.retryCount = (retryCount != null ? retryCount : 0) + 1;
  }

  /**
   * 更新进度并自动判定是否完成。
   *
   * @param percent 进度百分比（0-100）
   */
  public void updateProgress(int percent) {
    this.progressPercent = Math.clamp(percent, 0, 100);
    if (this.progressPercent >= 100) {
      this.status = AsyncTaskStatus.SUCCEEDED.getCode();
      this.completedAt = LocalDateTime.now();
    }
  }

  /**
   * 标记任务失败。
   *
   * @param message 失败原因
   */
  public void fail(String message) {
    this.errorMessage = message;
    this.status = AsyncTaskStatus.FAILED.getCode();
    this.completedAt = LocalDateTime.now();
  }

  /**
   * 标记任务成功。
   *
   * @param output 输出结果 JSON
   */
  public void succeed(String output) {
    this.outputPayload = output;
    this.status = AsyncTaskStatus.SUCCEEDED.getCode();
    this.progressPercent = 100;
    this.completedAt = LocalDateTime.now();
  }

  /**
   * 标记任务取消。
   */
  public void cancel() {
    this.status = AsyncTaskStatus.CANCELED.getCode();
    this.completedAt = LocalDateTime.now();
  }

  /**
   * 检查任务是否已过期。
   *
   * @return true=已过期
   */
  public boolean isExpired() {
    return expireAt != null && LocalDateTime.now().isAfter(expireAt);
  }

  /**
   * 获取输入参数（兼容性方法）。
   *
   * @return 输入参数 JSON
   */
  public String getInput() {
    return inputPayload;
  }

  /**
   * 获取输出结果（兼容性方法）。
   *
   * @return 输出结果 JSON
   */
  public String getOutput() {
    return outputPayload;
  }
}
