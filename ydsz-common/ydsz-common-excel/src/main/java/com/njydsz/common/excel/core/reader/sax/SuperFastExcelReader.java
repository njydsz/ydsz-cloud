package com.njydsz.common.excel.core.reader.sax;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.context.AnalysisContext;
import com.njydsz.common.excel.core.listener.ReadListener;
import com.njydsz.common.excel.core.reader.ColumnMetadata;
import com.njydsz.common.excel.exception.ExcelReadException;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor.ObjectInstantiator;

/**
 * 超高性能 Excel 读取器，基于 XML 流式解析实现
 *
 * <p>完全不依赖 POI，不使用 OPCPackage、SharedStringsTable、SAXParser 等组件， 直接通过 ZIP/XML
 * 手动解析提取数据。
 *
 * <h3>核心优化</h3>
 *
 * <ul>
 *   <li>无 POI 依赖：不使用 OPCPackage、SharedStringsTable、SAXParser
 *   <li>手动 XML 解析：通过字符串匹配直接提取标签内容，避免 SAX 事件开销
 *   <li>SST 按需加载：共享字符串表流式解析，不一次性加载到内存
 *   <li>大文件流式处理：sheet XML 通过临时文件管道传递，避免 OOM
 *   <li>文件大小限制：通过 ExcelConfig.maxReadFileSizeMB 防止超大文件 OOM
 *   <li>MethodHandle 字段访问：使用 MethodHandle 替代反射
 * </ul>
 *
 * <h3>Sheet 选择（P1-4 修复）</h3>
 *
 * <p>{@link #read(Path)} 通过 {@link ZipFile} 随机访问解析 {@code xl/workbook.xml} 与 {@code
 * xl/_rels/workbook.xml.rels}，按 sheetName（精确匹配）或 sheetIndex（workbook 声明顺序， 0-based，
 * 越界回落第一个——与 POI 路径 getSheet 语义对齐）定位目标 sheet。 此前固定读取 zip 中第一个 sheet
 * entry，忽略调用方的 Sheet 选择配置。
 *
 * <h3>zip bomb 膨胀比防护（P1-4 修复）</h3>
 *
 * <p>所有解压读取（SST / sheet XML / InputStream 落盘）均经 {@link BoundedInputStream} 限流：
 * 单个部件解压后超过 {@code maxReadFileSizeMB} 即中断并抛出异常——压缩前体积再小也无法膨胀越限。
 * 此前依赖 {@code ZipEntry.getSize()}（来自 zip 头，可伪造，常为 -1）事后检查， 临时文件分支先写满磁盘再校验，防护形同虚设。
 *
 * <h3>数值型日期识别（深度完善·方案 B）</h3>
 *
 * <p>{@code read(Path)} 同时解析 {@code xl/styles.xml}（{@link StylesReader}），
 * 数值单元格的样式索引（{@code <c s="N">}）经 numFmt 判定为日期格式时，序列值按
 * {@code use1904Windowing} 配置的窗口转换为 {@link java.util.Date} 交付转换链。
 * 此前 fast 引擎不解析 styles.xml，数值型日期单元格一律按纯数字读入——即
 * {@code fastNumericDateCellIsKnownLimitation} 存档的已知限制，现已解除。
 *
 * <h3>性能对比</h3>
 *
 * <table border="1">
 *   <tr><th>读取方式</th><th>100K 数据耗时</th><th>内存占用</th></tr>
 *   <tr><td>XSSFWorkbook 用户模式</td><td>~1500ms</td><td>~500MB</td></tr>
 *   <tr><td>SAX + POI 组件</td><td>~800ms</td><td>~200MB</td></tr>
 *   <tr><td>本库 XML 手工解析</td><td>~300ms</td><td>~50MB</td></tr>
 * </table>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class SuperFastExcelReader {

  private static final Logger LOG = LoggerFactory.getLogger(SuperFastExcelReader.class);

  /** 中等文件大小阈值（字节），小于此值直接加载到内存 */
  private static final long IN_MEMORY_THRESHOLD = 10 * 1024 * 1024; // 10MB

  /** 列元数据数组 */
  ColumnMetadata[] columnMetadataArray;

  /**
   * 表头行收集的列名（0-based 列索引 → 列名），由 SheetXmlReader 在解析表头行时填充。
   *
   * <p>P0-2 修复：fast 路径此前无法构建列元数据（POI 路径在 parseSheet 中通过 HeaderAnalyzer
   * 构建，fast 路径拿不到 POI Row），导致所有数据单元格被 parseDataCell 直接丢弃。
   */
  final Map<Integer, String> headerNames = new HashMap<>();

  /**
   * 列元数据工厂：当 {@link #columnMetadataArray} 为 null 时，基于收集到的表头列名惰性构建列元数据。
   *
   * <p>由 ExcelReader 接线为 HeaderAnalyzer.analyzeClassMetadataFromNames， 首个数据单元格到达时触发，保证表头行已完整收集。
   */
  private Function<Map<Integer, String>, ColumnMetadata[]> metadataFactory;

  /** 对象实例化器 */
  ObjectInstantiator instantiator;

  /** 分析上下文 */
  AnalysisContext context;

  /** 监听器列表 */
  List<ReadListener<?>> listeners;

  /** 表头行号 */
  int headRowNumber = 1;

  /** 最大读取行数限制，0 表示不限制 */
  int maxRows = 0;

  /** 是否跳过空行，默认 false（与 POI 路径默认语义对齐） */
  boolean skipEmptyRows = false;

  /**
   * Excel 全局配置。P1-4 修复：此前 fast 引擎内部恒用 ExcelConfig.defaults()，
   * maxReadFileSizeMB 等配置对本引擎无效。
   */
  private ExcelConfig excelConfig;

  /** Sheet 选择：按名称（精确匹配，优先于 sheetIndex） */
  private String sheetName;

  /** Sheet 选择：按 workbook 声明顺序（0-based，越界回落第一个，与 POI getSheet 对齐） */
  private Integer sheetIndex;

  /**
   * 读取 XLSX 文件。
   *
   * <p>InputStream 无法随机访问（Sheet 选择需先读 workbook.xml 再定位 entry）， bounded 落临时文件后走
   * {@link #read(Path)}。
   *
   * @param inputStream xlsx 文件输入流
   * @throws Exception 解析异常
   */
  public void read(InputStream inputStream) throws Exception {
    long limit = inflateLimitBytes();
    Path tempFile = Files.createTempFile("ydsz_xlsx_", ".zip");
    try {
      // bounded：压缩流本身也限 maxReadFileSizeMB，防止超大文件落盘
      Files.copy(
          new BoundedInputStream(inputStream, limit),
          tempFile,
          StandardCopyOption.REPLACE_EXISTING);
      read(tempFile);
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * 读取 XLSX 文件（文件源，推荐入口）。
   *
   * <p>通过 {@link ZipFile} 随机访问：先解析 workbook.xml 与 rels 定位目标 sheet， 再流式解析 SST 与
   * sheet XML。对大 sheet 采用流式处理策略：超过内存阈值时使用临时文件管道，避免 OOM。
   *
   * @param file xlsx 文件路径
   * @throws Exception 解析异常
   */
  public void read(Path file) throws Exception {
    try (ZipFile zipFile = new ZipFile(file.toFile())) {
      String targetEntry = resolveTargetSheetEntry(zipFile);

      ZipEntry sheetEntry = zipFile.getEntry(targetEntry);
      if (sheetEntry == null) {
        throw ExcelReadException.invalidFormat(targetEntry, "Sheet 不存在");
      }

      // SST 解析：流式读取，内存中只保留字符串索引；bounded 防 sharedStrings.xml 膨胀攻击
      SharedStringsReader ssReader = null;
      ZipEntry sstEntry = zipFile.getEntry("xl/sharedStrings.xml");
      if (sstEntry != null) {
        ssReader = new SharedStringsReader();
        try (InputStream is = bounded(zipFile.getInputStream(sstEntry))) {
          ssReader.parse(is);
        }
      }

      // 样式表解析（深度完善·方案 B）：判定数值单元格是否日期格式（numFmt），
      // 缺失时 fast 引擎把数值型日期单元格当纯数字读入（已知限制存档项）
      StylesReader stylesReader = null;
      ZipEntry stylesEntry = zipFile.getEntry("xl/styles.xml");
      if (stylesEntry != null) {
        stylesReader = new StylesReader();
        try (InputStream is = bounded(zipFile.getInputStream(stylesEntry))) {
          stylesReader.parse(is);
        }
      }

      // Sheet 数据：bounded 复制到临时文件（大小可信化——zip 头的 getSize 可伪造），
      // 再按实际大小决定内存加载或文件流式解析
      Path tempSheetFile = Files.createTempFile("ydsz_sheet_", ".xml");
      try {
        Files.copy(
            bounded(zipFile.getInputStream(sheetEntry)),
            tempSheetFile,
            StandardCopyOption.REPLACE_EXISTING);
        long actualSize = Files.size(tempSheetFile);

        if (actualSize <= IN_MEMORY_THRESHOLD) {
          byte[] bytes = Files.readAllBytes(tempSheetFile);
          Files.deleteIfExists(tempSheetFile);
          tempSheetFile = null;
          parseSheetStream(new ByteArrayInputStream(bytes), ssReader, stylesReader);
        } else {
          LOG.debug(
              "大文件模式: sheet XML 大小={}MB, 使用临时文件流式解析", actualSize / 1024 / 1024);
          try (InputStream sheetStream =
              new BufferedInputStream(Files.newInputStream(tempSheetFile))) {
            parseSheetStream(sheetStream, ssReader, stylesReader);
          }
        }
      } finally {
        if (tempSheetFile != null) {
          try {
            Files.deleteIfExists(tempSheetFile);
          } catch (IOException e) {
            LOG.warn("清理临时文件失败: {}", tempSheetFile, e);
          }
        }
      }
    }
  }

  /** 解析 sheet XML 流并通知监听器。 */
  private void parseSheetStream(
      InputStream sheetStream, SharedStringsReader ssReader, StylesReader stylesReader)
      throws Exception {
    SheetXmlReader sheetReader = new SheetXmlReader(this, ssReader, stylesReader);
    sheetReader.parse(sheetStream);
  }

  // ==================== Sheet 选择（workbook.xml + rels 解析） ====================

  /**
   * 解析目标 sheet 的 zip entry 名。
   *
   * <p>优先级：sheetName（精确匹配，未命中抛异常）＞ sheetIndex（声明顺序，越界回落第一个）＞ 第一个 sheet。
   * workbook.xml / rels 缺失或无映射时回落第一个 sheet entry（兼容非标准生成器）。
   *
   * @param zipFile zip 文件
   * @return 目标 entry 名
   * @throws IOException 读取异常
   */
  private String resolveTargetSheetEntry(ZipFile zipFile) throws IOException {
    ZipEntry workbookEntry = zipFile.getEntry("xl/workbook.xml");
    if (workbookEntry == null) {
      return fallbackFirstSheetEntry(zipFile);
    }

    List<SheetRef> sheets;
    try (InputStream is = bounded(zipFile.getInputStream(workbookEntry))) {
      sheets = parseWorkbookSheets(new String(readAll(is), StandardCharsets.UTF_8));
    }
    if (sheets.isEmpty()) {
      return fallbackFirstSheetEntry(zipFile);
    }

    Map<String, String> rels = new HashMap<>();
    ZipEntry relsEntry = zipFile.getEntry("xl/_rels/workbook.xml.rels");
    if (relsEntry != null) {
      try (InputStream is = bounded(zipFile.getInputStream(relsEntry))) {
        rels = parseRelationships(new String(readAll(is), StandardCharsets.UTF_8));
      }
    }

    if (sheetName != null && !sheetName.isEmpty()) {
      for (SheetRef sheet : sheets) {
        if (sheetName.equals(sheet.name)) {
          String entry = rels.get(sheet.rid);
          if (entry != null) {
            return toEntryName(entry);
          }
        }
      }
      // 对齐 POI 路径语义：按名未命中即失败（POI getSheet 返回 null → ExcelReader 抛"Sheet不存在"）
      throw ExcelReadException.invalidFormat(sheetName, "Sheet不存在");
    }

    int index = sheetIndex != null ? sheetIndex : 0;
    if (index < 0 || index >= sheets.size()) {
      index = 0;
    }
    String entry = rels.get(sheets.get(index).rid);
    if (entry == null) {
      return fallbackFirstSheetEntry(zipFile);
    }
    return toEntryName(entry);
  }

  /** 回落：zip 中第一个 sheet entry（兼容 workbook.xml 缺失的非标准文件）。 */
  private static String fallbackFirstSheetEntry(ZipFile zipFile) {
    Enumeration<? extends ZipEntry> entries = zipFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      String name = entry.getName();
      if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
        return name;
      }
    }
    return "xl/worksheets/sheet1.xml";
  }

  /** 将 rels Target 转为 zip entry 名（rel 目标相对 xl/ 目录）。 */
  private static String toEntryName(String target) {
    if (target.startsWith("/")) {
      return target.substring(1);
    }
    return "xl/" + target;
  }

  /** 解析 workbook.xml 中的 sheet 声明（保持声明顺序）。 */
  private static List<SheetRef> parseWorkbookSheets(String xml) {
    List<SheetRef> result = new ArrayList<>();
    int pos = 0;
    while (true) {
      int start = xml.indexOf("<sheet ", pos);
      if (start < 0) {
        break;
      }
      int end = xml.indexOf('>', start);
      if (end < 0) {
        break;
      }
      String tag = xml.substring(start, end);
      String name = extractAttribute(tag, "name");
      String rid = extractAttribute(tag, "r:id");
      if (rid == null) {
        rid = extractAttribute(tag, "id");
      }
      if (name != null && rid != null) {
        result.add(new SheetRef(xmlUnescape(name), rid));
      }
      pos = end + 1;
    }
    return result;
  }

  /** 解析 rels 中的 Id → Target 映射。 */
  private static Map<String, String> parseRelationships(String xml) {
    Map<String, String> result = new HashMap<>();
    int pos = 0;
    while (true) {
      int start = xml.indexOf("<Relationship ", pos);
      if (start < 0) {
        break;
      }
      int endTag = xml.indexOf('>', start);
      if (endTag < 0) {
        break;
      }
      int selfClose = xml.indexOf("/>", start);
      int end = (selfClose >= 0 && selfClose < endTag) ? selfClose : endTag;
      String tag = xml.substring(start, end);
      String id = extractAttribute(tag, "Id");
      String target = extractAttribute(tag, "Target");
      if (id != null && target != null) {
        result.put(id, target);
      }
      pos = endTag + 1;
    }
    return result;
  }

  /** 从标签文本中提取属性值（支持双/单引号）。 */
  private static String extractAttribute(String tag, String attr) {
    int idx = tag.indexOf(attr + "=\"");
    if (idx >= 0) {
      int valueStart = idx + attr.length() + 2;
      int valueEnd = tag.indexOf('"', valueStart);
      if (valueEnd > valueStart) {
        return tag.substring(valueStart, valueEnd);
      }
    }
    idx = tag.indexOf(attr + "='");
    if (idx >= 0) {
      int valueStart = idx + attr.length() + 2;
      int valueEnd = tag.indexOf('\'', valueStart);
      if (valueEnd > valueStart) {
        return tag.substring(valueStart, valueEnd);
      }
    }
    return null;
  }

  /** 最小 XML 反转义（workbook sheet 名）。 */
  private static String xmlUnescape(String s) {
    return s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&");
  }

  /** 读尽小部件（workbook.xml / rels，bounded 后体积可控）。 */
  private static byte[] readAll(InputStream is) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
    byte[] buffer = new byte[4096];
    int len;
    while ((len = is.read(buffer)) > 0) {
      baos.write(buffer, 0, len);
    }
    return baos.toByteArray();
  }

  // ==================== zip bomb 防护 ====================

  /** 解压安全上限（字节）：maxReadFileSizeMB。 */
  private long inflateLimitBytes() {
    return (long) excelConfig().getMaxReadFileSizeMB() * 1024L * 1024L;
  }

  /** 包一层解压限流（防膨胀攻击）。 */
  private InputStream bounded(InputStream in) {
    return new BoundedInputStream(in, inflateLimitBytes());
  }

  /** 生效的 ExcelConfig（未设置时回退默认值）。 */
  ExcelConfig excelConfig() {
    return excelConfig != null ? excelConfig : ExcelConfig.defaults();
  }

  /**
   * 解压限流流：累计读取超过 limit 即抛异常。
   *
   * <p>zip bomb 的本质是"压缩前体积小、解压后巨大"——以解压后绝对量上限阻断， 与 POI
   * ZipSecureFile 的膨胀比（MIN_INFLATE_RATIO）防护等价且更直观。
   */
  static final class BoundedInputStream extends FilterInputStream {

    private final long limit;
    private long totalRead = 0;

    BoundedInputStream(InputStream in, long limit) {
      super(in);
      this.limit = limit;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int n = super.read(b, off, len);
      if (n > 0) {
        totalRead += n;
        checkLimit();
      }
      return n;
    }

    @Override
    public int read() throws IOException {
      int c = super.read();
      if (c >= 0) {
        totalRead++;
        checkLimit();
      }
      return c;
    }

    private void checkLimit() {
      if (totalRead > limit) {
        throw ExcelReadException.invalidFormat(
            "zip", "解压内容超过安全上限（疑似 zip bomb 膨胀攻击）: limit=" + (limit / 1024 / 1024) + "MB");
      }
    }
  }

  /** workbook.xml 中的 sheet 声明（声明顺序 + rId）。 */
  private static final class SheetRef {

    final String name;
    final String rid;

    SheetRef(String name, String rid) {
      this.name = name;
      this.rid = rid;
    }
  }

  // ==================== setter ====================

  /**
   * 设置列元数据数组
   *
   * @param columnMetadataArray 列元数据数组
   */
  public void setColumnMetadataArray(ColumnMetadata[] columnMetadataArray) {
    this.columnMetadataArray = columnMetadataArray;
  }

  /**
   * 设置列元数据工厂（fast 路径惰性构建列元数据）
   *
   * <p>仅当 {@link #setColumnMetadataArray} 未提供（columnMetadataArray 为 null）时生效。
   *
   * @param metadataFactory 表头列名 → 列元数据的构建函数
   */
  public void setMetadataFactory(Function<Map<Integer, String>, ColumnMetadata[]> metadataFactory) {
    this.metadataFactory = metadataFactory;
  }

  /**
   * 获取列元数据数组；若未预置且配置了工厂，则基于已收集的表头列名惰性构建。
   *
   * @return 列元数据数组；无预置且无工厂时返回 null
   */
  ColumnMetadata[] resolveMetadata() {
    if (columnMetadataArray == null && metadataFactory != null) {
      columnMetadataArray = metadataFactory.apply(headerNames);
    }
    return columnMetadataArray;
  }

  /**
   * 设置对象实例化器
   *
   * @param instantiator 实例化器
   */
  public void setInstantiator(ObjectInstantiator instantiator) {
    this.instantiator = instantiator;
  }

  /**
   * 设置分析上下文
   *
   * @param context 上下文
   */
  public void setContext(AnalysisContext context) {
    this.context = context;
  }

  /**
   * 设置监听器列表
   *
   * @param listeners 监听器
   */
  public void setListeners(List<ReadListener<?>> listeners) {
    this.listeners = listeners;
  }

  /**
   * 设置表头行号
   *
   * @param headRowNumber 表头行号
   */
  public void setHeadRowNumber(int headRowNumber) {
    this.headRowNumber = headRowNumber;
  }

  /**
   * 设置最大读取行数限制
   *
   * @param maxRows 最大行数
   */
  public void setMaxRows(int maxRows) {
    this.maxRows = maxRows;
  }

  /**
   * 设置是否跳过空行
   *
   * @param skipEmptyRows 是否跳过空行
   */
  public void setSkipEmptyRows(boolean skipEmptyRows) {
    this.skipEmptyRows = skipEmptyRows;
  }

  /**
   * 设置 Excel 全局配置（P1-4：maxReadFileSizeMB 等配置对 fast 引擎生效）
   *
   * @param excelConfig Excel 配置，null 时回退默认值
   */
  public void setExcelConfig(ExcelConfig excelConfig) {
    this.excelConfig = excelConfig;
  }

  /**
   * 设置目标 Sheet 名称（精确匹配，优先于 {@link #setSheetIndex(Integer)}）
   *
   * @param sheetName Sheet 名称
   */
  public void setSheetName(String sheetName) {
    this.sheetName = sheetName;
  }

  /**
   * 设置目标 Sheet 序号（workbook 声明顺序，0-based；越界回落第一个，与 POI getSheet 对齐）
   *
   * @param sheetIndex Sheet 序号
   */
  public void setSheetIndex(Integer sheetIndex) {
    this.sheetIndex = sheetIndex;
  }
}
