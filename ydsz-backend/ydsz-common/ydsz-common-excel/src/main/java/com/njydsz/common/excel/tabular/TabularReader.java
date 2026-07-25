package com.njydsz.common.excel.tabular;

import java.io.Closeable;

/**
 * 表格数据读取器（统一接口）。
 *
 * <p>屏蔽 Excel/CSV/TSV 差异，提供一致的流式读取 API。
 * 实现类必须保证 {@link #open()} 之后才能调用 {@link #readAll(TabularReadListener)}，
 * 调用 {@link #close()} 之后流不可再使用。
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * try (TabularReader<User> reader = CsvReader.<User>builder()
 *         .file(new File("users.csv"))
 *         .mapper(new DefaultAnnotationRowMapper<>(User.class))
 *         .build()) {
 *     reader.open();
 *     reader.readAll((ctx, user) -> System.out.println(user));
 * } // 自动 close
 * }</pre>
 *
 * @param <T> 目标对象类型
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TabularReader<T> extends Closeable {

    /**
     * 打开资源（解析表头、初始化迭代器）。
     */
    void open();

    /**
     * 读取所有行（流式回调）。
     *
     * @param listener 行监听器
     */
    void readAll(TabularReadListener<T> listener);

    /**
     * 关闭资源。
     */
    @Override
    void close();

    /**
     * 数据格式。
     */
    TabularFormat format();
}
