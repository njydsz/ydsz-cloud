package com.njydsz.pmis.message.server.service.core;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 跨通道去重服务（P2-5）。
 *
 * <p>当同一消息（相同 bizId+bizType）在短时间内已通过其他通道发送,
 * 则跳过后续通道的发送,避免用户被多通道重复轰炸。
 *
 * <p>去重 key: {@code pmis:msg:cross-dedup:{bizType}:{bizId}}
 * TTL: 默认 5 分钟（300s）,可通过配置调整。
 *
 * @author ydsydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossChannelDedupService {

    private final StringRedisTemplate redisTemplate;

    /** 跨通道去重窗口（秒） */
    private static final long DEDUP_TTL_SECONDS = 300;

    /**
     * 检查是否重复（相同 bizType+bizId 已发送过）。
     *
     * @param bizType 业务类型
     * @param bizId   业务单据 ID
     * @param channel 当前通道（记录已发送通道）
     * @return true 表示重复（应跳过）
     */
    public boolean isDuplicate(String bizType, String bizId, String channel) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizId)) {
            return false;
        }
        String key = buildKey(bizType, bizId);
        try {
            String existing = redisTemplate.opsForValue().get(key);
            if (existing != null) {
                log.info("[CrossChannelDedup] 去重命中: bizType={} bizId={} channels={}",
                        bizType, bizId, existing);
                return true;
            }
            // 标记已发送
            redisTemplate.opsForValue().set(key, channel, Duration.ofSeconds(DEDUP_TTL_SECONDS));
            return false;
        } catch (Exception e) {
            log.warn("[CrossChannelDedup] Redis 异常,降级放行: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 清除去重标记（消息撤回时调用,允许重新发送）。
     *
     * @param bizType 业务类型
     * @param bizId   业务单据 ID
     */
    public void clearDedup(String bizType, String bizId) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizId)) {
            return;
        }
        try {
            redisTemplate.delete(buildKey(bizType, bizId));
        } catch (Exception e) {
            log.warn("[CrossChannelDedup] 清除去重标记失败: {}", e.getMessage());
        }
    }

    private String buildKey(String bizType, String bizId) {
        return "pmis:msg:cross-dedup:" + bizType + ":" + bizId;
    }
}
