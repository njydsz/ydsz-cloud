package com.njydsz.common.core.constant;

import com.njydsz.common.core.config.CoreProperties;

/**
 * 分页参数常量。
 *
 * <p>定义分页请求的默认值和上限。
 * 编译期常量（{@link #DEFAULT_PAGE_SIZE} / {@link #MAX_PAGE_SIZE}）仅作为注解等需要
 * 编译时常量场景的固定值；运行时实际值统一从 {@link CoreProperties} 读取，
 * 通过 {@link #init(CoreProperties)} 在启动时注入。</p>
 *
 * <h3>两类值的使用场景</h3>
 * <table>
 *   <tr><th>场景</th><th>使用</th><th>说明</th></tr>
 *   <tr><td>{@code @Max(value = PageConstants.MAX_PAGE_SIZE)}</td><td>编译期常量</td><td>注解需要编译时常量，5000 是安全上限</td></tr>
 *   <tr><td>{@code PageConstants.normalizePageSize(N)}</td><td>运行时值</td><td>归一化到运行时配置的 maxPageSize</td></tr>
 *   <tr><td>{@code PageConstants.getMaxPageSize()}</td><td>运行时值</td><td>返回 ydsz.core.max-page-size 配置值</td></tr>
 * </table>
 *
 * <p><b>初始化：</b>启动时由 {@code CoreAutoConfiguration} 调用 {@link #init(CoreProperties)}
 * 注入运行时配置值，替代旧版 {@code setMaxPageSize()}/{@code setDefaultPageSize()}。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CoreProperties
 */
public final class PageConstants {

    private PageConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ======================== 编译期常量（仅用于注解等场景） ========================

    /** 默认当前页码 */
    public static final int DEFAULT_PAGE_NUM = 1;

    /**
     * 编译期默认每页记录数，用于注解等需要编译时常量的场景。
     * 运行时请使用 {@link #getDefaultPageSize()} 获取配置值。
     */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 编译期最大每页记录数上限（5000），用于 {@code @Max} 等注解的编译时常量。
     * 运行时实际上限由 {@code ydsz.core.max-page-size} 配置控制，请使用 {@link #getMaxPageSize()}。
     */
    public static final int MAX_PAGE_SIZE = 5000;

    // ======================== 运行时值（由 CoreAutoConfiguration 注入） ========================

    /**
     * 运行时配置引用。{@code null} 表示尚未初始化。
     */
    private static volatile CoreProperties properties;

    /**
     * 注入运行时配置。由 {@code CoreAutoConfiguration} 在启动时调用。
     *
     * <p>此方法替代旧版 {@link #setMaxPageSize(int)} / {@link #setDefaultPageSize(int)}，
     * 以 {@link CoreProperties} 作为单一数据源消除全局可变状态。</p>
     *
     * @param coreProperties 已校验通过的 CoreProperties 实例
     * @since 1.5.0
     */
    public static void init(CoreProperties coreProperties) {
        properties = coreProperties;
    }

    /**
     * 获取运行时默认每页记录数。
     *
     * @return 运行时配置的默认每页记录数；未初始化时回退到 {@link #DEFAULT_PAGE_SIZE}
     */
    public static int getDefaultPageSize() {
        return properties != null ? properties.getDefaultPageSize() : DEFAULT_PAGE_SIZE;
    }

    /**
     * 获取运行时最大每页记录数。
     *
     * @return 运行时配置的最大每页记录数；未初始化时回退到 1000
     */
    public static int getMaxPageSize() {
        return properties != null ? properties.getMaxPageSize() : 1000;
    }

    // ======================== 已废弃的 Setter（保留向后兼容，推荐使用 init()） ========================

    /**
     * @deprecated 请使用 {@link #init(CoreProperties)} 替代。
     *             此方法保留仅为向后兼容，将在后续版本移除。
     */
    @Deprecated(since = "1.5.0", forRemoval = true)
    public static void setDefaultPageSize(int defaultPageSize) {
        // 创建一个临时的 CoreProperties 持有默认值和给定的 defaultPageSize
        if (properties == null) {
            properties = CoreProperties.temporary(defaultPageSize, 1000);
        }
    }

    /**
     * @deprecated 请使用 {@link #init(CoreProperties)} 替代。
     *             此方法保留仅为向后兼容，将在后续版本移除。
     */
    @Deprecated(since = "1.5.0", forRemoval = true)
    public static void setMaxPageSize(int maxPageSize) {
        if (properties == null) {
            properties = CoreProperties.temporary(20, maxPageSize);
        }
    }

    // ======================== 归一化工具方法 ========================

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
            return getDefaultPageSize();
        }
        return Math.min(pageSize, getMaxPageSize());
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
