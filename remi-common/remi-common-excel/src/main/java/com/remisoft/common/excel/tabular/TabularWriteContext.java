package com.remisoft.common.excel.tabular;

import java.time.Duration;

/**
 * 表格数据写入上下文。
 *
 * @author remi-team
 * @since 1.0.0
 */
public interface TabularWriteContext {

    /**
     * 当前数据格式。
     */
    TabularFormat format();

    /**
     * 已写入的数据行数。
     */
    long writtenCount();

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
}
