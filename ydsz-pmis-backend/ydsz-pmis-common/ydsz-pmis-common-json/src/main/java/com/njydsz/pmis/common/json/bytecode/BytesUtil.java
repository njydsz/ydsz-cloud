package com.njydsz.pmis.common.json.bytecode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 字节数组工具类
 *
 * <p>提供高效的字符数组操作，在 JVM 启动时检测是否真正启用了 SIMD 向量化加速。</p>
 *
 * <p><b>SIMD 运行时检测：</b></p>
 * <ul>
 *   <li>启动时检测 Vector API 是否可用（需要 --add-modules jdk.incubator.vector 且 JDK 版本支持）</li>
 *   <li>如果未启用 SIMD，在日志中记录 WARNING 提示</li>
 *   <li>无论是否启用 SIMD，均提供可用的回退实现</li>
 * </ul>
 *
 * <p><b>命名说明：</b></p>
 * <p>原类名为 VectorSimdUtil，但实际依赖 JVM 自动向量化而非显式 SIMD 指令，存在命名误导。
 * 重命名为 BytesUtil 以准确反映功能定位。</p>
 *
 * @since 1.3.0
 */
public final class BytesUtil {

    private static final Logger log = LoggerFactory.getLogger(BytesUtil.class);

    /**
     * SIMD 是否真正启用
     */
    public static final boolean SIMD_ENABLED;

    /**
     * 底层委托（VectorSimdUtil）的向量化可用性
     */
    private static final boolean VECTOR_API_AVAILABLE;

    static {
        boolean vectorApiAvailable = false;
        try {
            Class.forName("jdk.incubator.vector.CharVector");
            vectorApiAvailable = true;
        } catch (Throwable e) {
            // Vector API 不可用
        }
        VECTOR_API_AVAILABLE = vectorApiAvailable;

        if (VECTOR_API_AVAILABLE) {
            log.info("Vector API (SIMD) 已启用，批量字符操作将使用向量化加速");
            SIMD_ENABLED = true;
        } else {
            log.warn("Vector API (SIMD) 未启用，批量字符操作将使用标准循环实现。" +
                    "如需启用 SIMD 加速，请添加 JVM 参数: --add-modules jdk.incubator.vector " +
                    "（仅适用于支持 Vector API 的 JDK 版本）");
            SIMD_ENABLED = false;
        }
    }

    private BytesUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * 检测 SIMD 是否真正启用
     *
     * @return true 如果 SIMD 向量化已启用
     */
    public static boolean isSimdEnabled() {
        return SIMD_ENABLED;
    }

    /**
     * 在字符数组中查找目标字符的位置
     *
     * @param chars  字符数组
     * @param start  起始位置
     * @param len    有效长度
     * @param target 目标字符
     * @return 目标字符的位置，未找到返回 -1
     */
    public static int indexOf(char[] chars, int start, int len, char target) {
        return VectorSimdUtil.vectorizedIndexOf(chars, start, len, target);
    }

    /**
     * 跳过字符数组开头的空白字符
     *
     * @param chars 字符数组
     * @param start 起始位置
     * @param len   有效长度
     * @return 第一个非空白字符的位置
     */
    public static int skipWhitespace(char[] chars, int start, int len) {
        return VectorSimdUtil.vectorizedSkipWhitespace(chars, start, len);
    }

    /**
     * 检查字符数组指定范围内是否全部为空白字符
     *
     * @param chars 字符数组
     * @param start 起始位置
     * @param end   结束位置（不包含）
     * @return true 如果全部为空白字符
     */
    public static boolean isAllWhitespace(char[] chars, int start, int end) {
        return VectorSimdUtil.vectorizedIsAllWhitespace(chars, start, end);
    }

    /**
     * 检查字符数组指定范围内是否全部为数字字符
     *
     * @param chars 字符数组
     * @param start 起始位置
     * @param len   有效长度
     * @return true 如果全部为数字字符
     */
    public static boolean isAllDigits(char[] chars, int start, int len) {
        return VectorSimdUtil.vectorizedIsAllDigits(chars, start, len);
    }

    /**
     * 比较两个字符数组指定范围是否相等
     *
     * @param chars1  字符数组1
     * @param offset1 数组1的偏移
     * @param chars2  字符数组2
     * @param offset2 数组2的偏移
     * @param len     比较长度
     * @return true 如果相等
     */
    public static boolean equals(char[] chars1, int offset1, char[] chars2, int offset2, int len) {
        return VectorSimdUtil.vectorizedEquals(chars1, offset1, chars2, offset2, len);
    }

    /**
     * 在字符串中查找目标字符的位置
     *
     * @param str    字符串
     * @param start  起始位置
     * @param target 目标字符
     * @return 目标字符的位置，未找到返回 -1
     */
    public static int indexOf(String str, int start, char target) {
        return VectorSimdUtil.vectorizedIndexOf(str, start, target);
    }

    /**
     * 跳过字符串开头的空白字符
     *
     * @param str   字符串
     * @param start 起始位置
     * @return 第一个非空白字符的位置
     */
    public static int skipWhitespace(String str, int start) {
        return VectorSimdUtil.vectorizedSkipWhitespace(str, start);
    }

    /**
     * 快速匹配字符数组中指定位置是否与预期字符串相等
     *
     * @param chars    字符数组
     * @param start    起始位置
     * @param expected 预期字符串
     * @return true 如果匹配
     */
    public static boolean fastMatch(char[] chars, int start, String expected) {
        return VectorSimdUtil.fastMatch(chars, start, expected);
    }
}
