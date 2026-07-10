package com.njydsz.pmis.workflow.service.impl.integration;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.entity.notification.FlowNotifyOutboxDO;
import com.njydsz.pmis.workflow.entity.integration.FlowWebhookSubscriptionDO;
import com.njydsz.pmis.workflow.mapper.notification.FlowNotifyOutboxMapper;
import com.njydsz.pmis.workflow.mapper.integration.FlowWebhookSubscriptionMapper;
import com.njydsz.pmis.workflow.service.integration.FlowWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P1-6: 工作流 Webhook 事件订阅 Service 实现
 *
 * <p>负责 Webhook 订阅 CRUD + 事件投递（复用 Outbox Pattern）。
 * 投递时计算 HMAC-SHA256 签名，由 {@code NotifyOutboxScanner} 异步执行 HTTP POST。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowWebhookServiceImpl implements FlowWebhookService {

    /** Webhook 订阅 Mapper，管理 pmis_flow_webhook_subscription 表 */
    private final FlowWebhookSubscriptionMapper subscriptionMapper;
    /** 通知发件箱 Mapper，Webhook 投递失败时写入 outbox 重试 */
    private final FlowNotifyOutboxMapper outboxMapper;

    /**
     * 创建 Webhook 订阅
     *
     * @param subscription 订阅信息（名称、回调 URL、事件类型等）
     * @return 订阅 ID
     * @throws BizException 必填字段为空时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(FlowWebhookSubscriptionDO subscription) {
        validate(subscription);
        if (subscription.getEnabled() == null) {
            subscription.setEnabled(1);
        }
        if (!StringUtils.hasText(subscription.getTenantId())) {
            subscription.setTenantId("1");
        }
        subscriptionMapper.insert(subscription);
        log.info("[FlowWebhook] 创建订阅: id={} name={} url={}",
                subscription.getId(), subscription.getName(), subscription.getCallbackUrl());
        return subscription.getId();
    }

    /**
     * 更新 Webhook 订阅
     *
     * @param subscription 订阅信息（必须包含 ID）
     * @throws BizException ID 为空或必填字段缺失时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FlowWebhookSubscriptionDO subscription) {
        if (!StringUtils.hasText(subscription.getId())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_d34ce506");
        }
        validate(subscription);
        subscriptionMapper.updateById(subscription);
        log.info("[FlowWebhook] 更新订阅: id={}", subscription.getId());
    }

    /**
     * 删除 Webhook 订阅
     *
     * @param id 订阅 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        subscriptionMapper.deleteById(id);
        log.info("[FlowWebhook] 删除订阅: id={}", id);
    }

    /**
     * 根据 ID 查询 Webhook 订阅详情
     *
     * @param id 订阅 ID
     * @return 订阅信息；不存在返回 null
     */
    @Override
    @Transactional(readOnly = true)
    public FlowWebhookSubscriptionDO getById(String id) {
        return subscriptionMapper.selectById(id);
    }

    /**
     * 查询全部 Webhook 订阅列表
     *
     * @return 订阅列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<FlowWebhookSubscriptionDO> listAll() {
        return subscriptionMapper.selectAll();
    }

    /**
     * 向匹配的 Webhook 订阅者投递事件
     *
     * <p>查询当前租户下订阅了该事件类型的启用订阅，为每个订阅构造载荷
     * （含 HMAC-SHA256 签名）并写入 outbox，由异步扫描器执行 HTTP POST。
     *
     * @param tenantId  租户 ID（可空，默认 "1"）
     * @param eventType 事件类型（如 TASK_CREATED / TASK_COMPLETED）
     * @param instanceId 流程实例 ID
     * @param taskId    任务 ID（可空）
     * @param payload   事件载荷
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispatchEvent(String tenantId, String eventType, String instanceId,
                              String taskId, Map<String, Object> payload) {
        String effectiveTenant = tenantId == null ? "1" : tenantId;
        List<FlowWebhookSubscriptionDO> subs =
                subscriptionMapper.selectEnabledByEvent(effectiveTenant, eventType);
        if (subs == null || subs.isEmpty()) {
            return;
        }
        for (FlowWebhookSubscriptionDO sub : subs) {
            try {
                writeOutbox(sub, eventType, instanceId, taskId, payload);
            } catch (Exception e) {
                log.warn("[FlowWebhook] 写入 outbox 失败（不影响主流程）: subId={} event={} err={}",
                        sub.getId(), eventType, e.getMessage());
            }
        }
    }

    /**
     * 构造 Webhook 投递载荷并写入 outbox。
     *
     * <p>载荷结构：
     * <pre>
     * {
     *   "eventType": "TASK_CREATED",
     *   "instanceId": "...",
     *   "taskId": "...",
     *   "subscriptionId": "...",
     *   "callbackUrl": "https://...",
     *   "signature": "sha256=<hex>",
     *   "body": { ...原始 payload... }
     * }
     * </pre>
     */
    private void writeOutbox(FlowWebhookSubscriptionDO sub, String eventType,
                             String instanceId, String taskId,
                             Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        String bodyJson = JSON.toJSONString(body);
        String signature = sign(bodyJson, sub.getSecret());

        Map<String, Object> outboxPayload = new LinkedHashMap<>();
        outboxPayload.put("eventType", eventType);
        outboxPayload.put("instanceId", instanceId);
        outboxPayload.put("taskId", taskId);
        outboxPayload.put("subscriptionId", sub.getId());
        outboxPayload.put("subscriptionName", sub.getName());
        outboxPayload.put("callbackUrl", sub.getCallbackUrl());
        outboxPayload.put("signature", signature);
        outboxPayload.put("body", body);

        FlowNotifyOutboxDO outbox = new FlowNotifyOutboxDO();
        outbox.setTenantId(sub.getTenantId());
        outbox.setEventType(eventType);
        outbox.setBizType("WORKFLOW_WEBHOOK");
        outbox.setBizId(sub.getId());
        outbox.setInstanceId(instanceId);
        outbox.setTaskId(taskId);
        outbox.setPayload(JSON.toJSONString(outboxPayload));
        outbox.setTargetChannels("WEBHOOK");
        outbox.setTargetUserIds(null);
        outbox.setStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setMaxRetries(5);
        outbox.setNextRetryAt(LocalDateTime.now());
        outboxMapper.insert(outbox);

        log.debug("[FlowWebhook] 写入 outbox: subId={} event={} outboxId={}",
                sub.getId(), eventType, outbox.getId());
    }

    /**
     * HMAC-SHA256 签名计算。
     *
     * @param data   待签名数据（body JSON）
     * @param secret 密钥
     * @return "sha256=&lt;hex&gt;" 格式签名
     */
    private String sign(String data, String secret) {
        if (!StringUtils.hasText(secret)) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            log.warn("[FlowWebhook] 签名计算失败: err={}", e.getMessage());
            return null;
        }
    }

    private void validate(FlowWebhookSubscriptionDO sub) {
        if (sub == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_d34ce506");
        }
        if (!StringUtils.hasText(sub.getName())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_bbbf759d");
        }
        if (!StringUtils.hasText(sub.getCallbackUrl())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_e6f7a8b9");
        }
    }
}
