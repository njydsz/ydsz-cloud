package com.njydsz.common.util.hash;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link HashUtils} 单元测试 — 覆盖 CRC32/MurmurHash2 32-bit/Base62 关键路径。
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
        @DisplayName("空字符串 CRC32 为非负常量")
        void emptyStringCrcIsConstant() {
            assertThat(HashUtils.crc32("")).isNotNegative();
        }
    }

    @Nested
    @DisplayName("MurmurHash2 32-bit")
    class MurmurHash32 {

        @Test
        @DisplayName("相同输入产生相同 MurmurHash2")
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

        @Test
        @DisplayName("null / 空输入返回 0")
        void nullOrEmptyReturnsZero() {
            assertThat(HashUtils.murmurHash32((String) null)).isZero();
            assertThat(HashUtils.murmurHash32("")).isZero();
            assertThat(HashUtils.murmurHash32((byte[]) null)).isZero();
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
        @DisplayName("负数抛出 IllegalArgumentException")
        void negativeThrows() {
            try {
                HashUtils.longToBase62(-1L);
                assertThat(false).as("should throw").isTrue();
            } catch (IllegalArgumentException e) {
                // expected
            }
        }

        @Test
        @DisplayName("字节 → Base62 → 字节往返一致（无前导零场景）")
        void bytesRoundtrip() {
            byte[] input = "ydsz-snowflake-id-12345".getBytes();
            String encoded = HashUtils.bytesToBase62(input);
            byte[] decoded = HashUtils.base62ToBytes(encoded);
            assertThat(decoded).isEqualTo(input);
        }

        @Test
        @DisplayName("高位为 1 的字节往返一致（验证 sign byte 剥离）")
        void bytesRoundtripHighBitSet() {
            byte[] input = new byte[]{(byte) 0xFF, (byte) 0xCA, (byte) 0xFE};
            String encoded = HashUtils.bytesToBase62(input);
            byte[] decoded = HashUtils.base62ToBytes(encoded);
            assertThat(decoded).isEqualTo(input);
        }

        @Test
        @DisplayName("hashToBase62 同一字符串稳定")
        void hashToBase62Stable() {
            String b1 = HashUtils.hashToBase62("consistent-hash-key");
            String b2 = HashUtils.hashToBase62("consistent-hash-key");
            assertThat(b1).isEqualTo(b2);
        }
    }
}
