package com.njydsz.common.batch.item;

import java.util.List;

/**
 * 读取器接口
 *
 * <p>从数据源（DB / File / API / Queue）逐条或批量读取数据。
 * 与 Spring Batch 的 {@code ItemReader} / {@code ItemStream} 概念一致。
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * public class UserCsvReader implements ItemReader<User> {
 *     {@code @Override}
 *     public User read() {
 *         // 读取下一条，无数据返回 null
 *     }
 * }
 * }</pre>
 *
 * @param <T> 数据项类型
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ItemReader<T> {

    /**
     * 读取单条数据
     *
     * @return 数据项，无更多数据时返回 null
     * @throws Exception 读取异常
     */
    T read() throws Exception;

    /**
     * 批量读取（默认实现为循环 read()）
     *
     * @param limit 最大读取数
     * @return 数据项列表
     */
    default List<T> readBatch(int limit) throws Exception {
        java.util.List<T> result = new java.util.ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            T item = read();
            if (item == null) {
                break;
            }
            result.add(item);
        }
        return result;
    }
}
