package com.njydsz.cronjob.domain.dto.put;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * JobWebhook 修改请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobWebhookPutDTO implements Serializable {

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
}