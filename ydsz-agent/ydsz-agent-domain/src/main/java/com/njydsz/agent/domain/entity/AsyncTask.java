package com.njydsz.agent.domain.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.njydsz.agent.domain.asynctask.AsyncTaskStatus;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 异步任务（domain 层持久化实体，YDIZ-DDD-007 单包模式）
 *
 * <p>封装一次异步执行请求的完整生命周期数据，包括任务类型、输入参数、状态、进度和结果。
 *
 * <p><b>YDIZ-DDD-007</b>：domain Entity 直接携带 MyBatis-Plus ORM 注解，
 * infra 层通过依赖 domain 模块引用本类，禁止自建 PO/DO 副本。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
@TableName("ydsz_agt_async_task")
public class AsyncTask extends MpBaseEntity<Long> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 默认最大重试次数 */
  private static final int DEFAULT_MAX_RETRY = 3;

  /** 主键 ID（自增，对应数据库 sequence）。 */
  @TableId(type = IdType.AUTO)
  private Long id;

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

  /** 备注信息 */
  private String remark;

  /**
   * Creates a new {@code AsyncTask} instance.
   *
   * <p>默认无参构造器（MyBatis-Plus 要求）。
   */
  public AsyncTask() {}

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
    this.maxRetry = DEFAULT_MAX_RETRY;
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
