package com.remisoft.common.excel.tabular;

import java.util.List;

/**
 * 表格数据读取监听器（流式回调）。
 *
 * <p>与 {@link com.remisoft.common.excel.core.listener.ReadListener} 风格一致，
 * 适用于 Excel/CSV/TSV 等所有表格格式的流式处理。
 *
 * <h2>回调顺序</h2>
 * <pre>
 *   onOpen()   →  onRow() * N  →  onClose()
 *                       ↓
 *                  onError() (异常时)
 * </pre>
 *
 * @param <T> 目标对象类型
 * @author remi-team
 * @since 1.0.0
 */
public interface TabularReadListener<T> {

    /**
     * 读取开始时调用（资源已就绪、表头已解析）。
     */
    default void onOpen(TabularReadContext context) {
    }

    /**
     * 每行数据回调（流式逐行）。
     */
    void onRow(TabularReadContext context, T data);

    /**
     * 批量数据回调（当读取器配置 batchSize > 0 时，攒批后回调一次）。
     *
     * <p>默认实现：遍历批次逐个调用 {@link #onRow}。
     */
    default void onBatch(TabularReadContext context, List<T> batch) {
        for (T item : batch) {
            onRow(context, item);
        }
    }

    /**
     * 读取异常时调用。
     */
    default void onError(TabularReadContext context, Throwable error) {
    }

    /**
     * 读取结束时调用（资源即将释放）。
     */
    default void onClose(TabularReadContext context) {
    }
}
