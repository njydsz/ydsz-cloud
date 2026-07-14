package com.njydsz.pmis.common.util.hash;

import java.io.Serial;
import java.io.Serializable;
import java.util.BitSet;
import java.util.function.Function;

/**
 * 布隆过滤器工具类
 *
 * <p>纯 JDK 实现的布隆过滤器，适用于海量数据的去重判断。
 * 特点：空间效率高、查询时间 O(k)、存在误判率（假阳性）但无假阴性。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 创建布隆过滤器：预期 100 万元素，误判率 1%
 * BloomFilterUtils<String> filter = BloomFilterUtils.create(1_000_000, 0.01);
 *
 * // 添加元素
 * filter.add("user-001");
 * filter.add("user-002");
 *
 * // 查询元素
 * boolean mightContain = filter.mightContain("user-001");  // true
 * boolean notExist = filter.mightContain("user-999");      // false（确定不存在）
 * }</pre>
 *
 * @param <T> 元素类型
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public class BloomFilterUtils<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final BitSet bitSet;
    private final int bitSetSize;
    private final int numHashFunctions;
    private final Function<T, byte[]> serializer;
    private int elementCount;

    private BloomFilterUtils(int bitSetSize, int numHashFunctions, Function<T, byte[]> serializer) {
        this.bitSetSize = bitSetSize;
        this.numHashFunctions = numHashFunctions;
        this.bitSet = new BitSet(bitSetSize);
        this.serializer = serializer;
        this.elementCount = 0;
    }

    /**
     * 创建布隆过滤器
     *
     * @param expectedElements 预期元素数量
     * @param falsePositiveRate 可接受的误判率（0.0 ~ 1.0）
     * @param serializer     元素序列化函数（将元素转换为 byte[]）
     * @param <T>            元素类型
     * @return 布隆过滤器实例
     */
    public static <T> BloomFilterUtils<T> create(long expectedElements, double falsePositiveRate,
                                                  Function<T, byte[]> serializer) {
        if (expectedElements <= 0) {
            throw new IllegalArgumentException("expectedElements must be positive");
        }
        if (falsePositiveRate <= 0 || falsePositiveRate >= 1) {
            throw new IllegalArgumentException("falsePositiveRate must be between 0 and 1 (exclusive)");
        }

        // 最优位数组大小：m = -n * ln(p) / (ln(2)^2)
        int bitSetSize = (int) Math.ceil(-expectedElements * Math.log(falsePositiveRate) / (Math.log(2) * Math.log(2)));
        bitSetSize = Math.max(bitSetSize, 64);

        // 最优哈希函数数量：k = (m/n) * ln(2)
        int numHashFunctions = (int) Math.max(1, Math.round((double) bitSetSize / expectedElements * Math.log(2)));

        return new BloomFilterUtils<>(bitSetSize, numHashFunctions, serializer);
    }

    /**
     * 创建字符串布隆过滤器
     *
     * @param expectedElements 预期元素数量
     * @param falsePositiveRate 可接受的误判率
     * @return 布隆过滤器实例
     */
    public static BloomFilterUtils<String> createStringFilter(long expectedElements, double falsePositiveRate) {
        return create(expectedElements, falsePositiveRate,
                s -> s == null ? new byte[0] : s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 添加元素到布隆过滤器
     *
     * @param element 要添加的元素
     */
    public void add(T element) {
        if (element == null) {
            return;
        }
        byte[] data = serializer.apply(element);
        int[] positions = getBitPositions(data);
        for (int position : positions) {
            bitSet.set(position);
        }
        elementCount++;
    }

    /**
     * 判断元素是否可能存在
     *
     * @param element 要查询的元素
     * @return 如果返回 false，则元素一定不存在；如果返回 true，元素可能存在（有误判率）
     */
    public boolean mightContain(T element) {
        if (element == null) {
            return false;
        }
        byte[] data = serializer.apply(element);
        int[] positions = getBitPositions(data);
        for (int position : positions) {
            if (!bitSet.get(position)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 计算元素的多个哈希位置
     *
     * <p>使用双重哈希（Double Hashing）技术：
     * hash_i = (hash1 + i * hash2) % bitSetSize
     *
     * @param data 元素的字节数组
     * @return 位数组中的位置数组
     */
    private int[] getBitPositions(byte[] data) {
        int hash1 = murmurHash3(data, 0);
        int hash2 = murmurHash3(data, hash1);

        int[] positions = new int[numHashFunctions];
        int combined = hash1;
        for (int i = 0; i < numHashFunctions; i++) {
            positions[i] = Math.floorMod(combined, bitSetSize);
            combined += hash2;
            if (combined < 0) {
                combined = ~combined;
            }
        }
        return positions;
    }

    /**
     * 简化版 MurmurHash3 实现
     */
    private static int murmurHash3(byte[] data, int seed) {
        int h = seed;
        for (byte b : data) {
            h ^= b;
            h *= 0x5bd1e995;
            h ^= h >>> 15;
        }
        return h;
    }

    /**
     * 获取当前元素数量
     *
     * @return 已添加的元素数量
     */
    public int getElementCount() {
        return elementCount;
    }

    /**
     * 获取位数组大小
     *
     * @return 位数组大小
     */
    public int getBitSetSize() {
        return bitSetSize;
    }

    /**
     * 获取哈希函数数量
     *
     * @return 哈希函数数量
     */
    public int getNumHashFunctions() {
        return numHashFunctions;
    }

    /**
     * 获取估算的误判率
     *
     * @return 当前误判率
     */
    public double getEstimatedFalsePositiveRate() {
        return Math.pow(1 - Math.exp(-numHashFunctions * (double) elementCount / bitSetSize), numHashFunctions);
    }

    /**
     * 清空布隆过滤器
     */
    public void clear() {
        bitSet.clear();
        elementCount = 0;
    }
}
