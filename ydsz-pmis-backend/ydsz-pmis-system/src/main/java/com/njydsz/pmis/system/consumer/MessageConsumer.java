package com.njydsz.pmis.system.consumer;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.constant.PmisMessageTopics;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.system.entity.MessageLogDO;
import com.njydsz.pmis.system.mapper.MessageLogMapper;
import com.njydsz.pmis.system.service.MessageService;
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
 * RocketMQ 消息消费端（异步推送）
 *
 * <p>监听 PMIS 业务事件 Topic：<code>pmis-message-topic</code>，由 producer
 * （项目变更、审批通过、对账差异等场景）发送 {@link MessageRequest} JSON。
 * Consumer 负责：
 *   1. 解析消息体 → 调 MessageService.send
 *   2. 失败重试（messageDelayLevel 1s 5s 10s 30s 1m 2m ...）
 *   3. 异常转 BizException 上抛，由 RocketMQ 重投机制兜底
 *
 * <p>P0-6 幂等保障：基于 Redis SET NX EX 实现消费端幂等防重，
 * 防止 RocketMQ 重投导致同一消息被消费多次（短信/邮件重复发送）。
 * 幂等键构造策略：
 *   <ul>
 *     <li>优先使用 {@link MessageRequest#getMessageId()}（producer 生成的 UUID）</li>
 *     <li>无 messageId 时，使用 {@code bizType:bizId:templateCode:receiver} 组合键</li>
 *     <li>两者都缺失时，不做幂等检查（降级为直接消费）</li>
 *   </ul>
 * TTL = 10 分钟，覆盖 RocketMQ maxReconsumeTimes=3 的全部重投窗口（1s+5s+10s+30s+1m+2m ≈ 4 min）。
 *
 * <p>异常处理策略：
 *   <ul>
 *     <li>锁获取失败（重复消息）→ 跳过，不调 service</li>
 *     <li>BizException → 保留锁（防止重投 spam），不抛出</li>
 *     <li>系统异常 → 释放锁（允许 RocketMQ 重投），抛出 RuntimeException</li>
 *     <li>成功 → 保留锁（TTL 内防重）</li>
 *   </ul>
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

    /** 消息服务 */
    private final MessageService messageService;
    /** Redis 模板，用于幂等防重 */
    private final StringRedisTemplate redisTemplate;
    /** 消息日志 Mapper（P0-D3: BizException 不再静默丢弃，记录到日志表） */
    private final MessageLogMapper messageLogMapper;

    // ==================== 幂等防重常量 ====================

    /** 消费幂等 key 前缀 */
    private static final String IDEMPOTENT_KEY_PREFIX = "pmis:message:idempotent:";

    /** 幂等 TTL: 10 分钟（覆盖 RocketMQ 重投窗口） */
    private static final Duration IDEMPOTENT_TTL = Duration.ofMinutes(10);

    /** 当前实例标识（hostname:pid），用于锁值和安全释放 */
    private static final String INSTANCE_ID = initInstanceId();

    /** Lua 脚本: 安全释放锁（仅当 value 匹配时才 delete） */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = initReleaseScript();

    /**
     * 初始化当前实例标识，取自 JVM 运行时名称（hostname:pid）。
     *
     * @return 实例标识字符串
     */
    private static String initInstanceId() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        return name != null ? name : "unknown:" + ProcessHandle.current().pid();
    }

    /**
     * 初始化安全释放锁的 Lua 脚本（仅当 value 匹配时才 delete）。
     *
     * @return Redis 释放锁脚本
     */
    private static DefaultRedisScript<Long> initReleaseScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end");
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 消费消息：解析 → 幂等加锁 → 发送 → 异常分级处理。
     *
     * @param body 消息体 JSON 字符串
     */
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
            log.error("[MessageConsumer] parse failed, body={}, err={}", body, e.getMessage());
            return;
        }
        if (request == null) {
            log.warn("[MessageConsumer] parse to null, body={}", body);
            return;
        }

        // P0-6: 构造幂等键
        String idempotentKey = buildIdempotentKey(request);
        boolean locked = false;
        if (idempotentKey != null) {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, INSTANCE_ID, IDEMPOTENT_TTL);
            locked = Boolean.TRUE.equals(acquired);
            if (!locked) {
                log.info("[MessageConsumer] 重复消息已跳过: key={} messageId={}",
                        idempotentKey, request.getMessageId());
                return;
            }
            log.debug("[MessageConsumer] 获取幂等锁成功: key={} holder={}", idempotentKey, INSTANCE_ID);
        }

        try {
            messageService.send(request);
            log.info("[MessageConsumer] topic={} channel={} template={} cost={}ms idempotent={}",
                    PmisMessageTopics.TOPIC_MESSAGE, request.getChannel(), request.getTemplateCode(),
                    System.currentTimeMillis() - start, idempotentKey != null);
        } catch (BizException e) {
            // 业务异常：保留锁（防止重投 spam），不抛出触发重试；
            // P0-D3: 不再静默丢弃，落库到 pmis_message_log(status=FAILED) 便于后续排查/补偿
            log.error("[MessageConsumer] biz error, messageId={} err={} idempotentKey={}",
                    request.getMessageId(), e.getMessage(), idempotentKey);
            recordFailedLog(request, e.getMessage());
        } catch (Exception e) {
            // 系统异常：释放锁（允许 RocketMQ 重投），抛出触发重试
            log.error("[MessageConsumer] system error, messageId={} idempotentKey={}",
                    request.getMessageId(), idempotentKey, e);
            releaseLock(idempotentKey);
            throw new RuntimeException("MessageConsumer failed, will retry", e);
        }
        // 正常完成或 BizException：保留锁，TTL 内防止重复消费
    }

    /**
     * 业务异常时记录失败日志到 pmis_message_log（P0-D3）
     *
     * <p>原实现 BizException 静默丢弃，无任何痕迹。改为落库 status=FAILED，
     * 便于运维通过日志表排查/补偿，避免消息"消失"。
     * 落库失败仅记录 WARN 日志，不影响主流程（避免日志写入失败反抛异常触发无意义重投）。
     *
     * @param request      原始消息请求
     * @param errorMessage 错误信息
     */
    private void recordFailedLog(MessageRequest request, String errorMessage) {
        try {
            MessageLogDO logDO = new MessageLogDO();
            logDO.setChannel(request.getChannel());
            logDO.setBizType(request.getBizType());
            logDO.setBizId(request.getBizId());
            logDO.setReceiver(request.getReceiver());
            logDO.setTemplateCode(request.getTemplateCode());
            logDO.setContent(request.getContent());
            logDO.setStatus("FAILED");
            logDO.setErrorMessage(errorMessage);
            logDO.setMsgId(request.getMessageId());
            logDO.setTopic(PmisMessageTopics.TOPIC_MESSAGE);
            logDO.setReconsumeTimes(0);
            logDO.setTenantId(1L);
            messageLogMapper.insert(logDO);
        } catch (Exception logEx) {
            // 日志落库失败不影响主流程，仅记录
            log.warn("[MessageConsumer] 记录失败日志异常, messageId={} err={}",
                    request.getMessageId(), logEx.getMessage());
        }
    }

    /**
     * 构造幂等键。
     * <ul>
     *   <li>优先使用 messageId（producer 生成的 UUID）</li>
     *   <li>无 messageId 时，使用 bizType:bizId:templateCode:receiver 组合键</li>
     *   <li>两者都缺失时返回 null（不做幂等检查）</li>
     * </ul>
     */
    private String buildIdempotentKey(MessageRequest request) {
        if (request.getMessageId() != null && !request.getMessageId().isBlank()) {
            return IDEMPOTENT_KEY_PREFIX + request.getMessageId();
        }
        String bizType = request.getBizType();
        String bizId = request.getBizId();
        String templateCode = request.getTemplateCode();
        String receiver = request.getReceiver();
        if (isBlank(bizType) || isBlank(bizId) || isBlank(templateCode) || isBlank(receiver)) {
            // 关键字段缺失，无法构造稳定幂等键，降级为不做幂等检查
            log.warn("[MessageConsumer] 幂等键字段缺失，跳过幂等检查: bizType={} bizId={} template={} receiver={}",
                    bizType, bizId, templateCode, receiver);
            return null;
        }
        return IDEMPOTENT_KEY_PREFIX + bizType + ":" + bizId + ":" + templateCode + ":" + receiver;
    }

    /** 安全释放锁（仅当 value 匹配时才 delete） */
    private void releaseLock(String lockKey) {
        if (lockKey == null) {
            return;
        }
        try {
            redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(lockKey), INSTANCE_ID);
        } catch (Exception e) {
            log.warn("[MessageConsumer] 释放幂等锁失败(将等待 TTL 自动过期): key={} reason={}",
                    lockKey, e.getMessage());
        }
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param s 字符串
     * @return 为 null 或空白时返回 true
     */
    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
