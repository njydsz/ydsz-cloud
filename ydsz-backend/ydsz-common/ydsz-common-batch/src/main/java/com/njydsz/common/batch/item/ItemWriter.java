package com.njydsz.common.batch.item;

import java.util.List;

/**
 * 写出器接口
 *
 * <p>将处理后的数据写入目标存储（DB / File / API / Queue）。
 *
 * @param <T> 数据项类型
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ItemWriter<T> {

    /**
     * 写入单条数据
     */
    void write(T item) throws Exception;

    /**
     * 批量写入（默认实现为循环 write()，建议实现批量优化）
     *
     * @param items 数据项列表
     */
    default void writeBatch(List<T> items) throws Exception {
        for (T item : items) {
            write(item);
        }
    }

    /**
     * 刷新缓冲区（用于流式写）
     */
    default void flush() throws Exception {
    }
}
