package com.njydsz.common.excel.core.postprocess;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 合并单元格后处理器 — 扫描已生成的 xlsx，对带合并标记的列注入 {@code <mergeCells>} 节点。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 1. 正常写入（不含合并）
 * ExcelFacade.write("base.xlsx", Data.class).doWrite(data);
 *
 * // 2. 后处理注入合并
 * try (InputStream is = new FileInputStream("base.xlsx");
 *      OutputStream os = new FileOutputStream("merged.xlsx")) {
 *   MergeCellHelper.apply(is, os, config -&gt; {
 *     config.sheet(0).mergeColumn("region"); // 按字段名
 *     config.sheet(0).mergeColumn(1);        // 按列索引
 *   });
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class MergeCellHelper {

  private MergeCellHelper() {}

  /** 合并配置 */
  public static final class MergeConfig {
    private final Map<Integer, SheetConfig> sheets = new LinkedHashMap<>();

    public SheetConfig sheet(int sheetIndex) {
      return sheets.computeIfAbsent(sheetIndex, SheetConfig::new);
    }

    public SheetConfig firstSheet() {
      return sheet(0);
    }

    Map<Integer, SheetConfig> getSheets() {
      return sheets;
    }
  }

  /** 单 Sheet 合并配置 */
  public static final class SheetConfig {
    final int sheetIndex;
    final List<Integer> mergeColumns = new ArrayList<>();

    SheetConfig(int sheetIndex) {
      this.sheetIndex = sheetIndex;
    }

    public SheetConfig mergeColumn(int colIndex) {
      mergeColumns.add(colIndex);
      return this;
    }
  }

  /** 合并范围（内部不可变） */
  static final class MergeRange {
    final int col;
    final int startRow;
    final int endRow;

    MergeRange(int col, int startRow, int endRow) {
      this.col = col;
      this.startRow = startRow;
      this.endRow = endRow;
    }
  }

  /**
   * 对 xlsx 应用合并单元格后处理。
   *
   * @param sourceXlsx 源 xlsx 字节流
   * @param destXlsx 输出流
   * @param configurer 合并配置（Lambda）
   * @throws IOException IO 或 ZIP 解析异常
   */
  public static void apply(InputStream sourceXlsx, OutputStream destXlsx,
      java.util.function.Consumer<MergeConfig> configurer) throws IOException {
    MergeConfig config = new MergeConfig();
    configurer.accept(config);

    if (config.getSheets().isEmpty()) {
      // 无合并需求：直接复制
      byte[] buf = new byte[8192];
      int n;
      while ((n = sourceXlsx.read(buf)) != -1) {
        destXlsx.write(buf, 0, n);
      }
      return;
    }

    // 解析源 ZIP
    Map<String, byte[]> entries = new LinkedHashMap<>();
    try (ZipInputStream zis = new ZipInputStream(sourceXlsx)) {
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

    // 为每个需合并的 Sheet 解析 sheet XML 并注入 mergeCells
    for (Map.Entry<Integer, SheetConfig> entry : config.getSheets().entrySet()) {
      int sheetIdx = entry.getKey();
      SheetConfig cfg = entry.getValue();
      String sheetName = "xl/worksheets/sheet" + (sheetIdx + 1) + ".xml";
      byte[] sheetBytes = entries.get(sheetName);
      if (sheetBytes == null) {
        continue;
      }

      String sheetXml = new String(sheetBytes, StandardCharsets.UTF_8);
      List<MergeRange> ranges = detectMergeRanges(sheetXml, cfg.mergeColumns);

      if (!ranges.isEmpty()) {
        // 注入 mergeCells 节点
        String mergedXml = injectMergeCells(sheetXml, ranges);
        entries.put(sheetName, mergedXml.getBytes(StandardCharsets.UTF_8));
      }
    }

    // 重新打包 ZIP
    try (ZipOutputStream zos = new ZipOutputStream(destXlsx)) {
      for (Map.Entry<String, byte[]> e : entries.entrySet()) {
        ZipEntry zipEntry = new ZipEntry(e.getKey());
        zos.putNextEntry(zipEntry);
        zos.write(e.getValue());
        zos.closeEntry();
      }
      zos.finish();
    }
  }

  private static final Pattern ROW_PATTERN =
      Pattern.compile("<row[^>]*r=\"(\\d+)\">(.*?)</row>", Pattern.DOTALL);
  private static final Pattern CELL_REF_PATTERN = Pattern.compile("r=\"([A-Z]+)(\\d+)\"");

  /**
   * 在 Sheet XML 中检测每列连续值相同的范围。
   *
   * <p>简化实现：解析每行的每列单元格值（inlineStr / v），然后合并连续相同值。
   */
  private static List<MergeRange> detectMergeRanges(String sheetXml, List<Integer> mergeColumns) {
    List<MergeRange> result = new ArrayList<>();

    // 解析每行：行号 -> (列索引 -> 值)
    Map<Integer, Map<Integer, String>> rows = new LinkedHashMap<>();
    Matcher rowMatcher = ROW_PATTERN.matcher(sheetXml);
    while (rowMatcher.find()) {
      int rowNum = Integer.parseInt(rowMatcher.group(1));
      String rowContent = rowMatcher.group(2);
      Map<Integer, String> colValues = new LinkedHashMap<>();
      // 解析每个 <c> 的 ref 和值
      Pattern cellPattern = Pattern.compile("<c\\s+([^>]*)>(.*?)</c>", Pattern.DOTALL);
      Matcher cellMatcher = cellPattern.matcher(rowContent);
      while (cellMatcher.find()) {
        String attrs = cellMatcher.group(1);
        String content = cellMatcher.group(2);
        // 解析 ref 为列、行
        Matcher refMatcher = CELL_REF_PATTERN.matcher(attrs);
        if (refMatcher.find()) {
          String colLetters = refMatcher.group(1);
          int colIndex = colLettersToInt(colLetters);
          // 解析值
          String value = extractCellValue(content);
          colValues.put(colIndex, value);
        }
      }
      rows.put(rowNum, colValues);
    }

    // 对每个需合并的列扫描连续相同值
    for (int col : mergeColumns) {
      int startRow = -1;
      String prevValue = null;
      List<Integer> sortedRows = new ArrayList<>(rows.keySet());
      sortedRows.sort(Integer::compareTo);

      for (int rowNum : sortedRows) {
        Map<Integer, String> colValues = rows.get(rowNum);
        String curValue = colValues.get(col);
        if (curValue == null) {
          // 空值打断合并
          if (startRow != -1 && startRow < rowNum - 1) {
            result.add(new MergeRange(col, startRow, rowNum - 1));
          }
          startRow = -1;
          prevValue = null;
          continue;
        }
        if (curValue.equals(prevValue)) {
          // 继续合并
        } else {
          // 上一个合并段结束
          if (startRow != -1 && startRow < rowNum - 1) {
            result.add(new MergeRange(col, startRow, rowNum - 1));
          }
          startRow = rowNum;
          prevValue = curValue;
        }
      }
      // 最后一段
      if (startRow != -1 && !sortedRows.isEmpty()) {
        int lastRow = sortedRows.get(sortedRows.size() - 1);
        if (startRow < lastRow) {
          result.add(new MergeRange(col, startRow, lastRow));
        }
      }
    }

    return result;
  }

  /** 从 <c> 内容提取字符串值 */
  private static String extractCellValue(String cellContent) {
    // 优先 inlineStr: <is><t>val</t></is>
    Matcher m = Pattern.compile("<is><t[^>]*>(.*?)</t></is>", Pattern.DOTALL).matcher(cellContent);
    if (m.find()) {
      return m.group(1);
    }
    // v: <v>val</v>
    Matcher mv = Pattern.compile("<v>(.*?)</v>", Pattern.DOTALL).matcher(cellContent);
    if (mv.find()) {
      return mv.group(1);
    }
    return "";
  }

  /** 列字母（如 "AA"）转数字索引 */
  private static int colLettersToInt(String col) {
    int result = 0;
    for (int i = 0; i < col.length(); i++) {
      result = result * 26 + (col.charAt(i) - 'A' + 1);
    }
    return result - 1;
  }

  /** 数字索引转列字母 */
  private static String intToColLetters(int col) {
    StringBuilder sb = new StringBuilder();
    int c = col;
    while (c >= 0) {
      sb.insert(0, (char) ('A' + c % 26));
      c = c / 26 - 1;
      if (c < 0) break;
    }
    return sb.toString();
  }

  /**
   * 将 {@code <mergeCells>} 节点注入到 {@code <worksheet>} 内（在 {@code </sheetData>} 前）。
   *
   * <p>如果已有 mergeCells 则替换。
   */
  private static String injectMergeCells(String sheetXml, List<MergeRange> ranges) {
    if (ranges.isEmpty()) {
      return sheetXml;
    }
    StringBuilder sb = new StringBuilder(200);
    sb.append("<mergeCells count=\"").append(ranges.size()).append("\">");
    for (MergeRange range : ranges) {
      String col = intToColLetters(range.col);
      sb.append("<mergeCell ref=\"")
          .append(col).append(range.startRow)
          .append(":").append(col).append(range.endRow)
          .append("\"/>");
    }
    sb.append("</mergeCells>");

    String mergeCellsXml = sb.toString();

    // 尝试替换已有 mergeCells
    if (sheetXml.contains("<mergeCells")) {
      sheetXml = sheetXml.replaceAll("<mergeCells.*?</mergeCells>", mergeCellsXml);
    } else {
      // 插入到 </sheetData> 后
      int insertPos = sheetXml.indexOf("</sheetData>");
      if (insertPos >= 0) {
        insertPos += "</sheetData>".length();
        sheetXml = sheetXml.substring(0, insertPos) + mergeCellsXml + sheetXml.substring(insertPos);
      }
    }

    return sheetXml;
  }
}
