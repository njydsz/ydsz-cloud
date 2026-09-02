package com.njydsz.common.excel.core.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.core.config.ExcelConfig;

/**
 * 公式注入防护工具类
 *
 * <p>检测并清理可能被利用进行 CSV / Excel 公式注入的危险字符串前缀。 OWASP Cheat Sheet
 * 定义的完整危险前缀集为 {@code =}、{@code +}、{@code -}、{@code @}、{@code \t}、{@code \r}， 本类默认全部拦截。
 *
 * <h3>分路径防护策略</h3>
 *
 * <ul>
 *   <li><b>CSV 路径</b>（{@link #sanitizeFormulaInjection(String)}）：危险值前添加单引号前缀
 *       ({@code '})。Excel 导入 CSV 时将前导撇号识别为"文本标记"并隐藏显示，是 OWASP
 *       推荐的 CSV 场景标准做法。
 *   <li><b>XLSX 路径</b>（{@link #sanitizeForXlsx(String)}）：危险值前添加单个空格。OOXML
 *       中字符串单元格（t="s"/inlineStr）本身不会被求值为公式，但为防御"XLSX → 另存/复制为
 *       CSV → Excel 重新打开"的二次注入链，仍做前缀中和。 前导单引号在 XLSX 中会<b>字面显示</b>（仅
 *       Excel 手工输入时才被解释为文本标记），因此 XLSX 路径不使用撇号。
 * </ul>
 *
 * <p>检测前缀可通过系统属性 {@code ydsz.excel.formula-injection-prefixes} 配置， 逗号分隔，
 * 支持 {@code \t}、{@code \r} 转义。默认值为完整 OWASP 前缀集。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ExcelConfig#isFormulaInjectionProtection()
 */
public final class FormulaInjectionGuard {

  private static final Logger LOGGER = LoggerFactory.getLogger(FormulaInjectionGuard.class);

  /** OWASP Cheat Sheet 公式注入危险前缀集：= + - @ tab CR */
  private static final List<String> DEFAULT_PREFIXES =
      Collections.unmodifiableList(Arrays.asList("=", "+", "-", "@", "\t", "\r"));

  private static final List<String> FORMULA_INJECTION_PREFIXES = resolvePrefixes();

  private FormulaInjectionGuard() {}

  /**
   * 解析系统属性中的危险前缀配置。
   *
   * <p>系统属性 {@code ydsz.excel.formula-injection-prefixes} 格式为逗号分隔， 如 {@code
   * "=,+,-,@,\t,\r"}（支持 \t、\r 转义写法）。解析失败或为空时回退到 OWASP 默认前缀集。
   *
   * @return 不可变的危险前缀列表
   */
  private static List<String> resolvePrefixes() {
    String prop = System.getProperty("ydsz.excel.formula-injection-prefixes");
    if (prop != null && !prop.trim().isEmpty()) {
      try {
        String[] parts = prop.split(",");
        List<String> prefixes = new ArrayList<>(parts.length);
        for (String part : parts) {
          String trimmed = part.trim();
          if (!trimmed.isEmpty()) {
            prefixes.add(unescape(trimmed));
          }
        }
        if (!prefixes.isEmpty()) {
          LOGGER.info("Formula injection prefixes overridden by system property: {}", prefixes);
          return Collections.unmodifiableList(prefixes);
        }
      } catch (Exception e) {
        LOGGER.warn(
            "Failed to parse ydsz.excel.formula-injection-prefixes={}, using default", prop, e);
      }
    }
    return DEFAULT_PREFIXES;
  }

  /** 系统属性前缀项的反转义（支持 \t、\r） */
  private static String unescape(String raw) {
    if (raw.contains("\\t")) {
      raw = raw.replace("\\t", "\t");
    }
    if (raw.contains("\\r")) {
      raw = raw.replace("\\r", "\r");
    }
    return raw;
  }

  /**
   * 获取公式注入危险前缀列表
   *
   * @return 不可变的危险前缀列表（默认为 OWASP 完整前缀集 = + - @ \t \r）
   */
  public static List<String> getFormulaInjectionPrefixes() {
    return FORMULA_INJECTION_PREFIXES;
  }

  /**
   * 检测给定值是否为潜在的公式注入
   *
   * <p>当值以 {@code =}、{@code +}、{@code -}、{@code @}、{@code \t}、{@code \r} 开头时返回
   * {@code true}（OWASP Cheat Sheet 完整危险前缀集）
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
   * 清理潜在的公式注入（CSV 路径策略）
   *
   * <p>如果值被检测为潜在的公式注入，则在值前添加单引号({@code '})前缀。 Excel 导入 CSV
   * 时将前导撇号解释为文本标记并隐藏显示，是 OWASP 推荐的 CSV 场景标准做法。 XLSX 写入路径请改用
   * {@link #sanitizeForXlsx(String)}（撇号在 XLSX 单元格中会字面显示）。
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

  /**
   * 清理潜在的公式注入（XLSX 路径策略）
   *
   * <p>OOXML 字符串单元格（t="s"/inlineStr）本身不会被求值为公式，此处中和是纵深防御： 阻断
   * "XLSX → 另存/复制为 CSV → Excel 重新打开求值"的二次注入链。命中危险前缀时在值前添加单个空格
   * （前导空格使公式永不在单元格起始位置，且视觉上不可见）；前导单引号在 XLSX 中会字面显示，故不使用。
   *
   * @param value 待清理的字符串值
   * @return 清理后的安全字符串值
   */
  public static String sanitizeForXlsx(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    if (isPotentialFormulaInjection(value)) {
      return " " + value;
    }
    return value;
  }
}
