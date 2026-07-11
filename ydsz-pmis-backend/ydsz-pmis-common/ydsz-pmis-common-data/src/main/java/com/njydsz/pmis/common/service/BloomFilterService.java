package com.njydsz.pmis.common.service;

import jakarta.annotation.PostConstruct;
import org.redisson.api.RBloomFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 布隆过滤器通用服务
 *
 * <p>对 Redisson {@link RBloomFilter} 的统一封装,提供按逻辑名称访问过滤器的能力。
 * 业务层通过逻辑名称(如 {@code "user:username"})操作过滤器,无需感知底层 Redis Key。
 *
 * <h3>已注册过滤器</h3>
 * <table>
 *   <tr><th>逻辑名称</th><th>Redis Key</th><th>来源</th></tr>
 *   <tr><td>user:username</td><td>pmis:bloom:user:username</td><td>{@code BloomFilterConfig#userBloomFilter}</td></tr>
 *   <tr><td>user:id</td><td>pmis:bloom:user:id</td><td>{@code BloomFilterConfig#userIdBloomFilter}</td></tr>
 * </table>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 查询前校验
 * if (!bloomFilterService.mightContain("user:username", username)) {
 *     return null; // 判定不存在,直接返回
 * }
 * // 写入后添加
 * bloomFilterService.add("user:username", user.getUsername());
 * }</pre>
 *
 * <h3>注意事项</h3>
 * <ul>
 *   <li>布隆过滤器存在误判率(当前 0.1%):判定"存在"时可能误判,判定"不存在"时一定不存在</li>
 *   <li>过滤器不支持删除单个元素:如需删除,使用 {@link #rebuild} 重建</li>
 *   <li>{@link #clear} 和 {@link #rebuild} 会删除 Redis 中的过滤器数据,谨慎使用</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
public class BloomFilterService {

    private static final Logger log = LoggerFactory.getLogger(BloomFilterService.class);

    /** 预期元素数量(与 BloomFilterConfig 保持一致,用于 rebuild/clear 重新初始化) */
    private static final long EXPECTED_INSERTIONS = 100000L;

    /** 误判率(与 BloomFilterConfig 保持一致) */
    private static final double FALSE_PROBABILITY = 0.001;

    private final RBloomFilter<String> userBloomFilter;
    private final RBloomFilter<String> userIdBloomFilter;

    /** 过滤器实例缓存:逻辑名称 → RBloomFilter */
    private final Map<String, RBloomFilter<String>> filterMap = new ConcurrentHashMap<>();

    /**
     * @param userBloomFilter   用户名维度的布隆过滤器(由 BloomFilterConfig 注册)
     * @param userIdBloomFilter 用户 ID 维度的布隆过滤器(由 BloomFilterConfig 注册)
     */
    public BloomFilterService(RBloomFilter<String> userBloomFilter,
                              RBloomFilter<String> userIdBloomFilter) {
        this.userBloomFilter = userBloomFilter;
        this.userIdBloomFilter = userIdBloomFilter;
    }

    /**
     * 初始化:注册已知的布隆过滤器到内部缓存
     *
     * <p>启动时将 {@link BloomFilterConfig} 创建的过滤器按逻辑名称注册,
     * 后续业务代码通过逻辑名称访问,无需感知 Redis Key。
     */
    @PostConstruct
    public void init() {
        register("user:username", userBloomFilter);
        register("user:id", userIdBloomFilter);
        log.info("[BloomFilter] 已注册过滤器: {}", filterMap.keySet());
    }

    /**
     * 注册过滤器到内部缓存
     *
     * @param name   逻辑名称(业务层使用)
     * @param filter Redisson 布隆过滤器实例
     */
    private void register(String name, RBloomFilter<String> filter) {
        filterMap.put(name, filter);
    }

    /**
     * 获取已注册的过滤器,未注册时抛出异常
     *
     * @param filterName 逻辑名称
     * @return 布隆过滤器实例
     * @throws IllegalArgumentException 过滤器未注册
     */
    private RBloomFilter<String> getFilter(String filterName) {
        RBloomFilter<String> filter = filterMap.get(filterName);
        if (filter == null) {
            throw new IllegalArgumentException("未注册的布隆过滤器: " + filterName);
        }
        return filter;
    }

    /**
     * 检查 key 是否可能存在于过滤器中
     *
     * <p><b>判定规则</b>:返回 {@code false} 时一定不存在;返回 {@code true} 时可能存在(误判率 0.1%)。
     *
     * @param filterName 过滤器逻辑名称
     * @param key        待检查的元素
     * @return true:可能存在(需进一步查 DB/缓存);false:一定不存在(可直接返回)
     */
    public boolean mightContain(String filterName, String key) {
        return getFilter(filterName).contains(key);
    }

    /**
     * 添加单个元素到过滤器
     *
     * @param filterName 过滤器逻辑名称
     * @param key        待添加的元素
     */
    public void add(String filterName, String key) {
        getFilter(filterName).add(key);
    }

    /**
     * 批量添加元素到过滤器
     *
     * @param filterName 过滤器逻辑名称
     * @param keys       待添加的元素集合
     */
    public void addAll(String filterName, Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        RBloomFilter<String> filter = getFilter(filterName);
        for (String key : keys) {
            filter.add(key);
        }
    }

    /**
     * 重建过滤器:删除旧数据 → 重新初始化 → 批量添加新元素
     *
     * <p>适用场景:数据大规模变更后重建索引,或误判率累积过高时重置。
     *
     * @param filterName 过滤器逻辑名称
     * @param keys       新的元素集合(可为 null,表示清空后不添加)
     */
    public void rebuild(String filterName, Collection<String> keys) {
        RBloomFilter<String> filter = getFilter(filterName);
        filter.delete();
        filter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
        if (keys != null) {
            for (String key : keys) {
                filter.add(key);
            }
        }
        log.info("[BloomFilter] 重建过滤器: {}, 元素数量= {}", filterName, keys == null ? 0 : keys.size());
    }

    /**
     * 估算过滤器中的元素数量
     *
     * @param filterName 过滤器逻辑名称
     * @return 估算的元素数量
     */
    public long count(String filterName) {
        return getFilter(filterName).count();
    }

    /**
     * 清空过滤器:删除所有元素并重新初始化
     *
     * @param filterName 过滤器逻辑名称
     */
    public void clear(String filterName) {
        RBloomFilter<String> filter = getFilter(filterName);
        filter.delete();
        filter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
        log.info("[BloomFilter] 已清空过滤器: {}", filterName);
    }
}
