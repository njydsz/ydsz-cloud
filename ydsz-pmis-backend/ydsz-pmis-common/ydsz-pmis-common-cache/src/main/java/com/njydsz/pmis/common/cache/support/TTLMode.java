package com.njydsz.pmis.common.cache.support;

/**
 * TTL 缓存模式枚举
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public enum TTLMode {
    /**
     * 基于写入时间
     */
    WRITE,
    /**
     * 基于访问时间
     */
    ACCESS
}
