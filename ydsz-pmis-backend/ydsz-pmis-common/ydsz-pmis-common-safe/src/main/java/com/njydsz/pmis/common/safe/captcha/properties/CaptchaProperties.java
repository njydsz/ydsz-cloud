package com.njydsz.pmis.common.safe.captcha.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.pmis.common.safe.captcha.enums.CaptchaStoreType;
import com.njydsz.pmis.common.safe.captcha.enums.CaptchaType;

/**
 * 验证码配置属性类
 * 用于读取 application.yml 中的 ydsz.safe.captcha.* 配置项
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@ConfigurationProperties(prefix = "ydsz.safe.captcha")
public class CaptchaProperties {

    /**
     * 是否启用验证码功能
     */
    private boolean enabled = true;

    /**
     * 默认验证码类型
     */
    private CaptchaType defaultType = CaptchaType.IMAGE;

    /**
     * 存储类型
     */
    private CaptchaStoreType storeType = CaptchaStoreType.LOCAL;

    /**
     * 验证码长度(图形验证码)
     */
    private int length = 4;

    /**
     * 图片宽度(图形验证码)
     */
    private int width = 120;

    /**
     * 图片高度(图形验证码)
     */
    private int height = 40;

    /**
     * 验证码过期时间(秒)
     */
    private long expireSeconds = 120;

    /**
     * 滑块容差范围(像素)
     */
    private int sliderTolerance = 5;

    /**
     * Redis Key 前缀
     */
    private String redisKeyPrefix = "captcha:";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public CaptchaType getDefaultType() {
        return defaultType;
    }

    public void setDefaultType(CaptchaType defaultType) {
        this.defaultType = defaultType;
    }

    public CaptchaStoreType getStoreType() {
        return storeType;
    }

    public void setStoreType(CaptchaStoreType storeType) {
        this.storeType = storeType;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(long expireSeconds) {
        this.expireSeconds = expireSeconds;
    }

    public int getSliderTolerance() {
        return sliderTolerance;
    }

    public void setSliderTolerance(int sliderTolerance) {
        this.sliderTolerance = sliderTolerance;
    }

    public String getRedisKeyPrefix() {
        return redisKeyPrefix;
    }

    public void setRedisKeyPrefix(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix;
    }
}
