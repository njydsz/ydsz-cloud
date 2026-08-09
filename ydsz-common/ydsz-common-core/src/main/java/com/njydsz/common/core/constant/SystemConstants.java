package com.njydsz.common.core.constant;

/**
 * 系统级常量定义。
 *
 * <p>包含系统用户 ID、系统模块名称等全局共享常量。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class SystemConstants {

    private SystemConstants() {
        throw new UnsupportedOperationException("Constants class");
    }

    /** 系统用户 ID（用于标识系统自动操作或无具体用户上下文的场景） */
    public static final String SYSTEM_USER_ID = "SYSTEM";

    /** 系统模块名称 */
    public static final String SYSTEM_MODULE = "system";

    /** 默认租户 ID */
    public static final String DEFAULT_TENANT_ID = "0";

    /**
     * 默认语言（zh-CN）。
     *
     * @deprecated 1.11.0 运行时默认语言改由 {@code ydsz.core.default-locale} 配置（{@link com.njydsz.common.core.config.CoreProperties#getDefaultLocale()}）控制；
     *             此常量仅作编译期兜底。
     */
    @Deprecated(since = "1.11.0", forRemoval = false)
    public static final String DEFAULT_LOCALE = "zh-CN";
}
