package com.njydsz.pmis.common.config;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 布隆过滤器配置
 *
 * <p>用于防止缓存穿透:恶意请求不存在的 key 时,先经过布隆过滤器校验,
 * 不存在则直接返回,不查 DB / 缓存。
 *
 * <h3>适用场景</h3>
 * <ul>
 *   <li>用户登录:用户名不存在时快速拒绝</li>
 *   <li>合同/项目查询:ID 不存在时快速拒绝</li>
 *   <li>任何高并发查询接口,key 空间有限且可枚举</li>
 * </ul>
 *
 * <h3>过滤器注册</h3>
 * <table>
 *   <tr><th>Bean 名称</th><th>Redis Key</th><th>用途</th></tr>
 *   <tr><td>userBloomFilter</td><td>pmis:bloom:user:username</td><td>用户名维度</td></tr>
 *   <tr><td>userIdBloomFilter</td><td>pmis:bloom:user:id</td><td>用户 ID 维度</td></tr>
 * </table>
 *
 * <h3>容量与误判率</h3>
 * <p>预期元素 10 万,误判率 0.1%(千分之一)。
 * 实际占用内存约 0.18MB,在可接受范围内。
 * 如需调整,同步修改 {@link BloomFilterService} 中的常量。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Configuration
@ConditionalOnClass(RedissonClient.class)
public class BloomFilterConfig {

    /** 预期元素数量:10 万 */
    private static final long EXPECTED_INSERTIONS = 100000L;

    /** 误判率:0.1% */
    private static final double FALSE_PROBABILITY = 0.001;

    /**
     * 用户名布隆过滤器
     *
     * <p>用于 {@code UserAccountServiceImpl#findByUsername} 防穿透:
     * 请求不存在的用户名时,布隆过滤器判定不存在则直接返回 null,不查 DB。
     *
     * @param redissonClient Redisson 客户端
     * @return 用户名维度的布隆过滤器
     */
    @Bean
    public RBloomFilter<String> userBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<String> filter = redissonClient.getBloomFilter("pmis:bloom:user:username");
        // tryInit 是幂等的:已存在则返回 false,不改变现有数据
        filter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
        return filter;
    }

    /**
     * 用户 ID 布隆过滤器
     *
     * <p>用于 {@code UserAccountServiceImpl#findById} 防穿透:
     * 请求不存在的用户 ID 时,布隆过滤器判定不存在则直接返回,不查 DB。
     *
     * @param redissonClient Redisson 客户端
     * @return 用户 ID 维度的布隆过滤器
     */
    @Bean
    public RBloomFilter<String> userIdBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<String> filter = redissonClient.getBloomFilter("pmis:bloom:user:id");
        filter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
        return filter;
    }
}
