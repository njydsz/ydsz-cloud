package com.njydsz.common.batch.item;

/**
 * 数据处理器接口
 *
 * <p>对单条数据进行业务处理（转换、校验、计算等）。
 *
 * @param <T> 输入数据类型
 * @param <R> 输出数据类型
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ItemProcessor<T, R> {

    /**
     * 处理单条数据
     *
     * @param item 输入数据
     * @return 处理后数据，返回 null 表示过滤掉该条
     * @throws Exception 处理异常
     */
    R process(T item) throws Exception;
}
