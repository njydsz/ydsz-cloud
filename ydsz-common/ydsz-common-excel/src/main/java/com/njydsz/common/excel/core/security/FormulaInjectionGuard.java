package com.njydsz.common.excel.core.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.njydsz.common.excel.core.config.ExcelConfig;

/**
 * 公式注入防护工具类
 *
 * <p>检测并清理可能被利用进行 CSV / Excel 公式注入的危险字符串前缀。
 * 当单元格值以 {@code =}、{@code +}、{@code -}、{@code @} 开头时，
 * 恶意用户可能通过构造特殊值注入公式执行攻击。</p>
 *
 * <p>防护方式：在危险值前添加单引号前缀 ({@code '})，使 Excel 将其识别为文本而非公式。</p>
 *
 * <p>检测前缀可通过系统属性 {@code ydsz.excel.formula-injection-prefixes} 配置，
 * 逗号分隔。默认值为 {@code =,+}（仅拦截最常见的两个前缀），避免对以 {@code -} 或 {@code @}
 * 开头的合法业务数据进行过度转义。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExcelConfig#isFormulaInjectionProtection()
 */
public final class FormulaInjectionGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(FormulaInjectionGuard.class);

    private static final List<String> FORMULA_INJECTION_PREFIXES = resolvePrefixes();

    private FormulaInjectionGuard() {
    }

    /**
     * 解析系统属性中的危险前缀配置。
     *
     * <p>系统属性 {@code ydsz.excel.formula-injection-prefixes} 格式为逗号分隔，
     * 如 {@code "=,+,@",-}。解析失败时回退到默认值 {@code ["=", "+"]}。</p>
     *
     * @return 不可变的危险前缀列表
     */
    private static List<String> resolvePrefixes() {
        String prop = System.getProperty("ydsz.excel.formula-injection-prefixes");
        if (prop != null && !prop.trim().isEmpty()) {
            try {
                String[] parts = prop.split(",");
                List<String> prefixes = new java.util.ArrayList<>(parts.length);
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        prefixes.add(trimmed);
                    }
                }
                if (!prefixes.isEmpty()) {
                    LOGGER.info("Formula injection prefixes overridden by system property: {}", prefixes);
                    return Collections.unmodifiableList(prefixes);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to parse ydsz.excel.formula-injection-prefixes={}, using default", prop, e);
            }
        }
        return Arrays.asList("=", "+");
    }

    /**
     * 获取公式注入危险前缀列表
     *
     * @return 不可变的危险前缀列表
     */
    public static List<String> getFormulaInjectionPrefixes() {
        return FORMULA_INJECTION_PREFIXES;
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
