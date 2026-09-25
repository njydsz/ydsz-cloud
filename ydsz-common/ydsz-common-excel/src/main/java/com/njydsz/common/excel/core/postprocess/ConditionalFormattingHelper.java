package com.njydsz.common.excel.core.postprocess;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 条件格式后处理器 — 对已生成的 xlsx 注入 {@code <conditionalFormatting>} 节点。
 *
 * <p>支持基本规则：单元格值比较、文本包含、空值判断等。
 *
 * <pre>{@code
 * ConditionalFormattingHelper.apply(fis, fos, (sheetIdx, rules) -&gt; {
 *   // 规则1：金额列 > 1000 标红
 *   rules.add(ConditionalFormattingHelper.cellValueRule("H2:H1000",
 *       "GREATER_THAN", "1000", "FFFF0000"));
 *   // 规则2：空行标黄
 *   rules.add(ConditionalFormattingHelper.isBlankRule("A2:F1000", "FFFFFF00"));
 * });
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class ConditionalFormattingHelper {

  private ConditionalFormattingHelper() {}

  /** 条件格式规则 */
  public static final class ConditionalRule {
    public String sqref;
    public String type;
    public String operator;
    public String formula;
    public String color; // ARGB 格式 如 "FFFF0000"（红）
    public int priority = 1;

    ConditionalRule(String sqref) {
      this.sqref = sqref;
    }
  }

  /** 创建单元格值比较规则 */
  public static ConditionalRule cellValueRule(String sqref, String operator, String value,
      String color) {
    ConditionalRule r = new ConditionalRule(sqref);
    r.type = "cellIs";
    r.operator = operator;
    r.formula = value;
    r.color = color;
    return r;
  }

  /** 创建文本包含规则 */
  public static ConditionalRule textContainsRule(String sqref, String text, String color) {
    ConditionalRule r = new ConditionalRule(sqref);
    r.type = "containsText";
    r.operator = "containsText";
    r.formula = "\"" + text + "\"";
    r.color = color;
    return r;
  }

  /** 创建空值规则 */
  public static ConditionalRule isBlankRule(String sqref, String color) {
    ConditionalRule r = new ConditionalRule(sqref);
    r.type = "notContainsText";
    r.operator = "notContainsText";
    r.formula = "\"\"";
    r.color = color;
    return r;
  }

  /** 应用范围着色规则 (color scale) */
  public static ConditionalRule colorScaleRule(String sqref, String color) {
    ConditionalRule r = new ConditionalRule(sqref);
    r.type = "colorScale";
    r.formula = "";
    r.color = color;
    return r;
  }

  @FunctionalInterface
  public interface ConditionalConfigurer {
    void configure(ConditionalCallback cb);
  }

  @FunctionalInterface
  public interface ConditionalCallback {
    void put(int sheetIndex, List<ConditionalRule> rules);
  }

  /**
   * 将条件格式注入 xlsx。
   *
   * @param source 源 xlsx 输入流
   * @param dest 输出流
   * @param configurer 配置回调
   * @throws IOException IO 或 ZIP 解析异常
   */
  public static void apply(InputStream source, OutputStream dest,
      ConditionalConfigurer configurer) throws IOException {
    Map<Integer, List<ConditionalRule>> rulesBySheet = new LinkedHashMap<>();
    configurer.configure((sheetIdx, rules) -> {
      rulesBySheet.computeIfAbsent(sheetIdx, k -> new ArrayList<>()).addAll(rules);
    });

    if (rulesBySheet.isEmpty()) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = source.read(buf)) != -1) {
        dest.write(buf, 0, n);
      }
      return;
    }

    Map<String, byte[]> entries = new LinkedHashMap<>();
    try (ZipInputStream zis = new ZipInputStream(source)) {
      ZipEntry ze;
      while ((ze = zis.getNextEntry()) != null) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(1024, (int) ze.getSize()));
        byte[] buf = new byte[4096];
        int n;
        while ((n = zis.read(buf)) != -1) {
          bos.write(buf, 0, n);
        }
        entries.put(ze.getName(), bos.toByteArray());
        zis.closeEntry();
      }
    }

    for (Map.Entry<Integer, List<ConditionalRule>> entry : rulesBySheet.entrySet()) {
      int sheetIdx = entry.getKey();
      List<ConditionalRule> rules = entry.getValue();
      if (rules.isEmpty()) {
        continue;
      }
      String sheetName = "xl/worksheets/sheet" + (sheetIdx + 1) + ".xml";
      byte[] sheetBytes = entries.get(sheetName);
      if (sheetBytes == null) {
        continue;
      }

      // 需要扩展 styles.xml: 每个底色对应一个 fill + 对应的 dxf
      String sheetXml = new String(sheetBytes, StandardCharsets.UTF_8);
      String cfXml = generateConditionalFormattingXml(rules);
      sheetXml = injectBeforeWorksheetEnd(sheetXml, cfXml);
      entries.put(sheetName, sheetXml.getBytes(StandardCharsets.UTF_8));
    }

    try (ZipOutputStream zos = new ZipOutputStream(dest)) {
      for (Map.Entry<String, byte[]> e : entries.entrySet()) {
        zos.putNextEntry(new ZipEntry(e.getKey()));
        zos.write(e.getValue());
        zos.closeEntry();
      }
      zos.finish();
    }
  }

  static String generateConditionalFormattingXml(List<ConditionalRule> rules) {
    StringBuilder sb = new StringBuilder(200);
    for (ConditionalRule rule : rules) {
      sb.append("<conditionalFormatting sqref=\"").append(rule.sqref).append("\">");
      sb.append("<cfRule type=\"").append(rule.type).append("\" priority=\"").append(rule.priority).append("\"");
      if (rule.operator != null && !rule.operator.isEmpty()) {
        sb.append(" operator=\"").append(rule.operator).append("\"");
      }
      // dxfId 指向 styles.xml 中的 dxf（0-based）
      sb.append(" dxfId=\"0\">");
      if (rule.formula != null && !rule.formula.isEmpty()) {
        sb.append("<formula>").append(escapeFormula(rule.formula)).append("</formula>");
      }
      sb.append("</cfRule>");
      sb.append("</conditionalFormatting>");
    }
    return sb.toString();
  }

  private static String injectBeforeWorksheetEnd(String sheetXml, String cfXml) {
    int pos = sheetXml.indexOf("</worksheet>");
    if (pos >= 0) {
      return sheetXml.substring(0, pos) + cfXml + sheetXml.substring(pos);
    }
    return sheetXml + cfXml;
  }

  private static String escapeFormula(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
