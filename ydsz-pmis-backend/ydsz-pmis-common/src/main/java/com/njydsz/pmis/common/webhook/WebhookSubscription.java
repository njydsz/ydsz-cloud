package com.njydsz.pmis.common.webhook;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Webhook 订阅信息（P2-1 架构优化）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@Builder
public class WebhookSubscription implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订阅 ID */
    private String id;

    /** 订阅名称 */
    private String name;

    /** 回调 URL */
    private String callbackUrl;

    /** HMAC-SHA256 签名密钥 */
    private String secret;

    /** 订阅事件类型（逗号分隔，空=全部） */
    private String eventTypes;

    /** 是否启用 */
    private boolean enabled;

    /** 租户 ID */
    private String tenantId;

    /** 来源模块 */
    private String sourceModule;
}
