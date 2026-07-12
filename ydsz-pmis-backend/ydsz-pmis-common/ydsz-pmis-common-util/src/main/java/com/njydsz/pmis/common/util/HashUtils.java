package com.njydsz.pmis.common.util;

/**
 * 哈希工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class HashUtils {

    private HashUtils() {
    }

    /**
     * FNV-1a 32位哈希
     *
     * @param input 输入字符串
     * @return 32位哈希值
     */
    public static int fnv1a32(String input) {
        if (input == null) {
            return 0;
        }
        final int FNV_OFFSET = 0x811c9dc5;
        final int FNV_PRIME = 0x01000193;
        int hash = FNV_OFFSET;
        for (byte b : input.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            hash ^= (b & 0xFF);
            hash *= FNV_PRIME;
        }
        return hash;
    }

    /**
     * FNV-1a 64位哈希
     *
     * @param input 输入字符串
     * @return 64位哈希值
     */
    public static long fnv1a64(String input) {
        if (input == null) {
            return 0;
        }
        final long FNV_OFFSET = 0xcbf29ce484222325L;
        final long FNV_PRIME = 0x100000001b3L;
        long hash = FNV_OFFSET;
        for (byte b : input.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            hash ^= (b & 0xFF);
            hash *= FNV_PRIME;
        }
        return hash;
    }

    /**
     * MurmurHash3 32位
     *
     * @param data 字节数组
     * @param seed 种子
     * @return 32位哈希值
     */
    public static int murmurHash3_32(byte[] data, int seed) {
        if (data == null || data.length == 0) {
            return 0;
        }
        final int C1 = 0xcc9e2d51;
        final int C2 = 0x1b873593;
        int h = seed;
        int len = data.length;
        int nblocks = len / 4;

        for (int i = 0; i < nblocks; i++) {
            int k = (data[i * 4] & 0xFF)
                    | ((data[i * 4 + 1] & 0xFF) << 8)
                    | ((data[i * 4 + 2] & 0xFF) << 16)
                    | ((data[i * 4 + 3] & 0xFF) << 24);
            k *= C1;
            k = Integer.rotateLeft(k, 15);
            k *= C2;
            h ^= k;
            h = Integer.rotateLeft(h, 13);
            h = h * 5 + 0xe6546b64;
        }

        int tail = nblocks * 4;
        int k1 = 0;
        switch (len - tail) {
            case 3:
                k1 ^= (data[tail + 2] & 0xFF) << 16;
            case 2:
                k1 ^= (data[tail + 1] & 0xFF) << 8;
            case 1:
                k1 ^= (data[tail] & 0xFF);
                k1 *= C1;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= C2;
                h ^= k1;
        }

        h ^= len;
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;

        return h;
    }

    /**
     * 一致性哈希节点定位
     *
     * @param key       键
     * @param nodes     节点列表
     * @return 节点索引
     */
    public static int consistentHash(String key, int nodes) {
        if (nodes <= 0) {
            return 0;
        }
        return (fnv1a32(key) & 0x7FFFFFFF) % nodes;
    }
}
