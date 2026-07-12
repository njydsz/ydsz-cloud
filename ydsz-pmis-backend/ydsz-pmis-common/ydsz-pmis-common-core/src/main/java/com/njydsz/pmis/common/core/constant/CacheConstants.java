package com.njydsz.pmis.common.core.constant;

/**
 * 缓存键值常量
 *
 * <p>定义系统中缓存相关的键前缀常量，所有缓存键统一使用 {@code ydsz:} 前缀，
 * 便于在 Redis 等缓存系统中统一管理和清理。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public final class CacheConstants {

    private CacheConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 通用缓存键前缀 */
    public static final String CACHE_PREFIX = "ydsz:";
}
