package com.njydsz.pmis.message.server.consumer;

import com.njydsz.pmis.common.constant.PmisMessageTopics;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.api.dto.MessageRequest;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.util.JsonUtils;
import com.njydsz.pmis.message.domain.constant.MessageConstants;
import com.njydsz.pmis.message.domain.entity.core.MsgLogDO;
import com.njydsz.pmis.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.pmis.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.pmis.message.server.service.core.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Collections;

/**
 * RocketMQ 消息消费端。
 *
 * <p>监听 {@link PmisMessageTopics#TOPIC_MESSAGE},基于 Redis SET NX EX 实现消费端幂等防重。
 * 异常处理:BizException 保留锁并落库 FAILED 不重投;系统异常释放锁(Lua 安全释放)并抛出触发重投。
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
        maxReconsumeTimes = 3
)
public class MessageConsumer implements RocketMQListener<String> {

    private final MessageService messageService;
    private final StringRedisTemplate redisTemplate;
    private final MsgLogMapper msgLogMapper;

    /** 当前实例标识(hostname:pid),用于锁值与安全释放 */
    private static final String INSTANCE_ID = initInstanceId();

    /** Lua 脚本:仅当 value 匹配时才 delete(安全释放锁) */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = initReleaseScript();

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
        if (body == null || body.isBlank()) {
            log.warn("[MessageConsumer] 空消息体,跳过");
            return;
        }
        MessageRequest request;
        try {
            request = JsonUtils.parseObject(body, MessageRequest.class);
        } catch (Exception e) {
            log.error("[MessageConsumer] 解析失败: body={} err={}", body, e.getMessage());
            return;
        }
        if (request == null) {
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
            log.info("[MessageConsumer] 消费完成: messageId={} channel={}", request.getMessageId(), request.getChannel());
        } catch (BizException e) {
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
}
