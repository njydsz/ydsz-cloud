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

    /**
     * 标准化页大小。
     *
     * <p>统一分页参数的归一化规则，避免各业务模块重复实现：
     * <ul>
     *   <li>null 或小于 1 → 返回运行时默认值 {@link #getDefaultPageSize()}</li>
     *   <li>大于运行时上限 → 截断为 {@link #getMaxPageSize()}</li>
     *   <li>其余原样返回</li>
     * </ul>
     *
     * @param pageSize 原始页大小（可为 null）
     * @return 标准化后的页大小
     */
    public static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return runtimeDefaultPageSize;
        }
        return Math.min(pageSize, runtimeMaxPageSize);
    }

    /**
     * 标准化页码。
     *
     * <p>统一页码的归一化规则：
     * <ul>
     *   <li>null 或小于 1 → 返回 {@link #DEFAULT_PAGE_NUM}</li>
     *   <li>其余原样返回</li>
     * </ul>
     *
     * @param pageNum 原始页码（可为 null）
     * @return 标准化后的页码
     */
    public static int normalizePageNum(Integer pageNum) {
        return (pageNum == null || pageNum < DEFAULT_PAGE_NUM) ? DEFAULT_PAGE_NUM : pageNum;
    }

    /**
     * 计算偏移量（数据库 LIMIT 的 offset）。
     *
     * <p>统一分页查询的 offset 计算：{@code (pageNum - 1) * pageSize}，
     * 页码和页大小均先经过归一化。</p>
     *
     * @param pageNum  页码（可为 null，按第 1 页处理）
     * @param pageSize 页大小（可为 null，按默认值处理）
     * @return LIMIT 偏移量（long，避免 int 溢出）
     */
    public static long calcOffset(Integer pageNum, Integer pageSize) {
        int normalizedPageNum = normalizePageNum(pageNum);
        int normalizedPageSize = normalizePageSize(pageSize);
        return (long) (normalizedPageNum - 1) * normalizedPageSize;
    }
}
