package com.njydsz.pmis.common.excel.core.security;

import com.njydsz.pmis.common.excel.core.config.ExcelConfig;

/**
 * 公式注入防护工具类
 *
 * <p>检测并清理可能被利用进行CSV/Excel公式注入的危险字符串前缀。
 * 当单元格值以 {@code =}、{@code +}、{@code -}、{@code @} 开头时，
 * 恶意用户可能通过构造特殊值注入公式执行攻击。</p>
 *
 * <p>防护方式：在危险值前添加单引号前缀({@code '})，使Excel将其识别为文本而非公式。</p>
 *
 * @see ExcelConfig#isFormulaInjectionProtection()
 */
public final class FormulaInjectionGuard {

    /** 公式注入危险前缀，包含=、+、-、@ */
    private static final String[] FORMULA_INJECTION_PREFIXES = {"=", "+", "-", "@"};

    private FormulaInjectionGuard() {
    }

    /**
     * 获取公式注入危险前缀数组
     *
     * @return 危险前缀数组的副本
     */
    public static String[] getFormulaInjectionPrefixes() {
        return FORMULA_INJECTION_PREFIXES.clone();
    }

    /**
     * 检测给定值是否为潜在的公式注入
     *
     * <p>当值以 {@code =}、{@code +}、{@code -}、{@code @} 开头时返回 {@code true}</p>
     *
     * @param value 待检测的字符串值
     * @return 如果是潜在的公式注入返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isPotentialFormulaInjection(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (String prefix : FORMULA_INJECTION_PREFIXES) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 清理潜在的公式注入
     *
     * <p>如果值被检测为潜在的公式注入，则在值前添加单引号({@code '})前缀，
     * 使Excel将其识别为文本而非公式。否则原样返回。</p>
     *
     * @param value 待清理的字符串值
     * @return 清理后的安全字符串值
     */
    public static String sanitizeFormulaInjection(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (isPotentialFormulaInjection(value)) {
            return "'" + value;
        }
        return value;
    }
}
