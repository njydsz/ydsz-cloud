package com.njydsz.common.util.hash;

import java.io.Serial;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.Function;

/**
 * 布隆过滤器工具类
 *
 * <p>纯 JDK 实现的布隆过滤器，适用于海量数据的去重判断。
 * 特点：空间效率高、查询时间 O(k)、存在误判率（假阳性）但无假阴性。
 *
 * <p><b>线程安全：</b>内部使用 {@link AtomicLongArray} 实现无锁并发读写，
 * 支持多线程同时调用 {@link #add} 和 {@link #mightContain}。
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
 * @author ydsz-team
 * @since 1.0.0
 */
public class BloomFilterUtils<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 位数组，使用 AtomicLongArray 实现无锁并发读写 */
    private final AtomicLongArray bits;

    private final int bitSetSize;
    private final int numHashFunctions;
    private final Function<T, byte[]> serializer;
    private volatile int elementCount;

    private BloomFilterUtils(int bitSetSize, int numHashFunctions, Function<T, byte[]> serializer) {
        this.bitSetSize = bitSetSize;
        this.numHashFunctions = numHashFunctions;
        int longArrayLength = (bitSetSize + 63) >>> 6;
        this.bits = new AtomicLongArray(longArrayLength);
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
     * 添加元素到布隆过滤器（线程安全）
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
            setBit(position);
        }
        elementCount++;
    }

    /**
     * 判断元素是否可能存在（线程安全）
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
            if (!getBit(position)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 原子地设置指定位为 1
     *
     * @param bitIndex 位索引
     */
    private void setBit(int bitIndex) {
        int longIndex = bitIndex >>> 6;
        long bitMask = 1L << bitIndex;
        long oldValue;
        long newValue;
        do {
            oldValue = bits.get(longIndex);
            if ((oldValue & bitMask) != 0) {
                return; // 已经设置了，无需 CAS
            }
            newValue = oldValue | bitMask;
        } while (!bits.compareAndSet(longIndex, oldValue, newValue));
    }

    /**
     * 原子地读取指定位
     *
     * @param bitIndex 位索引
     * @return 该位是否为 1
     */
    private boolean getBit(int bitIndex) {
        int longIndex = bitIndex >>> 6;
        long bitMask = 1L << bitIndex;
        return (bits.get(longIndex) & bitMask) != 0;
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
     * MurmurHash3 x86_32 实现
     *
     * <p>参考 Google Guava Hashing.murmur3_32 实现，
     * 提供高质量的哈希分布，确保布隆过滤器 bit 位均匀分布。
     *
     * @param data 待哈希的字节数组
     * @param seed 哈希种子
     * @return 32 位哈希值
     */
    private static int murmurHash3(byte[] data, int seed) {
        int h1 = seed;
        int len = data.length;
        int nblocks = len >> 2;

        // body
        for (int i = 0; i < nblocks; i++) {
            int k1 = (data[i << 2] & 0xff)
                    | ((data[(i << 2) + 1] & 0xff) << 8)
                    | ((data[(i << 2) + 2] & 0xff) << 16)
                    | ((data[(i << 2) + 3] & 0xff) << 24);

            k1 *= 0xcc9e2d51;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= 0x1b873593;

            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        // tail
        int offset = nblocks << 2;
        int k1 = 0;
        switch (len - offset) {
            case 3:
                k1 ^= (data[offset + 2] & 0xff) << 16;
                // fall through
            case 2:
                k1 ^= (data[offset + 1] & 0xff) << 8;
                // fall through
            case 1:
                k1 ^= (data[offset] & 0xff);
                k1 *= 0xcc9e2d51;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= 0x1b873593;
                h1 ^= k1;
                break;
            default:
                // no tail bytes
        }

        // finalization
        h1 ^= len;
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;

        return h1;
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
        for (int i = 0; i < bits.length(); i++) {
            bits.set(i, 0L);
        }
        elementCount = 0;
    }
}
