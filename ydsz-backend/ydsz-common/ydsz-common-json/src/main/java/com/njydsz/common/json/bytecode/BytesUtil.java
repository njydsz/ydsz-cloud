package com.njydsz.common.json.bytecode;

/**
 * 字节数组工具类（JSON 解析辅助）
 *
 * <p>提供高效的字符数组操作，作为 {@link VectorSimdUtil} 的薄包装层，
 * 统一对外暴露字符数组/字符串批处理 API。</p>
 *
 * <p><b>性能策略：</b></p>
 * <ul>
 *   <li>底层为朴素循环实现，依赖 Hotspot JIT 的 SuperWord 自动向量化</li>
 *   <li>不再依赖 JDK Vector API（反射调用开销 &gt; SIMD 收益）</li>
 *   <li>紧密循环可被 JIT 内联到调用方热点路径</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class BytesUtil {

    /**
     * SIMD 是否真正启用 — 始终为 false
     *
     * <p>保留以兼容旧 API。当前实现依赖 JIT 自动向量化，
     * 不再使用显式 Vector API 调用。</p>
     */
    public static final boolean SIMD_ENABLED = false;

    private BytesUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * 检测 SIMD 是否真正启用
     *
     * @return 始终返回 false
     * @deprecated 当前实现依赖 JIT 自动向量化，不再使用 Vector API
     */
    @Deprecated(since = "1.0.0")
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
