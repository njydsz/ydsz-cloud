package com.njydsz.pmis.common.cache.listener;

/**
 * 缓存删除原因枚举
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public enum RemovalCause {
    /**
     * 显式删除
     */
    EXPLICIT,
    /**
     * 被替换
     */
    REPLACED,
    /**
     * 被 GC 回收
     */
    COLLECTED,
    /**
     * 过期
     */
    EXPIRED,
    /**
     * 超出容量限制
     */
    SIZE
}
