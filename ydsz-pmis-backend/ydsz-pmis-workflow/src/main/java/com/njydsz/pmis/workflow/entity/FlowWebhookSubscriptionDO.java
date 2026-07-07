package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * P1-6: 工作流 Webhook 事件订阅 DO
 *
 * <p>对标钉钉/飞书 Webhook 事件订阅：外部系统注册回调 URL，订阅指定事件类型，
 * 工作流事件触发时通过 Outbox Pattern 异步投递 HTTP POST 回调，
 * 含 HMAC-SHA256 签名校验 + 指数退避重试（最多 5 次）。
 *
 * <p>投递流程：
 * <ol>
 *   <li>{@code FlowWebhookEventListener} 监听工作流事件</li>
 *   <li>查匹配的订阅（按 eventType + enabled）</li>
 *   <li>对每条订阅：构造 payload + HMAC-SHA256 签名，写入 {@code pmis_flow_notify_outbox}</li>
 *   <li>{@code NotifyOutboxScanner} 扫描 outbox，HTTP POST 到回调 URL</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_webhook_subscription")
public class FlowWebhookSubscriptionDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 订阅名称 */
    private String name;

    /** 回调 URL（HTTPS 推荐） */
    private String callbackUrl;

    /**
     * 签名密钥（用于 HMAC-SHA256 签名）。
     * 投递时以 `X-Webhook-Signature: sha256=<hex>` 头部携带签名，
     * 接收方用相同密钥对 body 计算 HMAC 比对校验。
     */
    private String secret;

    /**
     * 订阅事件类型列表（逗号分隔，如 TASK_CREATED,TASK_COMPLETED）。
     * 为空表示订阅全部事件。
     */
    private String eventTypes;

    /** 1=启用 0=禁用 */
    private Integer enabled;

    /** 描述 */
    private String description;

    /** 链路追踪 ID */
    private String providerTraceId;
}
