package com.njydsz.common.util.hash;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.CRC32;

/**
 * Hash 工具类 — 非加密哈希与编码
 *
 * <p>提供加密哈希以外的哈希算法和编码方案：
 * <ul>
 *   <li>CRC32 校验和</li>
 *   <li>MurmurHash32（非加密哈希，用于哈希表/分片）</li>
 *   <li>Base62 编码/解码（用于短链接 ID）</li>
 *   <li>Base58 编码/解码（用于邀请码、加密货币地址）</li>
 *   <li>一致性哈希（用于分布式节点路由）</li>
 * </ul>
 *
 * <p>加密哈希（MD5/SHA-256/HMAC）请使用 {@link com.njydsz.common.util.security.DigestUtils}。
 * <br>布隆过滤器请使用 {@link com.njydsz.common.util.BloomFilterUtils}。
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

    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    private static final int BASE62_SIZE = BASE62_CHARS.length;

    private static final int BASE58_SIZE = BASE58_ALPHABET.length();

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

    // ==================== MurmurHash32 算法 ====================

    /**
     * 计算 MurmurHash32 哈希值（纯 JDK 实现，无第三方依赖）
     *
     * @param input 输入字符串
     * @return MurmurHash32 哈希值
     */
    public static int murmurHash32(String input) {
        if (input == null || input.isEmpty()) {
            return 0;
        }
        return murmurHash32(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算 MurmurHash32 哈希值（字节数组）
     *
     * @param data 输入字节数组
     * @return MurmurHash32 哈希值
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
     * @param str 输入字符串
     * @return Base62 编码字符串
     */
    public static String hashToBase62(String str) {
        int hash = murmurHash32(str);
        long num = hash < 0 ? Integer.MAX_VALUE - (long) hash : hash;
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
     * @param base62 Base62 编码字符串
     * @return 字节数组
     */
    public static byte[] base62ToBytes(String base62) {
        if (base62 == null || base62.isEmpty()) {
            return new byte[0];
        }
        BigInteger bigInteger = stringToBigInteger(base62, BASE62_SIZE);
        return bigInteger.toByteArray();
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

    // ==================== Base58 编码/解码 ====================

    /**
     * 将字符串转换为 Base58 编码（用于短链接、邀请码等）
     *
     * @param input 输入字符串
     * @return Base58 编码字符串
     */
    public static String stringToBase58(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return bytesToBase58(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将字节数组转换为 Base58 编码
     *
     * @param input 字节数组
     * @return Base58 编码字符串
     */
    public static String bytesToBase58(byte[] input) {
        if (input == null || input.length == 0) {
            return "";
        }

        StringBuilder encoded = new StringBuilder();
        BigInteger bigInt = new BigInteger(1, input);
        BigInteger base = BigInteger.valueOf(BASE58_SIZE);
        BigInteger zero = BigInteger.ZERO;

        while (bigInt.compareTo(zero) > 0) {
            BigInteger[] divAndRemainder = bigInt.divideAndRemainder(base);
            encoded.append(BASE58_ALPHABET.charAt(divAndRemainder[1].intValue()));
            bigInt = divAndRemainder[0];
        }

        for (byte b : input) {
            if (b == 0) {
                encoded.append(BASE58_ALPHABET.charAt(0));
            } else {
                break;
            }
        }

        return encoded.reverse().toString();
    }

    /**
     * 将 Base58 编码解码为字符串
     *
     * @param base58 Base58 编码字符串
     * @return 解码后的字符串
     */
    public static String base58ToString(String base58) {
        if (base58 == null || base58.isEmpty()) {
            return "";
        }
        byte[] bytes = base58ToBytes(base58);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 将 Base58 编码解码为字节数组
     *
     * @param base58 Base58 编码字符串
     * @return 字节数组
     */
    public static byte[] base58ToBytes(String base58) {
        if (base58 == null || base58.isEmpty()) {
            return new byte[0];
        }

        BigInteger decoded = BigInteger.ZERO;
        BigInteger base = BigInteger.valueOf(BASE58_SIZE);

        for (char c : base58.toCharArray()) {
            int index = BASE58_ALPHABET.indexOf(c);
            if (index < 0) {
                throw new IllegalArgumentException("Invalid Base58 character: " + c);
            }
            decoded = decoded.multiply(base).add(BigInteger.valueOf(index));
        }

        byte[] bytes = decoded.toByteArray();

        for (char c : base58.toCharArray()) {
            if (c == BASE58_ALPHABET.charAt(0)) {
                byte[] newBytes = new byte[bytes.length + 1];
                System.arraycopy(bytes, 0, newBytes, 1, bytes.length);
                bytes = newBytes;
            } else {
                break;
            }
        }

        int firstNonZero = 0;
        while (firstNonZero < bytes.length && bytes[firstNonZero] == 0) {
            firstNonZero++;
        }

        if (firstNonZero == bytes.length) {
            return new byte[0];
        }

        byte[] result = new byte[bytes.length - firstNonZero];
        System.arraycopy(bytes, firstNonZero, result, 0, result.length);

        return result;
    }

    // ==================== 一致性哈希算法 ====================

    /**
     * 一致性哈希 - 将键映射到虚拟节点
     *
     * @param key 键
     * @param numberOfNodes 节点数量
     * @return 节点索引
     */
    public static int consistentHash(String key, int numberOfNodes) {
        if (key == null || key.isEmpty() || numberOfNodes <= 0) {
            return 0;
        }
        int hash = murmurHash32(key);
        return Math.abs(hash) % numberOfNodes;
    }

    /**
     * 一致性哈希 - 带虚拟节点的完整实现
     *
     * @param key 键
     * @param nodes 节点列表
     * @param virtualNodes 每个真实节点的虚拟节点数量
     * @return 选中的节点
     */
    public static <T> T consistentHash(String key, List<T> nodes, int virtualNodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        if (nodes.size() == 1) {
            return nodes.get(0);
        }

        SortedMap<Integer, T> circle = new TreeMap<>();

        for (T node : nodes) {
            for (int i = 0; i < virtualNodes; i++) {
                String virtualNodeKey = node.toString() + "##" + i;
                int hash = murmurHash32(virtualNodeKey);
                circle.put(hash, node);
            }
        }

        int keyHash = murmurHash32(key);

        SortedMap<Integer, T> tailMap = circle.tailMap(keyHash);
        Integer nodeHash = tailMap.isEmpty() ? circle.firstKey() : tailMap.firstKey();

        return circle.get(nodeHash);
    }
}
