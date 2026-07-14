package com.njydsz.pmis.message.server.consumer;

import java.time.Duration;
import java.util.List;

import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.constant.PmisMessageTopics;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.json.YdszJson;
import com.njydsz.pmis.message.server.service.core.MessageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P1-11: 批量消息消费者。
 *
 * <p>监听 {@link PmisMessageTopics#TOPIC_MESSAGE_BATCH} Topic，
 * 批量消费消息（单次拉取多条，统一处理），提升消费吞吐量。
 *
 * <p>适用场景：
 * <ul>
 *   <li>大批量站内通知推送（如全员公告）</li>
 *   <li>批量短信/邮件发送</li>
 *   <li>非实时通知（允许延迟几秒）</li>
 * </ul>
 *
 * <p>批量大小由 RocketMQ {@code pullBatchSize} 参数控制（默认 32）。
 * 消息体格式为 JSON 数组：{@code [MessageRequest, MessageRequest, ...]}
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.apache.rocketmq.spring.annotation.RocketMQMessageListener")
@ConditionalOnProperty(prefix = "rocketmq.consumer", name = "batch-enabled", havingValue = "true",
        matchIfMissing = false)
@RocketMQMessageListener(
        topic = PmisMessageTopics.TOPIC_MESSAGE_BATCH,
        consumerGroup = PmisMessageTopics.GROUP_MESSAGE_BATCH,
        selectorExpression = "*",
        maxReconsumeTimes = 3,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class BatchMessageConsumer implements RocketMQListener<String> {

    private final MessageService messageService;
    private final StringRedisTemplate redisTemplate;

    /** 批量消费幂等前缀 */
    private static final String BATCH_IDEMPOTENT_PREFIX = "msg:batch:";

    /** 幂等 TTL */
    private static final long IDEMPOTENT_TTL_SECONDS = 300L;

    @Override
    public void onMessage(String body) {
        if (body == null || body.isBlank()) {
            log.warn("[BatchConsumer] 空消息体,跳过");
            return;
        }
        List<MessageRequest> requests;
        try {
            requests = YdszJson.parseArray(body, MessageRequest.class);
        } catch (Exception e) {
            log.error("[BatchConsumer] 批量消息解析失败,尝试单条解析: err={}", e.getMessage(), e);
            // 降级：尝试作为单条消息处理
            try {
                MessageRequest single = YdszJson.toObject(body, MessageRequest.class);
                if (single != null) {
                    requests = List.of(single);
                } else {
                    return;
                }
            } catch (Exception ex) {
                log.error("[BatchConsumer] 单条解析也失败: {}", ex.getMessage());
                return;
            }
        }
        if (requests == null || requests.isEmpty()) {
            return;
        }
        log.info("[BatchConsumer] 收到批量消息: count={}", requests.size());
        int success = 0;
        int failure = 0;
        for (MessageRequest request : requests) {
            // 批量内逐条幂等检查
            String idempotentKey = BATCH_IDEMPOTENT_PREFIX + request.getMessageId();
            if (request.getMessageId() != null) {
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(idempotentKey, "1", Duration.ofSeconds(IDEMPOTENT_TTL_SECONDS));
                if (!Boolean.TRUE.equals(acquired)) {
                    log.debug("[BatchConsumer] 批量内消息已处理,跳过: msgId={}", request.getMessageId());
                    continue;
                }
            }
            try {
                messageService.send(request);
                success++;
            } catch (Exception e) {
                failure++;
                log.error("[BatchConsumer] 批量内消息发送失败: msgId={} err={}",
                        request.getMessageId(), e.getMessage());
                // 释放幂等锁，允许重试
                if (request.getMessageId() != null) {
                    redisTemplate.delete(idempotentKey);
                }
            }
        }
        log.info("[BatchConsumer] 批量消费完成: total={} success={} failure={}",
                requests.size(), success, failure);
        // 如果全部失败，抛出异常触发重试
        if (failure > 0 && success == 0) {
            throw new RuntimeException("Batch consumption all failed: " + failure + " messages");
        }
    }
}
