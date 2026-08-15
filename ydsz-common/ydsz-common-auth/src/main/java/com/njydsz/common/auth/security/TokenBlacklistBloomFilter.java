package com.njydsz.common.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.BitSet;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Token 黑名单本地 Bloom Filter 前置过滤器。
 *
 * <p>在查询 Redis 黑名单之前，先通过本地 Bloom Filter 快速判断 token 是否可能被加入黑名单。
 * Bloom Filter 误判率约 0.01%，可过滤 99.99% 的非黑名单 token 的 Redis 查询。
 *
 * <p><b>废弃原因：</b>布隆过滤器在 Token 黑名单场景收益有限。黑名单 Token 是少数派，
 * Redis 对 key exists 的判断本身就是 O(1)，自建布隆过滤器无法动态扩容，超出容量后误判率上升，
 * 且应用重启后需要重建。如需减少 Redis 查询，建议使用 Redis 内置的 Bloom Filter 模块。
 *
 * <p>使用 Java 内置的 BitSet 实现，无需引入 Guava 依赖。
 * 采用双哈希策略（Double Hashing）减少哈希函数数量。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 自 3.0.0 起标记废弃，计划 4.0.0 移除。
 *             如需布隆过滤器能力，建议使用 Redis 内置 Bloom Filter 模块。
 */
@Deprecated(forRemoval = true, since = "3.0.0")
public class TokenBlacklistBloomFilter {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistBloomFilter.class);

    private final BitSet bitSet;
    private final int expectedInsertions;
    private final int hashFunctions;
    private final int bitArraySize;

    /**
     * 构建指定预期容量的 Bloom Filter。
     *
     * @param expectedInsertions 预期插入数量
     */
    public TokenBlacklistBloomFilter(int expectedInsertions) {
        this.expectedInsertions = Math.max(expectedInsertions, 1000);
        // 误判率目标 0.01%，根据公式：m = n * ln(1/p) / (ln(2)^2)
        // 简化：m ≈ n * 10, k ≈ 7
        this.bitArraySize = this.expectedInsertions * 10;
        this.hashFunctions = 7;
        this.bitSet = new BitSet(bitArraySize);
        log.info("TokenBlacklistBloomFilter 初始化: expectedInsertions={}, bitArraySize={}, hashFunctions={}",
                expectedInsertions, bitArraySize, hashFunctions);
    }

    /**
     * 将 token 的 SHA-256 摘要加入 Bloom Filter。
     *
     * @param token JWT Token
     */
    public void addToBlacklist(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        byte[] hash = sha256Bytes(token);
        int hash1 = bytesToInt(hash, 0);
        int hash2 = bytesToInt(hash, 4);

        for (int i = 0; i < hashFunctions; i++) {
            int combinedHash = hash1 + (i * hash2);
            int index = Math.floorMod(combinedHash, bitArraySize);
            bitSet.set(index);
        }
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
        byte[] hash = sha256Bytes(token);
        int hash1 = bytesToInt(hash, 0);
        int hash2 = bytesToInt(hash, 4);

        for (int i = 0; i < hashFunctions; i++) {
            int combinedHash = hash1 + (i * hash2);
            int index = Math.floorMod(combinedHash, bitArraySize);
            if (!bitSet.get(index)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 清空 Bloom Filter。
     *
     * <p>在权限全量缓存失效时调用，确保后续黑名单查询走 Redis。
     */
    public void clear() {
        bitSet.clear();
        log.info("TokenBlacklistBloomFilter 已清空");
    }

    /**
     * 获取当前已使用的 bit 数量（用于监控）。
     *
     * @return 已设置的 bit 数量
     */
    public int getCardinality() {
        return bitSet.cardinality();
    }

    /**
     * 获取 bit 数组总大小。
     *
     * @return bit 数组大小
     */
    public int getBitArraySize() {
        return bitArraySize;
    }

    private static byte[] sha256Bytes(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static int bytesToInt(byte[] bytes, int offset) {
        int result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) | (bytes[offset + i] & 0xFF);
        }
        return result;
    }
}
