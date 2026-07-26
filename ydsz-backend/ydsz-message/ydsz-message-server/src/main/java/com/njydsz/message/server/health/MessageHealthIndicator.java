package com.njydsz.message.server.health;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.web.health.AbstractModuleHealthIndicator;
import com.njydsz.common.redis.service.RedisService;
import com.njydsz.message.domain.entity.core.MsgLogDO;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.message.server.channel.ChannelRouter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息模块健康检查指示器。
 *
 * <p>报告消息引擎关键运行状态，暴露 {@code /actuator/health/message} 端点：
 * <ul>
 *   <li>已注册通道列表 + 通道数量</li>
 *   <li>Redis 连通性（PING）</li>
 *   <li>死信队列积压数（DEAD 状态，轻量探针 LIMIT 1）</li>
 *   <li>重试队列积压数（RETRY 状态，轻量探针 LIMIT 1）</li>
 *   <li>定时消息积压数（SCHEDULED 状态，轻量探针 LIMIT 1）</li>
 *   <li>发送中消息数（SENDING 状态，轻量探针 LIMIT 1）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "ydsz.message", name = "health-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MessageHealthIndicator extends AbstractModuleHealthIndicator {

    private final RedisService redisService;
    private final MsgLogMapper msgLogMapper;
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
        checkRedis(builder, () -> redisService.execute(conn -> conn.ping(), true));

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
     */
    private boolean probeStatus(String status) {
        IPage<MsgLogDO> page = msgLogMapper.selectPage(
                new Page<>(1, 1),
                new LambdaQueryWrapper<MsgLogDO>()
                        .eq(MsgLogDO::getStatus, status)
                        .last("LIMIT 1"));
        return page != null && page.getRecords() != null && !page.getRecords().isEmpty();
    }
}
