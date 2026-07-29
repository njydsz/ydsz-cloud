package com.njydsz.common.core.constant;

import com.njydsz.common.core.config.CoreProperties;

/**
 * 分页参数常量
 *
 * <p>定义分页请求的默认值和上限。
 * 编译期常量（{@link #DEFAULT_PAGE_SIZE} / {@link #MAX_PAGE_SIZE}）仅作为注解用编译时常量，
 * 运行时实际值由 {@link CoreProperties} 配置覆盖后通过 {@link #setMaxPageSize(int)} /
 * {@link #setDefaultPageSize(int)} 同步到本类的运行时覆盖值。
 *
 * <p><b>配置覆盖：</b>
 * <ul>
 *   <li>{@code ydsz.core.max-page-size} 覆盖 {@link #getMaxPageSize()}</li>
 *   <li>{@code ydsz.core.default-page-size} 覆盖 {@link #getDefaultPageSize()}</li>
 * </ul>
 *
 * <p><b>注意：</b>当需要运行时值时，请使用 {@link #getDefaultPageSize()} / {@link #getMaxPageSize()}，
 * 而非直接引用 {@code DEFAULT_PAGE_SIZE} / {@code MAX_PAGE_SIZE} 常量（编译期常量会被内联，不受配置影响）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 * @see CoreProperties
 */
public final class PageConstants {

    private PageConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 默认当前页码 */
    public static final int DEFAULT_PAGE_NUM = 1;

    /**
     * 编译期默认每页记录数，用于注解等需要编译时常量的场景。
     * 运行时请使用 {@link #getDefaultPageSize()}。
     */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 编译期最大每页记录数上限（5000），用于注解等需要编译时常量的场景。
     * 运行时实际上限由 {@code ydsz.core.max-page-size} 配置控制，请使用 {@link #getMaxPageSize()}。
     */
    public static final int MAX_PAGE_SIZE = 5000;

    /** 运行时默认每页记录数覆盖值（由 CoreAutoConfiguration 从 CoreProperties 注入） */
    private static volatile int runtimeDefaultPageSize = 20;

    /** 运行时最大每页记录数覆盖值（由 CoreAutoConfiguration 从 CoreProperties 注入） */
    private static volatile int runtimeMaxPageSize = 1000;

    /**
     * 获取运行时默认每页记录数
     *
     * @return 运行时配置的默认每页记录数
     */
    public static int getDefaultPageSize() {
        return runtimeDefaultPageSize;
    }

    /**
     * 获取运行时最大每页记录数
     *
     * @return 运行时配置的最大每页记录数
     */
    public static int getMaxPageSize() {
        return runtimeMaxPageSize;
    }

    /**
     * 设置运行时默认每页记录数（由 CoreAutoConfiguration 在启动时调用）
     *
     * @param defaultPageSize 配置的默认每页记录数
     */
    public static void setDefaultPageSize(int defaultPageSize) {
        runtimeDefaultPageSize = defaultPageSize;
    }

    /**
     * 设置运行时最大每页记录数（由 CoreAutoConfiguration 在启动时调用）
     *
     * @param maxPageSize 配置的最大每页记录数
     */
    public static void setMaxPageSize(int maxPageSize) {
        runtimeMaxPageSize = maxPageSize;
    }
}
