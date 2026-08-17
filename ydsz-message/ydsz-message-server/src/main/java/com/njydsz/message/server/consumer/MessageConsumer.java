package com.njydsz.message.server.consumer;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.lock.idempotent.IdempotentStrategy;
import com.njydsz.common.queue.compress.MessageCompressor;
import com.njydsz.common.queue.constant.YdszMessageTopics;
import com.njydsz.common.queue.trace.MessageTracer;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.message.domain.constant.MessageConstants;
import com.njydsz.message.domain.entity.core.MsgLog;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.infra.repository.MsgLogRepository;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.service.core.MessageService;

/**
 * RocketMQ 消息消费端。
 *
 * <p>监听 {@link YdszMessageTopics#TOPIC_MESSAGE},基于 Redis SET NX EX 实现消费端幂等防重。 异常处理:SysException
 * 保留锁并落库 FAILED 不重投;系统异常释放锁(Lua 安全释放)并抛出触发重投。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.apache.rocketmq.spring.annotation.RocketMQMessageListener")
@ConditionalOnProperty(
    prefix = "rocketmq.consumer",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
@RocketMQMessageListener(
    topic = YdszMessageTopics.TOPIC_MESSAGE,
    consumerGroup = YdszMessageTopics.GROUP_MESSAGE,
    selectorExpression = "*",
    maxReconsumeTimes = 3,
    consumeMode = ConsumeMode.ORDERLY)
public class MessageConsumer implements RocketMQListener<String> {

  private final MessageService messageService;
  private final IdempotentStrategy idempotentStrategy;
  private final MsgLogRepository msgLogRepository;
  private final MessageMetrics messageMetrics;
  private final MessageProperties messageProperties;

  /** 当前实例标识(hostname:pid),用于锁值与安全释放 */
  private static final String INSTANCE_ID = initInstanceId();

  /** Lua 脚本:仅当 value 匹配时才 delete(安全释放锁) */
  private static final DefaultRedisScript<Long> RELEASE_SCRIPT = initReleaseScript();

  /** P1-10: 优雅停机标志 */
  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

  /** P1-5: 在飞消息计数器（用于优雅停机等待） */
  private final AtomicInteger inFlight = new AtomicInteger(0);

  /** P1-5: 优雅停机最大等待时间（秒） */
  private static final int GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS = 30;

  /** P2-5: 丢弃原因常量 - TTL 过期 */
  private static final String DROP_REASON_TTL_EXPIRED = "TTL_EXPIRED";

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
      request = YdszJson.fromJson(body, MessageRequest.class);
    } catch (Exception e) {
      log.error("[MessageConsumer] 解析失败: body={} err={}", body, e.getMessage(), e);
      return;
    }
    if (request == null) {
      return;
    }

    // P1-12: 消息 TTL 检查，超时消息自动跳过
    // P2-5: TTL 阈值抽配置 + 丢弃计数指标
    if (isMessageExpired(request)) {
      log.warn(
          "[MessageConsumer] 消息已过期,跳过: messageId={} channel={}",
          request.getMessageId(),
          request.getChannel());
      messageMetrics.recordDropped(request.getChannel(), DROP_REASON_TTL_EXPIRED);
      return;
    }

    // 构造幂等键
    String idempotentKey = buildIdempotentKey(request);
    String idempotentToken = null;
    boolean locked = false;
    if (idempotentKey != null) {
      idempotentToken =
          idempotentStrategy.acquire(
              idempotentKey, MessageConstants.IDEMPOTENT_TTL_SECONDS * 1000L);
      locked = idempotentToken != null;
      if (!locked) {
        log.info(
            "[MessageConsumer] 重复消息已跳过: key={} messageId={}",
            idempotentKey,
            request.getMessageId());
        return;
      }
      // GAP-1: DB二级幂等检查——Redis宕机恢复后TTL可能已过期，用msg_log表兜底
      if (StringUtils.hasText(request.getMessageId())) {
        Long dbCount =
            msgLogRepository.selectCount(
                new LambdaQueryWrapper<MsgLog>()
                    .eq(MsgLog::getMsgId, request.getMessageId())
                    .in(
                        MsgLog::getStatus,
                        MessageStatusEnum.SUCCESS.name(),
                        MessageStatusEnum.SENDING.name())
                    .last("LIMIT 1"));
        if (dbCount != null && dbCount > 0) {
          log.warn("[MessageConsumer] DB二级幂等检查命中,跳过: messageId={}", request.getMessageId());
          return;
        }
      }
    }

    // GAP-2: 全链路 Trace ID 贯穿——消费者入口设置 MDC traceId
    try (MessageTracer.MessageTraceScope scope = MessageTracer.enter(request.getMessageId())) {
      inFlight.incrementAndGet();
      messageService.send(request);
      // P3-23: 记录消费延迟（从开始消费到消费完成的耗时）
      long consumeDuration = System.currentTimeMillis() - consumeStart;
      String channel = request.getChannel() != null ? request.getChannel() : "UNKNOWN";
      messageMetrics.recordConsumeDelay(channel, consumeDuration);
      log.info(
          "[MessageConsumer] 消费完成: messageId={} channel={} cost={}ms",
          request.getMessageId(),
          request.getChannel(),
          consumeDuration);
    } catch (SysException e) {
      // 业务异常:保留锁(防重投 spam),落库 FAILED 不抛出
      log.error(
          "[MessageConsumer] 业务异常: messageId={} err={}", request.getMessageId(), e.getMessage(), e);
      recordFailedLog(request, e.getMessage());
    } catch (Exception e) {
      // 系统异常:释放锁(允许重投),抛出触发重试
      log.error("[MessageConsumer] 系统异常: messageId={}", request.getMessageId(), e);
      releaseLock(idempotentKey, idempotentToken);
      throw new RuntimeException("MessageConsumer failed, will retry", e);
    } finally {
      inFlight.decrementAndGet();
    }
  }

  /**
   * 业务异常时记录 FAILED 日志(便于后续排查/补偿)。
   *
   * <p>优先按 msgId 更新已有记录的状态(避免 sendInternal 已落库后产生重复 msgId 记录), 仅当未匹配到已有记录时才 insert 新记录。
   *
   * @param request 原始消息请求
   * @param errorMessage 错误信息
   */
  private void recordFailedLog(MessageRequest request, String errorMessage) {
    try {
      // 先尝试按 msgId 更新已有记录状态为 FAILED
      String msgId = request.getMessageId();
      if (msgId != null && !msgId.isBlank()) {
        LambdaUpdateWrapper<MsgLog> updateWrapper =
            new LambdaUpdateWrapper<MsgLog>()
                .eq(MsgLog::getMsgId, msgId)
                .set(MsgLog::getStatus, MessageStatusEnum.FAILED.name())
                .set(MsgLog::getErrorMessage, errorMessage);
        int updated = msgLogRepository.update(null, updateWrapper);
        if (updated > 0) {
          log.info("[MessageConsumer] 已更新现有记录为 FAILED: messageId={}", msgId);
          return;
        }
      }
      // 未匹配到已有记录,insert 新的 FAILED 记录
      MsgLog logDO = new MsgLog();
      logDO.setChannel(request.getChannel());
      logDO.setBizType(request.getBizType());
      logDO.setBizId(request.getBizId());
      logDO.setReceiver(request.getReceiver());
      logDO.setTemplateCode(request.getTemplateCode());
      logDO.setContent(request.getContent());
      logDO.setStatus(MessageStatusEnum.FAILED.name());
      logDO.setErrorMessage(errorMessage);
      logDO.setMsgId(msgId);
      logDO.setTopic(YdszMessageTopics.TOPIC_MESSAGE);
      logDO.setReconsumeTimes(0);
      logDO.setTenantId(TenantContextHolder.getTenantId());
      msgLogRepository.insert(logDO);
    } catch (Exception logEx) {
      log.warn(
          "[MessageConsumer] 记录失败日志异常: messageId={} err={}",
          request.getMessageId(),
          logEx.getMessage());
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
      log.warn(
          "[MessageConsumer] 幂等键字段缺失,跳过幂等检查: bizType={} bizId={} template={} receiver={}",
          bizType,
          bizId,
          templateCode,
          receiver);
      return null;
    }
    return MessageConstants.IDEMPOTENT_KEY_PREFIX
        + bizType
        + ":"
        + bizId
        + ":"
        + templateCode
        + ":"
        + receiver;
  }

  private void releaseLock(String lockKey, String token) {
    if (lockKey == null || token == null) {
      return;
    }
    try {
      idempotentStrategy.release(lockKey, token);
    } catch (Exception e) {
      log.warn("[MessageConsumer] 释放幂等锁失败(等待 TTL 过期): key={} err={}", lockKey, e.getMessage(), e);
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  /**
   * P1-12: 检查消息是否已过期。
   *
   * <p>根据 {@code scheduledAt} 字段判断，如果消息的调度发送时间距今超过 TTL， 则视为过期消息（如定时消息错过了发送窗口）。
   *
   * <p>P2-5: TTL 阈值从 {@link MessageProperties#getMessageTtlSeconds()} 读取， 默认 3600s；配置为 0 表示不检查 TTL。
   *
   * @param request 消息请求
   * @return true 表示已过期
   */
  private boolean isMessageExpired(MessageRequest request) {
    long ttlSeconds = messageProperties.getMessageTtlSeconds();
    if (ttlSeconds <= 0) {
      return false;
    }
    if (request.getScheduledAt() == null) {
      return false;
    }
    try {
      long ageSeconds =
          Duration.between(request.getScheduledAt(), LocalDateTime.now()).getSeconds();
      if (ageSeconds > ttlSeconds) {
        log.warn(
            "[MessageConsumer] 消息 TTL 过期: messageId={} age={}s ttl={}s",
            request.getMessageId(),
            ageSeconds,
            ttlSeconds);
        return true;
      }
    } catch (Exception e) {
      log.debug(
          "[MessageConsumer] TTL 检查异常,放行: messageId={} err={}",
          request.getMessageId(),
          e.getMessage());
    }
    return false;
  }

  /**
   * P1-5: 优雅停机钩子（改进版）。
   *
   * <p>设置停机标志拒绝新消息后，使用在飞计数器等待当前处理中的消息完成 （最多 30 秒），替代原固定 Thread.sleep(2000)。
   */
  @PreDestroy
  public void gracefulShutdown() {
    log.info("[MessageConsumer] 开始优雅停机... inFlight={}", inFlight.get());
    shuttingDown.set(true);
    // P1-5: 等待在飞消息处理完成
    int waitSeconds = 0;
    while (inFlight.get() > 0 && waitSeconds < GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS) {
      try {
        Thread.sleep(1000);
        waitSeconds++;
        if (inFlight.get() > 0 && waitSeconds % 5 == 0) {
          log.info(
              "[MessageConsumer] 等待在飞消息完成: inFlight={} waited={}s", inFlight.get(), waitSeconds);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    if (inFlight.get() > 0) {
      log.warn("[MessageConsumer] 优雅停机超时,仍有 {} 条消息在处理中", inFlight.get());
    } else {
      log.info("[MessageConsumer] 优雅停机完成,所有消息已处理");
    }
  }
}
