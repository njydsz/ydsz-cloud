package com.njydsz.pmis.common.util.bytes;

/**
 * 十六进制编码/解码工具类（纯 JDK 实现，零第三方依赖）
 *
 * <p>提供 byte[] 与 Hex 字符串之间的双向转换，是安全模块的公共基础工具。
 *
 * <p><b>使用示例：</b>
 * <pre>
 * byte[] bytes = {0x01, 0x02, (byte) 0xFF};
 * String hex = HexUtils.bytesToHex(bytes);   // "0102ff"
 * byte[] decoded = HexUtils.hexToBytes(hex); // {0x01, 0x02, (byte) 0xFF}
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class HexUtils {

    /**
     * 十六进制字符表
     */
    private static final String HEX_CHARS = "0123456789abcdef";

    private HexUtils() {
        throw new UnsupportedOperationException("HexUtils 是工具类，不允许被实例化");
    }

    /**
     * 字节数组转十六进制字符串
     *
     * @param bytes 待转换的字节数组
     * @return 十六进制字符串，输入为 null 时返回 null
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(HEX_CHARS.charAt((b >> 4) & 0x0F));
            hex.append(HEX_CHARS.charAt(b & 0x0F));
        }
        return hex.toString();
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
        int len = hex.length() / 2;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hex character in string");
            }
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }
}
