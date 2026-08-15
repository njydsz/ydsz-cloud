package com.njydsz.common.auth.security;

import java.nio.charset.StandardCharsets;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Guava BloomFilter 的 Token 黑名单本地前置过滤器。
 *
 * <p>在查询 Redis 黑名单之前，先通过本地 Bloom Filter 快速判断 token 是否可能被加入黑名单。
 * Bloom Filter 误判率设为 0.01 (1%)，可过滤约 99% 的非黑名单 token 的 Redis 查询。
 *
 * <p><b>废弃原因：</b>布隆过滤器在 Token 黑名单场景收益有限。黑名单 Token 是少数派，
 * Redis 对 key exists 的判断本身就是 O(1)，自建布隆过滤器无法动态扩容，超出容量后误判率上升，
 * 且应用重启后需要重建。如需减少 Redis 查询，建议使用 Redis 内置的 Bloom Filter 模块。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 自 3.0.0 起标记废弃，计划 4.0.0 移除。
 *             如需布隆过滤器能力，建议使用 Redis 内置 Bloom Filter 模块。
 * @see TokenBlacklistBloomFilter
 */
@Deprecated(forRemoval = true, since = "3.0.0")
public class GuavaTokenBlacklistBloomFilter {

    private static final Logger log =
            LoggerFactory.getLogger(GuavaTokenBlacklistBloomFilter.class);

    private static final double FALSE_POSITIVE_RATE = 0.01;

    private final int expectedInsertions;
    private volatile BloomFilter<String> bloomFilter;

    /**
     * 构建指定预期容量的 Guava Bloom Filter。
     *
     * @param expectedInsertions 预期插入数量
     */
    public GuavaTokenBlacklistBloomFilter(int expectedInsertions) {
        this.expectedInsertions = Math.max(expectedInsertions, 1000);
        this.bloomFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                this.expectedInsertions,
                FALSE_POSITIVE_RATE
        );
        log.info(
                "GuavaTokenBlacklistBloomFilter 初始化: expectedInsertions={}, fpr={}",
                this.expectedInsertions, FALSE_POSITIVE_RATE
        );
    }

    /**
     * 创建指定预期容量的 Guava Bloom Filter 工厂方法。
     *
     * @param expectedInsertions 预期插入数量
     * @return 新的 GuavaTokenBlacklistBloomFilter 实例
     */
    public static GuavaTokenBlacklistBloomFilter create(int expectedInsertions) {
        return new GuavaTokenBlacklistBloomFilter(expectedInsertions);
    }

    /**
     * 将 token 加入 Bloom Filter。
     *
     * @param token JWT Token
     */
    public void addToBlacklist(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        bloomFilter.put(token);
    }

    /**
     * 判断 token 是否可能在黑名单中。
     *
     * @param token JWT Token
     * @return false 表示一定不在黑名单中（无需查 Redis），true 表示可能在（需查 Redis）
     */
    public boolean mightBeBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return bloomFilter.mightContain(token);
    }

    /**
     * 清空 Bloom Filter。
     *
     * <p>Guava 的 BloomFilter 不支持原地清空，因此重新创建新实例。
     * 在权限全量缓存失效时调用，确保后续黑名单查询走 Redis。
     */
    public void clear() {
        bloomFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                expectedInsertions,
                FALSE_POSITIVE_RATE
        );
        log.info("GuavaTokenBlacklistBloomFilter 已清空");
    }

    /**
     * 获取当前已插入元素的近似数量（用于监控）。
     *
     * @return 近似元素数量估计值
     */
    public long getCardinality() {
        return bloomFilter.approximateElementCount().estimate();
    }

    /**
     * 获取 bit 数组总大小。
     *
     * <p>Guava BloomFilter 不直接暴露 bit 数组大小，
     * 此处返回基于预期插入数和误判率计算的理论值：
     * m = -n * ln(p) / (ln2)^2。
     *
     * @return bit 数组理论大小
     */
    public long getBitArraySize() {
        double n = expectedInsertions;
        double p = FALSE_POSITIVE_RATE;
        return (long) Math.ceil(
                -n * Math.log(p) / (Math.log(2) * Math.log(2))
        );
    }
}
