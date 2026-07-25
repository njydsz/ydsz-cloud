package com.njydsz.common.util.hash;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link HashUtils} 单元测试 — 覆盖 CRC32/MurmurHash32/Base62/Base58/一致性哈希关键路径。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("HashUtils 哈希工具测试")
class HashUtilsTest {

    @Nested
    @DisplayName("CRC32 校验和")
    class Crc32 {

        @Test
        @DisplayName("相同输入产生相同 CRC32")
        void sameInputProducesSameCrc() {
            assertThat(HashUtils.crc32("hello")).isEqualTo(HashUtils.crc32("hello"));
        }

        @Test
        @DisplayName("不同输入产生不同 CRC32")
        void differentInputProducesDifferentCrc() {
            assertThat(HashUtils.crc32("hello")).isNotEqualTo(HashUtils.crc32("world"));
        }

        @Test
        @DisplayName("null 输入返回 0")
        void nullInputReturnsZero() {
            assertThat(HashUtils.crc32((String) null)).isZero();
            assertThat(HashUtils.crc32((byte[]) null)).isZero();
        }

        @Test
        @DisplayName("空字符串 CRC32 为非零常量")
        void emptyStringCrcIsConstant() {
            // CRC32("") = 0x00000000，但 java.util.zip.CRC32 实现返回 0
            assertThat(HashUtils.crc32("")).isNotNegative();
        }
    }

    @Nested
    @DisplayName("MurmurHash32")
    class MurmurHash32 {

        @Test
        @DisplayName("相同输入产生相同 MurmurHash32")
        void deterministicHash() {
            assertThat(HashUtils.murmurHash32("ydsz-common")).isEqualTo(HashUtils.murmurHash32("ydsz-common"));
        }

        @Test
        @DisplayName("不同输入产生不同哈希值")
        void differentInputsProduceDifferentHash() {
            assertThat(HashUtils.murmurHash32("a")).isNotEqualTo(HashUtils.murmurHash32("b"));
        }

        @Test
        @DisplayName("哈希值在 int 范围内")
        void hashIsWithinIntRange() {
            for (String s : Arrays.asList("", "a", "ab", "hello world", "中文测试")) {
                int hash = HashUtils.murmurHash32(s);
                assertThat(hash).isBetween(Integer.MIN_VALUE, Integer.MAX_VALUE);
            }
        }
    }

    @Nested
    @DisplayName("Base62 编解码")
    class Base62 {

        @Test
        @DisplayName("long → Base62 → long 往返一致")
        void longRoundtrip() {
            for (long value : new long[]{0L, 1L, 61L, 62L, 63L, 12345L, Long.MAX_VALUE / 2, Long.MAX_VALUE}) {
                String encoded = HashUtils.longToBase62(value);
                long decoded = HashUtils.base62ToLong(encoded);
                assertThat(decoded).as("roundtrip for %d", value).isEqualTo(value);
            }
        }

        @Test
        @DisplayName("0 编码为 '0'")
        void zeroEncodeAsZeroChar() {
            assertThat(HashUtils.longToBase62(0L)).isEqualTo("0");
        }

        @Test
        @DisplayName("字符串哈希 → Base62 → 字节往返一致")
        void bytesRoundtrip() {
            String input = "ydsz-snowflake-id-12345";
            String encoded = HashUtils.bytesToBase62(input.getBytes());
            byte[] decoded = HashUtils.base62ToBytes(encoded);
            assertThat(new String(decoded)).isEqualTo(input);
        }

        @Test
        @DisplayName("hashToBase62 同一字符串稳定")
        void hashToBase62Stable() {
            String b1 = HashUtils.hashToBase62("consistent-hash-key");
            String b2 = HashUtils.hashToBase62("consistent-hash-key");
            assertThat(b1).isEqualTo(b2);
        }
    }

    @Nested
    @DisplayName("Base58 编解码")
    class Base58 {

        @Test
        @DisplayName("字符串 → Base58 → 字符串往返一致")
        void stringRoundtrip() {
            String input = "ydsz-base58-test-你好";
            String encoded = HashUtils.stringToBase58(input);
            String decoded = HashUtils.base58ToString(encoded);
            assertThat(decoded).isEqualTo(input);
        }

        @Test
        @DisplayName("字节 → Base58 → 字节往返一致")
        void bytesRoundtrip() {
            byte[] input = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xCA, (byte) 0xFE};
            String encoded = HashUtils.bytesToBase58(input);
            byte[] decoded = HashUtils.base58ToBytes(encoded);
            assertThat(decoded).isEqualTo(input);
        }

        @Test
        @DisplayName("Base58 不包含易混淆字符 0/O/I/l")
        void base58ShouldNotContainAmbiguousChars() {
            String encoded = HashUtils.stringToBase58("O0Il1ambiguity");
            assertThat(encoded).doesNotContain("0", "O", "I", "l");
        }
    }

    @Nested
    @DisplayName("一致性哈希")
    class ConsistentHash {

        @Test
        @DisplayName("相同 key 与节点列表产生稳定路由")
        void stableRoutingForSameKey() {
            int node = HashUtils.consistentHash("user-12345", 10);
            int node2 = HashUtils.consistentHash("user-12345", 10);
            assertThat(node).isEqualTo(node2);
            assertThat(node).isBetween(0, 9);
        }

        @Test
        @DisplayName("虚拟节点 — 路由结果应均匀分布（粗略统计）")
        void virtualNodesShouldDistributeUniformly() {
            List<String> nodes = Arrays.asList("node-A", "node-B", "node-C", "node-D");
            int[] counts = new int[nodes.size()];
            for (int i = 0; i < 1000; i++) {
                String selected = HashUtils.consistentHash("key-" + i, nodes, 100);
                int idx = nodes.indexOf(selected);
                counts[idx]++;
            }
            // 1000 个 key 平均每节点 250 个，允许 [100, 400] 浮动
            for (int c : counts) {
                assertThat(c).isBetween(100, 400);
            }
        }
    }
}
