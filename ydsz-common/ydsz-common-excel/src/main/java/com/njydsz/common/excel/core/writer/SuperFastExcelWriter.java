package com.njydsz.common.excel.core.writer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.StandardCopyOption;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.annotation.ExcelSheet;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.metadata.WriteMetadata;
import com.njydsz.common.excel.core.metadata.WriteMetadata.WriteHeaderProperty;
import com.njydsz.common.excel.core.postprocess.XlsxPostProcessor;
import com.njydsz.common.excel.core.security.FormulaInjectionGuard;
import com.njydsz.common.excel.support.mh.MHFieldAccessor;

/**
 * 高性能 Excel 写入器 — 纯手工 XML 序列化（零 POI 依赖）。
 *
 * <p>直接生成 OOXML（.xlsx）格式的 XML 字节流并写入 ZIP 包，绕过 Apache POI 的对象模型，
 * 以极低的内存开销实现高性能写入。自 v26.10.01 起成为唯一写入引擎。
 *
 * <h3>写入原理</h3>
 *
 * <p>.xlsx 文件本质上是一个 ZIP 包，包含以下 XML 文件：
 *
 * <ul>
 *   <li>{@code [Content_Types].xml}：内容类型声明
 *   <li>{@code _rels/.rels}：根关系文件
 *   <li>{@code xl/workbook.xml}：工作簿定义
 *   <li>{@code xl/_rels/workbook.xml.rels}：工作簿关系
 *   <li>{@code xl/worksheets/sheet1.xml}：Sheet 数据
 *   <li>{@code xl/sharedStrings.xml}：共享字符串表
 * </ul>
 *
 * 其中 ContentTypes/Rels/Workbook 为固定模板，在静态块中一次性生成为字节常量复用；
 * 仅 Sheet 数据与 SST 需要按数据动态生成。
 *
 * <h3>输出结构（OOXML 规范顺序）</h3>
 *
 * <pre>{@code
 * <worksheet>
 *   <sheetViews>        ← 冻结窗格（可选）
 *   <cols>              ← 自定义列宽（可选）
 *   <sheetData>         ← 表头 + 数据行
 *   <mergeCells>        ← 合并单元格（可选）
 * </worksheet>
 * }</pre>
 *
 * <h3>性能优化</h3>
 *
 * <ul>
 *   <li>使用 MethodHandle 替代反射获取字段值
 *   <li>行级缓冲（1MB），减少 ZIP 写入次数
 *   <li>公式注入防护（{@link FormulaInjectionGuard}）
 *   <li>支持 {@code @ExcelProperty.order()} 列序排序
 *   <li>支持 {@code excludeColumnFiledNames} / {@code includeColumnFiledNames} 列过滤
 * </ul>
 *
 * <h3>功能特性</h3>
 *
 * <ul>
 *   <li>类型化写入：基于 {@code @ExcelProperty} 注解自动解析列元数据
 *   <li>动态表头：支持 {@code head(List<String>)} + {@code List<List<Object>>} 模式
 *   <li>冻结窗格：通过 metadata.freezePaneRow/freezePaneCol 配置
 *   <li>合并区域：通过 metadata.mergedRegions 配置
 *   <li>自动列宽：通过 metadata.isAutoColumnWidth 启用，写入过程中实时估算
 * </ul>
 *
 * <h3>注意事项</h3>
 *
 * <ul>
 *   <li><b>线程安全性</b>：实例持有行缓冲区、行游标、单元格引用暂存等可变状态，
 *       <b>既不能跨线程共享，也不能重复调用 {@code doWrite}</b>（行号会累加）。
 *       每次导出请新建实例。
 *   <li><b>回调限制</b>：本引擎不触发 {@code WriteHandler} / {@code WriteLifecycleHandler} 回调，
 *       也不应用 {@code @ExcelStyle} / {@code @ContentStyle} 样式注解。需要这些能力时由
 *       {@link com.njydsz.common.excel.core.ExcelFacade} 提供兼容性说明。
 *   <li><b>输出目标优先级</b>：{@code filePath} &gt; {@code file} &gt; {@code outputStream}，
 *       三者均未设置时抛 {@link IllegalArgumentException}。
 *   <li>写文件路径时先在系统临时目录落地 sheet1.xml 再转存进 ZIP，
 *       临时目录在 {@code finally} 中递归删除，清理失败仅记 warn 不影响结果。
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FormulaInjectionGuard
 * @see MHFieldAccessor
 */
public class SuperFastExcelWriter {

  private static final Logger LOG = LoggerFactory.getLogger(SuperFastExcelWriter.class);

  private static final int ROW_BUFFER_SIZE = 1024 * 1024;
  private static final int ZIP_BUFFER_SIZE = 1024 * 1024;
  private static final byte[] CONTENT_TYPES_BYTES;
  private static final byte[] RELS_BYTES;
  private static final byte[] WORKBOOK_RELS_BYTES;
  private static final byte[] WORKBOOK_BYTES_TEMPLATE;
  private static final byte[] SHEET_HEADER_BYTES;
  private static final byte[] FOOTER_BYTES;
  private static final byte[] SHEET_DATA_OPEN_BYTES;
  private static final byte[] SHEET_DATA_CLOSE_BYTES;

  static {
    CONTENT_TYPES_BYTES =
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\""
                + " ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\""
                + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\""
                + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/sharedStrings.xml\""
                + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>"
                + "</Types>")
            .getBytes(StandardCharsets.UTF_8);

    RELS_BYTES =
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>")
            .getBytes(StandardCharsets.UTF_8);

    WORKBOOK_RELS_BYTES =
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>"
                + "</Relationships>")
            .getBytes(StandardCharsets.UTF_8);

    WORKBOOK_BYTES_TEMPLATE =
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets>"
                + "<sheet name=\"%s\" sheetId=\"1\" r:id=\"rId1\"/>"
                + "</sheets>"
                + "</workbook>")
            .getBytes(StandardCharsets.UTF_8);

    // OOXML 规范：sheetViews / cols 在 sheetData 之前；mergeCells 在 sheetData 之后
    SHEET_HEADER_BYTES =
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            .getBytes(StandardCharsets.UTF_8);

    FOOTER_BYTES = "</worksheet>".getBytes(StandardCharsets.UTF_8);

    SHEET_DATA_OPEN_BYTES = "<sheetData>".getBytes(StandardCharsets.UTF_8);
    SHEET_DATA_CLOSE_BYTES = "</sheetData>".getBytes(StandardCharsets.UTF_8);
  }

  private final WriteMetadata metadata;
  private FieldAccessorInfo[] fieldInfoArray;
  private int fieldInfoSize;
  private Map<Integer, FieldAccessorInfo> fieldInfoMap;
  private byte[] columnTypeIds;

  /** 列宽追踪：-1 表示未设置；>= 0 表示已估算的最大列宽（字符数） */
  private int[] columnWidthTracker;
  /** 是否启用自动列宽追踪 */
  private boolean trackColumnWidths;

  private int currentRow = 0;

  private byte[] rowBuffer;
  private int rowBufferPos;

  private final byte[] cellRefBuffer = new byte[16];
  private final byte[] numberBuffer = new byte[32];
  private final char[] digitChars = "0123456789".toCharArray();

  private static final DateTimeFormatter DEFAULT_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /**
   * 字符串长度超过此阈值时直接以 inlineStr 方式写入，不进入 SST 共享字符串表。
   *
   * <p>阈值过低 → 高基数字符串场景 SST 膨胀，序列化性能下降；阈值过高 → inline 单元格占用更多体积。
   * 默认 50 字符，与主流工具（EasyExcel）一致；需要调整时通过系统属性 {@code ydsz.excel.sstInlineThreshold} 覆盖。
   */
  private static final int DEFAULT_SST_INLINE_THRESHOLD = 50;

  /** SST 字符串内联阈值（字符数），超过此长度的字符串直接 inlineStr */
  private final int sstInlineThreshold;

  public SuperFastExcelWriter(WriteMetadata metadata) {
    this.metadata = metadata;
    this.sstInlineThreshold = resolveSstInlineThreshold();
  }

  /**
   * 解析 SST 内联阈值：优先从 ExcelConfig 获取（用户配置），回退到系统属性 {@code ydsz.excel.sstInlineThreshold}，
   * 最终回退到默认值 50。
   *
   * @return 有效的内联阈值（≥ 1）
   */
  private static int resolveSstInlineThreshold() {
    ExcelConfig cfg = ExcelConfig.defaults();
    // 尝试从已配置的 WriteMetadata 获取 ExcelConfig（通过全局默认无法访问，此处为兼容性回退）
    String prop = System.getProperty("ydsz.excel.sstInlineThreshold");
    if (prop != null) {
      try {
        int v = Integer.parseInt(prop);
        if (v >= 1) {
          return v;
        }
      } catch (NumberFormatException ignored) {
        // fall through to default
      }
    }
    return cfg.getSstInlineThreshold();
  }

  private ExcelConfig getExcelConfig() {
    return metadata.getExcelConfig() != null ? metadata.getExcelConfig() : ExcelConfig.defaults();
  }

  /**
   * 将数据序列化为 xlsx 并输出到 {@link WriteMetadata} 指定的目标。
   *
   * <p><b>执行顺序</b>：空数据短路返回 → 按 {@code clazz} 解析列元数据（列过滤、排序、
   * MethodHandle 访问器绑定）→ 按目标类型分派到文件或流写入。
   *
   * <p><b>目标选择</b>：依次判断 {@code filePath}、{@code file}、{@code outputStream}，
   * 取第一个非空者；写流时不关闭调用方传入的流，由调用方负责关闭。
   *
   * <p><b>失败语义</b>：写文件过程中抛异常会残留不完整的目标文件，需调用方清理；
   * 临时目录的清理已在 {@code finally} 中兜底。
   *
   * @param data 待写入数据；非 {@link List} 时按单条记录处理，空列表则直接返回、不生成文件
   * @throws IllegalArgumentException 当未配置任何输出目标时抛出
   * @throws Exception 序列化或 IO 过程中的异常原样向上抛出，不做包装
   */
  public void doWrite(Object data) throws Exception {
    List<?> list = data instanceof List ? (List<?>) data : Collections.singletonList(data);
    if (list.isEmpty()) {
      return;
    }

    String filePath = metadata.getFilePath();
    File file = metadata.getFile();
    OutputStream os = metadata.getOutputStream();

    Class<?> clazz = metadata.getClazz();
    if (clazz != null) {
      analyzeClass(clazz);
    } else if (!metadata.getHeadList().isEmpty()) {
      // 动态表头模式：head(List<String>) + List<List<Object>> 数据
      analyzeHeadList();
    }

    // 初始化列宽追踪
    this.trackColumnWidths = Boolean.TRUE.equals(metadata.getIsAutoColumnWidth());
    if (trackColumnWidths && fieldInfoSize > 0) {
      this.columnWidthTracker = new int[fieldInfoSize];
      Arrays.fill(this.columnWidthTracker, -1);
    }

    String targetPath = filePath;
    if (targetPath == null && file != null) {
      targetPath = file.getAbsolutePath();
    }

    if (targetPath != null) {
      if (metadata.hasPostProcessors()) {
        applyPostProcessorsToFile(targetPath, list);
      } else {
        writeXlsxDirect(targetPath, list);
      }
    } else if (os != null) {
      if (metadata.hasPostProcessors()) {
        applyPostProcessorsToStream(os, list);
      } else {
        writeXlsxToStream(os, list);
      }
    } else {
      throw new IllegalArgumentException("No output target specified");
    }
  }

  /**
   * 写入基础 xlsx 后，通过管道依次应用 {@link XlsxPostProcessor}，
   * 最终结果写入目标文件路径。
   *
   * <p>管道模式：base → temp1 → temp2 → ... → targetPath。
   * 中间产物使用 JVM 临时目录，最终通过 {@link Files#move} 原子替换目标文件。
   *
   * @param targetPath 最终目标文件路径
   * @param list 数据列表
   */
  private void applyPostProcessorsToFile(String targetPath, List<?> list) throws Exception {
    Path tempBase = Files.createTempFile("ydsz_base_", ".xlsx");
    try {
      // 1. 写入基础 xlsx 到临时文件
      writeXlsxDirect(tempBase.toString(), list);

      // 2. 管道式应用后处理器链
      Path current = tempBase;
      List<XlsxPostProcessor> processors = metadata.getPostProcessors();
      Path[] intermediates = new Path[processors.size()];
      for (int i = 0; i < processors.size(); i++) {
        Path next = Files.createTempFile("ydsz_pp" + i + "_", ".xlsx");
        intermediates[i] = next;
        try (InputStream is = new BufferedInputStream(new FileInputStream(current.toFile()), ZIP_BUFFER_SIZE);
             OutputStream os = new BufferedOutputStream(new FileOutputStream(next.toFile()), ZIP_BUFFER_SIZE)) {
          processors.get(i).process(is, os);
        }
        // 清理上一个中间文件（避免残留）
        if (i > 0) {
          Files.deleteIfExists(current);
        }
        current = next;
      }

      // 3. 原子替换目标文件
      Files.move(current, Path.of(targetPath), StandardCopyOption.REPLACE_EXISTING);
    } finally {
      // best-effort 清理暂存文件（Files.move 成功则 current 已被移除）
      try { Files.deleteIfExists(tempBase); } catch (IOException ignored) {}
    }
  }

  /**
   * 写入基础 xlsx 后，通过管道依次应用 {@link XlsxPostProcessor}，
   * 最终结果写入输出流。
   *
   * <p>管道模式：base → temp1 → ... → ByteArrayOutputStream → os。
   *
   * @param os 目标输出流（由调用方管理关闭）
   * @param list 数据列表
   */
  private void applyPostProcessorsToStream(OutputStream os, List<?> list) throws Exception {
    Path tempBase = Files.createTempFile("ydsz_base_", ".xlsx");
    try {
      // 1. 写入基础 xlsx 到临时文件
      writeXlsxDirect(tempBase.toString(), list);

      // 2. 管道式应用后处理器链（与 applyPostProcessorsToFile 逻辑一致，最终不 move 而是 copy 到 os）
      Path current = tempBase;
      List<XlsxPostProcessor> processors = metadata.getPostProcessors();
      for (int i = 0; i < processors.size(); i++) {
        Path next = Files.createTempFile("ydsz_pp" + i + "_", ".xlsx");
        try (InputStream is = new BufferedInputStream(new FileInputStream(current.toFile()), ZIP_BUFFER_SIZE);
             OutputStream outs = new BufferedOutputStream(new FileOutputStream(next.toFile()), ZIP_BUFFER_SIZE)) {
          processors.get(i).process(is, outs);
        }
        if (i > 0) {
          Files.deleteIfExists(current);
        }
        current = next;
      }

      // 3. 将最终结果复制到输出流
      try (InputStream is = new BufferedInputStream(new FileInputStream(current.toFile()), ZIP_BUFFER_SIZE)) {
        is.transferTo(os);
      }
      Files.deleteIfExists(current);
    } finally {
      try { Files.deleteIfExists(tempBase); } catch (IOException ignored) {}
    }
  }

  // ==================== 写入核心（文件目标） ====================

  private void writeXlsxDirect(String filePath, List<?> list) throws Exception {
    // P0-1 优化 v2：双路径写入
    // — 默认路径（无自动列宽）：行数据直接写入 ZIP 条目的压缩流
    //   → 内存峰值 O(rowBuffer) = 1MB（不再持有整份 sheet 的 byte[]）
    //   → 消除了 ByteArrayOutputStream 5MB 堆 + GC 压力
    // — 自动列宽路径：必须先收集列宽后才能写 <cols> 段，仍需 ByteArrayOutputStream
    boolean autoColWidth = Boolean.TRUE.equals(metadata.getIsAutoColumnWidth());

    if (autoColWidth) {
      writeXlsxDirectWithColumnWidth(filePath, list);
    } else {
      writeXlsxDirectDirect(filePath, list);
    }
  }

  /**
   * 直接写入 ZIP，不经 ByteArrayOutputStream 中间堆缓存。
   * 行数据通过 rowBuffer 逐批进入 ZipOutputStream 的 Deflater，
   * 内存峰值从「全量 sheet byte[]」降至「ZIP 内部缓冲 + 1MB rowBuffer」。
   */
  private void writeXlsxDirectDirect(String filePath, List<?> list) throws Exception {
    try (FileOutputStream fos = new FileOutputStream(filePath);
        BufferedOutputStream bos = new BufferedOutputStream(fos, ZIP_BUFFER_SIZE);
        ZipOutputStream zipOut = new ZipOutputStream(bos)) {

      zipOut.setLevel(getExcelConfig().getCompressionLevel());
      writeStaticZipEntries(zipOut);

      zipOut.putNextEntry(new ZipEntry("xl/workbook.xml"));
      zipOut.write(getWorkbookBytes());
      zipOut.closeEntry();

      UltraFastSharedStrings ss = new UltraFastSharedStrings();

      // 直接打开 sheet ZIP 条目，行数据写入即压缩
      zipOut.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
      writeSheetContent(zipOut, list, ss);
      zipOut.closeEntry();

      byte[] ssBytes = ss.buildXmlDirect();
      zipOut.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
      zipOut.write(ssBytes);
      zipOut.closeEntry();

      zipOut.finish();
    }
  }

  /**
   * 带自动列宽的全量缓存路径（列宽需在 <sheetData> 前写入 <cols>）。
   * 此路径下 ByteArrayOutputStream 不可避免，但只在用户启用 autoColumnWidth 时触发。
   */
  private void writeXlsxDirectWithColumnWidth(String filePath, List<?> list) throws Exception {
    try (FileOutputStream fos = new FileOutputStream(filePath);
        BufferedOutputStream bos = new BufferedOutputStream(fos, ZIP_BUFFER_SIZE);
        ZipOutputStream zipOut = new ZipOutputStream(bos)) {

      zipOut.setLevel(getExcelConfig().getCompressionLevel());
      writeStaticZipEntries(zipOut);

      ZipEntry entry = new ZipEntry("xl/workbook.xml");
      zipOut.putNextEntry(entry);
      zipOut.write(getWorkbookBytes());
      zipOut.closeEntry();

      int listSize = list.size();
      int estimatedSheetBytes = 1024 + listSize * 300;

      UltraFastSharedStrings ss = new UltraFastSharedStrings();

      ByteArrayOutputStream sheetBaos = new ByteArrayOutputStream(
          Math.min(estimatedSheetBytes, 8 * 1024 * 1024));
      writeSheetContent(sheetBaos, list, ss);

      ZipEntry sheetEntry = new ZipEntry("xl/worksheets/sheet1.xml");
      zipOut.putNextEntry(sheetEntry);
      sheetBaos.writeTo(zipOut);
      zipOut.closeEntry();

      byte[] ssBytes = ss.buildXmlDirect();
      ZipEntry ssEntry = new ZipEntry("xl/sharedStrings.xml");
      zipOut.putNextEntry(ssEntry);
      zipOut.write(ssBytes);
      zipOut.closeEntry();

      zipOut.finish();
    }
  }

  // ==================== 写入核心（流目标） ====================

  private void writeXlsxToStream(OutputStream os, List<?> list) throws Exception {
    try (ZipOutputStream zipOut = new ZipOutputStream(os)) {
      zipOut.setLevel(getExcelConfig().getCompressionLevel());

      writeStaticZipEntries(zipOut);

      zipOut.putNextEntry(new ZipEntry("xl/workbook.xml"));
      zipOut.write(getWorkbookBytes());
      zipOut.closeEntry();

      UltraFastSharedStrings ss = new UltraFastSharedStrings();

      zipOut.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
      writeSheetContent(zipOut, list, ss);
      zipOut.closeEntry();

      byte[] ssBytes = ss.buildXmlDirect();
      ZipEntry ssEntry = new ZipEntry("xl/sharedStrings.xml");
      zipOut.putNextEntry(ssEntry);
      zipOut.write(ssBytes);
      zipOut.closeEntry();

      zipOut.finish();
    }
  }

  /**
   * 写入 ZIP 包中的固定模板条目（[Content_Types].xml、.rels、workbook.xml.rels）。
   *
   * <p>这些文件内容与数据无关，使用预生成的字节常量直接写出。
   *
   * @param zipOut 已打开的 ZIP 输出流
   * @throws Exception ZIP 写入异常
   */
  private void writeStaticZipEntries(ZipOutputStream zipOut) throws Exception {
    ZipEntry entry = new ZipEntry("[Content_Types].xml");
    zipOut.putNextEntry(entry);
    zipOut.write(CONTENT_TYPES_BYTES);
    zipOut.closeEntry();

    entry = new ZipEntry("_rels/.rels");
    zipOut.putNextEntry(entry);
    zipOut.write(RELS_BYTES);
    zipOut.closeEntry();

    entry = new ZipEntry("xl/_rels/workbook.xml.rels");
    zipOut.putNextEntry(entry);
    zipOut.write(WORKBOOK_RELS_BYTES);
    zipOut.closeEntry();
  }

  // ==================== Sheet 内容写入 ====================

  /**
   * 向目标输出流写入完整的 worksheet XML 内容。
   *
   * <p>按照 OOXML 规范顺序输出：worksheet 头部 → sheetViews(冻结) → cols(列宽) →
   * sheetData(表头+数据) → mergeCells(合并区域) → worksheet 尾部。
   *
   * <p><b>自动列宽实现策略</b>：因为列宽需在 {@code <sheetData>} 之前输出，而宽度值需在写入过程中逐步估算，
   * 故先用 ByteArrayOutputStream 收集 sheet body（不含 {@code <cols>} 段），数据全部写入后
   * 再根据追踪结果生成 {@code <cols>} 段并前置输出。
   *
   * @param out 目标输出流（BufferedOutputStream 或 ZipOutputStream）
   * @param list 要写入的数据列表
   * @param ss 共享字符串表实例
   * @throws Exception 写入过程中的 IO 或序列化异常
   */
  private void writeSheetContent(OutputStream out, List<?> list, UltraFastSharedStrings ss)
      throws Exception {
    out.write(SHEET_HEADER_BYTES);

    // 1. 冻结窗格（sheetViews）
    byte[] sheetViewsXml = buildSheetViewsXml();
    if (sheetViewsXml != null) {
      out.write(sheetViewsXml);
    }

    // 2. 列宽（cols）— 自定义列宽先写，自动列宽延迟到数据写完
    if (!trackColumnWidths && hasCustomColumnWidths()) {
      out.write(buildColsXml());
    }

    // 3. 数据区 — 自动列宽模式下先收集到内存缓冲
    ByteArrayOutputStream bodyBuffer = null;
    OutputStream dataOut = out;
    if (trackColumnWidths) {
      bodyBuffer = new ByteArrayOutputStream(fieldInfoSize * 64 + list.size() * 32);
      dataOut = bodyBuffer;
    }

    dataOut.write(SHEET_DATA_OPEN_BYTES);

    rowBuffer = new byte[ROW_BUFFER_SIZE];
    rowBufferPos = 0;

    Class<?> clazz = metadata.getClazz();

    // 写入表头行
    if (fieldInfoSize > 0) {
      currentRow++;
      int headerLen = writeHeaderRow(ss);
      dataOut.write(rowBuffer, 0, headerLen);
      rowBufferPos = 0;
    }

    // 写入数据行
    int listSize = list.size();
    for (int rowIdx = 0; rowIdx < listSize; rowIdx++) {
      Object item = list.get(rowIdx);
      currentRow++;
      int rowLen;
      if (clazz != null) {
        rowLen = writeRowToBuffer(item, ss);
      } else {
        rowLen = writeDynamicRowToBuffer(item, ss);
      }
      dataOut.write(rowBuffer, 0, rowLen);
      rowBufferPos = 0;
    }

    dataOut.write(SHEET_DATA_CLOSE_BYTES);

    // 4. 合并区域（mergeCells）
    byte[] mergeCellsXml = buildMergeCellsXml();
    if (mergeCellsXml != null) {
      dataOut.write(mergeCellsXml);
    }

    dataOut.write(FOOTER_BYTES);

    // 5. 自动列宽模式：根据追踪结果生成 <cols> 段前置输出
    if (trackColumnWidths && bodyBuffer != null) {
      byte[] autoColsXml = buildAutoColsXml();
      if (autoColsXml != null && autoColsXml.length > 0) {
        out.write(autoColsXml);
      }
      out.write(bodyBuffer.toByteArray());
    }
  }

  // ==================== 冻结窗格 XML ====================

  /**
   * 构建 OOXML 规范的 {@code <sheetViews>} 段用于冻结行/列。
   *
   * <p>输出示例（冻结首行）：
   * <pre>{@code
   * <sheetViews><sheetView workbookViewId="0">
   *   <pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>
   * </sheetView></sheetViews>
   * }</pre>
   *
   * @return XML 字节数组；未配置冻结窗格时返回 {@code null}
   */
  private byte[] buildSheetViewsXml() {
    Integer freezeRow = metadata.getFreezePaneRow();
    Integer freezeCol = metadata.getFreezePaneCol();
    if ((freezeRow == null || freezeRow <= 0) && (freezeCol == null || freezeCol <= 0)) {
      return null;
    }

    int rowSplit = (freezeRow != null && freezeRow > 0) ? freezeRow : 0;
    int colSplit = (freezeCol != null && freezeCol > 0) ? freezeCol : 0;

    // topLeftCell: 冻结后可见区域的左上角
    String topLeftCell = computeTopLeftCell(rowSplit, colSplit);

    StringBuilder sb = new StringBuilder(128);
    sb.append("<sheetViews><sheetView workbookViewId=\"0\">");
    sb.append("<pane");
    if (rowSplit > 0) {
      sb.append(" ySplit=\"").append(rowSplit).append('"');
    }
    if (colSplit > 0) {
      sb.append(" xSplit=\"").append(colSplit).append('"');
    }
    sb.append(" topLeftCell=\"").append(topLeftCell).append('"');
    sb.append(" activePane=\"").append(computeActivePane(rowSplit, colSplit)).append('"');
    sb.append(" state=\"frozen\"/></sheetView></sheetViews>");

    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * 计算冻结窗格后可见区域左上角单元格引用。
   *
   * <p>列名转换算法：标准 26 进制（0=A, 25=Z, 26=AA, 701=ZZ, 702=AAA ...）。
   * 使用 {@code c / 26 - 1} 进位策略（而非简单的 c--），正确处理 "Z → AA"、"AZ → BA" 等进位边界。
   *
   * @param rowSplit 冻结行数
   * @param colSplit 冻结列数
   * @return 单元格引用字符串（如 "A1"、"B2"、"AA2"、"AE6"）
   */
  private static String computeTopLeftCell(int rowSplit, int colSplit) {
    // 行号 1-based
    int visibleRow = rowSplit + 1;
    // 列号转为字母
    int visibleCol = colSplit; // 0-based
    StringBuilder sb = new StringBuilder(8);
    if (visibleCol == 0) {
      sb.append('A');
    } else {
      // 简单的列号转字母（最多支持到 Z，超过时进位）
      int temp = visibleCol;
      while (temp >= 0) {
        sb.append((char) ('A' + (temp % 26)));
        temp = temp / 26 - 1;
        if (temp < 0) break;
      }
      sb.reverse();
    }
    sb.append(visibleRow);
    return sb.toString();
  }

  /**
   * 根据冻结方向确定 activePane 值。
   *
   * @param rowSplit 冻结行数
   * @param colSplit 冻结列数
   * @return OOXML activePane 枚举值
   */
  private static String computeActivePane(int rowSplit, int colSplit) {
    if (rowSplit > 0 && colSplit > 0) {
      return "bottomRight";
    } else if (rowSplit > 0) {
      return "bottomLeft";
    } else {
      return "topRight";
    }
  }

  // ==================== 合并区域 XML ====================

  /**
   * 构建 OOXML 规范的 {@code <mergeCells>} 段。
   *
   * <p>输出示例：
   * <pre>{@code
   * <mergeCells count="1"><mergeCell ref="A1:B1"/></mergeCells>
   * }</pre>
   *
   * @return XML 字节数组；无合并区域时返回 {@code null}
   */
  private byte[] buildMergeCellsXml() {
    List<int[]> regions = metadata.getMergedRegions();
    if (regions == null || regions.isEmpty()) {
      return null;
    }

    StringBuilder sb = new StringBuilder(regions.size() * 48 + 32);
    sb.append("<mergeCells count=\"").append(regions.size()).append("\">");
    for (int[] region : regions) {
      if (region == null || region.length < 4) {
        continue;
      }
      String startRef = toCellRef(region[0], region[2]);
      String endRef = toCellRef(region[1], region[3]);
      sb.append("<mergeCell ref=\"").append(startRef).append(':').append(endRef).append("\"/>");
    }
    sb.append("</mergeCells>");
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  // ==================== 列宽 XML ====================

  /**
   * 构建 OOXML 规范的 {@code <cols>} 自定义列宽段（基于 {@code @ExcelProperty.width()}）。
   *
   * <p>仅包含 {@code width > 0} 的列，输出格式：{@code <cols><col min="1" max="1" width="15" customWidth="1"/></cols>}
   *
   * @return XML 字节数组；无自定义列宽时返回 {@code null}
   */
  private byte[] buildColsXml() {
    if (fieldInfoSize == 0 || fieldInfoArray == null) {
      return null;
    }

    StringBuilder sb = new StringBuilder(fieldInfoSize * 48);
    sb.append("<cols>");
    for (int col = 0; col < fieldInfoSize; col++) {
      FieldAccessorInfo info = fieldInfoArray[col];
      if (info == null || info.width == null) {
        continue;
      }
      int colOneBased = col + 1;
      sb.append("<col min=\"").append(colOneBased)
          .append("\" max=\"").append(colOneBased)
          .append("\" width=\"").append(info.width)
          .append("\" customWidth=\"1\"/>");
    }
    sb.append("</cols>");
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * 基于自动列宽追踪结果构建 {@code <cols>} 段。
   *
   * <p>估算规则：ASCII 字符计 1 单位，CJK 字符计 2 单位（近似中文字符宽度），
   * 最终列宽 = min(max(maxContentWidth + 2, 8), 255)（OOXML 列宽限制）。
   *
   * <p>若某列已通过 {@code @ExcelProperty(width=)} 指定自定义宽度，则取自定义宽度与自动估算的较大值。
   *
   * @return XML 字节数组；未启用自动列宽或无数据时返回空数组
   */
  private byte[] buildAutoColsXml() {
    if (!trackColumnWidths || columnWidthTracker == null || fieldInfoSize == 0) {
      return new byte[0];
    }

    StringBuilder sb = new StringBuilder(fieldInfoSize * 56);
    sb.append("<cols>");
    for (int col = 0; col < fieldInfoSize; col++) {
      int rawWidth = columnWidthTracker[col];
      if (rawWidth < 0) {
        // 该列无内容，回退到自定义宽度或默认值
        rawWidth = 8;
      }
      // 加 padding 2，最小 8，最大 255
      int autoWidth = Math.min(Math.max(rawWidth + 2, 8), 255);

      // 若存在自定义宽度，取较大值
      int width = autoWidth;
      if (fieldInfoArray != null && col < fieldInfoArray.length
          && fieldInfoArray[col] != null && fieldInfoArray[col].width != null) {
        int customWidth = fieldInfoArray[col].width.intValue();
        if (customWidth > 0) {
          width = Math.max(autoWidth, customWidth);
        }
      }

      int colOneBased = col + 1;
      sb.append("<col min=\"").append(colOneBased)
          .append("\" max=\"").append(colOneBased)
          .append("\" width=\"").append(width)
          .append("\" customWidth=\"1\"/>");
    }
    sb.append("</cols>");
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private boolean hasCustomColumnWidths() {
    if (fieldInfoArray == null) {
      return false;
    }
    for (int i = 0; i < fieldInfoSize; i++) {
      if (fieldInfoArray[i] != null && fieldInfoArray[i].width != null) {
        return true;
      }
    }
    return false;
  }

  // ==================== 类型化行写入 ====================

  private int writeRowToBuffer(Object item, UltraFastSharedStrings ss) throws Exception {
    ensureCapacity(32);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = 'o';
    rowBuffer[rowBufferPos++] = 'w';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    writeNumberToBuffer(currentRow);
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = '>';

    for (int col = 0; col < fieldInfoSize; col++) {
      FieldAccessorInfo info = fieldInfoArray[col];
      if (info == null) {
        continue;
      }

      Object value;
      if (info.getter != null) {
        value = info.getter.get(item);
      } else {
        info.field.setAccessible(true);
        value = info.field.get(item);
      }

      // 公式单元格处理
      if (info.formula != null && !info.formula.isEmpty()) {
        writeFormulaCell(col, info.formula, value);
      } else {
        writeCellTyped(col, value, info.dateFormatObj, columnTypeIds[col], ss);
      }
    }

    ensureCapacity(16);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = '/';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = 'o';
    rowBuffer[rowBufferPos++] = 'w';
    rowBuffer[rowBufferPos++] = '>';

    return rowBufferPos;
  }

  // ==================== 动态行写入（head + List<List<Object>>） ====================

  /**
   * 写入动态表头模式的数据行（无类型映射）。
   *
   * <p>数据可以是 {@code List<Object>}（一行数据）、{@code Map<String, Object>}
   * （按 headList 名称匹配列），或其他类型（toString 值输出）。
   *
   * @param item 单行数据对象
   * @param ss 共享字符串表
   * @return 写入缓冲区的字节数
   */
  private int writeDynamicRowToBuffer(Object item, UltraFastSharedStrings ss) {
    ensureCapacity(32);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = 'o';
    rowBuffer[rowBufferPos++] = 'w';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    writeNumberToBuffer(currentRow);
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = '>';

    List<WriteHeaderProperty> headList = metadata.getHeadList();
    int colCount = headList.size();

    if (item instanceof List) {
      List<?> rowData = (List<?>) item;
      for (int col = 0; col < colCount && col < rowData.size(); col++) {
        Object value = rowData.get(col);
        writeDynamicCell(col, value, ss);
      }
    } else if (item instanceof Map) {
      Map<?, ?> rowData = (Map<?, ?>) item;
      for (int col = 0; col < colCount; col++) {
        String colName = headList.get(col).getName();
        Object value = rowData.get(colName);
        writeDynamicCell(col, value, ss);
      }
    } else {
      // 单值：写入第 0 列
      writeDynamicCell(0, item, ss);
    }

    ensureCapacity(16);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = '/';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = 'o';
    rowBuffer[rowBufferPos++] = 'w';
    rowBuffer[rowBufferPos++] = '>';

    return rowBufferPos;
  }

  /**
   * 写入动态模式下的单元格值（自动类型推断 + 公式注入防护）。
   *
   * @param col 列索引（0-based）
   * @param value 单元格值
   * @param ss 共享字符串表
   */
  private void writeDynamicCell(int col, Object value, UltraFastSharedStrings ss) {
    if (value == null) {
      writeNullCell(col);
      return;
    }

    if (value instanceof Number) {
      if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
        double d = ((Number) value).doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
          writeStringCellInline(col, Double.toString(d));
        } else {
          writeDoubleCell(col, d);
        }
      } else {
        writeNumberCell(col, ((Number) value).longValue());
      }
    } else if (value instanceof Boolean) {
      writeBooleanCell(col, (Boolean) value);
    } else if (value instanceof Date) {
      String dateStr = ((Date) value).toInstant()
          .atZone(ZoneId.systemDefault())
          .format(DEFAULT_DATE_FORMATTER);
      writeStringCellInline(col, dateStr);
      trackColumnWidth(col, dateStr);
    } else if (value instanceof LocalDateTime) {
      String dateStr = ((LocalDateTime) value).format(DEFAULT_DATE_FORMATTER);
      writeStringCellInline(col, dateStr);
      trackColumnWidth(col, dateStr);
    } else if (value instanceof LocalDate) {
      String dateStr = ((LocalDate) value).format(DEFAULT_DATE_FORMATTER);
      writeStringCellInline(col, dateStr);
      trackColumnWidth(col, dateStr);
    } else {
      String str = value.toString();
      writeStringCell(col, str, ss);
      trackColumnWidth(col, str);
    }
  }

  /**
   * 追踪列宽：更新指定列的最大显示宽度。
   *
   * @param col 列索引
   * @param text 单元格文本内容
   */
  private void trackColumnWidth(int col, String text) {
    if (!trackColumnWidths || columnWidthTracker == null || col >= columnWidthTracker.length) {
      return;
    }
    if (text == null) {
      return;
    }
    int displayWidth = estimateDisplayWidth(text);
    if (columnWidthTracker[col] < displayWidth) {
      columnWidthTracker[col] = displayWidth;
    }
  }

  /**
   * 估算字符串在 Excel 中的显示宽度（近似值）。
   *
   * <p>规则：ASCII 字符 = 1，CJK 统一表意文字 = 2（中文字符在 Calibri 11pt 下约 2 倍宽度）。
   *
   * @param text 待估算文本
   * @return 估算的显示宽度（字符单位）
   */
  static int estimateDisplayWidth(String text) {
    if (text == null || text.isEmpty()) {
      return 0;
    }
    int width = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c >= 0x4E00 && c <= 0x9FFF) {
        // CJK 统一表意文字区
        width += 2;
      } else if (0xFF00 <= c && c <= 0xFFEF) {
        // 全角字符区
        width += 2;
      } else {
        width += 1;
      }
    }
    return width;
  }

  // ==================== 单元格写入（类型化路径） ====================

  private void writeCellTyped(
      int col, Object value, DateTimeFormatter dateFormat, byte typeId, UltraFastSharedStrings ss) {
    if (value == null) {
      writeNullCell(col);
      return;
    }

    switch (typeId) {
      case 1: // String
        writeStringCell(col, (String) value, ss);
        trackColumnWidth(col, (String) value);
        break;
      case 2: // Number
        if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
          writeDoubleCell(col, ((Number) value).doubleValue());
          trackColumnWidth(col, Double.toString(((Number) value).doubleValue()));
        } else {
          writeNumberCell(col, ((Number) value).longValue());
          trackColumnWidth(col, Long.toString(((Number) value).longValue()));
        }
        break;
      case 3: // Date
        String dateStr = formatDateValue(value, dateFormat);
        writeStringCellInline(col, dateStr);
        trackColumnWidth(col, dateStr);
        break;
      case 4: // Boolean
        writeBooleanCell(col, (Boolean) value);
        break;
      default:
        writeGenericCell(col, value);
        trackColumnWidth(col, value.toString());
        break;
    }
  }

  private String formatDateValue(Object value, DateTimeFormatter dateFormat) {
    if (value instanceof LocalDateTime ldt) {
      return ldt.format(dateFormat);
    } else if (value instanceof LocalDate ld) {
      return ld.format(dateFormat);
    } else {
      return ((Date) value).toInstant().atZone(ZoneId.systemDefault()).format(dateFormat);
    }
  }

  private void writeNullCell(int col) {
    ensureCapacity(16);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'c';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    writeCellRef(col);
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = '/';
    rowBuffer[rowBufferPos++] = '>';
  }

  private void writeStringCellInline(int col, String value) {
    if (getExcelConfig().getIsFormulaInjectionProtection()) {
      value = FormulaInjectionGuard.sanitizeForXlsx(value);
    }
    int strLen = value.length();
    int capacity = 64 + strLen * 2;
    ensureCapacity(capacity);

    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'c';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    writeCellRef(col);
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 't';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = 'i';
    rowBuffer[rowBufferPos++] = 'n';
    rowBuffer[rowBufferPos++] = 'l';
    rowBuffer[rowBufferPos++] = 'i';
    rowBuffer[rowBufferPos++] = 'n';
    rowBuffer[rowBufferPos++] = 'e';
    rowBuffer[rowBufferPos++] = 'S';
    rowBuffer[rowBufferPos++] = 't';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = '>';
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'i';
    rowBuffer[rowBufferPos++] = 's';
    rowBuffer[rowBufferPos++] = '>';
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 't';
    writeStringToBuffer(value, true);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = '/';
    rowBuffer[rowBufferPos++] = 't';
    rowBuffer[rowBufferPos++] = '>';
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = '/';
    rowBuffer[rowBufferPos++] = 'i';
    rowBuffer[rowBufferPos++] = 's';
    rowBuffer[rowBufferPos++] = '>';
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = '/';
    rowBuffer[rowBufferPos++] = 'c';
    rowBuffer[rowBufferPos++] = '>';
  }

  private void writeStringCell(int col, String value, UltraFastSharedStrings ss) {
    if (getExcelConfig().getIsFormulaInjectionProtection()) {
      value = FormulaInjectionGuard.sanitizeForXlsx(value);
    }
    int strLen = value.length();
    if (strLen > sstInlineThreshold) {
      writeStringCellInline(col, value);
      return;
    }

    int ssIndex = ss.add(value);
    ensureCapacity(64);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'c';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    writeCellRef(col);
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 't';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = 's';
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = '>';
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'v';
    rowBuffer[rowBufferPos++] = '>';
    writeNumberToBuffer(ssIndex);
    closeCellTag();
  }

  private void writeNumberCell(int col, long value) {
    ensureCapacity(64);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'c';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    writeCellRef(col);
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = '>';
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'v';
    rowBuffer[rowBufferPos++] = '>';
    writeNumberToBuffer(value);
    closeCellTag();
  }

  /**
   * 写入浮点数值单元格。
   *
   * <p>NaN/Infinity 不是合法的 OOXML 数值，降级为 inline 字符串单元格写出，避免产出损坏文件。
   */
  private void writeDoubleCell(int col, double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      writeStringCellInline(col, Double.toString(value));
      return;
    }
    String dStr = Double.toString(value);
    ensureCapacity(64 + dStr.length());
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'c';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    writeCellRef(col);
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = '>';
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'v';
    rowBuffer[rowBufferPos++] = '>';
    writeStringToBuffer(dStr, false);
    closeCellTag();
  }

  private void writeBooleanCell(int col, Boolean value) {
    ensureCapacity(64);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'c';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    writeCellRef(col);
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 't';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = 'b';
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = '>';
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'v';
    rowBuffer[rowBufferPos++] = '>';
    writeStringToBuffer(value ? "1" : "0", false);
    closeCellTag();
  }

  private void writeGenericCell(int col, Object value) {
    writeStringCellInline(col, value.toString());
  }

  /**
   * 写入公式单元格 — 产出 {@code <c r="REF"><f>FORMULA</f><v>CACHED</v></c>} 双节点。
   *
   * <p>公式通过 {@link com.njydsz.common.excel.annotation.ExcelProperty#formula()} 指定文本表达式，
   * 如 {@code SUM(B2:D2)}。Excel 打开时会重新计算公式；写入时同步输出字段当前值作为 {@code <v>} 缓存值，
   * 避免打开时依赖计算。公式文本经过 XML 转义防止破坏文档结构。
   *
   * @param col 列索引
   * @param formula 公式表达式文本（非 null 非空）
   * @param cachedValue 缓存值（toString 输出到 {@code <v>}），可为 null
   */
  private void writeFormulaCell(int col, String formula, Object cachedValue) {
    String cached = (cachedValue == null) ? "" : cachedValue.toString();
    String escapedFormula = escapeFormula(formula);
    String cellXml = "<c r=\"" + toCellRefPlain(currentRow, col) + "\">"
        + "<f>" + escapedFormula + "</f>"
        + "<v>" + escapeCachedValue(cached) + "</v>"
        + "</c>";
    byte[] cellBytes = cellXml.getBytes(StandardCharsets.UTF_8);
    ensureCapacity(cellBytes.length);
    System.arraycopy(cellBytes, 0, rowBuffer, rowBufferPos, cellBytes.length);
    rowBufferPos += cellBytes.length;
  }

  /** 公式表达式转义（防 XML 结构破坏） */
  private static String escapeFormula(String formula) {
    if (formula == null) return "";
    StringBuilder sb = new StringBuilder(formula.length());
    for (int i = 0; i < formula.length(); i++) {
      char c = formula.charAt(i);
      switch (c) {
        case '&': sb.append("&amp;"); break;
        case '<': sb.append("&lt;"); break;
        case '>': sb.append("&gt;"); break;
        case '"': sb.append("&quot;"); break;
        default: sb.append(c);
      }
    }
    return sb.toString();
  }

  /** 缓存值转义（防止 <v/> 首字符小于号等破坏 XML） */
  private static String escapeCachedValue(String v) {
    if (v == null) return "";
    StringBuilder sb = new StringBuilder(v.length());
    for (int i = 0; i < v.length(); i++) {
      char c = v.charAt(i);
      switch (c) {
        case '&': sb.append("&amp;"); break;
        case '<': sb.append("&lt;"); break;
        case '>': sb.append("&gt;"); break;
        default: sb.append(c);
      }
    }
    return sb.toString();
  }

  /**
   * 将列索引转换为列字母（AA 格式），供公式单元格使用。
   *
   * @param row 行索引（0-based）
   * @param col 列索引（0-based）
   * @return 单元格引用字符串（如 "A1"、"AE6"）
   */
  static String toCellRefPlain(int row, int col) {
    StringBuilder colRef = new StringBuilder(3);
    int c = col;
    while (c >= 0) {
      colRef.insert(0, (char) ('A' + c % 26));
      c = c / 26 - 1;
      if (c < 0) break;
    }
    return colRef.toString() + (row + 1);
  }

  private void closeCellTag() {
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = '/';
    rowBuffer[rowBufferPos++] = 'v';
    rowBuffer[rowBufferPos++] = '>';
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = '/';
    rowBuffer[rowBufferPos++] = 'c';
    rowBuffer[rowBufferPos++] = '>';
  }

  // ==================== 工具方法 ====================

  private void writeCellRef(int col) {
    int columnIndex = col;
    int len = 0;
    while (columnIndex >= 0) {
      cellRefBuffer[len++] = (byte) ('A' + columnIndex % 26);
      columnIndex = columnIndex / 26 - 1;
    }
    for (int i = len - 1; i >= 0; i--) {
      rowBuffer[rowBufferPos++] = cellRefBuffer[i];
    }
    writeNumberToBuffer(currentRow);
  }

  private void writeNumberToBuffer(long value) {
    if (value == 0) {
      rowBuffer[rowBufferPos++] = '0';
      return;
    }

    if (value < 0) {
      rowBuffer[rowBufferPos++] = '-';
      value = -value;
    }

    int numDigits = 0;
    long temp = value;
    while (temp > 0) {
      numberBuffer[numDigits++] = (byte) digitChars[(int) (temp % 10)];
      temp /= 10;
    }

    for (int i = numDigits - 1; i >= 0; i--) {
      rowBuffer[rowBufferPos++] = numberBuffer[i];
    }
  }

  private void writeStringToBuffer(String str, boolean needsEscape) {
    if (str == null) {
      return;
    }

    if (!needsEscape) {
      int len = str.length();
      ensureCapacity(len);
      for (int i = 0; i < len; i++) {
        char c = str.charAt(i);
        if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
          rowBuffer[rowBufferPos++] = (byte) c;
        }
      }
      return;
    }

    int len = str.length();
    int i = 0;
    while (i < len) {
      char c = str.charAt(i);

      if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
        i++;
        continue;
      }

      if (c < 128 && c != '&' && c != '<' && c != '>' && c != '"' && c != '\'') {
        rowBuffer[rowBufferPos++] = (byte) c;
        i++;
        continue;
      }

      if (c == '&') {
        ensureCapacity(5);
        rowBuffer[rowBufferPos++] = '&';
        rowBuffer[rowBufferPos++] = 'a';
        rowBuffer[rowBufferPos++] = 'm';
        rowBuffer[rowBufferPos++] = 'p';
        rowBuffer[rowBufferPos++] = ';';
      } else if (c == '<') {
        ensureCapacity(5);
        rowBuffer[rowBufferPos++] = '&';
        rowBuffer[rowBufferPos++] = 'l';
        rowBuffer[rowBufferPos++] = 't';
        rowBuffer[rowBufferPos++] = ';';
      } else if (c == '>') {
        ensureCapacity(5);
        rowBuffer[rowBufferPos++] = '&';
        rowBuffer[rowBufferPos++] = 'g';
        rowBuffer[rowBufferPos++] = 't';
        rowBuffer[rowBufferPos++] = ';';
      } else if (c == '"') {
        ensureCapacity(6);
        rowBuffer[rowBufferPos++] = '&';
        rowBuffer[rowBufferPos++] = 'q';
        rowBuffer[rowBufferPos++] = 'u';
        rowBuffer[rowBufferPos++] = 'o';
        rowBuffer[rowBufferPos++] = 't';
        rowBuffer[rowBufferPos++] = ';';
      } else if (c == '\'') {
        ensureCapacity(6);
        rowBuffer[rowBufferPos++] = '&';
        rowBuffer[rowBufferPos++] = 'a';
        rowBuffer[rowBufferPos++] = 'p';
        rowBuffer[rowBufferPos++] = 'o';
        rowBuffer[rowBufferPos++] = 's';
        rowBuffer[rowBufferPos++] = ';';
      } else {
        ensureCapacity(8);
        byte[] bytes = Character.toString(c).getBytes(StandardCharsets.UTF_8);
        System.arraycopy(bytes, 0, rowBuffer, rowBufferPos, bytes.length);
        rowBufferPos += bytes.length;
      }
      i++;
    }
  }

  private void ensureCapacity(int needed) {
    int required = rowBufferPos + needed;
    if (required <= rowBuffer.length) {
      return;
    }
    int newSize = Math.max(required, rowBuffer.length * 2);
    rowBuffer = Arrays.copyOf(rowBuffer, newSize);
  }

  // ==================== 类分析（类型化路径） ====================

  /**
   * 类型化写入的列元数据，绑定反射字段、表头文本、ASM 访问器、日期格式与列宽。
   *
   * <p>在 {@link #analyzeClass(Class)} 阶段构建；ASM 访问器生成失败时 {@code getter} 为 {@code null}，
   * 写入阶段回退为直接反射访问 {@code field}。
   */
  private static class FieldAccessorInfo {
    Field field;
    String headerName;
    MHFieldAccessor.FieldGetter getter;
    DateTimeFormatter dateFormatObj;
    /** 自定义列宽（单位：字符），null 表示使用默认宽度。 */
    Short width;
    /** 公式表达式（来自 @ExcelProperty.formula()），null/空 表示无公式。 */
    String formula;
  }

  private void analyzeClass(Class<?> clazz) {
    try {
      // 使用统一的列排序解析器
      List<Field> orderedFields =
          com.njydsz.common.excel.core.util.ColumnOrderResolver.resolveOrderedFields(clazz);
      if (orderedFields.isEmpty()) {
        return;
      }

      buildFieldInfoArrayFromFields(orderedFields, clazz);
    } catch (Exception e) {
      LOG.warn("ASM field accessor creation failed, using reflection", e);
    }
  }

  /**
   * 根据统一排序后的字段列表构建 fieldInfoArray。
   *
   * @param orderedFields 已按 ColumnOrderResolver 排序的字段列表
   * @param clazz 映射的源类类型
   */
  private void buildFieldInfoArrayFromFields(List<Field> orderedFields, Class<?> clazz) {
    fieldInfoSize = orderedFields.size();
    fieldInfoArray = new FieldAccessorInfo[fieldInfoSize];
    fieldInfoMap = new HashMap<>(16);
    columnTypeIds = new byte[fieldInfoSize];

    for (int compactIdx = 0; compactIdx < orderedFields.size(); compactIdx++) {
      Field field = orderedFields.get(compactIdx);
      ExcelProperty prop = field.getAnnotation(ExcelProperty.class);
      String dateFormat = prop != null ? prop.dateFormat() : "";

      FieldAccessorInfo info = new FieldAccessorInfo();
      info.field = field;
      String headerName = (prop != null && prop.value() != null && !prop.value().isEmpty())
          ? prop.value() : field.getName();
      info.headerName = headerName;
      info.getter = MHFieldAccessor.getGetter(clazz, field);
      info.dateFormatObj =
          (dateFormat != null && !dateFormat.isEmpty())
              ? DateTimeFormatter.ofPattern(dateFormat)
              : DEFAULT_DATE_FORMATTER;
      if (prop != null && prop.width() > 0) {
        info.width = (short) prop.width();
      }
      // 设置公式（如有）
      if (prop != null) {
        String formulaExpr = prop.formula();
        if (formulaExpr != null && !formulaExpr.isEmpty()) {
          info.formula = formulaExpr;
        }
      }
      fieldInfoMap.put(compactIdx, info);

      fieldInfoArray[compactIdx] = info;

      Class<?> fieldType = field.getType();
      if (fieldType == String.class) {
        columnTypeIds[compactIdx] = 1;
      } else if (Number.class.isAssignableFrom(fieldType)
          || fieldType == int.class
          || fieldType == long.class
          || fieldType == double.class
          || fieldType == float.class) {
        columnTypeIds[compactIdx] = 2;
      } else if (fieldType == Date.class
          || fieldType == LocalDateTime.class
          || fieldType == LocalDate.class) {
        columnTypeIds[compactIdx] = 3;
      } else if (fieldType == boolean.class || fieldType == Boolean.class) {
        columnTypeIds[compactIdx] = 4;
      } else {
        columnTypeIds[compactIdx] = 5;
      }
    }
  }

  // ==================== 动态表头分析 ====================

  /**
   * 分析动态表头（head(List<String>) + List<List<Object>> 模式）。
   *
   * <p>此时 metadata.clazz 为 null，metadata.headList 非空。构建 fieldInfoArray 仅含
   * 表头名称元数据，列类型统一为"通用"（运行时动态推断）。
   */
  private void analyzeHeadList() {
    List<WriteHeaderProperty> headList = metadata.getHeadList();
    if (headList == null || headList.isEmpty()) {
      return;
    }

    fieldInfoSize = headList.size();
    fieldInfoArray = new FieldAccessorInfo[fieldInfoSize];
    columnTypeIds = new byte[fieldInfoSize];
    fieldInfoMap = new HashMap<>(16);

    for (int i = 0; i < fieldInfoSize; i++) {
      WriteHeaderProperty property = headList.get(i);
      FieldAccessorInfo info = new FieldAccessorInfo();
      info.headerName = property.getName();
      // 动态模式下无类型信息，统一走通用路径
      columnTypeIds[i] = 5;
      fieldInfoArray[i] = info;
      fieldInfoMap.put(i, info);
    }
  }

  // ==================== 表头行写入 ====================

  /** 写入表头行（类型化路径 or 动态路径） */
  private int writeHeaderRow(UltraFastSharedStrings ss) {
    ensureCapacity(32);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = 'o';
    rowBuffer[rowBufferPos++] = 'w';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    writeNumberToBuffer(currentRow);
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = '>';

    for (int col = 0; col < fieldInfoSize; col++) {
      FieldAccessorInfo info = fieldInfoArray[col];
      if (info == null || info.headerName == null) {
        continue;
      }
      String headerName = info.headerName;
      writeStringCell(col, headerName, ss);
      trackColumnWidth(col, headerName);
    }

    ensureCapacity(16);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = '/';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = 'o';
    rowBuffer[rowBufferPos++] = 'w';
    rowBuffer[rowBufferPos++] = '>';
    return rowBufferPos;
  }

  // ==================== Workbook XML ====================

  /**
   * 获取 Workbook XML 字节。
   *
   * <p>Sheet 名称解析优先级与旧 POI 兼容路径一致：{@code @ExcelSheet} 注解（doWrite
   * 期折叠进 metadata 的契约） &gt; 显式 {@code sheet(name)} 配置 &gt; 默认 "Sheet1"。
   */
  private byte[] getWorkbookBytes() {
    String sheetName = metadata.getSheetName();
    Class<?> clazz = metadata.getClazz();
    if (clazz != null) {
      ExcelSheet sheetAnnotation = clazz.getAnnotation(ExcelSheet.class);
      if (sheetAnnotation != null && !sheetAnnotation.name().isEmpty()) {
        sheetName = sheetAnnotation.name();
      }
    }
    if (sheetName == null || sheetName.isEmpty()) {
      sheetName = "Sheet1";
    }
    // XML 转义
    sheetName =
        sheetName
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    return String.format(new String(WORKBOOK_BYTES_TEMPLATE, StandardCharsets.UTF_8), sheetName)
        .getBytes(StandardCharsets.UTF_8);
  }

  // ==================== 工具方法 ====================

  /**
   * 将 0-based 行列索引转换为 Excel 单元格引用字符串。
   *
   * @param row 0-based 行索引
   * @param col 0-based 列索引
   * @return Cell reference（如 "A1"、"B10"）
   */
  static String toCellRef(int row, int col) {
    StringBuilder colLetters = new StringBuilder(4);
    int c = col;
    while (c >= 0) {
      colLetters.append((char) ('A' + c % 26));
      c = c / 26 - 1;
    }
    colLetters.reverse();
    return colLetters.toString() + (row + 1);
  }

  // ==================== 共享字符串表（SST） ====================

  /**
   * 极速共享字符串表（SST）。
   *
   * <p>内部采用「数组 + HashMap」双结构：数组按插入顺序保存字符串以顺序生成 XML，
   * HashMap 建立去重映射以复用索引、压缩文件体积。超过 50 字符的长字符串不走此表。
   * 实例非线程安全，仅用于单线程的写入流程。
   */
  private static class UltraFastSharedStrings {
    /** 字符串数组。初始容量 256，按需 2x 指数扩容。 */
    private String[] strings = new String[256];

    /** 字符串计数 */
    private int count = 0;

    /** 字符串到索引的映射，使用 HashMap 避免 hashCode 冲突问题 */
    private final HashMap<String, Integer> stringToIndex = new HashMap<>(1024);

    public int add(String str) {
      if (str == null) {
        return -1;
      }

      Integer existing = stringToIndex.get(str);
      if (existing != null) {
        return existing;
      }

      if (count >= strings.length) {
        strings = Arrays.copyOf(strings, strings.length * 2);
      }

      int idx = count++;
      strings[idx] = str;
      stringToIndex.put(str, idx);
      return idx;
    }

    byte[] buildXmlDirect() throws IOException {
      // P1-1：字节级直接构造 SST XML，消除 StringBuilder → String → byte[] 三次拷贝
      // 原理：XML 转义仅涉及 ASCII 字符（& < > " '），UTF-8 中均为单字节，可字节级判断替换
      byte[] countBytes = Integer.toString(count).getBytes(StandardCharsets.UTF_8);

      // 预分配大小：头部 + 每行开销 + 字符串长度
      int estimatedSize = SST_HEADER_PART1.length + countBytes.length * 2
          + SST_HEADER_PART2.length + SST_HEADER_FOOTER.length
          + count * (SI_OPEN.length + SI_CLOSE.length + 32);
      ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(estimatedSize, 256));

      baos.write(SST_HEADER_PART1, 0, SST_HEADER_PART1.length);
      baos.write(countBytes, 0, countBytes.length);     // count
      baos.write(SST_HEADER_PART2, 0, SST_HEADER_PART2.length);
      baos.write(countBytes, 0, countBytes.length);     // uniqueCount（已去重，==count）
      baos.write(SST_HEADER_FOOTER, 0, SST_HEADER_FOOTER.length);

      for (int i = 0; i < count; i++) {
        baos.write(SI_OPEN, 0, SI_OPEN.length);        // "<si><t>"
        String s = strings[i];
        if (s != null) {
          writeEscapedUtf8(baos, s);
        }
        baos.write(SI_CLOSE, 0, SI_CLOSE.length);       // "</t></si>"
      }

      baos.write(SST_FOOTER, 0, SST_FOOTER.length);     // "</sst>"
      return baos.toByteArray();
    }

    /**
     * 字节级 XML UTF-8 转义写入。仅 ASCII 危险字符需要转义，UTF-8 中均为单字节。
     */
    private static void writeEscapedUtf8(ByteArrayOutputStream out, String value) throws IOException {
      byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
      for (byte b : utf8) {
        switch (b) {
          case 0x26:  out.write(AMP_BYTES);  break;  // '&'
          case 0x3C:  out.write(LT_BYTES);   break;  // '<'
          case 0x3E:  out.write(GT_BYTES);   break;  // '>'
          case 0x22:  out.write(QUOT_BYTES); break;  // '"'
          case 0x27:  out.write(APOS_BYTES); break;  // '\''
          default:    out.write(b);          break;
        }
      }
    }
  }

  /* ==================== SST XML 字节常量（P1-1 优化） ==================== */
  private static final byte[] SST_HEADER_PART1 = (
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
          + "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"")
      .getBytes(StandardCharsets.UTF_8);
  private static final byte[] SST_HEADER_PART2 = "\" uniqueCount=\""
      .getBytes(StandardCharsets.UTF_8);
  private static final byte[] SST_HEADER_FOOTER = "\">".getBytes(StandardCharsets.UTF_8);
  private static final byte[] SI_OPEN  = "<si><t>".getBytes(StandardCharsets.UTF_8);
  private static final byte[] SI_CLOSE = "</t></si>".getBytes(StandardCharsets.UTF_8);
  private static final byte[] SST_FOOTER = "</sst>".getBytes(StandardCharsets.UTF_8);

  /* XML 转义实体字节常量 */
  private static final byte[] AMP_BYTES  = "&amp;".getBytes(StandardCharsets.UTF_8);
  private static final byte[] LT_BYTES   = "&lt;".getBytes(StandardCharsets.UTF_8);
  private static final byte[] GT_BYTES   = "&gt;".getBytes(StandardCharsets.UTF_8);
  private static final byte[] QUOT_BYTES = "&quot;".getBytes(StandardCharsets.UTF_8);
  private static final byte[] APOS_BYTES = "&apos;".getBytes(StandardCharsets.UTF_8);
}
