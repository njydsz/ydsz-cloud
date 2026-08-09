package com.njydsz.common.json.autotype;

import java.util.Collections;
import java.util.Set;

/**
 * @deprecated AutoType 安全机制已移除（v1.1.0）。内部服务无需此级别反序列化 RCE 防护。
 * 此类作为编译占位保留，无任何功能。请勿使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Deprecated
public final class AutoTypeChecker {

    private AutoTypeChecker() {
    }

    /** @deprecated 已废弃 */
    @Deprecated
    public static boolean isAllowed(Class<?> type) {
        return true;
    }

    /** @deprecated 已废弃 */
    @Deprecated
    public static void addToWhitelist(Class<?> type) {
    }

    /** @deprecated 已废弃 */
    @Deprecated
    public static void addWhitelistPackage(String packageName) {
    }

    /** @deprecated 已废弃 */
    @Deprecated
    public static Set<String> getWhitelist() {
        return Collections.emptySet();
    }

    /** @deprecated 已废弃 */
    @Deprecated
    public static int size() {
        return 0;
    }

    /** @deprecated 已废弃 */
    @Deprecated
    public static void setSafeMode(boolean enabled) {
    }

    /** @deprecated 已废弃 */
    @Deprecated
    public static boolean isSafeMode() {
        return false;
    }
}
