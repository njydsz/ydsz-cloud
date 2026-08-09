package com.njydsz.common.json;

import java.util.Collections;
import java.util.Set;

/**
 * JSON 安全工具类。
 *
 * @deprecated AutoType 安全机制已移除（v1.1.0）。内部服务无需此级别反序列化 RCE 防护。
 * 此类作为编译占位保留，所有方法均为空实现。请勿使用。
 *
 * @author ydsz-team
 * @since 1.2.0
 * @deprecated since 1.1.0
 */
@Deprecated
public final class JsonSecurityUtils {

    private JsonSecurityUtils() {
    }

    /** @deprecated 已废弃，始终返回 true */
    @Deprecated
    public static boolean isTypeAllowed(Class<?> type) {
        return true;
    }

    /** @deprecated 已废弃，空实现 */
    @Deprecated
    public static void registerCacheTypes(Class<?>... types) {
    }

    /** @deprecated 已废弃，空实现 */
    @Deprecated
    public static void registerMessageTypes(Class<?>... types) {
    }

    /** @deprecated 已废弃，空实现 */
    @Deprecated
    public static void validateJsonForDeserialization(String json, Class<?> targetType) {
    }

    /** @deprecated 已废弃，始终返回空集合 */
    @Deprecated
    public static Set<String> getRegisteredTypes() {
        return Collections.emptySet();
    }

    /** @deprecated 已废弃，始终返回 0 */
    @Deprecated
    public static int getRegisteredTypeCount() {
        return 0;
    }
}
