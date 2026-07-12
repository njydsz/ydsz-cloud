package com.njydsz.pmis.common.safe.desensitize;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 字段脱敏执行器。
 *
 * <p>根据脱敏规则对字段值进行脱敏处理。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * ColumnDesensitizationExecutor executor = new ColumnDesensitizationExecutor();
 *
 * // 使用内置规则
 * String maskedPhone = executor.desensitize("13812345678", ColumnDesensitizationRule.PHONE);
 * // 结果：138****5678
 *
 * // 使用自定义规则
 * String maskedCustom = executor.desensitize("ABC123", ColumnDesensitizationRule.CUSTOM, "(\\w{3})(\\w+)", "$1***");
 * // 结果：ABC***
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see ColumnDesensitizationRule
 */
public class ColumnDesensitizationExecutor {

    private static final ColumnDesensitizationExecutor INSTANCE = new ColumnDesensitizationExecutor();

    private static final ConcurrentHashMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取脱敏执行器单例。
     *
     * @return 脱敏执行器实例
     */
    public static ColumnDesensitizationExecutor getInstance() {
        return INSTANCE;
    }

    public ColumnDesensitizationExecutor() {
    }

    /**
     * 使用内置脱敏规则对字段值进行脱敏。
     *
     * @param value 原始字段值
     * @param rule 脱敏规则
     * @return 脱敏后的字段值，输入为空时原样返回
     */
    public String desensitize(String value, ColumnDesensitizationRule rule) {
        if (value == null || rule == null) {
            return value;
        }
        return desensitize(value, rule.getPattern(), rule.getReplacement());
    }

    /**
     * 使用脱敏规则对字段值进行脱敏，支持自定义正则覆盖。
     *
     * <p>当规则为 {@link ColumnDesensitizationRule#CUSTOM} 时，使用自定义正则和替换模板；
     * 否则使用规则内置的正则和替换模板。
     *
     * @param value 原始字段值
     * @param rule 脱敏规则
     * @param customPattern 自定义正则表达式（仅 CUSTOM 规则生效）
     * @param customReplacement 自定义替换模板（仅 CUSTOM 规则生效）
     * @return 脱敏后的字段值，输入为空时原样返回
     */
    public String desensitize(String value, ColumnDesensitizationRule rule, String customPattern, String customReplacement) {
        if (value == null || rule == null) {
            return value;
        }
        if (rule == ColumnDesensitizationRule.CUSTOM) {
            return desensitize(value, customPattern, customReplacement);
        }
        return desensitize(value, rule.getPattern(), rule.getReplacement());
    }

    /**
     * 使用正则表达式对字段值进行脱敏。
     *
     * @param value 原始字段值
     * @param pattern 正则表达式
     * @param replacement 替换模板
     * @return 脱敏后的字段值，输入为空或正则匹配失败时原样返回
     */
    public String desensitize(String value, String pattern, String replacement) {
        if (value == null || pattern == null || replacement == null) {
            return value;
        }
        if (pattern.isEmpty() || replacement.isEmpty()) {
            return value;
        }
        try {
            return PATTERN_CACHE.computeIfAbsent(pattern, Pattern::compile).matcher(value).replaceAll(replacement);
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * 根据脱敏规则配置对字段值进行脱敏。
     *
     * <p>配置为自定义规则时使用自定义正则，否则使用内置规则。
     *
     * @param value 原始字段值
     * @param config 脱敏规则配置
     * @return 脱敏后的字段值，输入为空时原样返回
     */
    public String desensitize(String value, ColumnDesensitizationContext.DesensitizationRuleConfig config) {
        if (value == null) {
            return value;
        }
        if (config == null) {
            return value;
        }
        if (config.isCustom()) {
            return desensitize(value, config.getCustomPattern(), config.getCustomReplacement());
        }
        return desensitize(value, config.getRule());
    }
}
