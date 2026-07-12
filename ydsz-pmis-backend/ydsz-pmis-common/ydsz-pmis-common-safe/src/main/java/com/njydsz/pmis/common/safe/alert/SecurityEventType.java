package com.njydsz.pmis.common.safe.alert;

/**
 * 安全事件类型枚举
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public enum SecurityEventType {

    /**
     * XSS 攻击检测
     */
    XSS_ATTACK,

    /**
     * SQL 注入检测
     */
    SQL_INJECTION,

    /**
     * CSRF 攻击检测
     */
    CSRF_ATTACK,

    /**
     * 暴力破解检测
     */
    BRUTE_FORCE,

    /**
     * 非法访问检测
     */
    ILLEGAL_ACCESS,

    /**
     * 限流触发
     */
    RATE_LIMIT_TRIGGERED
}
