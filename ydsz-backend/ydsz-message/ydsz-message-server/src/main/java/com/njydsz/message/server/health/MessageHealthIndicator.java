package com.njydsz.message.server.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import com.njydsz.common.redis.service.RedisService;
import org.springframework.stereotype.Component;

import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.message.server.channel.ChannelRouter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.message.domain.entity.core.MsgLogDO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息模块健康检查指示器。
 *
 * <p>报告消息引擎关键运行状态，暴露 {@code /actuator/health/message} 端点：
 * <ul>
 *   <li>已注册通道列表 + 通道数量</li>
 *   <li>重试队列积压数（RETRY 状态，轻量探针 LIMIT 1）</li>
 *   <li>死信队列积压数（DEAD 状态，轻量探针 LIMIT 1）</li>
 *   <li>定时消息积压数（SCHEDULED 状态，轻量探针 LIMIT 1）</li>
 *   <li>发送中消息数（SENDING 状态，轻量探针 LIMIT 1）</li>
 *   <li>Redis 连通性（PING）</li>
 * </ul>
 *
 * <p>所有查询使用 {@code LIMIT 1} 轻量探针，避免在大表上做 COUNT 扫描。
 * 按状态维度检查：仅当 DEAD 积压数 > 0 且探针命中时标记为 DOWN，
 * RETRY/SCHEDULED 积压仅做信息报告不阻断健康。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Slf4j
@Component
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "ydsz.message", name = "health-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MessageHealthIndicator implements HealthIndicator {

    private final RedisService redisService;
    private final MsgLogMapper msgLogMapper;
    private final ChannelRouter channelRouter;

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        // ① 已注册通道列表
        try {
            Map<String, ?> channelCache = channelRouter.getChannelCache();
            details.put("channels", channelCache.keySet());
            details.put("channelCount", channelCache.size());
        } catch (Exception e) {
            details.put("channels", "ERROR - " + e.getMessage());
        }

        // ② Redis 连通性
        try {
            String ping = redisService.execute(conn -> conn.ping(), true);
            details.put("redis", "UP - " + ping);
        } catch (Exception e) {
            details.put("redis", "DOWN - " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        // ③ 死信队列积压探针（DEAD 状态，仅查 1 条，命中即标记 DOWN）
        try {
            boolean hasDead = probeStatus(MessageStatusEnum.DEAD.name());
            details.put("deadLetterBacklog", hasDead);
            if (hasDead) {
                return Health.down().withDetails(details).build();
            }
        } catch (Exception e) {
            details.put("deadLetterBacklog", "ERROR - " + e.getMessage());
        }

        // ④ 重试队列积压探针（RETRY 状态，仅报告不阻断）
        try {
            boolean hasRetry = probeStatus(MessageStatusEnum.RETRY.name());
            details.put("retryBacklog", hasRetry);
        } catch (Exception e) {
            details.put("retryBacklog", "ERROR - " + e.getMessage());
        }

        // ⑤ 定时消息积压探针（SCHEDULED 状态，仅报告不阻断）
        try {
            boolean hasScheduled = probeStatus(MessageStatusEnum.SCHEDULED.name());
            details.put("scheduledBacklog", hasScheduled);
        } catch (Exception e) {
            details.put("scheduledBacklog", "ERROR - " + e.getMessage());
        }

        // ⑥ 发送中消息探针（SENDING 状态，仅报告不阻断）
        try {
            boolean hasSending = probeStatus(MessageStatusEnum.SENDING.name());
            details.put("inFlightMessages", hasSending);
        } catch (Exception e) {
            details.put("inFlightMessages", "ERROR - " + e.getMessage());
        }

        return Health.up().withDetails(details).build();
    }

    /**
     * 轻量探针：仅查询指定状态是否存在记录（LIMIT 1），避免 COUNT 扫描大表。
     *
     * @param status 消息状态
     * @return true 表示存在该状态的记录
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
