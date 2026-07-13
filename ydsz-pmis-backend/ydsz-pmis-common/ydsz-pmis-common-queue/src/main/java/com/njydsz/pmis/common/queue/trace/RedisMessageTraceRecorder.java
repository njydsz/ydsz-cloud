package com.njydsz.pmis.common.queue.trace;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;

import com.njydsz.pmis.common.redis.service.RedisService;
import com.njydsz.pmis.common.util.json.JsonUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Redis 的消息轨迹记录器
 *
 * <p>使用 Redis Hash 存储消息轨迹数据。
 * <p>存储结构：
 * <ul>
 *   <li>Hash key: ydsz:queue:trace:{traceId}</li>
 *   <li>Hash field: messageId</li>
 *   <li>Hash value: MessageTrace JSON 字符串</li>
 * </ul>
 * <p>使用 Redis Set 维护 messageId -> traceId 的索引映射。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
public class RedisMessageTraceRecorder implements MessageTraceRecorder {

    private static final String TRACE_KEY_PREFIX = "ydsz:queue:trace:";
    private static final String INDEX_KEY_PREFIX = "ydsz:queue:trace:index:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final long ttlMinutes;

    public RedisMessageTraceRecorder(RedisService redisService, long ttlMinutes) {
        if (redisService == null) {
            throw new IllegalArgumentException("RedisService 不能为空");
        }
        this.redisTemplate = redisService.getRedisTemplate();
        this.ttlMinutes = ttlMinutes;
    }

    @Override
    public void record(MessageTrace trace) {
        if (trace == null || trace.getMessageId() == null) {
            log.warn("[MessageTrace] Redis 轨迹记录被忽略，trace 或 messageId 为空");
            return;
        }

        String traceId = trace.getTraceId();
        if (traceId == null || traceId.isEmpty()) {
            log.warn("[MessageTrace] Redis 轨迹记录被忽略，traceId 为空，messageId={}", trace.getMessageId());
            return;
        }

        try {
            String hashKey = TRACE_KEY_PREFIX + traceId;
            String value = JsonUtils.toJson(trace);
            redisTemplate.opsForHash().put(hashKey, trace.getMessageId(), value);
            redisTemplate.expire(hashKey, ttlMinutes, TimeUnit.MINUTES);

            // 维护 messageId -> traceId 索引
            String indexKey = INDEX_KEY_PREFIX + trace.getMessageId();
            redisTemplate.opsForSet().add(indexKey, traceId);
            redisTemplate.expire(indexKey, ttlMinutes, TimeUnit.MINUTES);

            log.debug("[MessageTrace] Redis 轨迹已记录，messageId={}, traceId={}, status={}",
                    trace.getMessageId(), traceId, trace.getStatus());
        } catch (Exception e) {
            log.error("[MessageTrace] Redis 轨迹记录失败，messageId={}, traceId={}",
                    trace.getMessageId(), trace.getTraceId(), e);
        }
    }

    @Override
    public List<MessageTrace> queryByMessageId(String messageId) {
        if (messageId == null) {
            return Collections.emptyList();
        }

        try {
            String indexKey = INDEX_KEY_PREFIX + messageId;
            Set<Object> traceIds = redisTemplate.opsForSet().members(indexKey);
            if (traceIds == null || traceIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<MessageTrace> result = new ArrayList<>();
            for (Object traceIdObj : traceIds) {
                String traceId = String.valueOf(traceIdObj);
                String hashKey = TRACE_KEY_PREFIX + traceId;
                Object value = redisTemplate.opsForHash().get(hashKey, messageId);
                if (value != null) {
                    try {
                        MessageTrace trace = JsonUtils.fromJson(String.valueOf(value), MessageTrace.class);
                        if (trace != null) {
                            result.add(trace);
                        }
                    } catch (Exception e) {
                        log.warn("[MessageTrace] Redis 轨迹解析失败，messageId={}, traceId={}", messageId, traceId, e);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.error("[MessageTrace] Redis 轨迹查询失败，messageId={}", messageId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<MessageTrace> queryByTraceId(String traceId) {
        if (traceId == null) {
            return Collections.emptyList();
        }

        try {
            String hashKey = TRACE_KEY_PREFIX + traceId;
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(hashKey);
            if (entries == null || entries.isEmpty()) {
                return Collections.emptyList();
            }

            return entries.values().stream()
                    .map(v -> {
                        try {
                            return JsonUtils.fromJson(String.valueOf(v), MessageTrace.class);
                        } catch (Exception e) {
                            log.warn("[MessageTrace] Redis 轨迹解析失败，traceId={}", traceId, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[MessageTrace] Redis 轨迹查询失败，traceId={}", traceId, e);
            return Collections.emptyList();
        }
    }
}
