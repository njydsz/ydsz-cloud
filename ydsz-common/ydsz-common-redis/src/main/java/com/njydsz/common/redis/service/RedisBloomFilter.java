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

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
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

    /**
     * MurmurHash3 128 位哈希函数（Guava 标准实现）
     * <p>使用种子 0 生成 128 位哈希值，拆分为两个 64 位字用于布隆过滤器的多哈希计算。
     */
    private static final HashFunction MURMUR3_128 = Hashing.murmur3_128(0);

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Boolean> addScript;
    private final DefaultRedisScript<Boolean> existsScript;

    private final long expectedInsertions;
    private final double falsePositiveRate;
    /** 预计算的位数组大小（构造后不变，避免每次调用重复计算） */
    private final long numBits;
    /** 预计算的哈希函数数量（构造后不变，避免每次调用重复计算） */
    private final int numHashes;

    /** 预计算的 add 脚本字节（避免每次 addAll/mightContainAll 都执行 getBytes） */
    private final byte[] addScriptBytes;

    /** 预计算的 exists 脚本字节（避免每次 addAll/mightContainAll 都执行 getBytes） */
    private final byte[] existsScriptBytes;

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

        // 预计算脚本字节数组，避免每次批量操作都执行 getBytes 转换
        this.addScriptBytes = this.addScript.getScriptAsString().getBytes(StandardCharsets.UTF_8);
        this.existsScriptBytes = this.existsScript.getScriptAsString().getBytes(StandardCharsets.UTF_8);
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
     * 添加多个元素到布隆过滤器（批量操作，使用单条 Lua 脚本一次性处理所有值）
     *
     * <p>相比逐条 Pipeline 调用，单条批量脚本优势：
     * <ul>
     *   <li>仅一次网络往返，大幅降低 RT</li>
     *   <li>Lua 脚本在服务端原子执行，无并发冲突</li>
     *   <li>利用脚本字节缓存（{@link #addScriptBytes}），避免重复编码</li>
     * </ul>
     *
     * @param filterKey 布隆过滤器的 Redis 键
     * @param values    要添加的元素集合
     */
    @Override
    public void addAll(String filterKey, Collection<String> values) {
        if (filterKey == null || values == null || values.isEmpty()) {
            return;
        }

        // 批量 Lua 脚本：外层遍历每个 value，内层遍历每个 hash 位
        // ARGV[1] = key, ARGV[2] = numHashes, ARGV[3] = numBits,
        // ARGV[4..N] = 每个 value 的长度前缀 + 哈希值序列
        // 为简化协议，改用 keys + args 传递方式：
        //   KEYS[1] = filterKey
        //   ARGV[1] = numHashes（用于分组）
        //   ARGV[2..N] = 所有 value 的哈希值扁平化数组
        // 每组 numHashes 个值对应一个 value 的所有哈希位

        // 预计算所有 value 的哈希值
        List<Long> allHashes = new ArrayList<>(values.size() * numHashes);
        int validCount = 0;
        for (String value : values) {
            if (value != null) {
                allHashes.addAll(murmurHash3(value, numHashes, numBits));
                validCount++;
            }
        }
        if (validCount == 0) {
            return;
        }

        byte[] keyBytes = filterKey.getBytes(StandardCharsets.UTF_8);
        byte[] numHashesBytes = String.valueOf(numHashes).getBytes(StandardCharsets.UTF_8);

        // 构建参数：key, numHashes, hash1, hash2, ...
        byte[][] allArgs = new byte[2 + allHashes.size()][];
        allArgs[0] = keyBytes;
        allArgs[1] = numHashesBytes;
        for (int i = 0; i < allHashes.size(); i++) {
            allArgs[i + 2] = String.valueOf(allHashes.get(i)).getBytes(StandardCharsets.UTF_8);
        }

        // 单条批量脚本：按 numHashes 分组处理每个 value 的哈希位
        String batchScriptText =
                "local key = KEYS[1]\n" +
                "local numHashes = tonumber(ARGV[1])\n" +
                "local idx = 2\n" +
                "while idx <= #ARGV do\n" +
                "    for i = 0, numHashes - 1 do\n" +
                "        redis.call('setbit', key, tonumber(ARGV[idx + i]), 1)\n" +
                "    end\n" +
                "    idx = idx + numHashes\n" +
                "end\n" +
                "return true";

        try {
            byte[] batchScriptBytes = batchScriptText.getBytes(StandardCharsets.UTF_8);
            redisTemplate.execute((RedisCallback<Object>) connection ->
                    connection.scriptingCommands().eval(batchScriptBytes, ReturnType.BOOLEAN, 1, allArgs));
        } catch (Exception e) {
            log.error("【Redis】布隆过滤器批量添加失败 | key={} | count={}", filterKey, validCount, e);
        }
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
            // 使用 Pipeline 批量执行，减少网络往返（使用预计算的脚本字节）
            List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (byte[][] allArgs : allArgsList) {
                    connection.scriptingCommands().eval(
                            existsScriptBytes,
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
     * <p>使用 Guava 的 MurmurHash3 x64 128 位标准实现，单次调用获取 128 位哈希值，
     * 拆分为两个 64 位字作为双哈希种子，再通过线性组合生成任意数量的哈希位。
     *
     * <p>相比手写实现的优势：
     * <ul>
     *   <li>标准库实现，经过广泛测试验证，避免手写常量/移位错误</li>
     *   <li>尾部的 switch fall-through 处理由 Guava 内部优化保证正确性</li>
     *   <li>减少约 200 行手容易出现边界错误的代码</li>
     * </ul>
     *
     * @param value     输入值
     * @param numHashes 需要的哈希值数量
     * @param numBits   位数组大小（用于取模）
     * @return 哈希值列表
     */
    private static List<Long> murmurHash3(String value, int numHashes, long numBits) {
        HashCode hashCode = MURMUR3_128.hashString(value, StandardCharsets.UTF_8);
        // 128 位哈希值拆分为两个 64 位字
        // 使用 writeBytesTo 将哈希写入字节数组，前 8 字节为高位，后 8 字节为低位
        byte[] hashBytes = hashCode.asBytes();
        long hash1 = bytesToLong(hashBytes, 0);
        long hash2 = bytesToLong(hashBytes, 8);

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
     * 将字节数组指定偏移处读取 8 字节为小端序 long
     *
     * @param bytes  字节数组
     * @param offset 起始偏移
     * @return 小端序 long 值
     */
    private static long bytesToLong(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFFL)
                | ((bytes[offset + 1] & 0xFFL) << 8)
                | ((bytes[offset + 2] & 0xFFL) << 16)
                | ((bytes[offset + 3] & 0xFFL) << 24)
                | ((bytes[offset + 4] & 0xFFL) << 32)
                | ((bytes[offset + 5] & 0xFFL) << 40)
                | ((bytes[offset + 6] & 0xFFL) << 48)
                | ((bytes[offset + 7] & 0xFFL) << 56);
    }
}
