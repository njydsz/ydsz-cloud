package com.njydsz.agent.server.chat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.njydsz.common.exception.custom.DuplicateException;
import com.njydsz.common.exception.custom.RateLimitException;

/**
 * Agent 请求守卫：幂等去重 + 限流
 *
 * <p>在 LLM 调用前进行前置检查，防止：
 * <ul>
 *   <li>重复请求（前端双击/网络重试）导致重复扣费</li>
 *   <li>恶意刷接口导致 LLM API Key 配额耗尽</li>
 * </ul>
 *
 * <h3>幂等去重</h3>
 * <p>基于 Redis SETNX，key = {@code ydsz:agent:idem:{requestId}}，TTL 60s。
 * 同一 requestId 60 秒内只能成功调用一次。
 *
 * <h3>限流</h3>
 * <p>基于 Redis 滑动窗口计数，key = {@code ydsz:agent:rate:{userId}}，
 * 默认 10 QPM（每分钟 10 次）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class AgentRequestGuard {

    private static final Logger log = LoggerFactory.getLogger(AgentRequestGuard.class);

    private static final String IDEM_KEY_PREFIX = "ydsz:agent:idem:";
    private static final String RATE_KEY_PREFIX = "ydsz:agent:rate:";
    private static final Duration IDEM_TTL = Duration.ofSeconds(60);
    private static final int MAX_REQUESTS_PER_MINUTE = 10;
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;

    public AgentRequestGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 检查请求是否允许执行（幂等 + 限流）
     *
     * @param requestId 请求幂等键（null 则跳过幂等检查）
     * @param userId    用户 ID（null 则用 "anonymous"）
     * @throws DuplicateException 重复请求
     * @throws RateLimitException 请求超限
     */
    public void check(String requestId, String userId) {
        String effectiveUserId = userId != null ? userId : "anonymous";
        checkRateLimit(effectiveUserId);
        if (requestId != null && !requestId.isBlank()) {
            checkIdempotent(requestId);
        }
    }

    /**
     * 幂等检查：SETNX，已存在则拒绝
     */
    private void checkIdempotent(String requestId) {
        String key = IDEM_KEY_PREFIX + requestId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", IDEM_TTL.toSeconds(), TimeUnit.SECONDS);
        if (acquired == null || !acquired) {
            log.warn("[Agent-Guard] 重复请求被拒绝: requestId={}", requestId);
            throw new DuplicateException("重复请求，请勿在 60 秒内重复提交");
        }
    }

    /**
     * 限流检查：滑动窗口计数
     *
     * <p>使用 Redis INCR + EXPIRE 实现固定窗口计数。
     * 窗口内首次请求设置 TTL，后续请求递增计数。
     */
    private void checkRateLimit(String userId) {
        String key = RATE_KEY_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, RATE_WINDOW.toSeconds(), TimeUnit.SECONDS);
        }
        if (count != null && count > MAX_REQUESTS_PER_MINUTE) {
            log.warn("[Agent-Guard] 限流触发: userId={}, count={}", userId, count);
            throw new RateLimitException("请求过于频繁，每分钟最多 " + MAX_REQUESTS_PER_MINUTE + " 次");
        }
    }

    /**
     * 释放幂等锁（业务异常时调用，允许重试）
     */
    public void releaseIdempotent(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        String key = IDEM_KEY_PREFIX + requestId;
        redisTemplate.delete(key);
    }
}
