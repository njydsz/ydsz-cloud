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

import com.njydsz.common.excel.annotation.ExcelDataValidation;

/**
 * 数据验证后处理器 — 在已生成的 xlsx 中注入 {@code <dataValidations>} 节点。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 先写入基础 xlsx
 * ExcelFacade.write("base.xlsx", Dto.class).doWrite(data);
 * // 后处理注入数据验证（单 Sheet：仅 sheet 0）
 * try (FileInputStream fis = new FileInputStream("base.xlsx");
 *      FileOutputStream fos = new FileOutputStream("validated.xlsx")) {
 *   DataValidationHelper.apply(fis, fos, (sheetIndex, validations) -&gt; {
 *     if (sheetIndex != 0) return; // 仅对第一个 Sheet 应用
 *     validations.add(DataValidationHelper.listValidation("C2:C1048576", "男,女"));
 *     validations.add(DataValidationHelper.rangeValidation("D2:D1048576", "DECIMAL", "BETWEEN", "0", "999999"));
 *   });
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class DataValidationHelper {

  private DataValidationHelper() {}

  /**
   * 数据验证条目。
   */
  public static final class DataValidation {
    public String sqref; // 应用范围的单元格引用 "A1:A10"
    public String type;
    public String operator;
    public String formula1;
    public String formula2;
    public boolean ignoreBlank;
    public boolean showDropdown;
    public String errorTitle;
    public String errorContent;
    public String errorStyle;
    public String promptTitle;
    public String promptContent;

    DataValidation(String sqref) {
      this.sqref = sqref;
      this.ignoreBlank = true;
      this.showDropdown = false;
      this.errorTitle = "输入错误";
      this.errorContent = "输入值无效";
      this.errorStyle = ExcelDataValidation.ErrorStyle.STOP.value;
      this.promptTitle = "";
      this.promptContent = "";
    }
  }

  /** 创建列表验证（下拉框） */
  public static DataValidation listValidation(String sqref, String items) {
    DataValidation v = new DataValidation(sqref);
    v.type = "list";
    v.showDropdown = true;
    v.formula1 = "\"" + items.replace("\"", "\"\"") + "\"";
    return v;
  }

  /** 创建范围验证（数值/日期） */
  public static DataValidation rangeValidation(String sqref, String ooxmlType, String operator,
      String formula1, String formula2) {
    DataValidation v = new DataValidation(sqref);
    v.type = ooxmlType;
    v.operator = operator;
    v.formula1 = formula1;
    v.formula2 = formula2;
    return v;
  }

  /** 创建文本长度验证 */
  public static DataValidation lengthValidation(String sqref, String minLen, String maxLen) {
    DataValidation v = new DataValidation(sqref);
    v.type = "textLength";
    v.operator = "between";
    v.formula1 = minLen;
    v.formula2 = maxLen;
    return v;
  }

  /** 创建自定义公式验证 */
  public static DataValidation customValidation(String sqref, String formula) {
    DataValidation v = new DataValidation(sqref);
    v.type = "custom";
    v.operator = "";
    v.formula1 = formula;
    return v;
  }

  /**
   * 对 xlsx 应用数据验证。
   *
   * <p>单 Sheet 调用示例：
   *
   * <pre>{@code
   * DataValidationHelper.apply(fis, fos, (sheetIndex, validations) -> {
   *   if (sheetIndex == 0) {
   *     validations.add(DataValidationHelper.listValidation("C2:C1048576", "男,女"));
   *   }
   * });
   * }</pre>
   *
   * <p>多 Sheet 调用时为每个 sheet 分别调用一次 {@code put(sheetIndex, list)}。
   * 当前简单实现仅对 sheet 0 回调一次，供单 Sheet 场景使用。
   *
   * @param source 源 xlsx 输入流
   * @param dest 输出流
   * @param callback 配置回调，通过 {@link ValidationCallback#put(int, List)} 提供验证规则
   * @throws IOException IO 或 ZIP 解析异常
   */
  public static void apply(InputStream source, OutputStream dest,
      ValidationCallback callback) throws IOException {
    Map<Integer, List<DataValidation>> validationsBySheet = new LinkedHashMap<>();
    // 单 Sheet 场景（最常见）：为 sheet 0 准备可变列表，交给调用方填充
    List<DataValidation> sheet0List = new ArrayList<>();
    callback.put(0, sheet0List);
    if (!sheet0List.isEmpty()) {
      validationsBySheet.put(0, sheet0List);
    }

    if (validationsBySheet.isEmpty()) {
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

    // 为每个 sheet 注入 dataValidations
    for (Map.Entry<Integer, List<DataValidation>> entry : validationsBySheet.entrySet()) {
      int sheetIdx = entry.getKey();
      List<DataValidation> validations = entry.getValue();
      if (validations.isEmpty()) {
        continue;
      }
      String sheetName = "xl/worksheets/sheet" + (sheetIdx + 1) + ".xml";
      byte[] sheetBytes = entries.get(sheetName);
      if (sheetBytes == null) {
        continue;
      }

      String sheetXml = new String(sheetBytes, StandardCharsets.UTF_8);
      String dvXml = generateDataValidationsXml(validations);
      sheetXml = injectAfterSheetData(sheetXml, dvXml);
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

  @FunctionalInterface
  public interface ValidationCallback {
    void put(int sheetIndex, List<DataValidation> validations);
  }

  static String generateDataValidationsXml(List<DataValidation> validations) {
    StringBuilder sb = new StringBuilder(200);
    sb.append("<dataValidations count=\"").append(validations.size()).append("\">");
    for (DataValidation v : validations) {
      sb.append("<dataValidation type=\"").append(v.type).append("\"");
      if (v.operator != null && !v.operator.isEmpty()) {
        sb.append(" operator=\"").append(v.operator).append("\"");
      }
      if (v.errorStyle != null && !v.errorStyle.isEmpty()) {
        sb.append(" errorStyle=\"").append(v.errorStyle).append("\"");
      }
      sb.append(" allowBlank=\"").append(v.ignoreBlank ? 1 : 0).append("\"");
      sb.append(" showInputMessage=\"1\" showErrorMessage=\"1\"");
      if (v.showDropdown) {
        sb.append(" showDropDown=\"1\"");
      }
      sb.append(" sqref=\"").append(v.sqref).append("\"");
      if (v.errorTitle != null) {
        sb.append(" errorTitle=\"").append(escapeXml(v.errorTitle)).append("\"");
      }
      if (v.errorContent != null) {
        sb.append(" error=\"").append(escapeXml(v.errorContent)).append("\"");
      }
      if (v.promptTitle != null && !v.promptTitle.isEmpty()) {
        sb.append(" promptTitle=\"").append(escapeXml(v.promptTitle)).append("\"");
      }
      if (v.promptContent != null && !v.promptContent.isEmpty()) {
        sb.append(" prompt=\"").append(escapeXml(v.promptContent)).append("\"");
      }
      sb.append(">");
      if (v.formula1 != null && !v.formula1.isEmpty()) {
        sb.append("<formula1>").append(escapeFormula(v.formula1)).append("</formula1>");
      }
      if (v.formula2 != null && !v.formula2.isEmpty()) {
        sb.append("<formula2>").append(escapeFormula(v.formula2)).append("</formula2>");
      }
      sb.append("</dataValidation>");
    }
    sb.append("</dataValidations>");
    return sb.toString();
  }

  private static String injectAfterSheetData(String sheetXml, String dvXml) {
    int insertPos = sheetXml.indexOf("</sheetData>");
    if (insertPos >= 0) {
      insertPos += "</sheetData>".length();
      return sheetXml.substring(0, insertPos) + dvXml + sheetXml.substring(insertPos);
    }
    return sheetXml + dvXml;
  }

  private static String escapeXml(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;").replace(">", "&gt;");
  }

  private static String escapeFormula(String s) {
    // 公式内联特殊字符仅 &
    return s.replace("&", "&amp;");
  }
}
