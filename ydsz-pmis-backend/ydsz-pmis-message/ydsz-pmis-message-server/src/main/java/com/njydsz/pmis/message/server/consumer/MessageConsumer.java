package com.njydsz.pmis.message.server.consumer;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;

import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.constant.PmisMessageTopics;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.message.domain.constant.MessageConstants;
import com.njydsz.pmis.message.domain.entity.core.MsgLogDO;
import com.njydsz.pmis.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.pmis.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.pmis.message.server.metrics.MessageServiceMetrics;
import com.njydsz.pmis.message.server.service.core.MessageService;
import com.njydsz.pmis.message.server.util.MessageCompressor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RocketMQ 消息消费端。
 *
 * <p>监听 {@link PmisMessageTopics#TOPIC_MESSAGE},基于 Redis SET NX EX 实现消费端幂等防重。
 * 异常处理:SysException 保留锁并落库 FAILED 不重投;系统异常释放锁(Lua 安全释放)并抛出触发重投。
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
        topic = PmisMessageTopics.TOPIC_MESSAGE,
        consumerGroup = PmisMessageTopics.GROUP_MESSAGE,
        selectorExpression = "*",
        maxReconsumeTimes = 3,
        consumeMode = ConsumeMode.ORDERLY
)
public class MessageConsumer implements RocketMQListener<String> {

    private final MessageService messageService;
    private final StringRedisTemplate redisTemplate;
    private final MsgLogMapper msgLogMapper;
    private final MessageServiceMetrics messageServiceMetrics;

    /** 当前实例标识(hostname:pid),用于锁值与安全释放 */
    private static final String INSTANCE_ID = initInstanceId();

    /** Lua 脚本:仅当 value 匹配时才 delete(安全释放锁) */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = initReleaseScript();

    /** P1-10: 优雅停机标志 */
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    /** P1-12: 消息 TTL（秒），超过此时间的消息自动跳过 */
    private static final long MESSAGE_TTL_SECONDS = 3600L;

    private static String initInstanceId() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        return name != null ? name : "unknown:" + ProcessHandle.current().pid();
    }

    private static DefaultRedisScript<Long> initReleaseScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end");
        script.setResultType(Long.class);
        return script;
    }

    @Override
    public void onMessage(String body) {
        long consumeStart = System.currentTimeMillis();
        // P1-10: 优雅停机检查
        if (shuttingDown.get()) {
            log.warn("[MessageConsumer] 服务正在关闭,拒绝新消息");
            throw new RuntimeException("Consumer is shutting down");
        }
        if (body == null || body.isBlank()) {
            log.warn("[MessageConsumer] 空消息体,跳过");
            return;
        }
        // P2-21: 消息解压（如果带 GZIP: 前缀则自动解压）
        body = MessageCompressor.decompressIfNeeded(body);
        MessageRequest request;
        try {
            request = JsonUtils.fromJson(body, MessageRequest.class);
        } catch (Exception e) {
            log.error("[MessageConsumer] 解析失败: body={} err={}", body, e.getMessage());
            return;
        }
        if (request == null) {
            return;
        }

        // P1-12: 消息 TTL 检查，超时消息自动跳过
        if (isMessageExpired(request)) {
            log.warn("[MessageConsumer] 消息已过期,跳过: messageId={} channel={}",
                    request.getMessageId(), request.getChannel());
            return;
        }

        // 构造幂等键
        String idempotentKey = buildIdempotentKey(request);
        boolean locked = false;
        if (idempotentKey != null) {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, INSTANCE_ID, Duration.ofSeconds(MessageConstants.IDEMPOTENT_TTL_SECONDS));
            locked = Boolean.TRUE.equals(acquired);
            if (!locked) {
                log.info("[MessageConsumer] 重复消息已跳过: key={} messageId={}", idempotentKey, request.getMessageId());
                return;
            }
        }

        try {
            messageService.send(request);
            // P3-23: 记录消费延迟（从开始消费到消费完成的耗时）
            long consumeDuration = System.currentTimeMillis() - consumeStart;
            String channel = request.getChannel() != null ? request.getChannel() : "UNKNOWN";
            messageServiceMetrics.recordConsumeDelay(channel, consumeDuration);
            log.info("[MessageConsumer] 消费完成: messageId={} channel={} cost={}ms",
                    request.getMessageId(), request.getChannel(), consumeDuration);
        } catch (SysException e) {
            // 业务异常:保留锁(防重投 spam),落库 FAILED 不抛出
            log.error("[MessageConsumer] 业务异常: messageId={} err={}", request.getMessageId(), e.getMessage());
            recordFailedLog(request, e.getMessage());
        } catch (Exception e) {
            // 系统异常:释放锁(允许重投),抛出触发重试
            log.error("[MessageConsumer] 系统异常: messageId={}", request.getMessageId(), e);
            releaseLock(idempotentKey);
            throw new RuntimeException("MessageConsumer failed, will retry", e);
        }
    }

    /**
     * 业务异常时记录 FAILED 日志(便于后续排查/补偿)。
     *
     * @param request      原始消息请求
     * @param errorMessage 错误信息
     */
    private void recordFailedLog(MessageRequest request, String errorMessage) {
        try {
            MsgLogDO logDO = new MsgLogDO();
            logDO.setChannel(request.getChannel());
            logDO.setBizType(request.getBizType());
            logDO.setBizId(request.getBizId());
            logDO.setReceiver(request.getReceiver());
            logDO.setTemplateCode(request.getTemplateCode());
            logDO.setContent(request.getContent());
            logDO.setStatus(MessageStatusEnum.FAILED.name());
            logDO.setErrorMessage(errorMessage);
            logDO.setMsgId(request.getMessageId());
            logDO.setTopic(PmisMessageTopics.TOPIC_MESSAGE);
            logDO.setReconsumeTimes(0);
            logDO.setTenantId(TenantContext.getTenantId());
            msgLogMapper.insert(logDO);
        } catch (Exception logEx) {
            log.warn("[MessageConsumer] 记录失败日志异常: messageId={} err={}",
                    request.getMessageId(), logEx.getMessage());
        }
    }

    private String buildIdempotentKey(MessageRequest request) {
        if (request.getMessageId() != null && !request.getMessageId().isBlank()) {
            return MessageConstants.IDEMPOTENT_KEY_PREFIX + request.getMessageId();
        }
        String bizType = request.getBizType();
        String bizId = request.getBizId();
        String templateCode = request.getTemplateCode();
        String receiver = request.getReceiver();
        if (isBlank(bizType) || isBlank(bizId) || isBlank(templateCode) || isBlank(receiver)) {
            log.warn("[MessageConsumer] 幂等键字段缺失,跳过幂等检查: bizType={} bizId={} template={} receiver={}",
                    bizType, bizId, templateCode, receiver);
            return null;
        }
        return MessageConstants.IDEMPOTENT_KEY_PREFIX + bizType + ":" + bizId + ":" + templateCode + ":" + receiver;
    }

    private void releaseLock(String lockKey) {
        if (lockKey == null) {
            return;
        }
        try {
            redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(lockKey), INSTANCE_ID);
        } catch (Exception e) {
            log.warn("[MessageConsumer] 释放幂等锁失败(等待 TTL 过期): key={} err={}", lockKey, e.getMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * P1-12: 检查消息是否已过期。
     *
     * <p>根据 {@code scheduledAt} 字段判断，如果消息的调度发送时间距今超过 TTL，
     * 则视为过期消息（如定时消息错过了发送窗口）。
     *
     * @param request 消息请求
     * @return true 表示已过期
     */
    private boolean isMessageExpired(MessageRequest request) {
        if (request.getScheduledAt() == null) {
            return false;
        }
        try {
            long ageSeconds = Duration.between(request.getScheduledAt(), LocalDateTime.now()).getSeconds();
            if (ageSeconds > MESSAGE_TTL_SECONDS) {
                log.warn("[MessageConsumer] 消息 TTL 过期: messageId={} age={}s ttl={}s",
                        request.getMessageId(), ageSeconds, MESSAGE_TTL_SECONDS);
                return true;
            }
        } catch (Exception e) {
            log.debug("[MessageConsumer] TTL 检查异常,放行: messageId={} err={}",
                    request.getMessageId(), e.getMessage());
        }
        return false;
    }

    /**
     * P1-10: 优雅停机钩子。
     *
     * <p>Spring 容器关闭时调用，设置停机标志拒绝新消息，
     * 等待当前处理中的消息完成（最多 30s）。
     */
    @PreDestroy
    public void gracefulShutdown() {
        log.info("[MessageConsumer] 开始优雅停机...");
        shuttingDown.set(true);
        // 等待 2s 让 RocketMQ 消费者完成当前批次
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[MessageConsumer] 优雅停机完成");
    }
}
