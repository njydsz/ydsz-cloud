package com.njydsz.common.util.hash;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Hash 工具类 — 非加密哈希与编码
 *
 * <p>提供加密哈希以外的哈希算法和编码方案：
 * <ul>
 *   <li>CRC32 校验和</li>
 *   <li>MurmurHash2 32-bit（非加密哈希，用于哈希表/分片）</li>
 *   <li>Base62 编码/解码（用于短链接 ID）</li>
 * </ul>
 *
 * <p>加密哈希（MD5/SHA-256/HMAC）请使用 {@link com.njydsz.common.util.security.DigestUtils}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class HashUtils {

    private HashUtils() {
        throw new UnsupportedOperationException("HashUtils is a utility class and cannot be instantiated");
    }

    private static final char[] BASE62_CHARS = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
    };

    private static final int BASE62_SIZE = BASE62_CHARS.length;

    // ==================== CRC32 校验和 ====================

    /**
     * 计算 CRC32 校验和
     *
     * @param input 输入字符串
     * @return CRC32 校验和（long 值）
     */
    public static long crc32(String input) {
        if (input == null) {
            return 0L;
        }
        CRC32 crc32 = new CRC32();
        crc32.update(input.getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }

    /**
     * 计算 CRC32 校验和（字节数组）
     *
     * @param input 输入字节数组
     * @return CRC32 校验和（long 值）
     */
    public static long crc32(byte[] input) {
        if (input == null) {
            return 0L;
        }
        CRC32 crc32 = new CRC32();
        crc32.update(input);
        return crc32.getValue();
    }

    // ==================== MurmurHash2 32-bit 算法 ====================

    /**
     * 计算 MurmurHash2 32-bit 哈希值（纯 JDK 实现，无第三方依赖）
     *
     * <p>注意：本方法实现的是 <b>MurmurHash2</b>（常数 m=0x5bd1e995, r=24），
     * 而非 MurmurHash3。如需与 MurmurHash3 实现互操作，请注意版本差异。
     *
     * @param input 输入字符串
     * @return MurmurHash2 32-bit 哈希值
     */
    public static int murmurHash32(String input) {
        if (input == null || input.isEmpty()) {
            return 0;
        }
        return murmurHash32(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算 MurmurHash2 32-bit 哈希值（字节数组）
     *
     * <p>注意：本方法实现的是 <b>MurmurHash2</b>（常数 m=0x5bd1e995, r=24），
     * 而非 MurmurHash3。
     *
     * @param data 输入字节数组
     * @return MurmurHash2 32-bit 哈希值
     */
    public static int murmurHash32(byte[] data) {
        if (data == null || data.length == 0) {
            return 0;
        }

        final int seed = 0x9747b28c;
        final int m = 0x5bd1e995;
        final int r = 24;

        int h = seed ^ data.length;
        int len = data.length;
        int len4 = len >> 2;

        for (int i = 0; i < len4; i++) {
            int i4 = i << 2;
            int k = (data[i4] & 0xff) | ((data[i4 + 1] & 0xff) << 8)
                  | ((data[i4 + 2] & 0xff) << 16) | ((data[i4 + 3] & 0xff) << 24);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
        }

        int offset = len4 << 2;
        int remainder = len & 0x03;
        if (remainder >= 3) {
            h ^= (data[offset + 2] & 0xff) << 16;
        }
        if (remainder >= 2) {
            h ^= (data[offset + 1] & 0xff) << 8;
        }
        if (remainder >= 1) {
            h ^= (data[offset] & 0xff);
            h *= m;
            h ^= h >>> r;
            h *= m;
        }

        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;

        return h;
    }

    // ==================== Base62 编码/解码 ====================

    /**
     * 将字符串哈希值转换为 Base62 编码
     *
     * <p>使用 MurmurHash2 32-bit 计算哈希，再将无符号 32 位整数编码为 Base62 字符串。
     *
     * @param str 输入字符串
     * @return Base62 编码字符串
     */
    public static String hashToBase62(String str) {
        int hash = murmurHash32(str);
        long num = hash & 0xFFFFFFFFL;
        return convertDecToBase62(num);
    }

    /**
     * 将 long 值转换为 Base62 编码
     *
     * @param num long 值
     * @return Base62 编码字符串
     */
    public static String longToBase62(long num) {
        if (num < 0) {
            throw new IllegalArgumentException("Number must be non-negative");
        }
        if (num == 0) {
            return "0";
        }
        return convertDecToBase62(num);
    }

    /**
     * 将 Base62 编码转换为 long 值
     *
     * @param base62 Base62 编码字符串
     * @return long 值
     */
    public static long base62ToLong(String base62) {
        if (base62 == null || base62.isEmpty()) {
            throw new IllegalArgumentException("Base62 string cannot be null or empty");
        }

        long result = 0;
        for (char c : base62.toCharArray()) {
            int index = charToBase62Index(c);
            if (index < 0) {
                throw new IllegalArgumentException("Invalid Base62 character: " + c);
            }
            result = result * BASE62_SIZE + index;
        }
        return result;
    }

    /**
     * 将字节数组转换为 Base62 编码
     *
     * <p><b>注意</b>：前导零字节会被丢弃（因 BigInteger 将字节数组视为无符号大整数，
     * 0x00[0x01] 与 0x01 编码结果相同）。如需保留前导零，请在调用方自行处理。
     *
     * @param bytes 字节数组
     * @return Base62 编码字符串
     */
    public static String bytesToBase62(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        BigInteger bigInteger = new BigInteger(1, bytes);
        return bigIntegerToString(bigInteger, BASE62_CHARS);
    }

    /**
     * 将 Base62 编码转换为字节数组
     *
     * <p>与 {@link #bytesToBase62(byte[])} 互逆。BigInteger.toByteArray() 在最高位为 1 时
     * 会添加前导 0x00 符号字节，本方法会将其剥离以保持与 {@code new BigInteger(1, bytes)} 的对称性。
     *
     * @param base62 Base62 编码字符串
     * @return 字节数组
     */
    public static byte[] base62ToBytes(String base62) {
        if (base62 == null || base62.isEmpty()) {
            return new byte[0];
        }
        BigInteger bigInteger = stringToBigInteger(base62, BASE62_SIZE);
        byte[] bytes = bigInteger.toByteArray();
        // BigInteger.toByteArray() 对正数也可能在首位添加 0x00（当次高位为 1 时用于区分符号），
        // 而 bytesToBase62 使用 new BigInteger(1, bytes) 视为无符号，不会产生前导 0x00。
        // 为保证 round-trip 一致，需剥离该多余的前导 0x00。
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }

    private static String convertDecToBase62(long num) {
        if (num == 0) {
            return "0";
        }
        char[] buffer = new char[11];
        int index = buffer.length;
        while (num > 0) {
            buffer[--index] = BASE62_CHARS[(int) (num % BASE62_SIZE)];
            num /= BASE62_SIZE;
        }
        return new String(buffer, index, buffer.length - index);
    }

    private static int charToBase62Index(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        } else if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 10;
        } else if (c >= 'a' && c <= 'z') {
            return c - 'a' + 36;
        }
        return -1;
    }

    private static String bigIntegerToString(BigInteger number, char[] alphabet) {
        if (number.equals(BigInteger.ZERO)) {
            return String.valueOf(alphabet[0]);
        }

        StringBuilder sb = new StringBuilder();
        BigInteger base = BigInteger.valueOf(alphabet.length);
        BigInteger zero = BigInteger.ZERO;

        while (number.compareTo(zero) > 0) {
            BigInteger[] divAndRemainder = number.divideAndRemainder(base);
            int index = divAndRemainder[1].intValue();
            sb.append(alphabet[index]);
            number = divAndRemainder[0];
        }

        return sb.reverse().toString();
    }

    private static BigInteger stringToBigInteger(String str, int base) {
        BigInteger result = BigInteger.ZERO;
        BigInteger baseBig = BigInteger.valueOf(base);

        for (char c : str.toCharArray()) {
            int index = charToBase62Index(c);
            if (index < 0) {
                throw new IllegalArgumentException("Invalid character: " + c);
            }
            result = result.multiply(baseBig).add(BigInteger.valueOf(index));
        }

        return result;
    }
}
