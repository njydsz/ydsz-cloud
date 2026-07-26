package com.njydsz.common.safe.captcha.core;

/**
 * 验证码存储器接口
 *
 * <p>定义验证码数据的存储与读取行为，支持多种存储方式（本地内存、Redis 等）。
 * 验证码在验证后立即删除，防止重放攻击。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface CaptchaStore {

    /**
     * 存储验证码
     *
     * @param captchaId 验证码 ID
     * @param captchaCode 验证码明文
     * @param expireSeconds 过期时间(秒)
     */
    void store(String captchaId, String captchaCode, long expireSeconds);

    /**
     * 获取并删除验证码
     * 验证后立即删除,防止重放攻击
     *
     * @param captchaId 验证码 ID
     * @return 验证码明文,如果不存在或已过期则返回 null
     */
    String getAndRemove(String captchaId);

    /**
     * 获取存储类型
     *
     * @return 存储类型
     */
    String getStoreType();
}
