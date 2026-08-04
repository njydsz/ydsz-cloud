package com.remisoft.message.server.service.impl;

import java.time.Duration;

import com.remisoft.common.redis.service.RedisService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P1-8: WebSocket ACK 确认服务。
 *
 * <p>实现消息投递的 At-Least-Once 语义：
 * <ol>
 *   <li>推送消息时记录 unacked 状态（Redis Hash 存储）</li>
 *   <li>客户端收到消息后回复 ACK</li>
 *   <li>超时未 ACK 的消息触发重推（由定时扫描器补偿）</li>
 * </ol>
 *
 * <p>OD-5: 改用 Redis Hash 存储每个用户的 unacked 消息，
 * 消除本地计数器在多实例部署下的不一致问题。
 *
 * <p>Redis Key 格式：{@code ws:unacked:{userId}} → Hash(msgId → timestamp)
 * <p>TTL：默认 60s，超时后可被重推
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketAckService {

    private final RedisService redisService;

    /** Redis Key 前缀 */
    private static final String UNACKED_KEY_PREFIX = "ws:unacked:";

    /** 默认 ACK 超时时间（秒） */
    private static final long DEFAULT_ACK_TIMEOUT_SECONDS = 60L;

    /**
     * 记录消息为 unacked。
     *
     * <p>OD-5: 改用 Redis Hash 存储，消除本地计数器多实例不一致问题。
     *
     * @param userId 用户 ID
     * @param msgId  消息 ID
     */
    public void markUnacked(String userId, String msgId) {
        String key = buildKey(userId);
        redisService.hSet(key, msgId, String.valueOf(System.currentTimeMillis()));
        redisService.expire(key, Duration.ofSeconds(DEFAULT_ACK_TIMEOUT_SECONDS));
        log.debug("[WS-ACK] 消息标记 unacked: userId={} msgId={}", userId, msgId);
    }

    /**
     * 处理客户端 ACK。
     *
     * <p>OD-5: 改用 Redis Hash 删除字段，消除本地计数器。
     *
     * @param userId 用户 ID
     * @param msgId  消息 ID
     * @return true 表示 ACK 成功（消息之前是 unacked 状态）
     */
    public boolean handleAck(String userId, String msgId) {
        String key = buildKey(userId);
        Long deleted = redisService.opsForHash().delete(key, msgId);
        if (deleted != null && deleted > 0) {
            log.debug("[WS-ACK] 收到 ACK: userId={} msgId={}", userId, msgId);
            return true;
        }
        return false;
    }

    /**
     * 检查消息是否处于 unacked 状态。
     *
     * @param userId 用户 ID
     * @param msgId  消息 ID
     * @return true 表示尚未 ACK
     */
    public boolean isUnacked(String userId, String msgId) {
        String key = buildKey(userId);
        return redisService.opsForHash().hasKey(key, msgId);
    }

    /**
     * 获取用户未 ACK 消息数。
     *
     * <p>OD-5: 使用 Redis Hash HLEN，多实例一致。
     *
     * @param userId 用户 ID
     * @return 未 ACK 消息数
     */
    public int getUnackedCount(String userId) {
        String key = buildKey(userId);
        Long size = redisService.opsForHash().size(key);
        return size != null ? size.intValue() : 0;
    }

    private String buildKey(String userId) {
        return UNACKED_KEY_PREFIX + userId;
    }
}
