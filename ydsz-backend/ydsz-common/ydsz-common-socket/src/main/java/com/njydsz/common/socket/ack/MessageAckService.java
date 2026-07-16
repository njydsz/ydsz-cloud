package com.njydsz.common.socket.ack;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.common.socket.config.WebSocketProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息 ACK 确认服务（P1-2）。
 *
 * <p>管理已推送消息的 ACK 状态，支持：
 * <ul>
 *   <li>消息推送时注册待 ACK 记录</li>
 *   <li>客户端发送 ACK 时移除记录</li>
 *   <li>定时扫描超时未 ACK 的消息，触发重试</li>
 * </ul>
 *
 * <p>当 Redis 不可用时降级为本地 {@link ConcurrentHashMap    public void cleanupExpiredLocalAcks() {
        if (!localPendingAcks.isEmpty()) {
            localPendingAcks.clear();
}
}
} 存储。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class MessageAckService {

    private static final String ACK_KEY_PREFIX = "ydsz:ws:ack:";

    private final StringRedisTemplate redisTemplate;
    private final WebSocketProperties properties;

    /** 本地降级存储（Redis 不可用时使用） */
    private final Set<String> localPendingAcks = ConcurrentHashMap.newKeySet();

    /**
     * 注册待 ACK 消息。
     *
     * @param messageId 消息 ID
     * @param userId    用户 ID
     */
    public void registerPendingAck(String messageId, String userId) {
        if (messageId == null) {
            return;
        }
        try {
            String key = ACK_KEY_PREFIX + messageId;
            Duration timeout = properties.getAck().getTimeout();
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(key, userId != null ? userId : "", timeout);
            } else {
                localPendingAcks.add(messageId);
            }
        } catch (Exception e) {
            localPendingAcks.add(messageId);
            log.debug("[WS-ACK] Redis 不可用, 降级本地存储: messageId={}", messageId);
        }
    }

    /**
     * 处理客户端 ACK，移除待确认记录。
     *
     * @param messageId 消息 ID
     * @return true 表示 ACK 成功（消息存在且已移除）
     */
    public boolean acknowledge(String messageId) {
        if (messageId == null) {
            return false;
        }
        try {
            String key = ACK_KEY_PREFIX + messageId;
            Boolean deleted = redisTemplate != null ? redisTemplate.delete(key) : null;
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("[WS-ACK] 消息确认成功: messageId={}", messageId);
                return true;
            }
            return localPendingAcks.remove(messageId);
        } catch (Exception e) {
            return localPendingAcks.remove(messageId);
        }
    }

    /**
     * 检查消息是否已 ACK（或超时）。
     *
     * @param messageId 消息 ID
     * @return true 表示已 ACK 或已超时（无需重试）
     */
    public boolean isAcknowledgedOrExpired(String messageId) {
        if (messageId == null) {
            return true;
        }
        try {
            String key = ACK_KEY_PREFIX + messageId;
            Boolean exists = redisTemplate != null ? redisTemplate.hasKey(key) : null;
            if (Boolean.TRUE.equals(exists)) {
                return false;
            }
            if (redisTemplate == null) {
                return !localPendingAcks.contains(messageId);
            }
            return true;
        } catch (Exception e) {
            return !localPendingAcks.contains(messageId);
        }
    }

    public void cleanupExpiredLocalAcks() {
        localPendingAcks.clear();
}
}
