package com.njydsz.pmis.message.server.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P1-8: WebSocket ACK 确认服务。
 *
 * <p>实现消息投递的 At-Least-Once 语义：
 * <ol>
 *   <li>推送消息时记录 unacked 状态（Redis + 本地内存双写）</li>
 *   <li>客户端收到消息后回复 ACK</li>
 *   <li>超时未 ACK 的消息触发重推（由定时扫描器补偿）</li>
 * </ol>
 *
 * <p>Redis Key 格式：{@code ws:unacked:{userId}:{msgId}} → timestamp
 * <p>TTL：默认 60s，超时后可被重推
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketAckService {

    private final StringRedisTemplate redisTemplate;

    /** Redis Key 前缀 */
    private static final String UNACKED_KEY_PREFIX = "ws:unacked:";

    /** 默认 ACK 超时时间（秒） */
    private static final long DEFAULT_ACK_TIMEOUT_SECONDS = 60L;

    /** 本地 unacked 计数（用于快速判断是否有待确认消息） */
    private final ConcurrentMap<String, AtomicInteger> localUnackedCount = new ConcurrentHashMap<>();

    /**
     * 记录消息为 unacked。
     *
     * @param userId 用户 ID
     * @param msgId  消息 ID
     */
    public void markUnacked(String userId, String msgId) {
        String key = buildKey(userId, msgId);
        redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()),
                Duration.ofSeconds(DEFAULT_ACK_TIMEOUT_SECONDS));
        localUnackedCount.computeIfAbsent(userId, k -> new AtomicInteger(0)).incrementAndGet();
        log.debug("[WS-ACK] 消息标记 unacked: userId={} msgId={}", userId, msgId);
    }

    /**
     * 处理客户端 ACK。
     *
     * @param userId 用户 ID
     * @param msgId  消息 ID
     * @return true 表示 ACK 成功（消息之前是 unacked 状态）
     */
    public boolean handleAck(String userId, String msgId) {
        String key = buildKey(userId, msgId);
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            AtomicInteger count = localUnackedCount.get(userId);
            if (count != null) {
                count.decrementAndGet();
            }
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
        String key = buildKey(userId, msgId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 获取用户未 ACK 消息数。
     *
     * @param userId 用户 ID
     * @return 未 ACK 消息数
     */
    public int getUnackedCount(String userId) {
        AtomicInteger count = localUnackedCount.get(userId);
        return count == null ? 0 : count.get();
    }

    private String buildKey(String userId, String msgId) {
        return UNACKED_KEY_PREFIX + userId + ":" + msgId;
    }
}
