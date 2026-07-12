package com.njydsz.pmis.common.util.string;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 字符集工具类
 *
 * <p>提供全面的字符集处理方法，功能对标 Apache Commons Codec 和 Hutool CharsetUtil，
 * 并进行了增强和优化。
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>常用字符集常量：ISO_8859_1、UTF_8、GBK、GB2312、GB18030、BIG5、UTF_16 等</li>
 *   <li>字符集转换：convert（字符串字节转换）</li>
 *   <li>字符集检测：detect、isUTF8、isGBK</li>
 *   <li>系统字符集：systemCharsetName、systemCharset</li>
 *   <li>字节编码转换：encode、decode</li>
 *   <li>字符集验证：isValidCharset</li>
 * </ul>
 *
 * <p><b>相比 Apache/Hutool 的增强：</b>
 * <ul>
 *   <li>提供更多中文字符集支持（GB18030、BIG5 等）</li>
 *   <li>支持字符集自动检测</li>
 *   <li>支持 BOM 处理</li>
 *   <li>所有方法 null 安全处理</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class CharsetUtils {

    private CharsetUtils() {
        throw new UnsupportedOperationException("CharsetUtils is a utility class and cannot be instantiated");
    }

    /**
     * ISO-8859-1
     */
    public static final String ISO_8859_1 = StandardCharsets.ISO_8859_1.name();
    /**
     * UTF-8
     */
    public static final String UTF_8 = StandardCharsets.UTF_8.name();
    /**
     * GBK
     */
    public static final String GBK = "GBK";

    /**
     * ISO-8859-1
     */
    public static final Charset CHARSET_ISO_8859_1 = StandardCharsets.ISO_8859_1;
    /**
     * UTF-8
     */
    public static final Charset CHARSET_UTF_8 = StandardCharsets.UTF_8;
    /**
     * GBK
     */
    public static final Charset CHARSET_GBK = Charset.forName(GBK);
    /**
     * GB2312
     */
    public static final String GB2312 = "GB2312";
    public static final Charset CHARSET_GB2312 = Charset.forName(GB2312);
    /**
     * GB18030
     */
    public static final String GB18030 = "GB18030";
    public static final Charset CHARSET_GB18030 = Charset.forName(GB18030);
    /**
     * BIG5（繁体中文）
     */
    public static final String BIG5 = "BIG5";
    public static final Charset CHARSET_BIG5 = Charset.forName(BIG5);
    /**
     * UTF-16
     */
    public static final String UTF_16 = StandardCharsets.UTF_16.name();
    public static final Charset CHARSET_UTF_16 = StandardCharsets.UTF_16;
    /**
     * UTF-16BE
     */
    public static final String UTF_16BE = StandardCharsets.UTF_16BE.name();
    public static final Charset CHARSET_UTF_16BE = StandardCharsets.UTF_16BE;
    /**
     * UTF-16LE
     */
    public static final String UTF_16LE = StandardCharsets.UTF_16LE.name();
    public static final Charset CHARSET_UTF_16LE = StandardCharsets.UTF_16LE;
    /**
     * US-ASCII
     */
    public static final String US_ASCII = StandardCharsets.US_ASCII.name();
    public static final Charset CHARSET_US_ASCII = StandardCharsets.US_ASCII;
    /**
     * ISO-8859-15
     */
    public static final String ISO_8859_15 = "ISO-8859-15";
    public static final Charset CHARSET_ISO_8859_15 = Charset.forName(ISO_8859_15);
    /**
     * Windows-1252
     */
    public static final String WINDOWS_1252 = "Windows-1252";
    public static final Charset CHARSET_WINDOWS_1252 = Charset.forName(WINDOWS_1252);
    /**
     * BOM 头（UTF-8）
     */
    public static final byte[] BOM_UTF8 = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    /**
     * BOM 头（UTF-16BE）
     */
    public static final byte[] BOM_UTF16BE = new byte[]{(byte) 0xFE, (byte) 0xFF};
    /**
     * BOM 头（UTF-16LE）
     */
    public static final byte[] BOM_UTF16LE = new byte[]{(byte) 0xFF, (byte) 0xFE};

    /**
     * 转换为 Charset 对象
     *
     * @param charset 字符集名称，为空则返回默认字符集
     * @return Charset
     */
    public static Charset charset(String charset) {
        return StringUtils.isEmpty(charset) ? Charset.defaultCharset() : Charset.forName(charset);
    }

    /**
     * 转换字符串的字符集编码
     *
     * @param source      字符串
     * @param srcCharset  源字符集名称
     * @param destCharset 目标字符集名称
     * @return 转换后的字符串
     */
    public static String convert(String source, String srcCharset, String destCharset) {
        return convert(source, charset(srcCharset), charset(destCharset));
    }

    /**
     * 转换字符串的字符集编码
     *
     * @param source      字符串
     * @param srcCharset  源字符集
     * @param destCharset 目标字符集
     * @return 转换后的字符串
     */
    public static String convert(String source, Charset srcCharset, Charset destCharset) {
        if (StringUtils.isEmpty(source)) {
            return source;
        }
        
        srcCharset = (srcCharset == null) ? StandardCharsets.ISO_8859_1 : srcCharset;
        destCharset = (destCharset == null) ? StandardCharsets.UTF_8 : destCharset;

        if (srcCharset.equals(destCharset)) {
            return source;
        }
        return new String(source.getBytes(srcCharset), destCharset);
    }

    /**
     * 获取系统字符集名称
     * @return 系统默认字符集名称
     */
    public static String systemCharsetName() {
        return Charset.defaultCharset().name();
    }

    /**
     * 获取系统字符集
     * @return 系统默认字符集
     */
    public static Charset systemCharset() {
        return Charset.defaultCharset();
    }

    /**
     * 验证字符集名称是否有效
     * @param charsetName 字符集名称
     * @return 是否有效
     */
    public static boolean isValidCharset(String charsetName) {
        if (StringUtils.isEmpty(charsetName)) {
            return false;
        }
        try {
            Charset.forName(charsetName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 编码转换（字符串转字节数组）
     * @param str 字符串
     * @param charset 字符集
     * @return 字节数组
     */
    public static byte[] encode(String str, String charset) {
        if (StringUtils.isEmpty(str)) {
            return new byte[0];
        }
        return str.getBytes(charset(charset));
    }

    /**
     * 解码转换（字节数组转字符串）
     * @param bytes 字节数组
     * @param charset 字符集
     * @return 字符串
     */
    public static String decode(byte[] bytes, String charset) {
        if (bytes == null || bytes.length == 0) {
            return StringUtils.EMPTY;
        }
        return new String(bytes, charset(charset));
    }

    /**
     * 检测字节数组的字符集（简单检测）
     * @param bytes 字节数组
     * @return 检测到的字符集名称
     */
    public static String detect(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return UTF_8;
        }
        
        // 检查 BOM 头
        if (bytes.length >= 3 && 
            bytes[0] == BOM_UTF8[0] && 
            bytes[1] == BOM_UTF8[1] && 
            bytes[2] == BOM_UTF8[2]) {
            return UTF_8;
        }
        
        if (bytes.length >= 2 && 
            bytes[0] == BOM_UTF16BE[0] && 
            bytes[1] == BOM_UTF16BE[1]) {
            return UTF_16BE;
        }
        
        if (bytes.length >= 2 && 
            bytes[0] == BOM_UTF16LE[0] && 
            bytes[1] == BOM_UTF16LE[1]) {
            return UTF_16LE;
        }
        
        // 简单判断是否为 UTF-8
        if (isUTF8(bytes)) {
            return UTF_8;
        }
        
        // 默认为 GBK（中文环境）
        return GBK;
    }

    /**
     * 判断字节数组是否为 UTF-8 编码
     * @param bytes 字节数组
     * @return 是否为 UTF-8
     */
    public static boolean isUTF8(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        
        int i = 0;
        while (i < bytes.length) {
            byte b = bytes[i];
            if (b >= 0) {
                i++;
            } else if (b < -64) {
                i += 2;
            } else if (b < -32) {
                i += 3;
            } else if (b < -16) {
                i += 4;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断字节数组是否为 GBK 编码（简单判断）
     * @param bytes 字节数组
     * @return 是否为 GBK
     */
    public static boolean isGBK(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            if (b >= 0) {
                continue;
            }
            if (i + 1 < bytes.length && bytes[i + 1] >= 0) {
                return false;
            }
            if ((b & 0xFF) >= 0x81 && (b & 0xFF) <= 0xFE) {
                i++;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * 移除 BOM 头
     * @param bytes 字节数组
     * @return 移除 BOM 头后的字节数组
     */
    public static byte[] removeBOM(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return bytes;
        }
        
        if (bytes.length >= 3 && 
            bytes[0] == BOM_UTF8[0] && 
            bytes[1] == BOM_UTF8[1] && 
            bytes[2] == BOM_UTF8[2]) {
            return Arrays.copyOfRange(bytes, 3, bytes.length);
        }
        
        if (bytes.length >= 2 && 
            bytes[0] == BOM_UTF16BE[0] && 
            bytes[1] == BOM_UTF16BE[1]) {
            return Arrays.copyOfRange(bytes, 2, bytes.length);
        }
        
        if (bytes.length >= 2 && 
            bytes[0] == BOM_UTF16LE[0] && 
            bytes[1] == BOM_UTF16LE[1]) {
            return Arrays.copyOfRange(bytes, 2, bytes.length);
        }
        
        return bytes;
    }

    /**
     * 添加 BOM 头
     * @param bytes 字节数组
     * @param charset 字符集
     * @return 添加 BOM 头后的字节数组
     */
    public static byte[] addBOM(byte[] bytes, String charset) {
        if (bytes == null || bytes.length == 0) {
            return bytes;
        }
        
        byte[] bom;
        if (UTF_8.equalsIgnoreCase(charset)) {
            bom = BOM_UTF8;
        } else if (UTF_16BE.equalsIgnoreCase(charset)) {
            bom = BOM_UTF16BE;
        } else if (UTF_16LE.equalsIgnoreCase(charset)) {
            bom = BOM_UTF16LE;
        } else {
            return bytes;
        }
        
        byte[] result = new byte[bytes.length + bom.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(bytes, 0, result, bom.length, bytes.length);
        return result;
    }

    /**
     * 获取所有可用的字符集
     * @return 字符集名称列表
     */
    public static List<String> availableCharsets() {
        List<String> charsets = new ArrayList<>();
        SortedMap<String, Charset> map = Charset.availableCharsets();
        for (String name : map.keySet()) {
            charsets.add(name);
        }
        return charsets;
    }
}
