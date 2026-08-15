package com.njydsz.common.safe.captcha.core;

/**
 * 验证码结果类
 *
 * <p>封装验证码生成后的数据，包括验证码 ID、明文、图片 Base64 等信息。
 * 适用于图形验证码、滑块验证码等多种验证码类型的数据传输。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CaptchaResult {

    /**
     * 验证码唯一标识(用于后续验证)
     */
    private String captchaId;

    /**
     * 验证码明文(用于存储和验证)
     */
    private String captchaCode;

    /**
     * 验证码图片 Base64 编码(图形验证码使用)
     */
    private String imageBase64;

    /**
     * 滑块验证码背景图片 Base64
     */
    private String bgImageBase64;

    /**
     * 滑块验证码缺口位置 X 坐标
     */
    private Integer sliderX;

    /**
     * 验证码过期时间(秒)
     */
    private Long expireTime;

    public CaptchaResult() {
    }

    public CaptchaResult(String captchaId, String captchaCode) {
        this.captchaId = captchaId;
        this.captchaCode = captchaCode;
    }

    public String getCaptchaId() {
        return captchaId;
    }

    public void setCaptchaId(String captchaId) {
        this.captchaId = captchaId;
    }

    public String getCaptchaCode() {
        return captchaCode;
    }

    public void setCaptchaCode(String captchaCode) {
        this.captchaCode = captchaCode;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public String getBgImageBase64() {
        return bgImageBase64;
    }

    public void setBgImageBase64(String bgImageBase64) {
        this.bgImageBase64 = bgImageBase64;
    }

    public Integer getSliderX() {
        return sliderX;
    }

    public void setSliderX(Integer sliderX) {
        this.sliderX = sliderX;
    }

    public Long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Long expireTime) {
        this.expireTime = expireTime;
    }
}
