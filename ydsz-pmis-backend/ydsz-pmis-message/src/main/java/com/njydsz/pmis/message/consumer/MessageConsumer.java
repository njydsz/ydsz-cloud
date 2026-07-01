package com.njydsz.pmis.message.consumer;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 消息消费端（异步推送）
 *
 * <p>监听 PMIS 业务事件 Topic：<code>pmis-message-topic</code>，由 producer
 * （项目变更、审批通过、对账差异等场景）发送 {@link MessageRequest} JSON。
 * Consumer 负责：
 *   1. 解析消息体 → 调 MessageService.send
 *   2. 失败重试（messageDelayLevel 1s 5s 10s 30s 1m 2m ...）
 *   3. 异常转 BizException 上抛，由 RocketMQ 重投机制兜底
 *
 * <p>注意：依赖 {@code rocketmq-spring-boot-starter}，需在 application.yml 显式
 * 启用 {@code rocketmq.consumer.enabled=true}；同时本类只在 RocketMQ starter
 * 类存在时注册（避免缺包启动失败）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.apache.rocketmq.spring.annotation.RocketMQMessageListener")
@ConditionalOnProperty(prefix = "rocketmq.consumer", name = "enabled", havingValue = "true", matchIfMissing = false)
@RocketMQMessageListener(
        topic = "pmis-message-topic",
        consumerGroup = "pmis-message-consumer",
        selectorExpression = "*",
        maxReconsumeTimes = 3
)
public class MessageConsumer implements RocketMQListener<String> {

    private final MessageService messageService;

    @Override
    public void onMessage(String body) {
        if (body == null || body.isBlank()) {
            log.warn("[MessageConsumer] empty body, skip");
            return;
        }
        long start = System.currentTimeMillis();
        MessageRequest request;
        try {
            request = JSON.parseObject(body, MessageRequest.class);
        } catch (Exception e) {
            // 解析失败：直接丢弃，不重试
            log.error("[MessageConsumer] parse failed, body={}, err={}", body, e.getMessage());
            return;
        }
        if (request == null) {
            log.warn("[MessageConsumer] parse to null, body={}", body);
            return;
        }
        try {
            // 透传到 MessageService.send（统一走模板引擎 + 多通道分发）
            messageService.send(request);
            log.info("[MessageConsumer] topic=pmis-message-topic channel={} template={} cost={}ms",
                    request.getChannel(), request.getTemplateCode(), System.currentTimeMillis() - start);
        } catch (BizException e) {
            // 业务异常：不重试，直接告警
            log.error("[MessageConsumer] biz error, body={}, err={}", body, e.getMessage());
            // 不抛出，避免无限重试；调用方应通过 NotificationService 二次告警
        } catch (Exception e) {
            // 系统异常：抛出，触发 RocketMQ 重投
            log.error("[MessageConsumer] system error, body={}", body, e);
            throw new RuntimeException("MessageConsumer failed, will retry", e);
        }
    }
}
