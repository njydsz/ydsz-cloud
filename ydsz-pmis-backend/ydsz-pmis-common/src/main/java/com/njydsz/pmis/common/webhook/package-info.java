/**
 * 统一 Webhook 分发框架（P2-1 架构优化）。
 *
 * <p>替代 message 模块的 WebhookChannel 和 cronjob 模块的 WebhookEventDispatcher。
 * 各模块通过 {@link com.njydsz.pmis.common.webhook.WebhookDispatcher} 统一发送 Webhook 通知。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
package com.njydsz.pmis.common.webhook;
