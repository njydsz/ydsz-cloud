package com.njydsz.pmis.common.safe.captcha.store;

import com.njydsz.pmis.common.safe.captcha.core.CaptchaStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Redis 鍒嗗竷寮忛獙璇佺爜瀛樺偍鍣?
 * 鍩轰簬 Redis 瀹炵幇锛岄€傜敤浜庨泦缇ょ幆澧?
 * 鏀寔鑷姩杩囨湡锛岀敱 Redis TTL 鏈哄埗绠＄悊锛屾棤闇€瀹氭椂浠诲姟
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class RedisCaptchaStore implements CaptchaStore {

    private static final Logger log = LoggerFactory.getLogger(RedisCaptchaStore.class);
    private static final String CAPTCHA_KEY_PREFIX = "captcha:";

    private final StringRedisTemplate redisTemplate;

    public RedisCaptchaStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void store(String captchaId, String captchaCode, long expireSeconds) {
        String key = CAPTCHA_KEY_PREFIX + captchaId;
        redisTemplate.opsForValue().set(key, captchaCode, Duration.ofSeconds(expireSeconds));
        log.debug("瀛樺偍楠岃瘉鐮? [{}], 杩囨湡鏃堕棿: [{}]s", captchaId, expireSeconds);
    }

    @Override
    public String getAndRemove(String captchaId) {
        String key = CAPTCHA_KEY_PREFIX + captchaId;
        String code = redisTemplate.opsForValue().getAndDelete(key);
        if (code == null) {
            log.warn("楠岃瘉鐮佷笉瀛樺湪鎴栧凡杩囨湡: [{}]", captchaId);
            return null;
        }
        return code;
    }

    @Override
    public String getStoreType() {
        return "REDIS";
    }
}
