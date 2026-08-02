package com.njydsz.common.json.bytecode;

/**
 * 字符数组批量操作工具类（JIT 自动向量化版本）
 *
 * <p><b>已废弃</b>：此工具类的所有方法均为朴素 {@code for} 循环，依赖 Hotspot JIT
 * SuperWord 自动向量化。将这些方法保留在独立工具类中并无实际价值——JIT 虽然可以内联
 * 调用方，但增加了不必要的间接调用开销和代码维护成本。</p>
 *
 * <p><b>替代方案</b>：调用方（如 {@link ZeroCopyDeserializer}）应直接内联循环逻辑，
 * 或使用 JDK 标准方法（如 {@code String.indexOf}、{@code Arrays.equals}）。
 * 新代码不应调用此类的方法。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 此类方法仅为朴素循环，应直接内联到调用方或使用 JDK 标准方法替代。
 *             将在 2.0.0 版本移除。
 */
@Deprecated(since = "1.4.0", forRemoval = true)
public final class VectorSimdUtil {

    private VectorSimdUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * 在字符数组中查找目标字符的位置
     *
     * <p>JIT 可对线性扫描自动向量化（SuperWord）。</p>
     *
     * @param chars  字符数组
     * @param start  起始位置
     * @param len    有效长度
     * @param target 目标字符
     * @return 目标字符的位置，未找到返回 -1
     */
    public static int vectorizedIndexOf(char[] chars, int start, int len, char target) {
        for (int i = start; i < len; i++) {
            if (chars[i] == target) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 跳过字符数组开头的空白字符
     *
     * @param chars 字符数组
     * @param start 起始位置
     * @param len   有效长度
     * @return 第一个非空白字符的位置
     */
    public static int vectorizedSkipWhitespace(char[] chars, int start, int len) {
        for (int i = start; i < len; i++) {
            if (chars[i] > ' ') {
                return i;
            }
        }
        return len;
    }

    /**
     * 检查字符数组指定范围内是否全部为空白字符
     *
     * @param chars 字符数组
     * @param start 起始位置
     * @param end   结束位置（不包含）
     * @return true 如果全部为空白字符
     */
    public static boolean vectorizedIsAllWhitespace(char[] chars, int start, int end) {
        for (int i = start; i < end; i++) {
            if (chars[i] > ' ') {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查字符数组指定范围内是否全部为数字字符
     *
     * @param chars 字符数组
     * @param start 起始位置
     * @param len   有效长度
     * @return true 如果全部为数字字符
     */
    public static boolean vectorizedIsAllDigits(char[] chars, int start, int len) {
        for (int i = start; i < len; i++) {
            char c = chars[i];
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
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
    public static boolean vectorizedEquals(char[] chars1, int offset1, char[] chars2, int offset2, int len) {
        for (int i = 0; i < len; i++) {
            if (chars1[offset1 + i] != chars2[offset2 + i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 在字符串中查找目标字符的位置
     *
     * @param str    字符串
     * @param start  起始位置
     * @param target 目标字符
     * @return 目标字符的位置，未找到返回 -1
     */
    public static int vectorizedIndexOf(String str, int start, char target) {
        int len = str.length();
        for (int i = start; i < len; i++) {
            if (str.charAt(i) == target) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 跳过字符串开头的空白字符
     *
     * @param str   字符串
     * @param start 起始位置
     * @return 第一个非空白字符的位置
     */
    public static int vectorizedSkipWhitespace(String str, int start) {
        int len = str.length();
        for (int i = start; i < len; i++) {
            if (str.charAt(i) > ' ') {
                return i;
            }
        }
        return len;
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
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        int len = expected.length();
        if (start + len > chars.length) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            if (chars[start + i] != expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
