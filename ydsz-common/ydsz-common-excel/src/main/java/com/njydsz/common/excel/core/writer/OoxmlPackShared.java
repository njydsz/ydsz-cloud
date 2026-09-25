package com.njydsz.common.excel.core.writer;

import java.nio.charset.StandardCharsets;

/**
 * OOXML 包共享常量 — 统一承载各写入器共用的 ZIP 条目字节内容。
 *
 * <p>消除跨写入器（{@link SuperFastExcelWriter}、{@link MultiSheetFastWriter}、
 * {@code ReactiveExcelWriter}）之间的静态字节数组重复定义，确保 OOXML 结构的唯一维护点。
 *
 * <h3>包含的共享条目</h3>
 *
 * <ul>
 *   <li>{@link #ROOT_RELS} — {@code _rels/.rels}（根关系表）</li>
 *   <li>{@link #THEME} — {@code xl/theme/theme1.xml}（Office 主题定义）</li>
 *   <li>{@link #STYLES} — {@code xl/styles.xml}（最小化样式）</li>
 *   <li>{@link #SHEET_HEADER} — sheet XML 前缀（worksheet + sheetData 头）</li>
 *   <li>{@link #SHEET_FOOTER} — sheet XML 后缀（闭合标签）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class OoxmlPackShared {

  private OoxmlPackShared() {}

  /** {@code _rels/.rels} */
  public static final byte[] ROOT_RELS = (
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
          + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
          + "<Relationship Id=\"rId1\" "
          + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" "
          + "Target=\"xl/workbook.xml\"/>"
          + "</Relationships>")
      .getBytes(StandardCharsets.UTF_8);

  /** {@code xl/theme/theme1.xml} — Office 默认主题（简化版） */
  public static final byte[] THEME = (
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
          + "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"Office Theme\">"
          + "<a:themeElements><a:clrScheme name=\"Office\"><a:dk1><a:sysClr val=\"windowText\" lastClr=\"000000\"/>"
          + "</a:dk1><a:lt1><a:sysClr val=\"window\" lastClr=\"FFFFFF\"/></a:lt1><a:dk2><a:srgbClr val=\"44546A\"/>"
          + "</a:dk2><a:lt2><a:srgbClr val=\"E7E6E6\"/></a:lt2><a:accent1><a:srgbClr val=\"4472C4\"/></a:accent1>"
          + "<a:accent2><a:srgbClr val=\"ED7D31\"/></a:accent2><a:accent3><a:srgbClr val=\"A5A5A5\"/></a:accent3>"
          + "<a:accent4><a:srgbClr val=\"FFC000\"/></a:accent4><a:accent5><a:srgbClr val=\"5B9BD5\"/></a:accent5>"
          + "<a:accent6><a:srgbClr val=\"70AD47\"/></a:accent6><a:hlink><a:srgbClr val=\"0563C1\"/></a:hlink>"
          + "<a:folHlink><a:srgbClr val=\"954F72\"/></a:folHlink></a:clrScheme><a:fontScheme name=\"Office\">"
          + "<a:majorFont><a:latin typeface=\"Calibri Light\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/>"
          + "</a:majorFont><a:minorFont><a:latin typeface=\"Calibri\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/>"
          + "</a:minorFont></a:fontScheme><a:fmtScheme name=\"Office\"><a:fillStyleLst>"
          + "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/>"
          + "</a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:fillStyleLst>"
          + "<a:lnStyleLst><a:ln/><a:ln/><a:ln/></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/>"
          + "</a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/>"
          + "</a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/>"
          + "</a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/>"
          + "</a:solidFill></a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>")
      .getBytes(StandardCharsets.UTF_8);

  /** {@code xl/styles.xml} — 最小化样式 */
  public static final byte[] STYLES = (
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
          + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
          + "<numFmts count=\"0\"/><fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
          + "<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>"
          + "<borders count=\"1\"><border><left/><right/><top/><bottom/></border></borders>"
          + "<cellStyleXfs count=\"1\"><xf/></cellStyleXfs>"
          + "<cellXfs count=\"1\"><xf/></cellXfs>"
          + "</styleSheet>")
      .getBytes(StandardCharsets.UTF_8);

  /** sheet XML 前缀 */
  public static final byte[] SHEET_HEADER = (
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
          + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
          + "<sheetData>")
      .getBytes(StandardCharsets.UTF_8);

  /** sheet XML 后缀 */
  public static final byte[] SHEET_FOOTER =
      "</sheetData></worksheet>".getBytes(StandardCharsets.UTF_8);
}
