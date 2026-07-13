package com.njydsz.pmis.common.safe.captcha.store;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.pmis.common.safe.captcha.core.CaptchaStore;

/**
 * Redis 分布式验证码存储器
 * 基于 Redis 实现，适用于集群环境
 * 支持自动过期，由 Redis TTL 机制管理，无需定时任务
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
        log.debug("存储验证码: [{}], 过期时间: [{}]s", captchaId, expireSeconds);
    }

    @Override
    public String getAndRemove(String captchaId) {
        String key = CAPTCHA_KEY_PREFIX + captchaId;
        String code = redisTemplate.opsForValue().getAndDelete(key);
        if (code == null) {
            log.warn("验证码不存在或已过期: [{}]", captchaId);
            return null;
        }
        return code;
    }

    @Override
    public String getStoreType() {
        return "REDIS";
    }
}
