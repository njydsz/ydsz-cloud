package com.remisoft.common.safe.captcha.core;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.remisoft.common.safe.captcha.exception.CaptchaException;

/**
 * 验证码频率限制器
 *
 * <p>基于 Redis 实现验证码请求的频率限制，防止验证码接口被恶意刷取：
 * <ul>
 *   <li>同一 IP 每分钟最多请求 10 次验证码</li>
 *   <li>连续验证失败 5 次后锁定该 IP 10 分钟</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * 
 */
public class CaptchaRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(CaptchaRateLimiter.class);

    private static final String REQUEST_RATE_KEY_PREFIX = "captcha:rate:request:";
    private static final String FAIL_LOCK_KEY_PREFIX = "captcha:rate:lock:";

    private static final long REQUEST_RATE_WINDOW_SECONDS = 60;
    private static final int MAX_REQUEST_PER_WINDOW = 10;
    private static final int MAX_FAIL_COUNT = 5;
    private static final long FAIL_LOCK_SECONDS = 600;

    private final StringRedisTemplate redisTemplate;

    public CaptchaRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 检查是否允许请求验证码
     *
     * @param clientIp 客户端 IP
     * @throws CaptchaException 频率超限时抛出
     */
    public void checkRequestRate(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return;
        }

        if (isLocked(clientIp)) {
            throw new CaptchaException("验证码请求过于频繁，请稍后再试");
        }

        String key = REQUEST_RATE_KEY_PREFIX + clientIp;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(REQUEST_RATE_WINDOW_SECONDS));
        }

        if (count != null && count > MAX_REQUEST_PER_WINDOW) {
            log.warn("[CaptchaRateLimiter] IP {} 每分钟请求次数超限 ({})", clientIp, count);
            throw new CaptchaException("验证码请求过于频繁，请稍后再试");
        }
    }

    /**
     * 记录验证失败
     *
     * @param clientIp 客户端 IP
     */
    public void recordFail(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return;
        }

        String key = FAIL_LOCK_KEY_PREFIX + clientIp;
        Long failCount = redisTemplate.opsForValue().increment(key);
        if (failCount != null && failCount == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(FAIL_LOCK_SECONDS));
        }

        if (failCount != null && failCount >= MAX_FAIL_COUNT) {
            log.warn("[CaptchaRateLimiter] IP {} 连续验证失败 {} 次，锁定 {} 秒",
                    clientIp, failCount, FAIL_LOCK_SECONDS);
        }
    }

    /**
     * 重置失败计数（验证成功时调用）
     *
     * @param clientIp 客户端 IP
     */
    public void resetFail(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return;
        }

        String key = FAIL_LOCK_KEY_PREFIX + clientIp;
        redisTemplate.delete(key);
    }

    /**
     * 检查 IP 是否被锁定
     *
     * @param clientIp 客户端 IP
     * @return 是否被锁定
     */
    private boolean isLocked(String clientIp) {
        String key = FAIL_LOCK_KEY_PREFIX + clientIp;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return false;
        }
        try {
            return Long.parseLong(value) >= MAX_FAIL_COUNT;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
