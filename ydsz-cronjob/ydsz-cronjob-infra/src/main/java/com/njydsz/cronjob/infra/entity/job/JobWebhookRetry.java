package com.njydsz.cronjob.infra.entity.job;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseIdEntity;

/**
 * WebHook 重试补偿记录（P1-3 Webhook 投递保障）。
 *
 * <p>当 WebhookEventDispatcher 实时推送失败（重试耗尽）时，写入本表。
 * 由 {@code WebhookRetryScanTask} 定期扫描并重试，实现最终一致性。
 *
 * <h3>重试策略</h3>
 *
 * <ul>
 *   <li>最大重试次数：5 次（含首次）
 *   <li>退避策略：指数退避 1s / 5s / 30s / 120s / 600s
 *   <li>超出最大重试后标记 DEAD，等待人工介入
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_job_webhook_retry")
public class JobWebhookRetry extends MpBaseIdEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** WebHook 订阅 ID（关联 ydsz_job_webhook.id） */
  private String webhookId;

  /** 事件类型: TASK_STARTED / TASK_SUCCESS / TASK_FAILED / TASK_TIMEOUT / DAG_COMPLETED */
  private String eventType;

  /** 任务 KEY */
  private String jobKey;

  /** 任务执行日志 ID（可选） */
  private String logId;

  /** 请求 URL */
  private String callbackUrl;

  /** 请求方法: POST / PUT */
  private String httpMethod;

  /** 请求头 JSON */
  private String headers;

  /** 密钥（用于签名验证） */
  private String webhookSecret;

  /** 请求体 JSON */
  private String payloadJson;

  /** 当前重试次数（从 0 开始） */
  private Integer retryCount;

  /** 最大重试次数（默认 5） */
  private Integer maxRetries;

  /** 下次重试时间 */
  private LocalDateTime nextRetryTime;

  /** 状态: PENDING / SUCCESS / DEAD */
  private String retryStatus;

  /** 最后错误信息 */
  private String lastError;

  /** 最后重试时间 */
  private LocalDateTime lastRetryTime;
}
