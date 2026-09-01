package com.njydsz.common.excel.core.reader.sax;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 样式表读取器 — 解析 xl/styles.xml，判定单元格样式是否为日期格式。
 *
 * <p>深度完善（方案 B）：fast 引擎此前不解析 styles.xml，数值型日期单元格
 * （如 {@code <c s="1"><v>45123.5</v></c>} 配合日期 numFmt）一律按纯数字读入，
 * {@code Date} 字段拿到的是错误值——即 {@code fastNumericDateCellIsKnownLimitation}
 * 存档的已知限制。本读取器补齐该缺口：
 *
 * <ol>
 *   <li>解析 {@code <numFmts>} 收集自定义 numFmtId → formatCode
 *   <li>解析 {@code <cellXfs>} 依序收集每个 {@code <xf>} 的 numFmtId（cellXfs 的
 *       列表下标即 sheet XML 中 {@code <c s="N">} 的样式索引）
 *   <li>{@link #isDateFormat(int)}：样式索引 → numFmtId → 内建日期格式区间
 *       （14-22、45-47，与 POI BuiltinFormats 一致）或自定义 formatCode 判定
 * </ol>
 *
 * <h3>判定语义</h3>
 *
 * <p>自定义 formatCode 判定对齐 POI {@code DateUtil.isADateFormat} 的简化版：剥离
 * 引号内文字、反斜杠转义、方括号块（颜色如 {@code [Red]}）后，仅取第一个分号前的
 * 格式节，检查是否含 y/m/d/h/s 占位符。styles.xml 缺失或样式索引越界时一律返回
 * false（保守方向：宁可当普通数字，不误转日期）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see SuperFastExcelReader
 * @see SheetXmlReader
 */
public class StylesReader {

  /** 内建日期格式区间：14-22（m/d/yy … m/d/yy h:mm）与 45-47（mm:ss … mmss.0），与 POI 一致 */
  private static final int[][] BUILTIN_DATE_RANGES = {{14, 22}, {45, 47}};

  /** 自定义 numFmtId → formatCode（numFmts 部件） */
  private final Map<Integer, String> customFormats = new HashMap<>();

  /** cellXfs 列表：下标即样式索引（s 属性），值为该 xf 的 numFmtId（缺省 0 = General） */
  private int[] xfNumFmtIds = new int[0];

  /** 是否已解析到有效内容（styles.xml 缺失时为 false，isDateFormat 恒 false） */
  private boolean parsed = false;

  /**
   * 解析 styles.xml 输入流。
   *
   * @param is styles.xml 输入流（调用方负责 bounded 限流与关闭）
   * @throws IOException 读取异常
   */
  void parse(InputStream is) throws IOException {
    String xml = new String(readAll(is), StandardCharsets.UTF_8);
    parseCustomFormats(xml);
    parseCellXfs(xml);
    parsed = true;
  }

  /**
   * 判断样式索引对应的格式是否为日期/时间格式。
   *
   * @param styleIndex sheet XML 中 {@code <c s="N">} 的样式索引
   * @return 是日期格式返回 true；未解析、索引越界或非日期格式返回 false
   */
  boolean isDateFormat(int styleIndex) {
    if (!parsed || styleIndex < 0 || styleIndex >= xfNumFmtIds.length) {
      return false;
    }
    return isDateFormatId(xfNumFmtIds[styleIndex]);
  }

  /** numFmtId → 是否日期格式（内建区间 + 自定义 formatCode 判定）。 */
  private boolean isDateFormatId(int numFmtId) {
    for (int[] range : BUILTIN_DATE_RANGES) {
      if (numFmtId >= range[0] && numFmtId <= range[1]) {
        return true;
      }
    }
    String formatCode = customFormats.get(numFmtId);
    return formatCode != null && containsDatePlaceholder(formatCode);
  }

  /** 解析 {@code <numFmts>} 中自定义格式：numFmtId → formatCode（含 XML 反转义）。 */
  private void parseCustomFormats(String xml) {
    int fmtsStart = xml.indexOf("<numFmts");
    if (fmtsStart < 0) {
      return;
    }
    int fmtsEnd = xml.indexOf("</numFmts>", fmtsStart);
    if (fmtsEnd < 0) {
      fmtsEnd = xml.length();
    }
    String section = xml.substring(fmtsStart, fmtsEnd);
    int pos = 0;
    while (true) {
      int tagStart = section.indexOf("<numFmt ", pos);
      if (tagStart < 0) {
        break;
      }
      int tagEnd = section.indexOf('>', tagStart);
      if (tagEnd < 0) {
        break;
      }
      String tag = section.substring(tagStart, tagEnd);
      Integer id = parsePositiveInt(extractAttribute(tag, "numFmtId"));
      String code = extractAttribute(tag, "formatCode");
      if (id != null && code != null) {
        customFormats.put(id, xmlUnescape(code));
      }
      pos = tagEnd + 1;
    }
  }

  /** 解析 {@code <cellXfs>}：依序收集每个 {@code <xf>} 的 numFmtId（缺省 0）。 */
  private void parseCellXfs(String xml) {
    int xfsStart = xml.indexOf("<cellXfs");
    if (xfsStart < 0) {
      return;
    }
    int contentStart = xml.indexOf('>', xfsStart);
    int xfsEnd = xml.indexOf("</cellXfs>", xfsStart);
    if (contentStart < 0) {
      return;
    }
    if (xfsEnd < 0) {
      xfsEnd = xml.length();
    }
    String section = xml.substring(contentStart + 1, xfsEnd);
    int count = 0;
    int pos = 0;
    while (true) {
      int tagStart = section.indexOf("<xf ", pos);
      if (tagStart < 0) {
        break;
      }
      count++;
      pos = tagStart + 4;
    }
    if (count == 0) {
      return;
    }
    xfNumFmtIds = new int[count];
    pos = 0;
    int idx = 0;
    while (true) {
      int tagStart = section.indexOf("<xf ", pos);
      if (tagStart < 0) {
        break;
      }
      int tagEnd = section.indexOf('>', tagStart);
      if (tagEnd < 0) {
        break;
      }
      String tag = section.substring(tagStart, tagEnd);
      Integer id = parsePositiveInt(extractAttribute(tag, "numFmtId"));
      xfNumFmtIds[idx++] = id != null ? id : 0;
      pos = tagEnd + 1;
    }
  }

  /**
   * 自定义 formatCode 是否含日期占位符（POI isADateFormat 简化版）。
   *
   * <p>剥离引号内文字、反斜杠转义、方括号块（{@code [Red]} 等颜色；{@code [h]} 等时长
   * 块按 POI 语义归入非日期占位符一并剥离），仅取第一个分号前的格式节， 再检查是否含
   * y/m/d/h/s 字母。"General"、"0.00"、"#,##0"、"0.00E+00"、"@" 等常见 数字格式均不含
   * 这些字母，不会误判。
   */
  private static boolean containsDatePlaceholder(String formatCode) {
    StringBuilder normalized = new StringBuilder(formatCode.length());
    boolean inQuote = false;
    for (int i = 0; i < formatCode.length(); i++) {
      char c = formatCode.charAt(i);
      if (c == '"') {
        inQuote = !inQuote;
        continue;
      }
      if (inQuote) {
        continue;
      }
      if (c == '\\') {
        i++;
        continue;
      }
      if (c == '[') {
        int close = formatCode.indexOf(']', i);
        if (close > i) {
          i = close;
        }
        continue;
      }
      if (c == ';') {
        break;
      }
      normalized.append(Character.toLowerCase(c));
    }
    String s = normalized.toString();
    return s.indexOf('y') >= 0
        || s.indexOf('m') >= 0
        || s.indexOf('d') >= 0
        || s.indexOf('h') >= 0
        || s.indexOf('s') >= 0;
  }

  /** 从标签文本提取属性值（双引号形式）。 */
  private static String extractAttribute(String tag, String attr) {
    int idx = tag.indexOf(attr + "=\"");
    if (idx < 0) {
      return null;
    }
    int valueStart = idx + attr.length() + 2;
    int valueEnd = tag.indexOf('"', valueStart);
    if (valueEnd <= valueStart) {
      return null;
    }
    return tag.substring(valueStart, valueEnd);
  }

  /** 解析非负整数（解析失败返回 null）。 */
  private static Integer parsePositiveInt(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** 最小 XML 反转义（formatCode 中的 &quot; 等）。 */
  private static String xmlUnescape(String s) {
    return s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&");
  }

  /** 读尽输入流（styles.xml 体积小，bounded 后可控）。 */
  private static byte[] readAll(InputStream is) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
    byte[] buffer = new byte[4096];
    int len;
    while ((len = is.read(buffer)) > 0) {
      baos.write(buffer, 0, len);
    }
    return baos.toByteArray();
  }
}
