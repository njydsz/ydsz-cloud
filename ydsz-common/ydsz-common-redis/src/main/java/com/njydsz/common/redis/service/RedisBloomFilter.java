package com.njydsz.common.redis.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.njydsz.common.redis.config.RedisProperties;
import com.njydsz.common.redis.enums.FailOpenPolicy;

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
public class RedisBloomFilter implements BloomFilterService {

    private static final double DEFAULT_FALSE_POSITIVE_RATE = 0.01;
    private static final long DEFAULT_EXPECTED_INSERTIONS = 1000000;

    /** MurmurHash3 64 位变体常量 c1 */
    private static final long MURMUR_C1 = 0x87c37b91114253d5L;
    /** MurmurHash3 64 位变体常量 c2 */
    private static final long MURMUR_C2 = 0x4cf5ad432745937fL;

    /** MurmurHash3 分块大小（字节） */
    private static final int MURMUR_BLOCK_SIZE = 16;
    /** MurmurHash3 半块大小（字节），用于拆分两个 64 位字 */
    private static final int MURMUR_BLOCK_HALF_SIZE = 8;
    /** MurmurHash3 尾部剩余字节数掩码（0~15） */
    private static final int MURMUR_TAIL_MASK = 15;
    /** MurmurHash3 状态更新乘数 */
    private static final long MURMUR_MULTIPLIER = 5L;
    /** MurmurHash3 状态更新常量一（0x52dce729） */
    private static final long MURMUR_STATE_C1 = 0x52dce729L;
    /** MurmurHash3 状态更新常量二（0x38495ab5） */
    private static final long MURMUR_STATE_C2 = 0x38495ab5L;
    /** MurmurHash3 最终混淆（fmix）第一乘法常量 */
    private static final long FMIX_MULTIPLIER_1 = 0xff51afd7ed558ccdL;
    /** MurmurHash3 最终混淆（fmix）第二乘法常量 */
    private static final long FMIX_MULTIPLIER_2 = 0xc4ceb9fe1a85ec53L;
    /** 字节无符号化掩码 */
    private static final long BYTE_UNSIGNED_MASK = 0xffL;

    /** MurmurHash3 循环左移位数 27 */
    private static final int ROTATE_LEFT_27 = 27;
    /** MurmurHash3 循环左移位数 31 */
    private static final int ROTATE_LEFT_31 = 31;
    /** MurmurHash3 循环左移位数 33 */
    private static final int ROTATE_LEFT_33 = 33;
    /** MurmurHash3 fmix 无符号右移位数 33 */
    private static final int UNSIGNED_RIGHT_SHIFT_33 = 33;

    /** 位移位数 8 */
    private static final int SHIFT_BITS_8 = 8;
    /** 位移位数 16 */
    private static final int SHIFT_BITS_16 = 16;
    /** 位移位数 24 */
    private static final int SHIFT_BITS_24 = 24;
    /** 位移位数 32 */
    private static final int SHIFT_BITS_32 = 32;
    /** 位移位数 40 */
    private static final int SHIFT_BITS_40 = 40;
    /** 位移位数 48 */
    private static final int SHIFT_BITS_48 = 48;
    /** 位移位数 56 */
    private static final int SHIFT_BITS_56 = 56;

    /** MurmurHash3 尾部剩余字节数 15 */
    private static final int TAIL_REMAINING_15 = 15;
    /** MurmurHash3 尾部剩余字节数 14 */
    private static final int TAIL_REMAINING_14 = 14;
    /** MurmurHash3 尾部剩余字节数 13 */
    private static final int TAIL_REMAINING_13 = 13;
    /** MurmurHash3 尾部剩余字节数 12 */
    private static final int TAIL_REMAINING_12 = 12;
    /** MurmurHash3 尾部剩余字节数 11 */
    private static final int TAIL_REMAINING_11 = 11;
    /** MurmurHash3 尾部剩余字节数 9 */
    private static final int TAIL_REMAINING_9 = 9;
    /** MurmurHash3 尾部剩余字节数 8 */
    private static final int TAIL_REMAINING_8 = 8;
    /** MurmurHash3 尾部剩余字节数 7 */
    private static final int TAIL_REMAINING_7 = 7;
    /** MurmurHash3 尾部剩余字节数 6 */
    private static final int TAIL_REMAINING_6 = 6;
    /** MurmurHash3 尾部剩余字节数 5 */
    private static final int TAIL_REMAINING_5 = 5;
    /** MurmurHash3 尾部剩余字节数 4 */
    private static final int TAIL_REMAINING_4 = 4;
    /** MurmurHash3 尾部剩余字节数 3 */
    private static final int TAIL_REMAINING_3 = 3;

    /** 小端读取字节偏移量 3 */
    private static final int BYTE_OFFSET_3 = 3;
    /** 小端读取字节偏移量 4 */
    private static final int BYTE_OFFSET_4 = 4;
    /** 小端读取字节偏移量 5 */
    private static final int BYTE_OFFSET_5 = 5;
    /** 小端读取字节偏移量 6 */
    private static final int BYTE_OFFSET_6 = 6;
    /** 小端读取字节偏移量 7 */
    private static final int BYTE_OFFSET_7 = 7;

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
            log.error("【Redis】布隆过滤器添加元素失败 | key={} | value={}", filterKey, value, e);
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
            log.error("【Redis】布隆过滤器检查元素失败 | key={} | value={} | failMode={}",
                    filterKey, value, failMode, e);
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
            log.error("【Redis】布隆过滤器计数失败 | key={}", filterKey, e);
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
            log.error("【Redis】布隆过滤器批量检查失败 | key={} | size={} | failMode={}",
                    filterKey, values.size(), failMode, e);
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
            log.error("【Redis】布隆过滤器删除失败 | key={}", filterKey, e);
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
            log.error("【Redis】布隆过滤器内存查询失败 | key={}", filterKey, e);
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
        int length = data.length;
        int numBlocks = length / MURMUR_BLOCK_SIZE;

        for (int i = 0; i < numBlocks; i++) {
            int offset = i * MURMUR_BLOCK_SIZE;
            long k1 = getLongLittleEndian(data, offset);
            long k2 = getLongLittleEndian(data, offset + MURMUR_BLOCK_HALF_SIZE);

            k1 *= MURMUR_C1;
            k1 = Long.rotateLeft(k1, ROTATE_LEFT_31);
            k1 *= MURMUR_C2;
            h1 ^= k1;
            h1 = Long.rotateLeft(h1, ROTATE_LEFT_27);
            h1 += h2;
            h1 = h1 * MURMUR_MULTIPLIER + MURMUR_STATE_C1;

            k2 *= MURMUR_C2;
            k2 = Long.rotateLeft(k2, ROTATE_LEFT_33);
            k2 *= MURMUR_C1;
            h2 ^= k2;
            h2 = Long.rotateLeft(h2, ROTATE_LEFT_31);
            h2 += h1;
            h2 = h2 * MURMUR_MULTIPLIER + MURMUR_STATE_C2;
        }

        // 处理尾部字节（使用 switch fall-through，与 Guava MurmurHash3 实现一致）
        long[] tail = applyTail(data, numBlocks * MURMUR_BLOCK_SIZE, length & MURMUR_TAIL_MASK, h1, h2);
        h1 = tail[0];
        h2 = tail[1];

        h1 ^= length;
        h2 ^= length;
        h1 += h2;
        h2 += h1;
        h1 = fmix64(h1);
        h2 = fmix64(h2);
        h1 += h2;
        return h1;
    }

    /**
     * 处理 MurmurHash3 64 位变体的尾部字节（不足 16 字节的剩余部分）。
     *
     * <p>采用 switch fall-through 递减处理，与 Guava 的 MurmurHash3 实现保持一致。
     *
     * @param data      输入数据
     * @param tailStart 尾部起始偏移
     * @param remaining 剩余字节数（0~15）
     * @param h1        状态值 h1
     * @param h2        状态值 h2
     * @return 更新后的 [h1, h2]
     */
    private static long[] applyTail(byte[] data, int tailStart, int remaining,
                                    long h1, long h2) {
        long k1 = 0;
        long k2 = 0;

        switch (remaining) {
            case TAIL_REMAINING_15:
                k2 ^= ((long) data[tailStart + TAIL_REMAINING_14] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_48;
                // fall through
            case TAIL_REMAINING_14:
                k2 ^= ((long) data[tailStart + TAIL_REMAINING_13] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_40;
                // fall through
            case TAIL_REMAINING_13:
                k2 ^= ((long) data[tailStart + TAIL_REMAINING_12] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_32;
                // fall through
            case TAIL_REMAINING_12:
                k2 ^= ((long) data[tailStart + TAIL_REMAINING_11] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_24;
                // fall through
            case TAIL_REMAINING_11:
                k2 ^= ((long) data[tailStart + 10] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_16;
                // fall through
            case 10:
                k2 ^= ((long) data[tailStart + TAIL_REMAINING_9] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_8;
                // fall through
            case TAIL_REMAINING_9:
                k2 ^= ((long) data[tailStart + TAIL_REMAINING_8] & BYTE_UNSIGNED_MASK);
                k2 *= MURMUR_C2;
                k2 = Long.rotateLeft(k2, ROTATE_LEFT_33);
                k2 *= MURMUR_C1;
                h2 ^= k2;
                // fall through
            case TAIL_REMAINING_8:
                k1 ^= ((long) data[tailStart + TAIL_REMAINING_7] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_56;
                // fall through
            case TAIL_REMAINING_7:
                k1 ^= ((long) data[tailStart + TAIL_REMAINING_6] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_48;
                // fall through
            case TAIL_REMAINING_6:
                k1 ^= ((long) data[tailStart + TAIL_REMAINING_5] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_40;
                // fall through
            case TAIL_REMAINING_5:
                k1 ^= ((long) data[tailStart + TAIL_REMAINING_4] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_32;
                // fall through
            case TAIL_REMAINING_4:
                k1 ^= ((long) data[tailStart + TAIL_REMAINING_3] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_24;
                // fall through
            case TAIL_REMAINING_3:
                k1 ^= ((long) data[tailStart + 2] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_16;
                // fall through
            case 2:
                k1 ^= ((long) data[tailStart + 1] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_8;
                // fall through
            case 1:
                k1 ^= ((long) data[tailStart] & BYTE_UNSIGNED_MASK);
                k1 *= MURMUR_C1;
                k1 = Long.rotateLeft(k1, ROTATE_LEFT_31);
                k1 *= MURMUR_C2;
                h1 ^= k1;
                // fall through
            default:
                break;
        }

        return new long[]{h1, h2};
    }

    private static long getLongLittleEndian(byte[] data, int offset) {
        return ((long) data[offset] & BYTE_UNSIGNED_MASK)
                | (((long) data[offset + 1] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_8)
                | (((long) data[offset + 2] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_16)
                | (((long) data[offset + BYTE_OFFSET_3] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_24)
                | (((long) data[offset + BYTE_OFFSET_4] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_32)
                | (((long) data[offset + BYTE_OFFSET_5] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_40)
                | (((long) data[offset + BYTE_OFFSET_6] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_48)
                | (((long) data[offset + BYTE_OFFSET_7] & BYTE_UNSIGNED_MASK) << SHIFT_BITS_56);
    }

    private static long fmix64(long k) {
        k ^= k >>> UNSIGNED_RIGHT_SHIFT_33;
        k *= FMIX_MULTIPLIER_1;
        k ^= k >>> UNSIGNED_RIGHT_SHIFT_33;
        k *= FMIX_MULTIPLIER_2;
        k ^= k >>> UNSIGNED_RIGHT_SHIFT_33;
        return k;
    }
}
