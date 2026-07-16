package com.njydsz.common.util.bytes;

import java.util.HexFormat;

/**
 * 十六进制编码/解码工具类
 *
 * <p>基于 JDK {@link HexFormat} 实现，提供 byte[] 与 Hex 字符串之间的双向转换。
 *
 * <p><b>使用示例：</b>
 * <pre>
 * byte[] bytes = {0x01, 0x02, (byte) 0xFF};
 * String hex = HexUtils.bytesToHex(bytes);   // "0102ff"
 * byte[] decoded = HexUtils.hexToBytes(hex); // {0x01, 0x02, (byte) 0xFF}
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class HexUtils {

    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private HexUtils() {
        throw new UnsupportedOperationException("HexUtils is a utility class and cannot be instantiated");
    }

    /**
     * 字节数组转十六进制字符串（小写）
     *
     * @param bytes 待转换的字节数组
     * @return 十六进制字符串，输入为 null 时返回 null
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return HEX_FORMAT.formatHex(bytes);
    }

    /**
     * 十六进制字符串转字节数组
     *
     * @param hex 十六进制字符串
     * @return 对应的字节数组
     * @throws IllegalArgumentException 当 hex 为 null 或长度为奇数时
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must not be null and must have even length");
        }
        return HEX_FORMAT.parseHex(hex);
    }
}
