package com.njydsz.common.core.constant;

import java.util.concurrent.atomic.AtomicReference;

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
     * 运行时配置引用（AtomicReference 保证线程安全和一次性设置）。
     *
     * <p>启动后由 {@code CoreAutoConfiguration} 注入，采用一次性设置语义，
     * 确保分页配置在应用生命周期内不可变。</p>
     */
    private static final AtomicReference<CoreProperties> PROPERTIES = new AtomicReference<>();

    /**
     * 注入运行时配置。由 {@code CoreAutoConfiguration} 在启动时调用。
     *
     * <p>采用一次性设置语义，重复调用将抛出 IllegalStateException。</p>
     *
     * @param coreProperties 已校验通过的 CoreProperties 实例
     * @throws IllegalStateException 如果配置已被初始化
     * @since 1.6.0
     */
    public static void init(CoreProperties coreProperties) {
        if (coreProperties == null) {
            throw new IllegalArgumentException("CoreProperties must not be null");
        }
        if (!PROPERTIES.compareAndSet(null, coreProperties)) {
            throw new IllegalStateException("PageConstants already initialized");
        }
    }

    /**
     * 获取运行时默认每页记录数。
     *
     * @return 运行时配置的默认每页记录数；未初始化时回退到 {@link #DEFAULT_PAGE_SIZE}
     */
    public static int getDefaultPageSize() {
        CoreProperties p = PROPERTIES.get();
        return p != null ? p.getDefaultPageSize() : DEFAULT_PAGE_SIZE;
    }

    /**
     * 获取运行时最大每页记录数。
     *
     * @return 运行时配置的最大每页记录数；未初始化时回退到 1000
     */
    public static int getMaxPageSize() {
        CoreProperties p = PROPERTIES.get();
        return p != null ? p.getMaxPageSize() : 1000;
    }

    // ======================== 归一化工具方法 ========================

    /**
     * 标准化 offset（偏移量）。
     *
     * <p>适用于 offset/limit 分页模式（常用于大数据量深度分页场景）。
     * 统一 offset 的归一化规则：
     * <ul>
     *   <li>null 或小于 0 → 返回 0</li>
     *   <li>其余原样返回</li>
     * </ul>
     *
     * @param offset 原始偏移量（可为 null）
     * @return 标准化后的偏移量
     * @since 1.6.0
     */
    public static long normalizeOffset(Long offset) {
        return offset == null || offset < 0 ? 0L : offset;
    }

    /**
     * 标准化 limit（返回记录数）。
     *
     * <p>适用于 offset/limit 分页模式。统一的 limit 归一化规则：
     * <ul>
     *   <li>null 或小于 1 → 返回运行时默认值 {@link #getDefaultPageSize()}</li>
     *   <li>大于运行时上限 → 截断为 {@link #getMaxPageSize()}</li>
     *   <li>其余原样返回</li>
     * </ul>
     *
     * @param limit 原始返回记录数（可为 null）
     * @return 标准化后的返回记录数
     * @since 1.6.0
     */
    public static int normalizeLimit(Long limit) {
        if (limit == null || limit < 1) {
            return getDefaultPageSize();
        }
        return (int) Math.min(limit, getMaxPageSize());
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
            return getDefaultPageSize();
        }
        return Math.min(pageSize, getMaxPageSize());
    }

    /**
     * 标准化页大小并返回是否被归一化的结果。
     *
     * <p>与 {@link #normalizePageSize(Integer)} 语义一致，但额外返回归一化是否发生，
     * 便于调用方在响应中标记"分页参数已被框架调整"。</p>
     *
     * @param pageSize 原始页大小（可为 null）
     * @return 包含归一化结果和是否被调整标记的 NormalizeResult
     * @since 1.7.0
     */
    public static NormalizeResult normalizePageSizeWithResult(Integer pageSize) {
        int raw = (pageSize == null || pageSize < 1) ? 0 : pageSize;
        int normalized = normalizePageSize(pageSize);
        boolean adjusted = raw != normalized;
        return new NormalizeResult(normalized, adjusted);
    }

    /**
     * 归一化结果封装。
     *
     * @since 1.7.0
     */
    public static final class NormalizeResult {
        private final int value;
        private final boolean adjusted;

        private NormalizeResult(int value, boolean adjusted) {
            this.value = value;
            this.adjusted = adjusted;
        }

        /** 获取归一化后的值。 */
        public int getValue() {
            return value;
        }

        /** 判断是否发生了归一化调整（原始值被截断或替换）。 */
        public boolean isAdjusted() {
            return adjusted;
        }
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

    /**
     * 从 offset 反算页码（用于 offset/limit 分页模式）。
     *
     * <p>计算公式：{@code pageNum = (offset / pageSize) + 1}
     * 当 offset 不能被 pageSize 整除时，向上取整。</p>
     *
     * @param offset   偏移量（可为 null，按 0 处理）
     * @param pageSize 页大小（可为 null，按默认值处理）
     * @return 计算出的页码（从 1 开始）
     * @since 1.6.0
     */
    public static int calcPageNum(Long offset, Integer pageSize) {
        long normalizedOffset = normalizeOffset(offset);
        int normalizedPageSize = normalizePageSize(pageSize);
        return (int) (normalizedOffset / normalizedPageSize) + 1;
    }

    /**
     * 计算总页数（用于 offset/limit 分页场景下返回分页元信息）。
     *
     * <p>计算公式：{@code (total + pageSize - 1) / pageSize}</p>
     *
     * @param total    总记录数
     * @param pageSize 页大小（可为 null，按默认值处理）
     * @return 总页数
     * @since 1.6.0
     */
    public static long calcTotalPages(long total, Integer pageSize) {
        if (total <= 0) {
            return 0;
        }
        int normalizedPageSize = normalizePageSize(pageSize);
        return (total + normalizedPageSize - 1) / normalizedPageSize;
    }

    /**
     * 判断偏移量是否安全（不会导致深度分页性能问题）。
     *
     * <p>当 offset 超过 {@link #MAX_SAFE_OFFSET} 时，建议改用
     * 游标分页（cursor-based pagination）或其他优化方案。</p>
     *
     * @param offset 偏移量（可为 null，按 0 处理）
     * @return true=安全，false=可能导致性能问题
     * @since 1.6.0
     */
    public static boolean isOffsetSafe(Long offset) {
        long normalizedOffset = normalizeOffset(offset);
        return normalizedOffset <= MAX_SAFE_OFFSET;
    }

    /**
     * 最大安全偏移量阈值（10000）。
     *
     * <p>超过此值的深度分页在 PostgreSQL/MySQL 中可能导致性能急剧下降，
     * 建议改用 WHERE id > lastId 的游标模式。
     *
     * @since 1.6.0
     */
    public static final long MAX_SAFE_OFFSET = 10000L;
}
