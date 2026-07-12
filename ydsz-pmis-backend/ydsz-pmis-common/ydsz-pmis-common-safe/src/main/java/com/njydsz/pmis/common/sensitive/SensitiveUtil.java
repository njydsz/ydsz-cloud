package com.njydsz.pmis.common.sensitive;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 脱敏工具
 *
 * <p>集中管理各策略的脱敏算法，并允许应用扩展 CUSTOM 策略。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class SensitiveUtil {

    /** 自定义脱敏函数注册表（name -> handler） */
    private static final Map<String, Function<String, String>> CUSTOM_REGISTRY = new ConcurrentHashMap<>();

    private SensitiveUtil() {
    }

    /**
     * 注册自定义脱敏函数
     *
     * @param name    处理器名称
     * @param handler 脱敏函数
     */
    public static void register(String name, Function<String, String> handler) {
        if (name != null && handler != null) {
            CUSTOM_REGISTRY.put(name, handler);
        }
    }

    /**
     * 按策略脱敏（默认前 1 后 1 保留）
     *
     * @param value   原始值
     * @param strategy 脱敏策略
     * @return 脱敏后的值
     */
    public static String desensitize(String value, SensitiveStrategy strategy) {
        return desensitize(value, strategy, 1, 1);
    }

    /**
     * 按策略脱敏
     *
     * @param value      原始值
     * @param strategy   脱敏策略
     * @param prefixKeep 前置保留长度
     * @param suffixKeep 后置保留长度
     * @return 脱敏后的值
     */
    public static String desensitize(String value, SensitiveStrategy strategy,
                                     int prefixKeep, int suffixKeep) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (strategy == null || strategy == SensitiveStrategy.NONE) {
            return value;
        }
        return switch (strategy) {
            case NAME -> maskName(value);
            case ID_CARD -> maskIdCard(value);
            case PHONE -> maskPhone(value);
            case EMAIL -> maskEmail(value);
            case BANK_CARD -> maskBankCard(value);
            case ADDRESS -> maskAddress(value, prefixKeep, suffixKeep);
            case CUSTOM -> maskCustom(value, "default");
            case NONE -> value;
        };
    }

    /**
     * 姓名脱敏：保留首末字，中间以 * 填充
     *
     * @param s 原始值
     * @return 脱敏后的值
     */
    public static String maskName(String s) {
        if (s.length() <= 1) return s + "*";
        if (s.length() == 2) return s.charAt(0) + "*";
        return s.charAt(0)
                + "*".repeat(Math.max(1, s.length() - 2))
                + s.charAt(s.length() - 1);
    }

    /**
     * 身份证脱敏：保留前 6 后 4
     *
     * @param s 原始值
     * @return 脱敏后的值
     */
    public static String maskIdCard(String s) {
        if (s.length() <= 10) return s;
        return s.substring(0, 6) + "*".repeat(s.length() - 10) + s.substring(s.length() - 4);
    }

    /**
     * 手机号脱敏：保留前 3 后 4
     *
     * @param s 原始值
     * @return 脱敏后的值
     */
    public static String maskPhone(String s) {
        if (s.length() < 7) return "****";
        return s.substring(0, 3) + "****" + s.substring(s.length() - 4);
    }

    /**
     * 邮箱脱敏：保留 @ 及前 3 字符
     *
     * @param s 原始值
     * @return 脱敏后的值
     */
    public static String maskEmail(String s) {
        int at = s.indexOf('@');
        if (at < 1) return "***" + s;
        if (at <= 3) {
            return s.substring(0, 1) + "***" + s.substring(at);
        }
        return s.substring(0, 3) + "***" + s.substring(at);
    }

    /**
     * 银行卡脱敏：保留前 4 后 4
     *
     * @param s 原始值
     * @return 脱敏后的值
     */
    public static String maskBankCard(String s) {
        if (s.length() <= 8) return s;
        return s.substring(0, 4) + "*".repeat(s.length() - 8) + s.substring(s.length() - 4);
    }

    /**
     * 地址脱敏：保留前 prefixKeep 后 suffixKeep，中间以 *** 填充
     *
     * @param s          原始值
     * @param prefixKeep 前置保留长度
     * @param suffixKeep 后置保留长度
     * @return 脱敏后的值
     */
    public static String maskAddress(String s, int prefixKeep, int suffixKeep) {
        if (s.length() <= prefixKeep + suffixKeep + 3) {
            return s.substring(0, Math.max(0, prefixKeep)) + "***";
        }
        int end = s.length() - Math.max(0, suffixKeep);
        return s.substring(0, Math.max(0, prefixKeep)) + "***" + s.substring(end);
    }

    /**
     * 自定义脱敏：调用已注册的 handler 处理
     *
     * @param s           原始值
     * @param handlerName 处理器名称
     * @return 脱敏后的值；未注册或处理异常时返回原值
     */
    public static String maskCustom(String s, String handlerName) {
        Function<String, String> fn = CUSTOM_REGISTRY.get(handlerName);
        if (fn == null) return s;
        try {
            return fn.apply(s);
        } catch (Exception e) {
            return s;
        }
    }
}
