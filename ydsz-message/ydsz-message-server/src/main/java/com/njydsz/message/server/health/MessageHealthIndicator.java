package com.njydsz.message.server.health;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.web.health.AbstractModuleHealthIndicator;
import com.njydsz.message.domain.dto.MessageLogQueryDTO;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.repository.MsgLogRepository;
import com.njydsz.message.server.channel.ChannelRouter;

/**
 * 消息模块健康检查指示器。
 *
 * <p>报告消息引擎关键运行状态，暴露 {@code /actuator/health/message} 端点：
 *
 * <ul>
 *   <li>已注册通道列表 + 通道数量
 *   <li>Redis 连通性（PING）
 *   <li>死信队列积压数（DEAD 状态，轻量探针 LIMIT 1）
 *   <li>重试队列积压数（RETRY 状态，轻量探针 LIMIT 1）
 *   <li>定时消息积压数（SCHEDULED 状态，轻量探针 LIMIT 1）
 *   <li>发送中消息数（SENDING 状态，轻量探针 LIMIT 1）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(
    prefix = "ydsz.message",
    name = "health-enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
public class MessageHealthIndicator extends AbstractModuleHealthIndicator {

  private final RedisStringOps redisStringOps;
  private final MsgLogRepository msgLogRepository;
  private final ChannelRouter channelRouter;

  @Override
  protected void doHealthCheck(Health.Builder builder) {
    // ① 已注册通道列表
    try {
      Map<String, ?> channelCache = channelRouter.getChannelCache();
      builder.withDetail("channels", channelCache.keySet());
      builder.withDetail("channelCount", channelCache.size());
    } catch (Exception e) {
      builder.withDetail("channels", "ERROR - " + extractMessage(e));
    }

    // ② Redis 连通性
    checkRedis(
        builder,
        () -> {
          redisStringOps.hasKey("__message_health_check__");
          return "PONG";
        });

    // ③ 死信队列积压探针（DEAD 状态，命中即标记 DOWN）
    boolean hasDead = probeStatus(MessageStatusEnum.DEAD.name());
    builder.withDetail("deadLetterBacklog", hasDead);
    if (hasDead) {
      builder.down();
    }

    // ④ 重试队列积压探针（仅报告不阻断）
    builder.withDetail("retryBacklog", probeStatus(MessageStatusEnum.RETRY.name()));

    // ⑤ 定时消息积压探针（仅报告不阻断）
    builder.withDetail("scheduledBacklog", probeStatus(MessageStatusEnum.SCHEDULED.name()));

    // ⑥ 发送中消息探针（仅报告不阻断）
    builder.withDetail("inFlightMessages", probeStatus(MessageStatusEnum.SENDING.name()));
  }

  /**
   * 轻量探针：仅查询指定状态是否存在记录（LIMIT 1），避免 COUNT 扫描大表。
   *
   * @param status 要探测的消息状态（如 DEAD、RETRY、SCHEDULED）
   * @return 是否存在该状态的消息（true=存在积压）
   */
  private boolean probeStatus(String status) {
    MessageLogQueryDTO query = new MessageLogQueryDTO();
    query.setStatus(status);
    query.setPageNum(1);
    query.setPageSize(1);
    List<?> records = msgLogRepository.findList(query);
    return records != null && !records.isEmpty();
  }
}
