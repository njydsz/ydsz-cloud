package com.njydsz.common.json.autotype;

import java.util.Collections;
import java.util.Set;

/**
 * 编译期最小桩（仅用于脱离 Spring 的核心子集编译/验证）。
 *
 * <p>真实实现依赖 Spring 上下文（白名单扫描），此处仅复刻公开静态方法签名，
 * 默认安全模式关闭（允许所有类型），足以让核心引擎 + provider + reader 编译通过，
 * 并运行硬化验证 harness。生产运行使用真实实现，请勿将此桩打进正式产物。</p>
 */
public final class AutoTypeChecker {

    private static volatile boolean safeMode = false;

    private AutoTypeChecker() {}

    public static void checkType(Class<?> clazz) {
        // 桩：安全模式关闭，全部放行
    }

    public static void checkType(String className) {
        // 桩：安全模式关闭，全部放行
    }

    public static boolean isTypeAllowed(Class<?> clazz) {
        return true;
    }

    public static boolean isTypeAllowed(String className) {
        return true;
    }

    public static void addToWhitelist(String className) {}

    public static void removeFromWhitelist(String className) {}

    public static void addToBlacklist(String className) {}

    public static void removeFromBlacklist(String className) {}

    public static void addToBlacklist(String... classNames) {}

    public static void addToWhitelist(String... classNames) {}

    public static void setSafeMode(boolean enabled) {
        safeMode = enabled;
    }

    public static boolean isSafeMode() {
        return safeMode;
    }

    public static Set<String> getExplicitWhitelist() {
        return Collections.emptySet();
    }

    public static Set<String> getBuiltinWhitelist() {
        return Collections.emptySet();
    }

    public static Set<String> getAnnotationWhitelist() {
        return Collections.emptySet();
    }

    public static Set<String> getBuiltinBlacklist() {
        return Collections.emptySet();
    }

    public static Set<String> getExplicitBlacklist() {
        return Collections.emptySet();
    }

    public static void addWhitelistPackage(String packageName) {}

    public static int getTypeCheckCacheSize() {
        return 0;
    }

    public static void clearCache() {}

    public static void reset() {
        safeMode = false;
    }
}
