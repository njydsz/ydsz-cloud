package com.njydsz.common.jdbc.datasource.hint;

import java.util.Optional;

import org.springframework.core.NamedThreadLocal;

/**
 * 强制数据源路由管理器——对标 ShardingSphere 的 HintManager。
 *
 * <p>提供编程式 API 强制某次查询走主库或指定数据源，
 * 优先级高于事务上下文（@Transactional）和 {@code @DS} 注解的路由。
 *
 * <p>使用方式：
 * <pre>{@code
 * // 方式一：手动清理
 * HintManager.masterOnly();
 * try {
 *     // 此处所有数据库操作强制走主库
 *     userService.getById(1L);
 * } finally {
 *     HintManager.clear();
 * }
 *
 * // 方式二：try-with-resources（推荐）
 * try (HintManager.Scope scope = HintManager.masterOnlyScope()) {
 *     userService.getById(1L);  // 强制走主库
 * }
 *
 * // 方式三：指定数据源
 * try (HintManager.Scope scope = HintManager.datasourceScope("report_slave")) {
 *     reportService.queryAll();  // 强制走 report_slave
 * }
 * }</pre>
 *
 * <p><b>线程安全：</b>基于 {@link ThreadLocal} 实现，每个线程独立维护自己的 Hint。
 * 注意：线程池场景下若子线程需继承父线程 Hint，需显式传递。
 *
 * <p><b>实现原理：</b>{@code DynamicRoutingDataSource.determineCurrentLookupKey()}
 * 在路由决策前优先检查当前线程的 Hint，有 Hint 时直接使用 Hint 指定的路由。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.common.jdbc.datasource.DynamicRoutingDataSource
 */
public final class HintManager {

    private HintManager() {
    }

    private static final ThreadLocal<Hint> CURRENT_HINT =
            new NamedThreadLocal<>("Datasource Hint") {
                @Override
                protected Hint initialValue() {
                    return null;
                }
            };

    /**
     * 强制后续数据库操作走主库。
     *
     * <p>等效于 {@code HintManager.set(new Hint(HintType.MASTER))}。
     * 需在操作完成后调用 {@link #clear()} 或使用 try-with-resources 自动清理。
     */
    public static void masterOnly() {
        CURRENT_HINT.set(new Hint(HintType.MASTER));
    }

    /**
     * 强制后续数据库操作走指定名称的数据源。
     *
     * @param dsName 数据源名称，不可为 null 或空
     * @throws IllegalArgumentException dsName 为空时抛出
     */
    public static void datasource(String dsName) {
        if (dsName == null || dsName.isBlank()) {
            throw new IllegalArgumentException("数据源名称不可为空");
        }
        CURRENT_HINT.set(new Hint(HintType.CUSTOM, dsName));
    }

    /**
     * 设置自定义 Hint。
     *
     * @param hint 路由提示，不可为 null
     */
    public static void set(Hint hint) {
        if (hint == null) {
            CURRENT_HINT.remove();
        } else {
            CURRENT_HINT.set(hint);
        }
    }

    /**
     * 清除当前线程的 Hint，恢复默认路由逻辑。
     */
    public static void clear() {
        CURRENT_HINT.remove();
    }

    /**
     * 获取当前线程的 Hint（供 {@code DynamicRoutingDataSource} 内部使用）。
     *
     * @return 当前 Hint，未设置时返回 {@link Optional#empty()}
     */
    public static Optional<Hint> get() {
        return Optional.ofNullable(CURRENT_HINT.get());
    }

    /**
     * 创建强制主库路由的 AutoCloseable Scope，支持 try-with-resources 自动清理。
     *
     * @return Scope 对象，关闭后自动清除 Hint
     */
    public static Scope masterOnlyScope() {
        masterOnly();
        return new DefaultScope();
    }

    /**
     * 创建强制指定数据源路由的 AutoCloseable Scope，支持 try-with-resources 自动清理。
     *
     * @param dsName 数据源名称
     * @return Scope 对象，关闭后自动清除 Hint
     */
    public static Scope datasourceScope(String dsName) {
        datasource(dsName);
        return new DefaultScope();
    }

    /**
     * 创建自定义 Hint 的 AutoCloseable Scope。
     *
     * @param hint 路由提示
     * @return Scope 对象，关闭后自动清除 Hint
     */
    public static Scope hintScope(Hint hint) {
        set(hint);
        return new DefaultScope();
    }

    /**
     * Hint 作用域接口——try-with-resources 的 AutoCloseable 抽象。
     */
    public interface Scope extends AutoCloseable {

        /**
         * 关闭作用域，清除当前线程的 Hint。
         */
        @Override
        void close();
    }

    /**
     * 默认 Scope 实现，关闭时清除 Hint。
     */
    private static class DefaultScope implements Scope {

        @Override
        public void close() {
            clear();
        }
    }
}
