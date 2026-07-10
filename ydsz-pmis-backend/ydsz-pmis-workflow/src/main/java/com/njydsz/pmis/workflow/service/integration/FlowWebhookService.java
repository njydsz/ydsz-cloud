package com.njydsz.pmis.workflow.service.integration;

import com.njydsz.pmis.workflow.entity.integration.FlowWebhookSubscriptionDO;

import java.util.List;
import java.util.Map;

/**
 * P1-6: 工作流 Webhook 事件订阅 Service
 *
 * <p>对标钉钉/飞书 Webhook 事件订阅：外部系统注册回调 URL，订阅指定事件类型，
 * 工作流事件触发时通过 Outbox Pattern 异步投递 HTTP POST 回调，
 * 含 HMAC-SHA256 签名校验 + 指数退避重试（最多 5 次）。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
public interface FlowWebhookService {

    /**
     * 创建 Webhook 订阅
     *
     * @param subscription 订阅信息（需含 name / callbackUrl / secret / eventTypes）
     * @return 订阅 ID
     */
    String create(FlowWebhookSubscriptionDO subscription);

    /**
     * 更新 Webhook 订阅
     */
    void update(FlowWebhookSubscriptionDO subscription);

    /**
     * 删除 Webhook 订阅（软删除）
     */
    void delete(String id);

    /**
     * 按 ID 查订阅
     */
    FlowWebhookSubscriptionDO getById(String id);

    /**
     * 查全部订阅
     */
    List<FlowWebhookSubscriptionDO> listAll();

    /**
     * 投递事件到匹配的 Webhook 订阅（写入 outbox，异步投递）。
     *
     * <p>本方法在主事务内调用，仅写入 outbox 表，实际 HTTP POST 由
     * {@code NotifyOutboxScanner} 异步执行。事件投递失败不影响主流程。
     *
     * @param tenantId   租户 ID
     * @param eventType  事件类型（如 TASK_CREATED）
     * @param instanceId 实例 ID（可空）
     * @param taskId     任务 ID（可空）
     * @param payload    事件载荷（可空）
     */
    void dispatchEvent(String tenantId, String eventType, String instanceId,
                       String taskId, Map<String, Object> payload);
}
