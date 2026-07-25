package com.njydsz.common.excel.tabular;

import java.io.Closeable;
import java.util.List;

/**
 * 表格数据写入器（统一接口）。
 *
 * <p>屏蔽 Excel/CSV/TSV 差异，提供一致的流式写入 API。
 * 实现类必须保证 {@link #open()} 之后才能调用 {@link #write(Object)}，
 * 写入完成后必须调用 {@link #close()} 以释放资源（try-with-resources 自动调用）。
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * try (TabularWriter<User> writer = CsvWriter.<User>builder()
 *         .file(new File("users.csv"))
 *         .mapper(new DefaultAnnotationRowMapper<>(User.class))
 *         .build()) {
 *     writer.open();
 *     writer.writeAll(users);
 * } // 自动 flush + close
 * }</pre>
 *
 * @param <T> 写入对象类型
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TabularWriter<T> extends Closeable {

    /**
     * 打开资源（创建 Workbook/Writer、写入表头）。
     */
    void open();

    /**
     * 写入单行。
     */
    void write(T data);

    /**
     * 写入一批行（流式，逐行调用 {@link #write(Object)}）。
     */
    void writeAll(List<T> dataList);

    /**
     * 强制刷写缓冲到磁盘。
     */
    void flush();

    /**
     * 关闭资源（自动 flush）。
     */
    @Override
    void close();

    /**
     * 数据格式。
     */
    TabularFormat format();
}
