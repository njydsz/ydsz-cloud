package com.njydsz.message.server.service.core;

import java.time.Duration;

import com.njydsz.common.redis.service.RedisService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 跨渠道去重服务。
 * <p>同一用户同一内容多渠道发送时去重。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


@Slf4j
@Component
@RequiredArgsConstructor
public class CrossChannelDedupService {

    private final RedisService redisService;

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
            String existing = redisService.get(key, String.class);
            if (existing != null) {
                log.info("[CrossChannelDedup] 去重命中: bizType={} bizId={} channels={}",
                        bizType, bizId, existing);
                return true;
            }
            // 标记已发送
            redisService.set(key, channel, Duration.ofSeconds(DEDUP_TTL_SECONDS));
            return false;
        } catch (Exception e) {
            log.warn("[CrossChannelDedup] Redis 异常,降级放行: {}", e.getMessage(), e);
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
            redisService.delete(buildKey(bizType, bizId));
        } catch (Exception e) {
            log.warn("[CrossChannelDedup] 清除去重标记失败: {}", e.getMessage(), e);
        }
    }

    private String buildKey(String bizType, String bizId) {
        return "ydsz:msg:cross-dedup:" + bizType + ":" + bizId;
    }
}
