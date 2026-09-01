package com.njydsz.common.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.njydsz.common.excel.core.security.FormulaInjectionGuard;

/**
 * P1-1 回归测试 — 公式注入防护对齐 OWASP 前缀集 + XLSX/CSV 分路径策略。
 *
 * <p>OWASP Cheat Sheet 完整危险前缀集：{@code = + - @ \t \r}。 此前默认仅拦截 {@code = +}，
 * 且 XLSX 路径加撇号前缀会在单元格中字面显示。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class FormulaInjectionGuardTest {

  @Test
  void defaultPrefixesCoverFullOwaspSet() {
    List<String> prefixes = FormulaInjectionGuard.getFormulaInjectionPrefixes();

    assertTrue(prefixes.contains("="), "缺少 = 前缀");
    assertTrue(prefixes.contains("+"), "缺少 + 前缀");
    assertTrue(prefixes.contains("-"), "缺少 - 前缀");
    assertTrue(prefixes.contains("@"), "缺少 @ 前缀");
    assertTrue(prefixes.contains("\t"), "缺少 tab 前缀");
    assertTrue(prefixes.contains("\r"), "缺少 CR 前缀");
  }

  @Test
  void detectsAllOwaspDangerousPrefixes() {
    assertTrue(FormulaInjectionGuard.isPotentialFormulaInjection("=1+1"));
    assertTrue(FormulaInjectionGuard.isPotentialFormulaInjection("+cmd"));
    assertTrue(FormulaInjectionGuard.isPotentialFormulaInjection("-2+3"));
    assertTrue(FormulaInjectionGuard.isPotentialFormulaInjection("@SUM(A1)"));
    assertTrue(FormulaInjectionGuard.isPotentialFormulaInjection("\t=cmd"));
    assertTrue(FormulaInjectionGuard.isPotentialFormulaInjection("\r=cmd"));
  }

  @Test
  void safeValuesNotFlagged() {
    assertFalse(FormulaInjectionGuard.isPotentialFormulaInjection(null));
    assertFalse(FormulaInjectionGuard.isPotentialFormulaInjection(""));
    assertFalse(FormulaInjectionGuard.isPotentialFormulaInjection("普通文本"));
    assertFalse(FormulaInjectionGuard.isPotentialFormulaInjection("1+1"));
    assertFalse(FormulaInjectionGuard.isPotentialFormulaInjection("a@b.com"));
  }

  @Test
  void csvStrategyPrefixesSingleQuote() {
    // CSV 路径：前导撇号（Excel 导入 CSV 时解释为文本标记并隐藏）
    assertEquals("'=1+1", FormulaInjectionGuard.sanitizeFormulaInjection("=1+1"));
    assertEquals("'@SUM(A1)", FormulaInjectionGuard.sanitizeFormulaInjection("@SUM(A1)"));
    assertEquals("'-2+3", FormulaInjectionGuard.sanitizeFormulaInjection("-2+3"));
  }

  @Test
  void xlsxStrategyPrefixesSpaceNotApostrophe() {
    // XLSX 路径：前导空格（撇号在 XLSX 单元格中会字面显示，属缺陷）
    assertEquals(" =1+1", FormulaInjectionGuard.sanitizeForXlsx("=1+1"));
    assertEquals(" @SUM(A1)", FormulaInjectionGuard.sanitizeForXlsx("@SUM(A1)"));
    assertEquals(" -2+3", FormulaInjectionGuard.sanitizeForXlsx("-2+3"));
    assertEquals(" \t=cmd", FormulaInjectionGuard.sanitizeForXlsx("\t=cmd"));
  }

  @Test
  void safeValuesPassThroughUnchanged() {
    assertEquals("普通文本", FormulaInjectionGuard.sanitizeFormulaInjection("普通文本"));
    assertEquals("普通文本", FormulaInjectionGuard.sanitizeForXlsx("普通文本"));
    assertEquals("1+1", FormulaInjectionGuard.sanitizeFormulaInjection("1+1"));
    assertEquals(null, FormulaInjectionGuard.sanitizeFormulaInjection(null));
    assertEquals(null, FormulaInjectionGuard.sanitizeForXlsx(null));
    assertEquals("", FormulaInjectionGuard.sanitizeFormulaInjection(""));
    assertEquals("", FormulaInjectionGuard.sanitizeForXlsx(""));
  }
}
