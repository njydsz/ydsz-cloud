package com.njydsz.common.excel.tabular;

import java.time.Duration;
import java.util.List;

/**
 * 表格数据读取上下文。
 *
 * <p>贯穿整个读取过程，向监听器传递「当前位置、已读条数、耗时、是否取消」等状态信息。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TabularReadContext {

    /**
     * 当前数据格式。
     */
    TabularFormat format();

    /**
     * 解析后的表头（与 {@link TabularRowMapper#headers()} 一致）。
     */
    List<String> headers();

    /**
     * 当前行号（从 1 开始，表头占第 1 行，数据从第 2 行起；与 Excel 行号约定一致）。
     */
    long currentRowNumber();

    /**
     * 已成功处理的数据行数。
     */
    long processedCount();

    /**
     * 错误行数（被监听器 {@code onError} 捕获的行）。
     */
    long errorCount();

    /**
     * 起始时间戳（{@link System#nanoTime()}）。
     */
    long startNanos();

    /**
     * 已用时。
     */
    default Duration elapsed() {
        return Duration.ofNanos(System.nanoTime() - startNanos());
    }

    /**
     * 取消后续读取（监听器在 onRow / onBatch 中可通过此方法中断）。
     */
    void cancel();
}
