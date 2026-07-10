package com.njydsz.pmis.cronjob.entity.job;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * WebHook 事件订阅实体（P3-13 WebHook 事件订阅）。
 *
 * <p>记录用户配置的 WebHook 订阅，在任务生命周期事件发生时推送通知。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@TableName("pmis_job_webhook")
public class JobWebhookDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
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
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除 */
    private Integer deleted;
}
