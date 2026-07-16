package com.njydsz.common.redis.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.njydsz.common.redis.config.RedisProperties;
import com.njydsz.common.redis.enums.FailOpenPolicy;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis 布隆过滤器工具类
 *
 * <p>基于 Redis BitMap 实现高性能分布式布隆过滤器，用于缓存穿透防护。
 * 支持自动扩容、误判率控制、以及批量操作。
 *
 * <p><b>核心特性：</b>
 * <ul>
 *   <li>基于 MurmurHash 3 算法计算多个哈希值</li>
 *   <li>可配置的预期元素数量和误判率</li>
 *   <li>使用 Lua 脚本保证 add/exists 操作的原子性</li>
 *   <li>支持批量添加和批量检查</li>
 *   <li>自动计算最优的位数组大小和哈希函数数量</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * RedisBloomFilter bloomFilter; // 注入
 *
 * // 添加元素
 * bloomFilter.add("user:bloom:1", "user123");
 *
 * // 检查是否存在（可能误判）
 * boolean mightExist = bloomFilter.mightContain("user:bloom:1", "user123");
 *
 * // 批量操作
 * bloomFilter.addAll("user:bloom:1", Arrays.asList("user1", "user2"));
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class RedisBloomFilter implements BloomFilterService {

    private static final double DEFAULT_FALSE_POSITIVE_RATE = 0.01;
    private static final long DEFAULT_EXPECTED_INSERTIONS = 1000000;

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Boolean> addScript;
    private final DefaultRedisScript<Boolean> existsScript;

    private final long expectedInsertions;
    private final double falsePositiveRate;
    /** 预计算的位数组大小（构造后不变，避免每次调用重复计算） */
    private final long numBits;
    /** 预计算的哈希函数数量（构造后不变，避免每次调用重复计算） */
    private final int numHashes;

    /**
     * 布隆过滤器故障处理策略（默认 FAIL_OPEN）
     * <p>当 Redis 异常时 mightContain 的返回策略：
     * <ul>
     *   <li>FAIL_OPEN: 返回 false（放行，可能导致缓存穿透）</li>
     *   <li>FAIL_CLOSED: 返回 true（保守策略，阻止穿透）</li>
     *   <li>FAIL_THROW: 抛出异常（由业务层处理）</li>
     * </ul>
     *
     * <p>使用 volatile 保证可见性：该字段可能由 {@link #init()} 或 {@link #setFailMode} 修改，
     * 而读取在工作线程中执行。
     */
    private volatile FailOpenPolicy failMode = FailOpenPolicy.FAIL_OPEN;

    @Autowired(required = false)
    private RedisProperties redisProperties;

    public RedisBloomFilter(RedisTemplate<String, Object> redisTemplate) {
        this(redisTemplate, DEFAULT_EXPECTED_INSERTIONS, DEFAULT_FALSE_POSITIVE_RATE);
    }

    /**
     * 构造布隆过滤器（支持自定义参数）
     *
     * @param redisTemplate   Redis 模板
     * @param expectedInsertions 预期插入元素数量
     * @param falsePositiveRate  误判率（必须在 (0, 1) 之间）
     */
    public RedisBloomFilter(RedisTemplate<String, Object> redisTemplate,
                           long expectedInsertions,
                           double falsePositiveRate) {
        if (expectedInsertions <= 0) {
            throw new IllegalArgumentException("expectedInsertions 必须大于 0，当前值: " + expectedInsertions);
        }
        if (falsePositiveRate <= 0 || falsePositiveRate >= 1) {
            throw new IllegalArgumentException("falsePositiveRate 必须在 (0, 1) 之间，当前值: " + falsePositiveRate);
        }
        log.info("【Redis】布隆过滤器初始化 | expectedInsertions={} | falsePositiveRate={}",
                expectedInsertions, falsePositiveRate);

        this.redisTemplate = redisTemplate;
        this.expectedInsertions = expectedInsertions;
        this.falsePositiveRate = falsePositiveRate;
        // 预计算位数组大小和哈希函数数量（构造后不变）
        this.numBits = optimalNumOfBits(expectedInsertions, falsePositiveRate);
        this.numHashes = optimalNumOfHashFunctions(expectedInsertions, this.numBits);

        this.addScript = new DefaultRedisScript<>();
        this.addScript.setScriptText(
                "local key = KEYS[1]\n" +
                "local bits = ARGV\n" +
                "for i = 1, #bits do\n" +
                "    redis.call('setbit', key, tonumber(bits[i]), 1)\n" +
                "end\n" +
                "return true"
        );
        this.addScript.setResultType(Boolean.class);

        this.existsScript = new DefaultRedisScript<>();
        this.existsScript.setScriptText(
                "local key = KEYS[1]\n" +
                "local bits = ARGV\n" +
                "for i = 1, #bits do\n" +
                "    if redis.call('getbit', key, tonumber(bits[i])) == 0 then\n" +
                "        return false\n" +
                "    end\n" +
                "end\n" +
                "return true"
        );
        this.existsScript.setResultType(Boolean.class);
    }

    /**
     * 从配置中读取 fail-mode 策略
     */
    @PostConstruct
    public void init() {
        if (redisProperties != null && redisProperties.getBloomFilter() != null
                && redisProperties.getBloomFilter().getFailMode() != null) {
            this.failMode = redisProperties.getBloomFilter().getFailMode();
        }
        log.info("【Redis】布隆过滤器 fail-mode 策略 | failMode={}", failMode);
    }

    /**
     * 设置故障处理策略（便于手动 new 的场景配置）
     *
     * @param failMode 故障处理策略
     */
    public void setFailMode(FailOpenPolicy failMode) {
        this.failMode = failMode;
    }

    /**
     * 添加单个元素到布隆过滤器
     *
     * @param filterKey 布隆过滤器的 Redis 键
     * @param value     要添加的元素值
     * @return true 表示元素之前不存在（布隆过滤器为概率型数据结构，返回值仅供参考）
     */
    @Override
    public boolean add(String filterKey, String value) {
        if (filterKey == null || value == null) {
            return false;
        }
        try {
            List<Long> hashes = murmurHash3(value, numHashes, numBits);
            List<String> keys = Collections.singletonList(filterKey);
            String[] args = hashes.stream().map(String::valueOf).toArray(String[]::new);
            redisTemplate.execute(addScript, keys, (Object[]) args);
            return true;
        } catch (Exception e) {
            log.error("【Redis】布隆过滤器添加元素失败 | key={} | value={} | error={}", filterKey, value, e.getMessage());
            throw new RuntimeException("布隆过滤器添加元素失败: " + e.getMessage(), e);
        }
    }

    /**
     * 添加多个元素到布隆过滤器（批量操作，使用 Pipeline 优化）
     *
     * @param filterKey 布隆过滤器的 Redis 键
     * @param values    要添加的元素集合
     */
    @Override
    public void addAll(String filterKey, Collection<String> values) {
        if (filterKey == null || values == null || values.isEmpty()) {
            return;
        }
        String scriptText = addScript.getScriptAsString();
        byte[] scriptBytes = scriptText.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = filterKey.getBytes(StandardCharsets.UTF_8);

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String value : values) {
                if (value != null) {
                    List<Long> hashes = murmurHash3(value, numHashes, numBits);
                    // keys + args: [key, hash1, hash2, ...]
                    byte[][] allArgs = new byte[1 + hashes.size()][];
                    allArgs[0] = keyBytes;
                    for (int i = 0; i < hashes.size(); i++) {
                        allArgs[i + 1] = String.valueOf(hashes.get(i)).getBytes(StandardCharsets.UTF_8);
                    }
                    connection.scriptingCommands().eval(
                            scriptBytes,
                            ReturnType.BOOLEAN,
                            1,
                            allArgs
                    );
                }
            }
            return null;
        });
    }

    /**
     * 检查元素是否可能存在于布隆过滤器中
     *
     * <p><b>注意：</b>
     * <ul>
     *   <li>返回 true：元素可能存在（有误判率）</li>
     *   <li>返回 false：元素一定不存在</li>
     * </ul>
     *
     * @param filterKey 布隆过滤器的 Redis 键
     * @param value     要检查的元素值
     * @return true-可能存在，false-一定不存在
     */
    @Override
    public boolean mightContain(String filterKey, String value) {
        if (filterKey == null || value == null) {
            return false;
        }
        try {
            List<Long> hashes = murmurHash3(value, numHashes, numBits);
            List<String> keys = Collections.singletonList(filterKey);
            String[] args = hashes.stream().map(String::valueOf).toArray(String[]::new);
            Boolean result = redisTemplate.execute(existsScript, keys, (Object[]) args);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("【Redis】布隆过滤器检查元素失败 | key={} | value={} | error={} | failMode={}",
                    filterKey, value, e.getMessage(), failMode);
            if (failMode == FailOpenPolicy.FAIL_CLOSED) {
                return true;
            }
            if (failMode == FailOpenPolicy.FAIL_THROW) {
                throw new RuntimeException("布隆过滤器检查元素失败: " + e.getMessage(), e);
            }
            return false;
        }
    }

    /**
     * 获取布隆过滤器中的近似元素数量
     *
     * <p>通过 Redis BITCOUNT 命令统计位数组中设置为 1 的位数，
     * 除以哈希函数数量得到近似元素数量。
     *
     * @param filterKey 布隆过滤器的 Redis 键
     * @return 近似元素数量（下限），失败时返回 -1
     */
    @Override
    public long count(String filterKey) {
        if (filterKey == null) {
            return -1;
        }
        try {
            Long bitCount = redisTemplate.execute((RedisCallback<Long>) connection ->
                    connection.stringCommands().bitCount(filterKey.getBytes(StandardCharsets.UTF_8)));
            if (bitCount == null || numHashes == 0) {
                return 0;
            }
            return bitCount / numHashes;
        } catch (Exception e) {
            log.error("【Redis】布隆过滤器计数失败 | key={} | error={}", filterKey, e.getMessage());
            return -1;
        }
    }

    /**
     * 批量检查多个元素是否可能存在于布隆过滤器中
     *
     * <p>使用 Redis Pipeline 批量执行 Lua 脚本，减少网络往返开销。
     * 当 values 数量较多时，性能显著优于逐个调用 {@link #mightContain}。</p>
     *
     * @param filterKey 布隆过滤器的 Redis 键
     * @param values    要检查的元素集合
     * @return 可能存在的元素集合
     */
    public List<String> mightContainAll(String filterKey, List<String> values) {
        if (filterKey == null || values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        byte[] scriptBytes = existsScript.getScriptAsString().getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = filterKey.getBytes(StandardCharsets.UTF_8);

        // 预计算每个 value 的哈希值
        List<String> validValues = new ArrayList<>(values.size());
        List<byte[][]> allArgsList = new ArrayList<>(values.size());
        for (String value : values) {
            if (value != null) {
                validValues.add(value);
                List<Long> hashes = murmurHash3(value, numHashes, numBits);
                byte[][] allArgs = new byte[1 + hashes.size()][];
                allArgs[0] = keyBytes;
                for (int i = 0; i < hashes.size(); i++) {
                    allArgs[i + 1] = String.valueOf(hashes.get(i)).getBytes(StandardCharsets.UTF_8);
                }
                allArgsList.add(allArgs);
            }
        }

        if (validValues.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // 使用 Pipeline 批量执行，减少网络往返
            List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (byte[][] allArgs : allArgsList) {
                    connection.scriptingCommands().eval(
                            scriptBytes,
                            ReturnType.BOOLEAN,
                            1,
                            allArgs
                    );
                }
                return null;
            });

            // 解析结果（Pipeline 返回顺序与提交顺序一致）
            List<String> result = new ArrayList<>();
            for (int i = 0; i < validValues.size() && i < results.size(); i++) {
                Object r = results.get(i);
                boolean exists = (r instanceof Boolean && (Boolean) r)
                        || (r instanceof Long && ((Long) r) == 1L);
                if (exists) {
                    result.add(validValues.get(i));
                }
            }
            return result;
        } catch (Exception e) {
            log.error("【Redis】布隆过滤器批量检查失败 | key={} | size={} | error={} | failMode={}",
                    filterKey, values.size(), e.getMessage(), failMode);
            if (failMode == FailOpenPolicy.FAIL_CLOSED) {
                return new ArrayList<>(validValues);
            }
            if (failMode == FailOpenPolicy.FAIL_THROW) {
                throw new RuntimeException("布隆过滤器批量检查失败: " + e.getMessage(), e);
            }
            return Collections.emptyList();
        }
    }

    /**
     * 删除布隆过滤器（谨慎使用）
     *
     * <p>注意：布隆过滤器不支持单独删除元素，只能整体删除。
     *
     * @param filterKey 布隆过滤器的 Redis 键
     */
    public void delete(String filterKey) {
        if (filterKey == null) {
            return;
        }
        try {
            redisTemplate.delete(filterKey);
        } catch (Exception e) {
            log.error("【Redis】布隆过滤器删除失败 | key={} | error={}", filterKey, e.getMessage());
        }
    }

    /**
     * 获取布隆过滤器占用的内存大小（字节）
     *
     * @param filterKey 布隆过滤器的 Redis 键
     * @return 内存大小（字节），查询失败返回 -1
     */
    public long memoryUsage(String filterKey) {
        if (filterKey == null) {
            return -1;
        }
        try {
            Long size = redisTemplate.execute((RedisCallback<Long>) connection ->
                    connection.stringCommands().strLen(filterKey.getBytes(StandardCharsets.UTF_8)));
            return size != null ? size : -1;
        } catch (Exception e) {
            log.error("【Redis】布隆过滤器内存查询失败 | key={} | error={}", filterKey, e.getMessage());
            return -1;
        }
    }

    /**
     * 计算最优的位数组大小
     *
     * @param expectedInsertions 预期插入元素数量
     * @param falsePositiveRate  期望的误判率
     * @return 位数组大小
     */
    static long optimalNumOfBits(long expectedInsertions, double falsePositiveRate) {
        if (falsePositiveRate == 0) {
            falsePositiveRate = Double.MIN_VALUE;
        }
        return (long) (-expectedInsertions * Math.log(falsePositiveRate) / (Math.log(2) * Math.log(2)));
    }

    /**
     * 计算最优的哈希函数数量
     *
     * @param expectedInsertions 预期插入元素数量
     * @param numBits            位数组大小
     * @return 哈希函数数量
     */
    static int optimalNumOfHashFunctions(long expectedInsertions, long numBits) {
        if (numBits == 0) {
            numBits = 1;
        }
        return Math.max(1, (int) Math.round((double) numBits / expectedInsertions * Math.log(2)));
    }

    /**
     * MurmurHash 3 哈希函数，生成多个哈希值
     *
     * @param value     输入值
     * @param numHashes 需要的哈希值数量
     * @param numBits   位数组大小（用于取模）
     * @return 哈希值列表
     */
    private static List<Long> murmurHash3(String value, int numHashes, long numBits) {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        long hash1 = hash64A(data, 0);
        long hash2 = hash64A(data, 1);

        List<Long> positions = new ArrayList<>(numHashes);
        for (int i = 0; i < numHashes; i++) {
            long combinedHash = hash1 + i * hash2;
            if (combinedHash < 0) {
                combinedHash = ~combinedHash;
            }
            positions.add(combinedHash % numBits);
        }
        return positions;
    }

    /**
     * MurmurHash 3 的 64 位变体
     *
     * @param data 输入数据
     * @param seed 种子值
     * @return 64 位哈希值
     */
    private static long hash64A(byte[] data, int seed) {
        long h1 = seed;
        long h2 = seed;
        final long c1 = 0x87c37b91114253d5L;
        final long c2 = 0x4cf5ad432745937fL;
        int length = data.length;
        int numBlocks = length / 16;

        for (int i = 0; i < numBlocks; i++) {
            int offset = i * 16;
            long k1 = getLongLittleEndian(data, offset);
            long k2 = getLongLittleEndian(data, offset + 8);

            k1 *= c1;
            k1 = Long.rotateLeft(k1, 31);
            k1 *= c2;
            h1 ^= k1;
            h1 = Long.rotateLeft(h1, 27);
            h1 += h2;
            h1 = h1 * 5 + 0x52dce729;

            k2 *= c2;
            k2 = Long.rotateLeft(k2, 33);
            k2 *= c1;
            h2 ^= k2;
            h2 = Long.rotateLeft(h2, 31);
            h2 += h1;
            h2 = h2 * 5 + 0x38495ab5;
        }

        int tailStart = numBlocks * 16;
        long k1 = 0;
        long k2 = 0;
        int remaining = length & 15;

        // 处理尾部字节，避免 fall-through
        if (remaining >= 15) {
            k2 ^= ((long) data[tailStart + 14] & 0xff) << 48;
        }
        if (remaining >= 14) {
            k2 ^= ((long) data[tailStart + 13] & 0xff) << 40;
        }
        if (remaining >= 13) {
            k2 ^= ((long) data[tailStart + 12] & 0xff) << 32;
        }
        if (remaining >= 12) {
            k2 ^= ((long) data[tailStart + 11] & 0xff) << 24;
        }
        if (remaining >= 11) {
            k2 ^= ((long) data[tailStart + 10] & 0xff) << 16;
        }
        if (remaining >= 10) {
            k2 ^= ((long) data[tailStart + 9] & 0xff) << 8;
        }
        if (remaining >= 9) {
            k2 ^= ((long) data[tailStart + 8] & 0xff);
            k2 *= c2;
            k2 = Long.rotateLeft(k2, 33);
            k2 *= c1;
            h2 ^= k2;
        }
        if (remaining >= 8) {
            k1 ^= ((long) data[tailStart + 7] & 0xff) << 56;
        }
        if (remaining >= 7) {
            k1 ^= ((long) data[tailStart + 6] & 0xff) << 48;
        }
        if (remaining >= 6) {
            k1 ^= ((long) data[tailStart + 5] & 0xff) << 40;
        }
        if (remaining >= 5) {
            k1 ^= ((long) data[tailStart + 4] & 0xff) << 32;
        }
        if (remaining >= 4) {
            k1 ^= ((long) data[tailStart + 3] & 0xff) << 24;
        }
        if (remaining >= 3) {
            k1 ^= ((long) data[tailStart + 2] & 0xff) << 16;
        }
        if (remaining >= 2) {
            k1 ^= ((long) data[tailStart + 1] & 0xff) << 8;
        }
        if (remaining >= 1) {
            k1 ^= ((long) data[tailStart] & 0xff);
            k1 *= c1;
            k1 = Long.rotateLeft(k1, 31);
            k1 *= c2;
            h1 ^= k1;
        }

        h1 ^= length;
        h2 ^= length;
        h1 += h2;
        h2 += h1;
        h1 = fmix64(h1);
        h2 = fmix64(h2);
        h1 += h2;
        return h1;
    }

    private static long getLongLittleEndian(byte[] data, int offset) {
        return ((long) data[offset] & 0xff)
                | (((long) data[offset + 1] & 0xff) << 8)
                | (((long) data[offset + 2] & 0xff) << 16)
                | (((long) data[offset + 3] & 0xff) << 24)
                | (((long) data[offset + 4] & 0xff) << 32)
                | (((long) data[offset + 5] & 0xff) << 40)
                | (((long) data[offset + 6] & 0xff) << 48)
                | (((long) data[offset + 7] & 0xff) << 56);
    }

    private static long fmix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }
}
