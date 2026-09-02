package com.njydsz.cronjob.infra.entity.job;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * MapReduce 子任务记录（P0-4）。
 *
 * <p>对应 {@code ydsz_job_task} 表，存储动态产生的子任务及其执行结果。 一个 JobInstance（{@link #logId}）对应多个子任务，由 {@code
 * MapTaskExecutor} 管理： root task 调用 {@code context.map()} 产生子任务，框架执行后记录结果。
 *
 * <p>与 {@link com.njydsz.cronjob.infra.entity.log.JobLog} 的关系：
 *
 * <ul>
 *   <li>{@link com.njydsz.cronjob.infra.entity.log.JobLog}：记录整个任务实例的执行日志（一对多子任务）
 *   <li>本表：记录 root task 和每个子任务的执行明细（含状态/结果/错误信息）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_job_task")
public class JobTask extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 任务 ID（关联 ydsz_job.id） */
  private String jobId;

  /** 执行日志 ID（关联 ydsz_job_log.id） */
  private String logId;

  /** 任务 KEY（冗余，便于查询） */
  private String jobKey;

  /** 子任务名称（root task 为 "root"，子任务为业务侧定义的 taskName） */
  private String taskName;

  /** 子任务参数 JSON */
  private String taskParams;

  /**
   * 子任务类型：ROOT 根任务 / SUB_TASK 子任务。
   *
   * <p>ROOT 类型记录 root task 的执行状态（仅一条）；SUB_TASK 记录 map() 产生的子任务。
   */
  private String taskType;

  /** 执行状态：PENDING 待执行 / RUNNING 执行中 / SUCCESS 成功 / FAILED 失败。 */
  private String taskStatus;

  /** 执行结果 JSON（ProcessResult.result 序列化后的字符串） */
  private String result;

  /** 错误信息（失败时填充） */
  private String errorMessage;

  /** 执行节点 ID（hostname:port） */
  private String execNodeId;

  /**
   * P1-5: 重试次数（默认 0，每次重试递增）。
   *
   * <p>用于限制子任务最大重试次数（默认 3 次），防止无限重试。
   */
  private Integer retryCount;
}
