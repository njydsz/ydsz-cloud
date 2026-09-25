package com.njydsz.common.excel.core.writer;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.context.AnalysisContext;
import com.njydsz.common.excel.core.metadata.WriteMetadata;
import com.njydsz.common.excel.core.security.FormulaInjectionGuard;
import com.njydsz.common.excel.support.cache.ReflectCache;
import com.njydsz.common.excel.support.mh.MHFieldAccessor;

/**
 * 零 POI 模板引擎 — 基于 .xlsx 模板文件填充数据，保留模板样式/列宽/合并区域/条件格式。
 *
 * <p>对标被删除的 {@code ExcelTemplateWriter} 的 POI 能力（表头映射、列样式复用、区域循环填充），
 * 通过直接操作 OOXML ZIP 包内的 XML 元素实现，完全脱离 Apache POI。
 *
 * <h3>工作原理</h3>
 *
 * <ol>
 *   <li><b>模板加载</b>：读取模板 .xlsx ZIP 包，将各 entry 缓存为 byte[]</li>
 *   <li><b>workbook/rels 解析</b>：定位目标 sheet 条目路径</li>
 *   <li><b>styles.xml 解析</b>：提取 cellXfs 数量（确保写入时不超边界）</li>
 *   <li><b>SST 解析</b>：读入共享字符串供查询；写入新字符串时追加</li>
 *   <li><b>sheet XML 解析</b>：提取 {@code <cols>}（列宽）、{@code <mergeCells>}（合并区域）、
 *       模板行结构（每列的 cell 样式）</li>
 *   <li><b>表头分析</b>：从模板表头行提取列名，按 {@link ExcelProperty} 注解规则匹配字段映射</li>
 *   <li><b>数据写入</b>：每行数据 cell 复用模板列对应的 styleId；区域循环模式克隆模板行区间</li>
 *   <li><b>ZIP 输出</b>：保留模板所有原始条目，仅替换目标 sheet 和（如有新字符串）SST</li>
 * </ol>
 *
 * <h3>保留的模板能力</h3>
 *
 * <ul>
 *   <li><b>列样式复用</b>：新数据行每列 styleId 取自模板对应列（模板首行数据行/表头行）</li>
 *   <li><b>列宽</b>：{@code <cols>} 元素整体复制到输出</li>
 *   <li><b>合并区域</b>：{@code <mergeCells>} 复制到输出（不扩展合并范围）</li>
 *   <li><b>Sheet 视图</b>：冻结窗格、选中状态等从模板 sheetViews 复制</li>
 *   <li><b>公式行偏移</b>：区域循环模式中，公式中的行引用自动按行偏移调整</li>
 *   <li><b>SST 追加</b>：模板原有字符串保留，新字符串追加到 SST 尾部</li>
 * </ul>
 *
 * <h3>限制</h3>
 *
 * <ul>
 *   <li><b>单 Sheet 写入</b>：模板模式一次只写入一个 Sheet（多 Sheet 循环请多次调用）</li>
 *   <li><b>图片/图表/条件格式</b>：模板中的图片/图表/CF 通过保留原始 XML 维持，不会被修改/
 *       扩展；新增行不会自动生成条件格式</li>
 *   <li><b>公式引用超出模板区域</b>：仅区域循环模式支持公式行偏移；新数据行的公式需要
 *       通过 {@link ExcelProperty#formula()} 设定（行号由 {@code rowIndex} 占位符自动替换）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see com.njydsz.common.excel.core.template.TemplateRegion
 */
public class SuperFastExcelTemplateWriter {

  private static final Logger LOG = LoggerFactory.getLogger(SuperFastExcelTemplateWriter.class);

  private static final int ZIP_BUFFER_SIZE = 1024 * 1024;

  /** 公式中单元格的行引用正则：匹配 字母+数字 形式的引用（如 A1、BC123）。 */
  private static final Pattern CELL_REF_PATTERN = Pattern.compile("([A-Z]+)(\\d+)");

  /** 行号占位符（@ExcelProperty.formula 中用 {row} 表示当前行 Excel 1-based 行号）。 */
  private static final Pattern ROW_PLACEHOLDER = Pattern.compile("\\{row\\}", Pattern.LITERAL);

  // ==================== 模板加载后的解析产物 ====================

  /** 模板所有 ZIP entry 名 → 字节内容。 */
  private final Map<String, byte[]> templateEntries = new LinkedHashMap<>();

  /** 模板 styles.xml 解析出的 cellXfs 总数（用于边界校验）。 */
  private int templateStyleCount = 0;

  /** 模板 sharedStrings.xml 解析出的共享字符串数组。 */
  private String[] templateSst = new String[0];

  /** 模板 sheet XML 解析出的 {@code <cols>} 元素内容（含首尾的 &lt;cols&gt; 标签）。 */
  private String templateColsXml = "";

  /** 模板 sheet XML 解析出的 {@code <mergeCells>} 元素内容。 */
  private String templateMergeCellsXml = "";

  /** 模板 sheet XML 解析出的 {@code <sheetViews>} 元素内容。 */
  private String templateSheetViewsXml = "";

  /** 模板行号(1-based) → 行原始 XML（不含 &lt;row&gt; 首尾标签，仅内层 &lt;c&gt; 序列）。 */
  private final Map<Integer, String> templateRowContents = new LinkedHashMap<>();

  /**
   * 列索引(0-based) → 该列在模板首数据行对应的 styleId。
   *
   * <p>用于写入数据行时复用单元格样式（&lt;c s="N"&gt;）。无模板数据行时 fallback 到 0。
   */
  private final Map<Integer, Integer> columnIndexToStyleId = new LinkedHashMap<>();

  // ==================== 写入配置 ====================

  private final WriteMetadata metadata;
  private final AnalysisContext context;
  private final ExcelConfig excelConfig;

  /** 目标 sheet 的名称（用于日志与错误消息）。 */
  private String targetSheetName = "sheet1";

  /** 目标 sheet 的 ZIP entry 路径（如 "xl/worksheets/sheet1.xml"）。 */
  private String targetSheetEntryPath;

  /**
   * 构造模板写入器（文件路径 + 输出路径）。
   *
   * @param templatePath 模板文件路径（.xlsx）
   * @param outputPath 输出文件路径（.xlsx）
   * @param metadata 写入元数据
   */
  public SuperFastExcelTemplateWriter(String templatePath, String outputPath, WriteMetadata metadata) {
    this.metadata = metadata;
    this.context = new AnalysisContext(metadata);
    this.excelConfig =
        metadata.getExcelConfig() != null ? metadata.getExcelConfig() : ExcelConfig.defaults();
    metadata.setFilePath(outputPath);
    loadTemplate(templatePath);
  }

  /**
   * 构造模板写入器（InputStream + 输出路径）。
   *
   * @param templateStream 模板文件输入流（调用方负责关闭）
   * @param outputPath 输出文件路径
   * @param metadata 写入元数据
   */
  public SuperFastExcelTemplateWriter(
      InputStream templateStream, String outputPath, WriteMetadata metadata) {
    this.metadata = metadata;
    this.context = new AnalysisContext(metadata);
    this.excelConfig =
        metadata.getExcelConfig() != null ? metadata.getExcelConfig() : ExcelConfig.defaults();
    metadata.setFilePath(outputPath);
    try {
      loadTemplateFromStream(templateStream);
    } catch (IOException e) {
      throw new IllegalArgumentException("模板流读取失败: " + e.getMessage(), e);
    }
  }

  /**
   * 构造模板写入器（字节数组 + 输出路径）。
   *
   * @param templateBytes 模板文件字节内容
   * @param outputPath 输出文件路径
   * @param metadata 写入元数据
   */
  public SuperFastExcelTemplateWriter(byte[] templateBytes, String outputPath, WriteMetadata metadata) {
    this.metadata = metadata;
    this.context = new AnalysisContext(metadata);
    this.excelConfig =
        metadata.getExcelConfig() != null ? metadata.getExcelConfig() : ExcelConfig.defaults();
    metadata.setFilePath(outputPath);
    loadTemplateFromBytes(templateBytes);
  }

  // ==================== 模板加载 ====================

  private void loadTemplate(String templatePath) {
    try (FileInputStream fis = new FileInputStream(templatePath)) {
      loadTemplateFromStream(fis);
    } catch (IOException e) {
      throw new IllegalArgumentException("模板文件读取失败: " + templatePath, e);
    }
  }

  private void loadTemplateFromStream(InputStream is) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int n;
    while ((n = is.read(buf)) > 0) {
      baos.write(buf, 0, n);
    }
    loadTemplateFromBytes(baos.toByteArray());
  }

  private void loadTemplateFromBytes(byte[] bytes) {
    try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
        new java.io.ByteArrayInputStream(bytes))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          templateEntries.put(entry.getName(), readAll(zis));
        }
        zis.closeEntry();
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("模板文件不是有效的 ZIP/OOXML 格式", e);
    }
    parseTemplate();
  }

  /**
   * 解析模板的关键 XML 组件（workbook/rels/styles/SST/sheet）。
   */
  private void parseTemplate() {
    parseWorkbook();
    parseStyles();
    parseSharedStrings();
    parseTargetSheet();
  }

  /**
   * 解析 workbook.xml.rels 确定模板 entry 名对应的 sheet ZIP entry 路径。
   *
   * <p>按 metadata.sheetName 精确匹配 → metadata.sheetNo 顺序 → 第一个 sheet。
   */
  private void parseWorkbook() {
    byte[] relsBytes = templateEntries.get("xl/_rels/workbook.xml.rels");
    if (relsBytes == null) {
      LOG.warn("模板缺少 xl/_rels/workbook.xml.rels，使用默认路径");
      targetSheetEntryPath = "xl/worksheets/sheet1.xml";
      return;
    }
    String rels = new String(relsBytes, StandardCharsets.UTF_8);
    List<String[]> sheetRefs = new ArrayList<>();
    Pattern p = Pattern.compile(
        "<Relationship[^>]*Id=\"([^\"]*)\"[^>]*?Target=\"([^\"]*?)\"", Pattern.DOTALL);
    Matcher m = p.matcher(rels);
    while (m.find()) {
      String target = m.group(2);
      if (target.contains("worksheets/") && target.endsWith(".xml")) {
        sheetRefs.add(new String[]{m.group(1), target});
      }
    }
    if (sheetRefs.isEmpty()) {
      targetSheetEntryPath = "xl/worksheets/sheet1.xml";
      return;
    }

    // 按 sheetName 或 sheetNo 选择目标 sheet
    String sheetName = metadata.getSheetName();
    byte[] workbookBytes = templateEntries.get("xl/workbook.xml");
    if (sheetName != null && !sheetName.isEmpty() && workbookBytes != null) {
      List<String> sheetNamesInOrder = extractSheetNamesFromWorkbook(
          new String(workbookBytes, StandardCharsets.UTF_8));
      for (int i = 0; i < sheetNamesInOrder.size(); i++) {
        if (sheetName.equals(sheetNamesInOrder.get(i)) && i < sheetRefs.size()) {
          targetSheetEntryPath = toAbsoluteEntryPath(sheetRefs.get(i)[1]);
          targetSheetName = sheetName;
          return;
        }
      }
    }
    Integer sheetNo = metadata.getSheetNo();
    int idx = (sheetNo != null && sheetNo >= 0 && sheetNo < sheetRefs.size()) ? sheetNo : 0;
    targetSheetEntryPath = toAbsoluteEntryPath(sheetRefs.get(idx)[1]);
  }

  /**
   * 从 workbook.xml 提取 Sheet 名称列表（声明顺序）。
   *
   * @param xml workbook.xml 内容
   * @return Sheet 名称列表
   */
  private List<String> extractSheetNamesFromWorkbook(String xml) {
    List<String> names = new ArrayList<>();
    Pattern p = Pattern.compile("<sheet\\s+name=\"([^\"]*)\"", Pattern.DOTALL);
    Matcher m = p.matcher(xml);
    while (m.find()) {
      names.add(m.group(1));
    }
    return names;
  }

  private List<String> workbookSheetNames = new ArrayList<>();

  private String toAbsoluteEntryPath(String relPath) {
    if (relPath.startsWith("/")) {
      return relPath.substring(1);
    }
    if (relPath.startsWith("xl/")) {
      return relPath;
    }
    return "xl/" + relPath;
  }

  /**
   * 解析 styles.xml，提取 cellXfs 总数。
   */
  private void parseStyles() {
    byte[] stylesBytes = templateEntries.get("xl/styles.xml");
    if (stylesBytes == null) {
      return;
    }
    String styles = new String(stylesBytes, StandardCharsets.UTF_8);
    int count = 0;
    Pattern p = Pattern.compile("<xf\\s", Pattern.DOTALL);
    Matcher m = p.matcher(styles);
    while (m.find()) {
      count++;
    }
    templateStyleCount = count;
  }

  /**
   * 解析 sharedStrings.xml 为字符串数组。
   */
  private void parseSharedStrings() {
    byte[] sstBytes = templateEntries.get("xl/sharedStrings.xml");
    if (sstBytes == null) {
      templateSst = new String[0];
      return;
    }
    String xml = new String(sstBytes, StandardCharsets.UTF_8);
    List<String> values = new ArrayList<>(64);
    Pattern p = Pattern.compile("<si>(.*?)</si>", Pattern.DOTALL);
    Matcher m = p.matcher(xml);
    while (m.find()) {
      String si = m.group(1);
      StringBuilder sb = new StringBuilder(si.length());
      Pattern tp = Pattern.compile("<t[^>]*>(.*?)</t>", Pattern.DOTALL);
      Matcher tm = tp.matcher(si);
      while (tm.find()) {
        sb.append(unescapeXml(tm.group(1)));
      }
      values.add(sb.toString());
    }
    templateSst = values.toArray(new String[0]);
  }

  /**
   * 解析目标 sheet XML，提取 col widths / merge cells / sheetViews / 模板行结构。
   */
  private void parseTargetSheet() {
    byte[] sheetBytes = templateEntries.get(targetSheetEntryPath);
    if (sheetBytes == null) {
      throw new IllegalArgumentException("模板缺少目标 Sheet XML: " + targetSheetEntryPath);
    }
    String xml = new String(sheetBytes, StandardCharsets.UTF_8);

    // 提取 <cols>...</cols>
    int colsStart = xml.indexOf("<cols>");
    int colsEnd = xml.indexOf("</cols>");
    if (colsStart != -1 && colsEnd != -1) {
      templateColsXml = xml.substring(colsStart, colsEnd + "</cols>".length());
    }

    // 提取 <sheetViews>...</sheetViews>
    int svStart = xml.indexOf("<sheetViews>");
    int svEnd = xml.indexOf("</sheetViews>");
    if (svStart != -1 && svEnd != -1) {
      templateSheetViewsXml = xml.substring(svStart, svEnd + "</sheetViews>".length());
    }

    // 提取 <mergeCells>...</mergeCells>
    int mcStart = xml.indexOf("<mergeCells>");
    int mcEnd = xml.indexOf("</mergeCells>");
    if (mcStart != -1 && mcEnd != -1) {
      templateMergeCellsXml = xml.substring(mcStart, mcEnd + "</mergeCells>".length());
    }

    // 提取所有 <row> 的内容
    extractTemplateRows(xml);
  }

  /**
   * 提取所有 {@code <row>...</row>} 的内部内容（不含首尾 row 标签）。
   *
   * <p>同时构建 columnIndexToStyleId 映射：从模板第一个含数据的行（通常是第一行或第二行）
   * 读取每列的 styleId（{@code <c s="N">}）。
   */
  private void extractTemplateRows(String xml) {
    Pattern rowPattern = Pattern.compile("<row[^>]*>(.*?)</row>", Pattern.DOTALL);
    Matcher rowMatcher = rowPattern.matcher(xml);
    boolean firstDataRow = true;
    while (rowMatcher.find()) {
      String rowContent = rowMatcher.group(1);
      // 提取行号
      String rowTag = rowMatcher.group(0);
      int rowNum = parseRowNumber(rowTag);
      templateRowContents.put(rowNum, rowContent);
      // 从第一个含单元格数据的行读取列 → styleId 映射
      if (firstDataRow && rowContent.contains("<c ")) {
        buildColumnStyleMap(rowContent);
        firstDataRow = false;
      }
    }
    // 如果 columnIndexToStyleId 为空（模板样式在表头行），尝试从第一个 row 读取
    if (columnIndexToStyleId.isEmpty() && !templateRowContents.isEmpty()) {
      String firstRow = templateRowContents.values().iterator().next();
      buildColumnStyleMap(firstRow);
    }
  }

  private int parseRowNumber(String rowTag) {
    Pattern p = Pattern.compile("r=\"(\\d+)\"");
    Matcher m = p.matcher(rowTag);
    if (m.find()) {
      return Integer.parseInt(m.group(1));
    }
    return -1;
  }

  /**
   * 从行 XML 解析 列索引 → styleId 映射。
   */
  private void buildColumnStyleMap(String rowContent) {
    Pattern cellPattern = Pattern.compile("<c\\s+([^>]*)/?>", Pattern.DOTALL);
    Matcher cellMatcher = cellPattern.matcher(rowContent);
    while (cellMatcher.find()) {
      String attrs = cellMatcher.group(1);
      // 列索引：从 r="A1" 提取
      Pattern rP = Pattern.compile("r=\"([A-Z]+)\\d+\"");
      Matcher rM = rP.matcher(attrs);
      if (!rM.find()) {
        continue;
      }
      int colIdx = columnToIndex(rM.group(1));
      // styleId：从 s="N" 提取
      int styleId = 0;
      Pattern sP = Pattern.compile("s=\"(\\d+)\"");
      Matcher sM = sP.matcher(attrs);
      if (sM.find()) {
        styleId = Integer.parseInt(sM.group(1));
      }
      columnIndexToStyleId.putIfAbsent(colIdx, styleId);
    }
  }

  // ==================== 主写入方法 ====================

  /**
   * 执行数据写入。
   *
   * <p>将数据列表填充到模板中：
   *
   * <ul>
   *   <li>表头行取模板表头行 fieldRowNumber 行的原始 XML（保持样式）</li>
   *   <li>数据行每列 cell styleId 取自模板对应列样式映射</li>
   *   <li>列宽 / 合并区域 / 冻结窗格从模板复制</li>
   * </ul>
   *
   * @param data 数据列表（每条为匿名对象或 Map）
   */
  public void doWrite(List<?> data) {
    if (data == null || data.isEmpty()) {
      // 仅输出模板副本
      writeOutput(true);
      return;
    }

    Class<?> clazz = metadata.getClazz();
    Map<Integer, Field> columnFieldMap = new LinkedHashMap<>(16);
    List<String> headers = new ArrayList<>(16);

    // 表头分析：从模板表头行提取列名
    int headRowNumber = metadata.getHeadRowNumber() != null ? metadata.getHeadRowNumber() : 1;
    List<String> templateHeaders = extractTemplateHeaders(headRowNumber);

    if (clazz != null) {
      buildHeaderMapping(clazz, templateHeaders, columnFieldMap, headers);
    } else {
      headers = templateHeaders;
    }

    // 生成新的 sheet XML
    String newSheetXml = buildSheetXml(headRowNumber, data, columnFieldMap, headers, clazz);

    // 替换模板 sheet entry
    templateEntries.put(targetSheetEntryPath, newSheetXml.getBytes(StandardCharsets.UTF_8));

    // 如果新增了 SST 字符串，替换 SST entry
    if (!newSstStrings.isEmpty()) {
      byte[] newSstBytes = buildMergedSst();
      templateEntries.put("xl/sharedStrings.xml", newSstBytes);
      // 更新 workbook rels 确保 SST 关系存在
      ensureWorkbookRelsHasSst();
    }

    writeOutput(false);
  }

  /**
   * 带区域循环的数据写入。
   *
   * <p>每条数据项 clone 模板源行区间（sourceStartRow..sourceEndRow），行号按 targetStartRow
   * 起始依次填充。公式中的行引用自动偏移。
   *
   * @param data 数据列表
   * @param region 循环区域描述符
   */
  public void doWrite(List<?> data, com.njydsz.common.excel.core.template.TemplateRegion region) {
    if (data == null || data.isEmpty()) {
      writeOutput(true);
      return;
    }

    int sourceStart = region.getSourceStartRow() + 1; // 转 1-based
    int sourceEnd = region.getSourceEndRow() + 1;
    int targetStart = region.getTargetStartRow();
    if (targetStart < 0) {
      targetStart = sourceStart - 1; // 原地覆盖模式
    }
    targetStart++; // 转 1-based

    // 提取模板源行区间内容（行号 → cell 内层）
    List<String> templateRegionRows = new ArrayList<>();
    for (int r = sourceStart; r <= sourceEnd; r++) {
      String content = templateRowContents.get(r);
      if (content == null) {
        content = "";
      }
      templateRegionRows.add(content);
    }
    if (templateRegionRows.isEmpty()) {
      throw new IllegalArgumentException(
          String.format("模板源区域 [%d, %d] 无可克隆的行", sourceStart, sourceEnd));
    }

    Class<?> clazz = metadata.getClazz();
    Map<Integer, Field> columnFieldMap = new LinkedHashMap<>(16);
    List<String> headers = new ArrayList<>(16);

    // 表头分析：从第一个模板源行提取列名
    if (clazz != null && !templateRegionRows.isEmpty()) {
      List<String> regionHeaders = extractHeadersFromRow(templateRegionRows.get(0));
      buildHeaderMapping(clazz, regionHeaders, columnFieldMap, headers);
    }

    // 构建数据行
    StringBuilder dataRowsSb = new StringBuilder(data.size() * 256);
    int rowSpan = sourceEnd - sourceStart + 1;
    for (int i = 0; i < data.size(); i++) {
      Object item = data.get(i);
      int baseRow = targetStart + i * rowSpan;
      for (int r = 0; r < rowSpan; r++) {
        int newRowNum = baseRow + r;
        String rowContent = templateRegionRows.get(r);
        // 替换行号引用 + 公式偏移
        String adjusted = adjustRowReferences(rowContent, newRowNum - sourceStart - r + sourceStart);
        // 如果是填充行（区域第一行），注入数据
        if (r == 0 && clazz != null && !columnFieldMap.isEmpty()) {
          adjusted = injectDataToRow(adjusted, item, columnFieldMap, newRowNum, headers);
        }
        dataRowsSb.append("<row r=\"").append(newRowNum).append("\">");
        dataRowsSb.append(adjusted);
        dataRowsSb.append("</row>");
      }
    }

    // 生成完整的 sheet XML（保留模板表头行，追加数据行）
    String newSheetXml = buildSheetFromTemplate(dataRowsSb.toString());
    templateEntries.put(targetSheetEntryPath, newSheetXml.getBytes(StandardCharsets.UTF_8));

    // SST 处理
    if (!newSstStrings.isEmpty()) {
      templateEntries.put("xl/sharedStrings.xml", buildMergedSst());
      ensureWorkbookRelsHasSst();
    }

    writeOutput(false);
  }

  // ==================== Sheet XML 构建 ====================

  private String buildSheetXml(
      int headRowNumber,
      List<?> data,
      Map<Integer, Field> columnFieldMap,
      List<String> headers,
      Class<?> clazz) {
    StringBuilder sb = new StringBuilder(65536);
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
    sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");

    // sheetViews（保留模板冻结窗格）
    if (!templateSheetViewsXml.isEmpty()) {
      sb.append(templateSheetViewsXml);
    }

    // cols（保留模板列宽）
    if (!templateColsXml.isEmpty()) {
      sb.append(templateColsXml);
    }

    sb.append("<sheetData>");

    // 表头行：保留模板原始表头行 XML
    String headerRowContent = templateRowContents.get(headRowNumber);
    sb.append("<row r=\"").append(headRowNumber).append("\">");
    sb.append(headerRowContent != null ? headerRowContent : "");
    sb.append("</row>");

    // 数据行
    int startRow = headRowNumber + 1;
    for (int i = 0; i < data.size(); i++) {
      int rowNum = startRow + i;
      Object item = data.get(i);
      sb.append("<row r=\"").append(rowNum).append("\">");
      if (clazz != null && !columnFieldMap.isEmpty()) {
        appendDataCells(sb, item, columnFieldMap, rowNum, headers);
      } else if (item instanceof List<?> rowList) {
        appendListDataCells(sb, rowList, rowNum);
      }
      sb.append("</row>");
    }

    sb.append("</sheetData>");

    // mergeCells
    if (!templateMergeCellsXml.isEmpty()) {
      sb.append(templateMergeCellsXml);
    }

    sb.append("</worksheet>");
    return sb.toString();
  }

  /**
   * 基于模板 sheet 结构追加数据行，保留模板中原有的行。
   *
   * @param newDataRowsXml 已生成的数据行 XML 字符串
   */
  private String buildSheetFromTemplate(String newDataRowsXml) {
    byte[] originalBytes = templateEntries.get(targetSheetEntryPath);
    String original = new String(originalBytes, StandardCharsets.UTF_8);

    // 在 </sheetData> 前追加新行
    int sheetDataEnd = original.indexOf("</sheetData>");
    if (sheetDataEnd == -1) {
      // 无 sheetData 结束标签，回退到完整重建
      return buildSheetXml(0, List.of(), Map.of(), List.of(), null)
          .replace("<sheetData></sheetData>", "<sheetData>" + newDataRowsXml + "</sheetData>");
    }
    StringBuilder sb = new StringBuilder(original.length() + newDataRowsXml.length());
    sb.append(original, 0, sheetDataEnd);
    sb.append(newDataRowsXml);
    sb.append(original.substring(sheetDataEnd));
    return sb.toString();
  }

  // ==================== 单元格生成 ====================

  /**
   * 为一行对象数据生成 cell 序列 XML。
   *
   * <p>每列 cell 的 styleId 取自模板 columnIndexToStyleId（fallback 到 0）。公式按 {@code
   * @ExcelProperty.formula()} 设定，其中 {@code {row}} 替换为当前 Excel 行号。
   */
  private void appendDataCells(
      StringBuilder sb,
      Object item,
      Map<Integer, Field> columnFieldMap,
      int rowNum,
      List<String> headers) {
    for (Map.Entry<Integer, Field> entry : columnFieldMap.entrySet()) {
      int colIdx = entry.getKey();
      Field field = entry.getValue();
      ExcelProperty prop = field.getAnnotation(ExcelProperty.class);

      // cell ref (e.g., A1)
      String cellRef = toCellRef(colIdx, rowNum);
      int styleId = columnIndexToStyleId.getOrDefault(colIdx, 0);

      // 边界校验：不超模板 styles.xml 最大 styleId
      if (styleId >= templateStyleCount) {
        styleId = 0;
      }

      String value = getFieldValueAsString(item, field);
      String formula = prop != null ? prop.formula() : "";

      sb.append("<c r=\"").append(cellRef).append('"');
      if (styleId > 0) {
        sb.append(" s=\"").append(styleId).append('"');
      }

      if (formula != null && !formula.isEmpty()) {
        // 有公式：替换 {row} 占位符后输出公式元素
        String resolvedFormula = ROW_PLACEHOLDER.matcher(formula).replaceAll(Integer.toString(rowNum));
        sb.append(" t=\"str\"><f>").append(escapeXml(resolvedFormula)).append("</f><v>")
            .append(escapeXml(value)).append("</v></c>");
      } else if (value != null) {
        // 根据值类型输出（数值 inline / 字符串 SST）
        appendTypedCellValue(sb, value);
        sb.append("</c>");
      } else {
        sb.append("/>");
      }
    }
  }

  private void appendListDataCells(StringBuilder sb, List<?> rowList, int rowNum) {
    for (int colIdx = 0; colIdx < rowList.size(); colIdx++) {
      Object val = rowList.get(colIdx);
      String cellRef = toCellRef(colIdx, rowNum);
      int styleId = columnIndexToStyleId.getOrDefault(colIdx, 0);
      if (styleId >= templateStyleCount) {
        styleId = 0;
      }
      sb.append("<c r=\"").append(cellRef).append('"');
      if (styleId > 0) {
        sb.append(" s=\"").append(styleId).append('"');
      }
      if (val != null) {
        appendTypedCellValue(sb, val.toString());
        sb.append("</c>");
      } else {
        sb.append("/>");
      }
    }
  }

  /**
   * 根据值内容判断类型并输出 typed cell value。
   *
   * <p>数字 → {@code <v>number</v>}；字符串 → 追加到 SST 输出 {@code <c t="s"><v>idx</v></c>}。
   */
  private void appendTypedCellValue(StringBuilder sb, String value) {
    if (value == null) {
      sb.append("/>");
      return;
    }
    // 尝试数值
    if (isNumeric(value)) {
      sb.append("><v>").append(value).append("</v></c>");
      return;
    }
    // 字符串 → SST
    int sstIdx = addToStringTable(value);
    sb.append(" t=\"s\"><v>").append(sstIdx).append("</v></c>");
  }

  // 新增的 SST 字符串（模板填充阶段.append 到此；输出前合并为新 SST）
  private final List<String> newSstStrings = new ArrayList<>();

  private int addToStringTable(String value) {
    // 查找是否已在模板 SST 中存在
    for (int i = 0; i < templateSst.length; i++) {
      if (value.equals(templateSst[i])) {
        return i;
      }
    }
    // 查找是否已在新添加列表
    for (int i = 0; i < newSstStrings.size(); i++) {
      if (value.equals(newSstStrings.get(i))) {
        return templateSst.length + i;
      }
    }
    newSstStrings.add(value);
    return templateSst.length + newSstStrings.size() - 1;
  }

  // ==================== 模板表头分析 ====================

  /**
   * 从模板指定行号提取列名字符串列表（按 cell ref 排序）。
   */
  private List<String> extractTemplateHeaders(int rowNumber) {
    String rowContent = templateRowContents.get(rowNumber);
    if (rowContent == null) {
      // 尝试第一个 row
      if (!templateRowContents.isEmpty()) {
        rowContent = templateRowContents.values().iterator().next();
      }
    }
    if (rowContent == null) {
      return List.of();
    }
    return extractHeadersFromRow(rowContent);
  }

  /**
   * 从行内容 XML 提取列名（按 cell 引用排序后的字符串值）。
   *
   * <p>cell 值类型支持内联字符串（{@code <is><t>...</t></is>}）和 SST 引用（{@code t="s"}）。
   */
  private List<String> extractHeadersFromRow(String rowContent) {
    Map<Integer, String> colValues = new LinkedHashMap<>();
    Pattern cellPattern = Pattern.compile("<c\\s+([^>]*>(?:.*?)</c>|[^>]*/>)", Pattern.DOTALL);
    Matcher cellMatcher = cellPattern.matcher(rowContent);
    while (cellMatcher.find()) {
      String cellTag = cellMatcher.group(0);
      String attrs = cellMatcher.group(1);
      // colIdx
      Pattern rP = Pattern.compile("r=\"([A-Z]+)\\d+\"");
      Matcher rM = rP.matcher(cellTag);
      if (!rM.find()) {
        continue;
      }
      int colIdx = columnToIndex(rM.group(1));

      // 值：inlineStr or SST
      String value = "";
      if (cellTag.contains("<is>")) {
        Pattern tP = Pattern.compile("<t[^>]*>(.*?)</t>", Pattern.DOTALL);
        Matcher tM = tP.matcher(cellTag);
        if (tM.find()) {
          value = unescapeXml(tM.group(1));
        }
      } else if (cellTag.contains("t=\"s\"")) {
        Pattern vP = Pattern.compile("<v>(\\d+)</v>");
        Matcher vM = vP.matcher(cellTag);
        if (vM.find()) {
          int sstIdx = Integer.parseInt(vM.group(1));
          if (sstIdx >= 0 && sstIdx < templateSst.length) {
            value = templateSst[sstIdx];
          }
        }
      } else {
        // 数值或纯字符串（inline 的 <v>）
        Pattern vP = Pattern.compile("<v>(.*?)</v>", Pattern.DOTALL);
        Matcher vM = vP.matcher(cellTag);
        if (vM.find()) {
          value = vM.group(1);
        }
      }
      colValues.put(colIdx, value);
    }
    // 按列索引排序
    List<Integer> sortedCols = new ArrayList<>(colValues.keySet());
    Collections.sort(sortedCols);
    List<String> headers = new ArrayList<>(sortedCols.size());
    for (int col : sortedCols) {
      headers.add(colValues.get(col));
    }
    return headers;
  }

  /**
   * 构建字段 → 列索引映射。
   *
   * <p>映射规则与 {@code HeaderAnalyzer.analyzeClassMetadataFromNames} 一致：
   *
   * <ol>
   *   <li>@ExcelProperty.index 指定列最高优先级</li>
   *   <li>@ExcelProperty.value 与表头名称精确匹配</li>
   *   <li>字段名兜底</li>
   * </ol>
   */
  private void buildHeaderMapping(
      Class<?> clazz,
      List<String> templateHeaders,
      Map<Integer, Field> columnFieldMap,
      List<String> headers) {
    Field[] fields = ReflectCache.getCachedFields(clazz);
    headers.addAll(templateHeaders);

    Set<String> excludeFields = metadata.getExcludeColumnFiledNames();
    Set<String> includeFields = metadata.getIncludeColumnFiledNames();
    boolean autoTrim = excelConfig.getIsAutomaticTrim();

    for (Field field : fields) {
      if (field.isAnnotationPresent(ExcelIgnore.class)) {
        continue;
      }
      ExcelProperty prop = field.getAnnotation(ExcelProperty.class);
      if (prop == null || prop.ignore()) {
        continue;
      }
      String fieldName = !prop.value().isEmpty() ? prop.value() : field.getName();
      if (excludeFields != null && excludeFields.contains(fieldName)) {
        continue;
      }
      if (includeFields != null && !includeFields.isEmpty() && !includeFields.contains(fieldName)) {
        continue;
      }

      int targetCol;
      if (prop.index() >= 0) {
        targetCol = prop.index();
      } else {
        targetCol = findHeaderCol(templateHeaders, fieldName, autoTrim);
      }

      if (targetCol >= 0 && targetCol < templateHeaders.size()) {
        field.setAccessible(true);
        columnFieldMap.put(targetCol, field);
      }
    }
  }

  private int findHeaderCol(List<String> headers, String fieldName, boolean autoTrim) {
    for (int i = 0; i < headers.size(); i++) {
      String h = headers.get(i);
      if (h == null) {
        continue;
      }
      if (h.equals(fieldName)) {
        return i;
      }
      if (autoTrim && h.trim().equals(fieldName.trim())) {
        return i;
      }
    }
    return -1;
  }

  // ==================== 区域循环：数据注入 + 公式偏移 ====================

  /**
   * 向已有的行 XML 字符串中注入数据值。
   *
   * <p>遍历行内的 {@code <c>} 元素，找到与 columnFieldMap 匹配的列后替换其值。
   */
  private String injectDataToRow(
      String rowContent,
      Object item,
      Map<Integer, Field> columnFieldMap,
      int rowNum,
      List<String> headers) {
    // 简化处理：重新生成该行的 cell 内容（保持 styleId）
    StringBuilder newCells = new StringBuilder(rowContent.length());
    Pattern cellPattern = Pattern.compile("<c\\s+([^>]*)(/>|>(.*?)</c>)", Pattern.DOTALL);
    Matcher cellMatcher = cellPattern.matcher(rowContent);
    while (cellMatcher.find()) {
      String attrs = cellMatcher.group(1);
      boolean selfClosing = "/>".equals(cellMatcher.group(2));
      String inner = selfClosing ? "" : cellMatcher.group(3);

      // 列索引
      Pattern rP = Pattern.compile("r=\"([A-Z]+)(\\d+)\"");
      Matcher rM = rP.matcher(attrs);
      int colIdx = -1;
      int colRowNum = rowNum;
      if (rM.find()) {
        colIdx = columnToIndex(rM.group(1));
        colRowNum = Integer.parseInt(rM.group(2));
      }

      // 尝试从列映射获取新值
      Field field = columnFieldMap.get(colIdx);
      if (field != null) {
        String value = getFieldValueAsString(item, field);
        ExcelProperty prop = field.getAnnotation(ExcelProperty.class);
        String formula = prop != null ? prop.formula() : "";
        String cellRef = attrs.replaceAll("r=\"[A-Z]+\\d+\"", "r=\"" + toCellRef(colIdx, rowNum) + "\"");
        newCells.append("<c ").append(cellRef);
        if (!selfClosing && inner != null) {
          // 保留原始 type 属性
          if (inner.contains("<f>") && formula != null && !formula.isEmpty()) {
            String resolvedFormula = ROW_PLACEHOLDER.matcher(formula).replaceAll(Integer.toString(rowNum));
            newCells.append("><f>").append(escapeXml(resolvedFormula)).append("</f><v>")
                .append(escapeXml(value)).append("</v></c>");
          } else if (value != null) {
            newCells.append(">");
            appendTypedCellValue(newCells, value);
          } else {
            newCells.append("/>");
          }
        } else {
          if (value != null) {
            newCells.append(">");
            appendTypedCellValue(newCells, value);
          } else {
            newCells.append("/>");
          }
        }
      } else {
        // 非数据列：保留原始 cell XML（仅调整行号）
        String adjusted = adjustRowReferences(cellMatcher.group(0), rowNum - colRowNum);
        newCells.append(adjusted);
      }
    }
    return newCells.toString();
  }

  /**
   * 将行内容 XML 中所有行号为 oldRefRow 的引用替换为 newRefRow。
   *
   * <p>调整 cell ref（如 A5 → A8）和公式中的行引用。
   *
   * @param xml 行内容
   * @param rowOffset 行偏移（newRow - oldRow）
   */
  private String adjustRowReferences(String xml, int rowOffset) {
    if (rowOffset == 0) {
      return xml;
    }
    StringBuffer sb = new StringBuffer(xml.length());
    Matcher m = CELL_REF_PATTERN.matcher(xml);
    while (m.find()) {
      String colLetters = m.group(1);
      int rowNum = Integer.parseInt(m.group(2));
      int newRow = rowNum + rowOffset;
      if (newRow < 1) {
        newRow = 1;
      }
      m.appendReplacement(sb, colLetters + newRow);
    }
    m.appendTail(sb);
    return sb.toString();
  }

  // ==================== SST 合并 ====================

  private byte[] buildMergedSst() {
    StringBuilder sb = new StringBuilder(8192);
    int totalCount = templateSst.length + newSstStrings.size();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
    sb.append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"")
        .append(" count=\"").append(totalCount).append("\" uniqueCount=\"").append(totalCount).append("\">");
    for (String s : templateSst) {
      sb.append("<si><t>").append(escapeXml(s)).append("</t></si>");
    }
    for (String s : newSstStrings) {
      sb.append("<si><t>").append(escapeXml(s)).append("</t></si>");
    }
    sb.append("</sst>");
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * 确保 workbook rels 包含 SST 关系（如果新字符串追加了）。
   */
  private void ensureWorkbookRelsHasSst() {
    byte[] relsBytes = templateEntries.get("xl/_rels/workbook.xml.rels");
    if (relsBytes == null) {
      return;
    }
    String rels = new String(relsBytes, StandardCharsets.UTF_8);
    if (rels.contains("sharedStrings")) {
      return;
    }
    // 追加 SST relationship
    int insertPoint = rels.indexOf("</Relationships>");
    if (insertPoint == -1) {
      return;
    }
    int maxRid = 1;
    Pattern p = Pattern.compile("Id=\"r(\\d+)\"");
    Matcher m = p.matcher(rels);
    while (m.find()) {
      int rid = Integer.parseInt(m.group(1));
      if (rid > maxRid) {
        maxRid = rid;
      }
    }
    String newRel = "<Relationship Id=\"rId" + (maxRid + 1)
        + "\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\""
        + " Target=\"sharedStrings.xml\"/>";
    String updated = rels.substring(0, insertPoint) + newRel + rels.substring(insertPoint);
    templateEntries.put("xl/_rels/workbook.xml.rels", updated.getBytes(StandardCharsets.UTF_8));

    // 更新 Content_Types 添加 SST 类型
    byte[] ctBytes = templateEntries.get("[Content_Types].xml");
    if (ctBytes != null) {
      String ct = new String(ctBytes, StandardCharsets.UTF_8);
      if (!ct.contains("sharedStrings")) {
        String newType = "<Override PartName=\"/xl/sharedStrings.xml\""
            + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>";
        ct = ct.replace("</Types>", newType + "</Types>");
        templateEntries.put("[Content_Types].xml", ct.getBytes(StandardCharsets.UTF_8));
      }
    }
  }

  // ==================== 输出 ====================

  private void writeOutput(boolean templateOnly) {
    String filePath = metadata.getFilePath();
    File file = metadata.getFile();
    OutputStream os = metadata.getOutputStream();

    try {
      if (filePath != null) {
        try (FileOutputStream fos = new FileOutputStream(filePath);
            BufferedOutputStream bos = new BufferedOutputStream(fos, ZIP_BUFFER_SIZE);
            ZipOutputStream zipOut = new ZipOutputStream(bos)) {
          zipOut.setLevel(excelConfig.getCompressionLevel());
          writeZipEntries(zipOut);
        }
      } else if (file != null) {
        try (FileOutputStream fos = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(fos, ZIP_BUFFER_SIZE);
            ZipOutputStream zipOut = new ZipOutputStream(bos)) {
          zipOut.setLevel(excelConfig.getCompressionLevel());
          writeZipEntries(zipOut);
        }
      } else if (os != null) {
        try (BufferedOutputStream bos = new BufferedOutputStream(os, ZIP_BUFFER_SIZE);
            ZipOutputStream zipOut = new ZipOutputStream(bos)) {
          zipOut.setLevel(excelConfig.getCompressionLevel());
          writeZipEntries(zipOut);
        }
      } else {
        throw new IllegalArgumentException("No output target specified");
      }
    } catch (IOException e) {
      throw new RuntimeException("Output failed: " + e.getMessage(), e);
    }
  }

  private void writeZipEntries(ZipOutputStream zipOut) throws IOException {
    for (Map.Entry<String, byte[]> entry : templateEntries.entrySet()) {
      ZipEntry zipEntry = new ZipEntry(entry.getKey());
      zipOut.putNextEntry(zipEntry);
      zipOut.write(entry.getValue());
      zipOut.closeEntry();
    }
  }

  // ==================== 工具方法 ====================

  private String getFieldValueAsString(Object item, Field field) {
    try {
      Object value = MHFieldAccessor.getGetter(item.getClass(), field).get(item);
      if (value == null) {
        return null;
      }
      if (value instanceof java.util.Date) {
        String fmt = excelConfig.getDefaultDateFormat();
        if (field.getAnnotation(ExcelProperty.class) != null
            && !field.getAnnotation(ExcelProperty.class).dateFormat().isEmpty()) {
          fmt = field.getAnnotation(ExcelProperty.class).dateFormat();
        }
        return new java.text.SimpleDateFormat(fmt).format((java.util.Date) value);
      }
      if (value instanceof LocalDateTime) {
        return value.toString();
      }
      if (value instanceof LocalDate) {
        return value.toString();
      }
      if (value instanceof Number) {
        if (value instanceof Double || value instanceof Float || value instanceof java.math.BigDecimal) {
          double d = ((Number) value).doubleValue();
          if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return String.valueOf((long) d);
          }
        }
      }
      String str = value.toString();
      // 公式注入防护
      if (excelConfig.getIsFormulaInjectionProtection()) {
        str = FormulaInjectionGuard.sanitizeForXlsx(str);
      }
      return str;
    } catch (Exception e) {
      LOG.warn("取值失败: field={}", field.getName(), e);
      return null;
    }
  }

  private static boolean isNumeric(String s) {
    if (s == null || s.isEmpty()) {
      return false;
    }
    try {
      Double.parseDouble(s);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static int columnToIndex(String col) {
    int result = 0;
    for (int i = 0; i < col.length(); i++) {
      result = result * 26 + (col.charAt(i) - 'A' + 1);
    }
    return result - 1;
  }

  private static String toCellRef(int colIdx, int rowNum1Based) {
    StringBuilder col = new StringBuilder();
    int c = colIdx;
    while (c >= 0) {
      col.insert(0, (char) ('A' + c % 26));
      c = c / 26 - 1;
      if (c < 0) {
        break;
      }
    }
    return col.toString() + rowNum1Based;
  }

  private static byte[] readAll(InputStream is) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
    byte[] buf = new byte[4096];
    int n;
    while ((n = is.read(buf)) > 0) {
      bos.write(buf, 0, n);
    }
    return bos.toByteArray();
  }

  private static String escapeXml(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  private static String unescapeXml(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&apos;", "'")
        .replace("&quot;", "\"");
  }
}
