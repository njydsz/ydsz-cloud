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

    /** 字典缓存名称 */
    public static final String DICT_CACHE = "dict";

    /** 用户按用户名查询缓存名称 */
    public static final String USER_BY_USERNAME_CACHE = "user:username";

    /** 用户按 ID 查询缓存名称 */
    public static final String USER_BY_ID_CACHE = "user:id";

    /** 经营驾驶舱缓存名称 */
    public static final String COCKPIT_CACHE = "cockpit";

    /** 流程定义已发布缓存名称 */
    public static final String FLOW_DEF_PUBLISHED_CACHE = "flow:def:published";

    /** 流程定义最新版本缓存名称 */
    public static final String FLOW_DEF_LATEST_CACHE = "flow:def:latest";

    /** 流程第三方账号按用户缓存名称 */
    public static final String FLOW_THIRDPARTY_BY_USER_CACHE = "flow:thirdparty:user";

    /** 流程第三方账号按 OpenID 缓存名称 */
    public static final String FLOW_THIRDPARTY_BY_OPENID_CACHE = "flow:thirdparty:openid";
}
