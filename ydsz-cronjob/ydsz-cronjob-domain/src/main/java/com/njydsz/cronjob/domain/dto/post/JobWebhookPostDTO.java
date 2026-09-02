package com.njydsz.cronjob.domain.dto.post;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * JobWebhook 新增请求 DTO。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class JobWebhookPostDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Schema(description = "WebHook 名称")
  private String name;

  @Schema(description = "订阅的事件类型: TASK_STARTED/TASK_SUCCESS/TASK_FAILED/TASK_TIMEOUT/DAG_COMPLETED")
  private String eventType;

  @Schema(description = "订阅的任务 KEY（null=所有任务）")
  private String jobKey;

  @Schema(description = "订阅的任务组（null=所有分组）")
  private String jobGroup;

  @Schema(description = "WebHook 回调 URL", requiredMode = Schema.RequiredMode.REQUIRED)
  private String callbackUrl;

  @Schema(description = "请求方法: POST/PUT")
  private String httpMethod;

  @Schema(description = "请求头 JSON")
  private String headers;

  @Schema(description = "密钥（用于签名验证）")
  private String secret;

  @Schema(description = "状态: ACTIVE/INACTIVE（默认 ACTIVE）")
  private String webhookStatus;
}
