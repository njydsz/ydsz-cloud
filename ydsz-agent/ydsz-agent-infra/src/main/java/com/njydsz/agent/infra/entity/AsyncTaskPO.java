package com.njydsz.agent.infra.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 异步任务持久化对象（映射 ydsz_agt_async_task 表）。
 *
 * <p>对应 agents-flex async-task 模块，支持长任务持久化。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_async_task")
public class AsyncTaskPO extends MpBaseEntity<Long> {

  private static final long serialVersionUID = 1L;

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

  /** 任务执行结果（JSON 字符串） */
  private String outputPayload;

  /** 失败原因 */
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

  /** Worker 节点标识 */
  private String workerId;

  /** 任务开始执行时间 */
  private LocalDateTime startedAt;

  /** 任务完成时间 */
  private LocalDateTime completedAt;

  /** 任务过期时间 */
  private LocalDateTime expireAt;

  /** 备注信息 */
  private String remark;
}
