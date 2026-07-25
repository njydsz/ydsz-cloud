package com.njydsz.common.util.encoding;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * 编码工具类（Base64 / Base32 / Base16 / URL 编码统一入口）
 *
 * <p>对标 Apache Commons Codec 与 Guava BaseEncoding，零依赖纯 JDK 实现。
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>Base64</b>：标准 / URL 安全 / MIME 三种模式，编解码双向</li>
 *   <li><b>Base32</b>：RFC 4648 标准 Base32（A-Z2-7，填充 =），编解码双向</li>
 *   <li><b>Base16</b>：即 Hex 十六进制（0-9A-F / 0-9a-f），编解码双向</li>
 *   <li><b>URL</b>：application/x-www-form-urlencoded 编码</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // Base64
 * String b64 = EncodingUtils.encodeBase64("hello".getBytes(StandardCharsets.UTF_8));
 * byte[] decoded = EncodingUtils.decodeBase64(b64);
 *
 * // Base32
 * String b32 = EncodingUtils.encodeBase32("hello".getBytes(StandardCharsets.UTF_8));
 *
 * // Hex
 * String hex = EncodingUtils.encodeHex(new byte[]{(byte) 0xCA, (byte) 0xFE});
 * // 结果："CAFE"
 *
 * // URL 编码
 * String encoded = EncodingUtils.encodeUrl("a b&c=中文", StandardCharsets.UTF_8);
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class EncodingUtils {

    /** RFC 4648 Base32 字符表（大写）。 */
    private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    /** Base32 解码表（-1 表示非法字符）。 */
    private static final int[] BASE32_DECODE_TABLE = buildBase32DecodeTable();

    /** RFC 4648 Base16（Hex）大写字符表。 */
    private static final char[] HEX_UPPER = "0123456789ABCDEF".toCharArray();

    /** RFC 4648 Base16（Hex）小写字符表。 */
    private static final char[] HEX_LOWER = "0123456789abcdef".toCharArray();

    private EncodingUtils() {
        throw new UnsupportedOperationException("EncodingUtils is a utility class and cannot be instantiated");
    }

    // ============== Base64 ==============

    /**
     * 标准 Base64 编码（RFC 4648，含 = 填充）。
     *
     * @param data 待编码字节数组，不能为 null
     * @return Base64 字符串
     */
    public static String encodeBase64(byte[] data) {
        Objects.requireNonNull(data, "data");
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * 标准 Base64 解码。
     *
     * @param text Base64 字符串，不能为 null
     * @return 解码后的字节数组
     * @throws IllegalArgumentException 输入不是合法 Base64
     */
    public static byte[] decodeBase64(String text) {
        Objects.requireNonNull(text, "text");
        return Base64.getDecoder().decode(text);
    }

    /**
     * URL 安全 Base64 编码（- 替换 +，_ 替换 /，无 = 填充）。
     *
     * @param data 待编码字节数组
     * @return URL 安全 Base64 字符串
     */
    public static String encodeBase64Url(byte[] data) {
        Objects.requireNonNull(data, "data");
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /**
     * URL 安全 Base64 解码（兼容有/无填充）。
     *
     * @param text URL 安全 Base64 字符串
     * @return 解码后的字节数组
     */
    public static byte[] decodeBase64Url(String text) {
        Objects.requireNonNull(text, "text");
        return Base64.getUrlDecoder().decode(text);
    }

    // ============== Base32 ==============

    /**
     * Base32 编码（RFC 4648，大写字母 + 数字 2-7，含 = 填充）。
     *
     * @param data 待编码字节数组，不能为 null
     * @return Base32 字符串
     */
    public static String encodeBase32(byte[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int idx = (buffer >> (bitsLeft - 5)) & 0x1F;
                sb.append(BASE32_ALPHABET[idx]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int idx = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(BASE32_ALPHABET[idx]);
        }
        // 填充到 8 的倍数
        while (sb.length() % 8 != 0) {
            sb.append('=');
        }
        return sb.toString();
    }

    /**
     * Base32 解码（大小写不敏感，容忍 = 填充缺失/多余）。
     *
     * @param text Base32 字符串，不能为 null
     * @return 解码后的字节数组
     * @throws IllegalArgumentException 输入含非法字符
     */
    public static byte[] decodeBase32(String text) {
        Objects.requireNonNull(text, "text");
        String upper = text.replace("=", "").toUpperCase();
        if (upper.isEmpty()) {
            return new byte[0];
        }
        byte[] out = new byte[upper.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int idx = 0;
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            int v = (c < 128) ? BASE32_DECODE_TABLE[c] : -1;
            if (v < 0) {
                throw new IllegalArgumentException("Illegal Base32 character: " + c + " at index " + i);
            }
            buffer = (buffer << 5) | v;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out[idx++] = (byte) ((buffer >> bitsLeft) & 0xFF);
            }
        }
        return (idx == out.length) ? out : java.util.Arrays.copyOf(out, idx);
    }

    // ============== Base16 / Hex ==============

    /**
     * 十六进制编码（大写）。
     *
     * @param data 待编码字节数组
     * @return 大写 Hex 字符串
     */
    public static String encodeHex(byte[] data) {
        return encodeHex(data, true);
    }

    /**
     * 十六进制编码。
     *
     * @param data   待编码字节数组
     * @param upper  true=大写，false=小写
     * @return Hex 字符串
     */
    public static String encodeHex(byte[] data, boolean upper) {
        Objects.requireNonNull(data, "data");
        char[] table = upper ? HEX_UPPER : HEX_LOWER;
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int v = data[i] & 0xFF;
            out[i * 2] = table[v >>> 4];
            out[i * 2 + 1] = table[v & 0x0F];
        }
        return new String(out);
    }

    /**
     * 十六进制解码（大小写不敏感）。
     *
     * @param text Hex 字符串（长度必须为偶数）
     * @return 解码后的字节数组
     * @throws IllegalArgumentException 长度奇数或含非法字符
     */
    public static byte[] decodeHex(String text) {
        Objects.requireNonNull(text, "text");
        int len = text.length();
        if ((len & 0x01) != 0) {
            throw new IllegalArgumentException("Hex string must have even length: " + len);
        }
        byte[] out = new byte[len / 2];
        for (int i = 0, j = 0; i < len; i += 2, j++) {
            int hi = hexCharToDigit(text.charAt(i));
            int lo = hexCharToDigit(text.charAt(i + 1));
            out[j] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    // ============== URL 编码 ==============

    /**
     * URL 编码（application/x-www-form-urlencoded，UTF-8）。
     *
     * @param text 待编码字符串
     * @return URL 编码字符串
     */
    public static String encodeUrl(String text) {
        return encodeUrl(text, StandardCharsets.UTF_8);
    }

    /**
     * URL 编码（指定字符集）。
     *
     * @param text    待编码字符串
     * @param charset 字符集
     * @return URL 编码字符串
     */
    public static String encodeUrl(String text, Charset charset) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(charset, "charset");
        return java.net.URLEncoder.encode(text, charset);
    }

    /**
     * URL 解码（UTF-8）。
     *
     * @param text URL 编码字符串
     * @return 解码后字符串
     */
    public static String decodeUrl(String text) {
        return decodeUrl(text, StandardCharsets.UTF_8);
    }

    /**
     * URL 解码（指定字符集）。
     *
     * @param text    URL 编码字符串
     * @param charset 字符集
     * @return 解码后字符串
     */
    public static String decodeUrl(String text, Charset charset) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(charset, "charset");
        return java.net.URLDecoder.decode(text, charset);
    }

    // ============== 内部工具 ==============

    private static int[] buildBase32DecodeTable() {
        int[] table = new int[128];
        java.util.Arrays.fill(table, -1);
        for (int i = 0; i < BASE32_ALPHABET.length; i++) {
            table[BASE32_ALPHABET[i]] = i;
            // 大小写不敏感：小写也写入
            table[Character.toLowerCase(BASE32_ALPHABET[i])] = i;
        }
        return table;
    }

    private static int hexCharToDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        throw new IllegalArgumentException("Illegal hex character: " + c);
    }
}
