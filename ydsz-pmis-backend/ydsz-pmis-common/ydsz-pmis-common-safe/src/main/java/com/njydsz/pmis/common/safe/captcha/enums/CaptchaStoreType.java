package com.njydsz.pmis.common.safe.captcha.enums;

/**
 * 验证码存储类型枚举
 *
 * <p>定义验证码数据的存储方式，支持本地内存存储和 Redis 分布式存储。
 * 单机环境可使用 LOCAL，集群环境建议使用 REDIS。
 *
 * @since 1.0.0
 * 
 */
public enum CaptchaStoreType {

    /**
     * 本地内存存储(适用于单机环境)
     */
    LOCAL,

    /**
     * Redis 分布式存储(适用于集群环境)
     */
    REDIS
}
