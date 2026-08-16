package com.njydsz.cronjob.domain.dto.put;

import java.io.Serial;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "主键 ID（更新时必填）")
    private String id;

    @Schema(description = "WebHook 名称")
    private String name;

    @Schema(description = "订阅的事件类型: TASK_STARTED/TASK_SUCCESS/TASK_FAILED/TASK_TIMEOUT/DAG_COMPLETED")
    private String eventType;

    @Schema(description = "订阅的任务 KEY（null=所有任务）")
    private String jobKey;

    @Schema(description = "订阅的任务组（null=所有分组）")
    private String jobGroup;

    @Schema(description = "WebHook 回调 URL")
    private String callbackUrl;

    @Schema(description = "请求方法: POST/PUT")
    private String httpMethod;

    @Schema(description = "请求头 JSON")
    private String headers;

    @Schema(description = "密钥（用于签名验证）")
    private String secret;

    @Schema(description = "状态: ACTIVE/INACTIVE")
    private String webhookStatus;
}
