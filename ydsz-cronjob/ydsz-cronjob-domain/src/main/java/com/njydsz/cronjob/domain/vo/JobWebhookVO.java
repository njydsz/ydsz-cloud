package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * JobWebhook 视图对象。
 *
 * <p>用于 Controller 层返回 WebHook 订阅数据，对应实体 {@link com.njydsz.cronjob.domain.entity.job.JobWebhook}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobWebhookVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    private String id;

    /** WebHook 名称 */
    private String name;

    /** 订阅的事件类型: TASK_STARTED / TASK_SUCCESS / TASK_FAILED / TASK_TIMEOUT / DAG_COMPLETED */
    private String eventType;

    /** 订阅的任务 KEY（null=所有任务） */
    private String jobKey;

    /** 订阅的任务组（null=所有分组） */
    private String jobGroup;

    /** WebHook 回调 URL */
    private String callbackUrl;

    /** 请求方法: POST / PUT */
    private String httpMethod;

    /** 请求头 JSON */
    private String headers;

    /** 密钥（用于签名验证） */
    private String secret;

    /** 状态: ACTIVE / INACTIVE */
    private String webhookStatus;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新人 */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
