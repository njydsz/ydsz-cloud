package com.njydsz.pmis.common.webhook;

import java.io.Serializable;

import lombok.Builder;
import lombok.Data;

/**
 * Webhook 订阅信息。
 *
 * <p>描述一个外部系统的 Webhook 回调订阅，包含回调 URL、订阅事件类型、
 * 签名密钥等元数据。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Data
@Builder
public class WebhookSubscription implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订阅 ID（唯一标识） */
    private String id;

    /** 回调 URL */
    private String callbackUrl;

    /** 订阅事件类型（逗号分隔，如 "MESSAGE_SENT,MESSAGE_FAILED"） */
    private String eventTypes;

    /** HMAC 签名密钥 */
    private String secret;

    /** 是否启用 */
    private Boolean enabled;

    /** 来源模块 */
    private String sourceModule;
}
