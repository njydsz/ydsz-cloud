package com.njydsz.pmis.workflow.service.impl;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.entity.FlowNotifyOutboxDO;
import com.njydsz.pmis.workflow.entity.FlowWebhookSubscriptionDO;
import com.njydsz.pmis.workflow.mapper.FlowNotifyOutboxMapper;
import com.njydsz.pmis.workflow.mapper.FlowWebhookSubscriptionMapper;
import com.njydsz.pmis.workflow.service.FlowWebhookService;
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

    private final FlowWebhookSubscriptionMapper subscriptionMapper;
    private final FlowNotifyOutboxMapper outboxMapper;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        subscriptionMapper.deleteById(id);
        log.info("[FlowWebhook] 删除订阅: id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public FlowWebhookSubscriptionDO getById(String id) {
        return subscriptionMapper.selectById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowWebhookSubscriptionDO> listAll() {
        return subscriptionMapper.selectAll();
    }

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
