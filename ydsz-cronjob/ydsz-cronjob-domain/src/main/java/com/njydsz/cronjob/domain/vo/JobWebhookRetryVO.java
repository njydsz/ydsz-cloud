package com.njydsz.cronjob.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * WebHook 重试补偿记录 VO（P1-3 Webhook 投递保障）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class JobWebhookRetryVO {

  /** 主键 ID */
  private String id;

  /** WebHook 订阅 ID */
  private String webhookId;

  /** 事件类型 */
  private String eventType;

  /** 任务 KEY */
  private String jobKey;

  /** 任务执行日志 ID */
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

  /** 当前重试次数 */
  private Integer retryCount;

  /** 最大重试次数 */
  private Integer maxRetries;

  /** 下次重试时间 */
  private LocalDateTime nextRetryTime;

  /** 状态: PENDING / SUCCESS / DEAD */
  private String retryStatus;

  /** 最后错误信息 */
  private String lastError;

  /** 最后重试时间 */
  private LocalDateTime lastRetryTime;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
