package com.njydsz.pmis.common.util;

import java.util.regex.Pattern;

/**
 * 正则表达式工具类
 *
 * <p>提供常用正则校验方法。
 * 对标 remi-comm RegexUtils。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class RegexUtils {

    private RegexUtils() {
    }

    /**
     * 校验字符串是否匹配正则
     *
     * @param regex  正则表达式
     * @param input  输入字符串
     * @return true 如果匹配
     */
    public static boolean matches(String regex, String input) {
        if (input == null || regex == null) {
            return false;
        }
        return Pattern.matches(regex, input);
    }

    /**
     * 校验字符串是否匹配正则（编译后的 Pattern）
     *
     * @param pattern 编译后的 Pattern
     * @param input   输入字符串
     * @return true 如果匹配
     */
    public static boolean matches(Pattern pattern, String input) {
        if (input == null || pattern == null) {
            return false;
        }
        return pattern.matcher(input).matches();
    }

    /**
     * 提取第一个匹配的子串
     *
     * @param regex  正则表达式
     * @param input  输入字符串
     * @return 第一个匹配的子串，未匹配返回 null
     */
    public static String extractFirst(String regex, String input) {
        if (input == null || regex == null) {
            return null;
        }
        java.util.regex.Matcher matcher = Pattern.compile(regex).matcher(input);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    /**
     * 提取所有匹配的子串
     *
     * @param regex  正则表达式
     * @param input  输入字符串
     * @return 匹配的子串列表
     */
    public static java.util.List<String> extractAll(String regex, String input) {
        if (input == null || regex == null) {
            return new java.util.ArrayList<>();
        }
        java.util.List<String> result = new java.util.ArrayList<>();
        java.util.regex.Matcher matcher = Pattern.compile(regex).matcher(input);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }

    /**
     * 替换所有匹配的子串
     *
     * @param regex       正则表达式
     * @param input       输入字符串
     * @param replacement 替换字符串
     * @return 替换后的字符串
     */
    public static String replaceAll(String regex, String input, String replacement) {
        if (input == null || regex == null) {
            return input;
        }
        return input.replaceAll(regex, replacement);
    }
}
