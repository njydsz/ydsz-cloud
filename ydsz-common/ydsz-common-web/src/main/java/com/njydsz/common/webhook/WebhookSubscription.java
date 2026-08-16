package com.njydsz.common.webhook;

import lombok.Builder;
import lombok.Data;

/**
 * Webhook 订阅信息。
 *
 * <p>描述外部系统对特定事件的订阅关系，包含回调 URL、订阅事件类型、 签名密钥等。由 {@link WebhookDispatcher} 统一管理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
public class WebhookSubscription {

  /** 订阅唯一标识 */
  private String id;

  /** 回调 URL */
  private String callbackUrl;

  /** 订阅事件类型（逗号分隔，如 {@code MESSAGE_SENT,MESSAGE_FAILED}） */
  private String eventTypes;

  /** HMAC-SHA256 签名密钥（回调时附带签名头） */
  private String secret;

  /** 是否启用 */
  private Boolean enabled;

  /** 来源模块（如 message / workflow / project） */
  private String sourceModule;
}
