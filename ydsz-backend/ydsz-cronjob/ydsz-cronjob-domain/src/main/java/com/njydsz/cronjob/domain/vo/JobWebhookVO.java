package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * JobWebhook 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobWebhookVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String eventType;
    private String jobKey;
    private String jobGroup;
    private String callbackUrl;
    private String httpMethod;
    private String headers;
    private String secret;
    private String webhookStatus;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}