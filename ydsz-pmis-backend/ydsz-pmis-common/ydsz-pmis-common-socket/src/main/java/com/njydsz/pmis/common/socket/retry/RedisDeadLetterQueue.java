package com.njydsz.pmis.common.socket.retry;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.pmis.common.json.Json;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis List 实现的死信队列（P0-4）。
 *
 * <p>使用 Redis List 存储死信消息（LPUSH 入队，LRANGE 查询），
 * 保留最近 1000 条死信消息，供人工排查。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class RedisDeadLetterQueue implements DeadLetterQueue {

    private static final String DEAD_LETTER_KEY = "pmis:ws:retry:deadletter";
    private static final int MAX_SIZE = 1000;

    private final StringRedisTemplate redisTemplate;

    @Override
    public void enqueue(RetryableMessage message) {
        if (message == null) {
            return;
        }
        try {
            String json = Json.toJson(message);
            redisTemplate.opsForList().leftPush(DEAD_LETTER_KEY, json);
            redisTemplate.opsForList().trim(DEAD_LETTER_KEY, 0, MAX_SIZE - 1);
            log.warn("[WS-DeadLetter] 消息移入死信队列: messageId={}, retryCount={}",
                    message.getMessageId(), message.getRetryCount());
        } catch (Exception e) {
            log.error("[WS-DeadLetter] 死信入队失败: messageId={}, err={}",
                    message.getMessageId(), e.getMessage());
        }
    }

    @Override
    public List<RetryableMessage> list(int offset, int limit) {
        try {
            List<String> raw = redisTemplate.opsForList().range(DEAD_LETTER_KEY, offset, offset + limit - 1);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            List<RetryableMessage> result = new ArrayList<>(raw.size());
            for (String json : raw) {
                try {
                    result.add(Json.toObject(json, RetryableMessage.class));
                } catch (Exception e) {
                    log.warn("[WS-DeadLetter] 死信解析失败: err={}", e.getMessage());
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[WS-DeadLetter] 查询死信失败: err={}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public long count() {
        try {
            Long size = redisTemplate.opsForList().size(DEAD_LETTER_KEY);
            return size == null ? 0L : size;
        } catch (Exception e) {
            log.debug("[WS-DeadLetter] 获取死信数量失败: {}", e.getMessage());
            return 0L;
        }
    }
}
