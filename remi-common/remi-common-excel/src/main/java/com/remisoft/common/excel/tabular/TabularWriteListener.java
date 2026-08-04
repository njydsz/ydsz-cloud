package com.remisoft.common.excel.tabular;

/**
 * 表格数据写入监听器（流式回调）。
 *
 * <p>与 {@link com.remisoft.common.excel.core.listener.WriteHandler} 风格一致，
 * 适用于 Excel/CSV/TSV 等所有表格格式的流式写入。
 *
 * @param <T> 写入对象类型
 * @author remi-team
 * @since 1.0.0
 */
public interface TabularWriteListener<T> {

    /**
     * 写入开始时调用（Workbook/Writer 已创建、表头已写入）。
     */
    default void onOpen(TabularWriteContext context) {
    }

    /**
     * 每行数据写入后回调。
     */
    default void onRow(TabularWriteContext context, T data) {
    }

    /**
     * 刷新时回调（缓冲区刷到磁盘后）。
     */
    default void onFlush(TabularWriteContext context) {
    }

    /**
     * 写入异常时回调。
     */
    default void onError(TabularWriteContext context, Throwable error) {
    }

    /**
     * 写入完成、关闭资源前回调。
     */
    default void onClose(TabularWriteContext context) {
    }
}
