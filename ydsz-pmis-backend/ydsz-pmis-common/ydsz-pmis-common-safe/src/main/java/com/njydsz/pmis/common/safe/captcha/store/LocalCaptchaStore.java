package com.njydsz.pmis.common.safe.captcha.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.safe.captcha.core.CaptchaStore;
import com.njydsz.pmis.common.util.concurrent.ExecutorUtils;

/**
 * 本地内存验证码存储器
 * 基于 ConcurrentHashMap 实现,适用于单机环境
 * 支持自动过期清理
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class LocalCaptchaStore implements CaptchaStore {

    private static final Logger log = LoggerFactory.getLogger(LocalCaptchaStore.class);

    /**
     * 验证码存储映射
     */
    private final Map<String, CaptchaData> store = new ConcurrentHashMap<>();

    /**
     * 定时清理任务
     */
    private final ScheduledExecutorService scheduler;

    /**
     * 构建本地验证码存储器
     * 启动定时清理任务,每 60 秒清理过期验证码
     */
    public LocalCaptchaStore() {
        this.scheduler = ExecutorUtils.newScheduledThreadPool(1, "captcha-cleaner");
        this.scheduler.scheduleAtFixedRate(this::cleanExpired, 60, 60, TimeUnit.SECONDS);
    }

    @Override
    public void store(String captchaId, String captchaCode, long expireSeconds) {
        long expireAt = System.currentTimeMillis() + expireSeconds * 1000;
        store.put(captchaId, new CaptchaData(captchaCode, expireAt));
        log.debug("存储验证码: [{}], 过期时间: [{}]s", captchaId, expireSeconds);
    }

    @Override
    public String getAndRemove(String captchaId) {
        CaptchaData data = store.remove(captchaId);
        if (data == null) {
            log.warn("验证码不存在或已被删除: [{}]", captchaId);
            return null;
        }
        if (System.currentTimeMillis() > data.expireAt) {
            log.warn("验证码已过期: [{}]", captchaId);
            return null;
        }
        return data.captchaCode;
    }

    @Override
    public String getStoreType() {
        return "LOCAL";
    }

    /**
     * 清理过期验证码
     */
    private void cleanExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(entry -> {
            if (now > entry.getValue().expireAt) {
                log.debug("清理过期验证码: [{}]", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * 验证码数据类
     */
    private static class CaptchaData {
        private final String captchaCode;
        private final long expireAt;

        CaptchaData(String captchaCode, long expireAt) {
            this.captchaCode = captchaCode;
            this.expireAt = expireAt;
        }
    }
}
