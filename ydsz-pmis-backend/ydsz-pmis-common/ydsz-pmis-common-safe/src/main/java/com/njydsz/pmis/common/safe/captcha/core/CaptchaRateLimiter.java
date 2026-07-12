package com.njydsz.pmis.common.safe.captcha.core;

import com.njydsz.pmis.common.safe.captcha.exception.CaptchaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 楠岃瘉鐮侀鐜囬檺鍒跺櫒
 *
 * <p>鍩轰簬 Redis 瀹炵幇楠岃瘉鐮佽姹傜殑棰戠巼闄愬埗锛岄槻姝㈤獙璇佺爜鎺ュ彛琚伓鎰忓埛鍙栵細
 * <ul>
 *   <li>鍚屼竴 IP 姣忓垎閽熸渶澶氳姹?10 娆￠獙璇佺爜</li>
 *   <li>杩炵画楠岃瘉澶辫触 5 娆″悗閿佸畾璇?IP 10 鍒嗛挓</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
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
     * 妫€鏌ユ槸鍚﹀厑璁歌姹傞獙璇佺爜
     *
     * @param clientIp 瀹㈡埛绔?IP
     * @throws CaptchaException 棰戠巼瓒呴檺鏃舵姏鍑?
     */
    public void checkRequestRate(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return;
        }

        if (isLocked(clientIp)) {
            throw new CaptchaException("楠岃瘉鐮佽姹傝繃浜庨绻侊紝璇风◢鍚庡啀璇?);
        }

        String key = REQUEST_RATE_KEY_PREFIX + clientIp;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(REQUEST_RATE_WINDOW_SECONDS));
        }

        if (count != null && count > MAX_REQUEST_PER_WINDOW) {
            log.warn("[CaptchaRateLimiter] IP {} 姣忓垎閽熻姹傛鏁拌秴闄?({})", clientIp, count);
            throw new CaptchaException("楠岃瘉鐮佽姹傝繃浜庨绻侊紝璇风◢鍚庡啀璇?);
        }
    }

    /**
     * 璁板綍楠岃瘉澶辫触
     *
     * @param clientIp 瀹㈡埛绔?IP
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
            log.warn("[CaptchaRateLimiter] IP {} 杩炵画楠岃瘉澶辫触 {} 娆★紝閿佸畾 {} 绉?,
                    clientIp, failCount, FAIL_LOCK_SECONDS);
        }
    }

    /**
     * 閲嶇疆澶辫触璁℃暟锛堥獙璇佹垚鍔熸椂璋冪敤锛?
     *
     * @param clientIp 瀹㈡埛绔?IP
     */
    public void resetFail(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return;
        }

        String key = FAIL_LOCK_KEY_PREFIX + clientIp;
        redisTemplate.delete(key);
    }

    /**
     * 妫€鏌?IP 鏄惁琚攣瀹?
     *
     * @param clientIp 瀹㈡埛绔?IP
     * @return 鏄惁琚攣瀹?
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
