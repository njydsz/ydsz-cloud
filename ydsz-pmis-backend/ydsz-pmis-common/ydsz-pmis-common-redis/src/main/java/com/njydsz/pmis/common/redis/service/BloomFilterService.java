package com.njydsz.pmis.common.redis.service;

import java.util.Collection;

/**
 * 布隆过滤器服务接口。
 *
 * <p>基于 Redis BloomFilter 实现，用于高效判断元素是否存在。
 *
 * @author ydsz-pmis-team
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
