package com.remisoft.common.redis.service;

import java.util.Collection;

/**
 * Redis 布隆过滤器服务接口。
 * <p>用于海量 ID 存在性判断。
 * <p>误判率 < 1%。
 *
 * @author remi-team
 * @since 1.0.0
 */


public interface BloomFilterService {

    /**
     * 添加元素到布隆过滤器。
     *
     * @param filterName 过滤器名称
     * @param value      元素值
     * @return true 表示元素之前不存在
     */
    boolean add(String filterName, String value);

    /**
     * 批量添加元素到布隆过滤器。
     *
     * @param filterName 过滤器名称
     * @param values     元素值集合
     */
    void addAll(String filterName, Collection<String> values);

    /**
     * 判断元素可能存在于布隆过滤器中。
     *
     * @param filterName 过滤器名称
     * @param value      元素值
     * @return true 表示可能存在（有误判率），false 表示一定不存在
     */
    boolean mightContain(String filterName, String value);

    /**
     * 获取布隆过滤器中的元素数量（近似值）。
     *
     * @param filterName 过滤器名称
     * @return 元素数量
     */
    long count(String filterName);
}
