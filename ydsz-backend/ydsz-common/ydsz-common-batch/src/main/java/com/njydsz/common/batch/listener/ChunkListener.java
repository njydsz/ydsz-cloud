package com.njydsz.common.batch.listener;

import java.util.List;

/**
 * Chunk 监听器（在每个 chunk 完成后触发）
 *
 * @param <T> 读取数据类型
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ChunkListener<T> {

    /**
     * Chunk 写入前
     */
    default void beforeChunk(List<T> items) {
    }

    /**
     * Chunk 写入后
     */
    default void afterChunk(List<T> items) {
    }

    /**
     * Chunk 错误
     */
    default void onChunkError(List<T> items, Throwable ex) {
    }
}
