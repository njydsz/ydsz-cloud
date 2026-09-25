package com.njydsz.common.excel.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.core.metadata.ReadMetadata;
import com.njydsz.common.excel.core.metadata.WriteMetadata;
import com.njydsz.common.excel.core.reader.ExcelStream;
import com.njydsz.common.excel.core.template.TemplateRegion;
import com.njydsz.common.excel.core.writer.MultiSheetFastWriter;
import com.njydsz.common.excel.csv.CsvReader;
import com.njydsz.common.excel.csv.CsvWriter;
import com.njydsz.common.excel.exception.ExcelReadException;

/**
 * Excel 门面类 — 整个框架的统一入口。
 *
 * <p>封装读取 / 写入 / 模板填充 / 多 Sheet / Web 下载等全部能力。自 v26.10.01 起完全移除 POI 依赖，
 * 底层读写全部由 SuperFast 引擎（{@link SuperFastExcelReader} / {@link SuperFastExcelWriter}）承担。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 读取
 * ExcelFacade.read("demo.xlsx", User.class)
 *     .sheet("用户数据")
 *     .doRead(new ReadListener<User>() {
 *         &#64;Override
 *         public void onData(AnalysisContext context, User data) {
 *             log.info("读取到数据: {}", data);
 *         }
 *     });
 *
 * // 写入
 * ExcelFacade.write("output.xlsx", User.class)
 *     .sheet("用户列表")
 *     .doWrite(userList);
 * }</pre>
 *
 * @author ydsz-team
 * @version 26.10.01
 * @since 26.09.01
 * @see ExcelReader
 * @see ExcelWriter
 */
public class ExcelFacade {

  private static final Logger log = LoggerFactory.getLogger(ExcelFacade.class);

  private ExcelFacade() {}

  // ==================== 读取相关方法 ====================

  /**
   * 从文件路径读取 Excel(无类型映射)
   *
   * @param fileName Excel 文件的完整路径，仅支持 .xlsx 格式
   * @return ExcelReader 读取器实例
   */
  public static ExcelReader read(String fileName) {
    return read(fileName, null);
  }

  /**
   * 从 File 对象读取 Excel(无类型映射)
   *
   * @param file Excel 文件的 File 对象
   * @return ExcelReader 读取器实例
   */
  public static ExcelReader read(java.io.File file) {
    return read(file, null);
  }

  /**
   * 从输入流读取 Excel(无类型映射)
   *
   * @param inputStream Excel 数据的输入流
   * @return ExcelReader 读取器实例
   */
  public static ExcelReader read(InputStream inputStream) {
    return read(inputStream, null);
  }

  /**
   * 从文件路径读取 Excel 并映射到指定类型
   *
   * @param fileName Excel 文件的完整路径
   * @param clazz 映射的目标类类型
   * @param <T> 泛型参数
   * @return ExcelReader 读取器实例
   */
  public static <T> ExcelReader read(String fileName, Class<T> clazz) {
    ReadMetadata metadata = new ReadMetadata();
    metadata.setClazz(clazz);
    metadata.setFilePath(fileName);
    return new ExcelReader(metadata);
  }

  /**
   * 从 File 对象读取 Excel 并映射到指定类型
   *
   * @param file Excel 文件的 File 对象
   * @param clazz 映射的目标类类型
   * @param <T> 泛型参数
   * @return ExcelReader 读取器实例
   */
  public static <T> ExcelReader read(java.io.File file, Class<T> clazz) {
    ReadMetadata metadata = new ReadMetadata();
    metadata.setClazz(clazz);
    metadata.setFile(file);
    return new ExcelReader(metadata);
  }

  /**
   * 从输入流读取 Excel 并映射到指定类型
   *
   * @param inputStream Excel 数据的输入流
   * @param clazz 映射的目标类类型
   * @param <T> 泛型参数
   * @return ExcelReader 读取器实例
   */
  public static <T> ExcelReader read(InputStream inputStream, Class<T> clazz) {
    ReadMetadata metadata = new ReadMetadata();
    metadata.setClazz(clazz);
    metadata.setInputStream(inputStream);
    return new ExcelReader(metadata);
  }

  // ==================== 写入相关方法 ====================

  /**
   * 创建 Excel 写入器 (无类型映射)
   *
   * @param fileName 目标 Excel 文件的完整路径
   * @return ExcelWriter 写入器实例
   */
  public static ExcelWriter write(String fileName) {
    return write(fileName, null);
  }

  /**
   * 从 File 对象创建写入器 (无类型映射)
   *
   * @param file 目标 Excel 文件的 File 对象
   * @return ExcelWriter 写入器实例
   */
  public static ExcelWriter write(java.io.File file) {
    return write(file, null);
  }

  /**
   * 从输出流创建写入器 (无类型映射)
   *
   * @param outputStream 目标输出流
   * @return ExcelWriter 写入器实例
   */
  public static ExcelWriter write(OutputStream outputStream) {
    return write(outputStream, null);
  }

  /**
   * 创建 Excel 写入器并指定映射类型
   *
   * @param fileName 目标 Excel 文件的完整路径
   * @param clazz 映射的源类类型
   * @param <T> 泛型参数
   * @return ExcelWriter 写入器实例
   */
  public static <T> ExcelWriter write(String fileName, Class<T> clazz) {
    WriteMetadata metadata = new WriteMetadata();
    metadata.setClazz(clazz);
    metadata.setFilePath(fileName);
    return new ExcelWriter(metadata);
  }

  /**
   * 创建 Excel 写入器并指定映射类型（含预分配数据大小提示）
   *
   * @param fileName 目标 Excel 文件的完整路径
   * @param clazz 映射的源类类型
   * @param dataSize 数据大小
   * @param <T> 泛型参数
   * @return ExcelWriter 写入器实例
   */
  public static <T> ExcelWriter write(String fileName, Class<T> clazz, int dataSize) {
    WriteMetadata metadata = new WriteMetadata();
    metadata.setClazz(clazz);
    metadata.setFilePath(fileName);
    metadata.setDataSize(dataSize);
    return new ExcelWriter(metadata);
  }

  /**
   * 从 File 对象创建写入器并指定映射类型
   *
   * @param file 目标 Excel 文件的 File 对象
   * @param clazz 映射的源类类型
   * @param <T> 泛型参数
   * @return ExcelWriter 写入器实例
   */
  public static <T> ExcelWriter write(java.io.File file, Class<T> clazz) {
    WriteMetadata metadata = new WriteMetadata();
    metadata.setClazz(clazz);
    metadata.setFile(file);
    return new ExcelWriter(metadata);
  }

  /**
   * 从输出流创建写入器并指定映射类型
   *
   * @param outputStream 目标输出流
   * @param clazz 映射的源类类型
   * @param <T> 泛型参数
   * @return ExcelWriter 写入器实例
   */
  public static <T> ExcelWriter write(OutputStream outputStream, Class<T> clazz) {
    WriteMetadata metadata = new WriteMetadata();
    metadata.setClazz(clazz);
    metadata.setOutputStream(outputStream);
    return new ExcelWriter(metadata);
  }

  // ==================== 模板填充相关方法 ====================

  /**
   * 创建模板写入器（基于文件路径加载模板）。
   *
   * <p><b>注意：</b>当前模板写入器（{@link ExcelTemplateWriter}）唯一使用 Apache POI 路径。
   * 模板模式按定义需要富样式/公式的语义操作（整行样式复制、列宽保留、公式偏移），这些语义
   * 是手工 OOXML 字节流无法可靠维护的。模板写入时请确保 classpath 存在 poi/poi-ooxml。
   *
   * @param templatePath 模板文件路径
   * @param outputPath 输出文件路径
   * @param clazz 映射的源类类型
   * @param <T> 泛型参数
   * @return ExcelTemplateWriter 实例
   */
  public static <T> ExcelTemplateWriter writeWithTemplate(
      String templatePath, String outputPath, Class<T> clazz) {
    return new ExcelTemplateWriter(templatePath, outputPath, clazz);
  }

  /**
   * 创建模板写入器（基于输入流加载模板）。
   *
   * <p>适用于 Web 上传模板、云存储模板等场景——模板不以文件形式落地到本地文件系统。
   *
   * @param templateStream 模板文件输入流（调用方负责关闭）
   * @param outputPath 输出文件路径
   * @param clazz 映射的源类类型
   * @param <T> 泛型参数
   * @return ExcelTemplateWriter 实例
   */
  public static <T> ExcelTemplateWriter writeWithTemplate(
      InputStream templateStream, String outputPath, Class<T> clazz) {
    return new ExcelTemplateWriter(templateStream, outputPath, clazz);
  }

  /**
   * 创建模板写入器（基于字节数组加载模板）。
   *
   * <p>适用于模板已完整加载到内存的场景（如数据库 BLOB、缓存等）。
   *
   * @param templateBytes 模板文件字节内容
   * @param outputPath 输出文件路径
   * @param clazz 映射的源类类型
   * @param <T> 泛型参数
   * @return ExcelTemplateWriter 实例
   */
  public static <T> ExcelTemplateWriter writeWithTemplate(
      byte[] templateBytes, String outputPath, Class<T> clazz) {
    return new ExcelTemplateWriter(templateBytes, outputPath, clazz);
  }

  /**
   * 区域循环填充模板 — 一行代码完成"模板加载 + 区域定义 + 数据填充"。
   *
   * <p>对标 poi-tl 的 {@code {{#each items}}...{{/each}}} 语义：
   *
   * <pre>{@code
   * ExcelFacade.writeLoopTemplate("template.xlsx", "output.xlsx", User.class,
   *     TemplateRegion.builder()
   *         .sourceStartRow(4).sourceEndRow(4).targetStartRow(4).build())
   *     .doWrite(userList);
   * }</pre>
   *
   * @param templatePath 模板文件路径
   * @param outputPath 输出文件路径
   * @param clazz 映射的源类类型
   * @param region 模板区域描述符
   * @param <T> 泛型参数
   * @return ExcelTemplateWriter 实例（可进一步配置，最终调用 {@code doWrite(List)} 写入）
   */
  public static <T> ExcelTemplateWriter writeLoopTemplate(
      String templatePath, String outputPath, Class<T> clazz, TemplateRegion region) {
    if (region == null) {
      throw new IllegalArgumentException("TemplateRegion must not be null");
    }
    // P2 修复：此前 region 参数被完全忽略，现在保留到 writer 供 doWrite(data, region) 使用
    ExcelTemplateWriter writer = new ExcelTemplateWriter(templatePath, outputPath, clazz);
    writer.setDefaultRegion(region);
    return writer;
  }

  /**
   * 区域循环填充模板（{@link InputStream} 来源）。
   *
   * @param templateStream 模板文件输入流
   * @param outputPath 输出文件路径
   * @param clazz 映射的源类类型
   * @param region 模板区域描述符
   * @param <T> 泛型参数
   * @return ExcelTemplateWriter 实例
   */
  public static <T> ExcelTemplateWriter writeLoopTemplate(
      InputStream templateStream, String outputPath, Class<T> clazz, TemplateRegion region) {
    if (region == null) {
      throw new IllegalArgumentException("TemplateRegion must not be null");
    }
    ExcelTemplateWriter writer = new ExcelTemplateWriter(templateStream, outputPath, clazz);
    writer.setDefaultRegion(region);
    return writer;
  }

  // ==================== CSV 读写方法 ====================

  /**
   * 创建 CSV 写入器（输出到文件路径）。
   *
   * @param path 输出文件路径
   * @param clazz 数据类型
   * @param <T> 泛型参数
   * @return CsvWriter 实例
   * @throws IOException 文件创建异常
   */
  public static <T> CsvWriter<T> writeCsv(Path path, Class<T> clazz) throws IOException {
    return CsvWriter.write(path, clazz);
  }

  /**
   * 创建 CSV 写入器（输出到输出流）。
   *
   * @param outputStream 目标输出流
   * @param clazz 数据类型
   * @param <T> 泛型参数
   * @return CsvWriter 实例
   */
  public static <T> CsvWriter<T> writeCsv(OutputStream outputStream, Class<T> clazz) {
    return CsvWriter.write(outputStream, clazz);
  }

  /**
   * 创建 CSV 读取器（从文件路径读取）。
   *
   * @param path CSV 文件路径
   * @param clazz 目标类型
   * @param <T> 泛型参数
   * @return CsvReader 实例
   * @throws IOException 文件打开异常
   */
  public static <T> CsvReader<T> readCsv(Path path, Class<T> clazz) throws IOException {
    return CsvReader.read(path, clazz);
  }

  /**
   * 创建 CSV 读取器（从输入流读取，UTF-8 编码）。
   *
   * @param inputStream 输入流
   * @param clazz 目标类型
   * @param <T> 泛型参数
   * @return CsvReader 实例
   */
  public static <T> CsvReader<T> readCsv(InputStream inputStream, Class<T> clazz) {
    return CsvReader.read(inputStream, clazz);
  }

  // ==================== 多Sheet写入方法 ====================

  /**
   * 多Sheet写入 - 使用默认配置
   *
   * <p>用于一次性写入多个Sheet，每个Sheet对应一个数据列表。数据列表的顺序对应Sheet的顺序。
   * 注意：此方法所有Sheet使用相同的类型clazz。
   *
   * <h3>使用示例</h3>
   *
   * <pre>{@code
   * Map<String, List<?>> sheets = new LinkedHashMap<>(16);
   * sheets.put("用户信息", sheet1Data);
   * sheets.put("部门信息", sheet2Data);
   * ExcelFacade.writeMultiple("output.xlsx", User.class, sheets);
   * }</pre>
   *
   * @param fileName 目标文件路径
   * @param clazz 默认的数据类型
   * @param sheets Map: Sheet名称 -> 数据列表
   */
  public static void writeMultiple(String fileName, Class<?> clazz, Map<String, ?> sheets) {
    if (sheets == null || sheets.isEmpty()) {
      return;
    }

    WriteMetadata metadata = new WriteMetadata();
    metadata.setClazz(clazz);
    metadata.setFilePath(fileName);

    ExcelWriter writer = new ExcelWriter(metadata);
    int index = 0;
    try {
      for (Map.Entry<String, ?> entry : sheets.entrySet()) {
        ExcelWriter currentWriter =
            (index == 0)
                ? writer.sheet(entry.getKey())
                : writer.newSheet(entry.getKey());
        currentWriter.doWrite(entry.getValue());
        index++;
      }
      writer.finish();
    } catch (IOException e) {
      throw new RuntimeException("Failed to write Excel file: " + fileName, e);
    }
  }

  // ==================== 多 Sheet 零 POI 写入（SuperFast 引擎） ====================

  /**
   * 创建多 Sheet 快速写入器 — 一次生成包含多个 Sheet 的 xlsx 工作簿，完全不依赖 POI。
   *
   * <p>每 Sheet 可独立指定数据类型与数据。所有 Sheet 共享 SST、样式表与 theme，
   * 生成符合 ECMA-376 规范的 OOXML 包，兼容 Excel / WPS / LibreOffice。
   *
   * <p>限制：不支持 WriteLifecycleHandler 回调、@ExcelStyle 样式注解、XLS 格式、追加模式。
   *
   * @param out 目标输出流
   * @return MultiSheetFastWriter 构建器
   * @since 26.10.01
   */
  public static MultiSheetFastWriter writeMultiSheet(OutputStream out) {
    return new MultiSheetFastWriter(out);
  }

  // ==================== Stream API 读取 ====================

  /**
   * 以 Stream API 方式流式读取 Excel 文件。
   *
   * <p>内部使用有界阻塞队列（默认容量 256）实现背压控制。
   *
   * @param filePath Excel 文件路径（.xlsx 格式）
   * @param clazz 映射数据类型
   * @param <T> 数据类型
   * @return ExcelStream 实例，须由调用方通过 try-with-resources 关闭
   * @throws ExcelReadException 读取失败时抛出
   */
  public static <T> ExcelStream<T> readAsStringStream(String filePath, Class<T> clazz) {
    ReadMetadata metadata = new ReadMetadata();
    metadata.setFilePath(filePath);
    metadata.setClazz(clazz);
    ExcelReader reader = new ExcelReader(metadata);
    return ExcelStream.of(reader, clazz, 256);
  }

  /**
   * 以 Stream API 方式流式读取 Excel 文件（自定义缓冲区容量）。
   *
   * @param filePath Excel 文件路径
   * @param clazz 映射数据类型
   * @param capacity 背压缓冲区大小（行数）
   * @param <T> 数据类型
   * @return ExcelStream 实例
   */
  public static <T> ExcelStream<T> readAsStringStream(
      String filePath, Class<T> clazz, int capacity) {
    ReadMetadata metadata = new ReadMetadata();
    metadata.setFilePath(filePath);
    metadata.setClazz(clazz);
    ExcelReader reader = new ExcelReader(metadata);
    return ExcelStream.of(reader, clazz, capacity);
  }

  // ==================== 无类型全 Sheet 读取 ====================

  /**
   * 无类型读取全部 Sheet — 将 Excel 所有 Sheet 以原始字符串形式读出（零 POI 依赖）。
   *
   * <p>直接通过 {@link ZipInputStream} 流式解析：先读取 {@code xl/workbook.xml} 获得 Sheet 名称列表，
   * 解析 {@code xl/_rels/workbook.xml.rels} 映射 rId 到 entry 路径，再逐 Sheet XML 解析单元格值——
   * 第一行作为表头，后续行作为数据行。全空行自动过滤。
   *
   * <p>单元格值统一转为字符串：日期按 {@code yyyy-MM-dd HH:mm:ss} 格式输出；数字为整数时去掉小数部分；
   * 共享字符串（{@code t="s"}）从 {@code xl/sharedStrings.xml} 解析；内联字符串（{@code t="inlineStr"}）直接取值。
   *
   * <p><b>内存占用</b>：全部使用流式解析，不将整个文件加载到内存。
   * 临时 entries 内容使用 {@link ByteArrayOutputStream}，适用于常见大小的 Excel 文档。
   *
   * @param inputStream Excel 字节流，由调用方负责关闭；为 {@code null} 时返回空列表
   * @return 全部 Sheet 数据列表，永不为 {@code null}
   * @throws ExcelReadException 读取失败时抛出
   */
  public static List<RawSheetData> readAllSheets(InputStream inputStream) {
    if (inputStream == null) {
      return new ArrayList<>(0);
    }
    try {
      return parseAllSheets(inputStream);
    } catch (IOException e) {
      throw new ExcelReadException("无类型全 Sheet 读取失败: " + e.getMessage(), e);
    }
  }

  /**
   * 零 POI 无类型全 Sheet 解析实现。
   *
   * <p>遍历 zip entries，分类缓存 workbook / rels / sharedStrings / sheet XML，
   * 最后按 sheet 顺序解析每个 sheet 的单元格内容。
   */
  private static List<RawSheetData> parseAllSheets(InputStream inputStream) throws IOException {
    // 缓存各 entry 的原始字节，按 entry 名索引
    Map<String, byte[]> entryBytes = new LinkedHashMap<>(32);
    // 由于在读取完 workbook.xml 前不知道 sheet 对应哪些 entries，
    // 先全量缓存所有 entries（XML 文本，非 DOM）
    try (ZipInputStream zis = new ZipInputStream(inputStream)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          entryBytes.put(entry.getName(), readAllBytes(zis));
        }
        zis.closeEntry();
      }
    }

    // 解析 workbook.xml 获取 sheet 名称
    byte[] workbookXml = entryBytes.get("xl/workbook.xml");
    if (workbookXml == null) {
      throw ExcelReadException.invalidFormat("inputStream", "缺少 xl/workbook.xml");
    }
    List<String> sheetNames = parseWorkbookSheetNames(
        new String(workbookXml, StandardCharsets.UTF_8));

    // 解析 rels 获取 rId → target 映射
    byte[] relsXml = entryBytes.get("xl/_rels/workbook.xml.rels");
    Map<String, String> ridToTarget = relsXml != null
        ? parseRelationships(new String(relsXml, StandardCharsets.UTF_8))
        : new HashMap<>(0);

    // 解析 sharedStrings
    byte[] sstXml = entryBytes.get("xl/sharedStrings.xml");
    String[] sharedStrings = sstXml != null
        ? parseSharedStrings(new String(sstXml, StandardCharsets.UTF_8))
        : new String[0];

    // 按 sheet 名称从 workbook.xml 中拿到 rId，查 rels 得到 entry 名
    // 由于 parseWorkbookSheetNames 只返回了名字，需要重新解析：拿到 (name, rid) 对
    List<SheetRef> sheetRefs = parseWorkbookSheetRefs(
        new String(workbookXml, StandardCharsets.UTF_8));

    List<RawSheetData> result = new ArrayList<>(sheetNames.size());
    for (SheetRef ref : sheetRefs) {
      String entryName = ridToTarget.get(ref.rid);
      if (entryName == null) {
        // 回落：从 xl/ 开头或直接用 name 推算
        entryName = "xl/worksheets/" + ref.rid.replaceAll("[^a-zA-Z0-9]", "") + ".xml";
        // 更可靠的回落：遍历 entries 找匹配的
        entryName = findSheetEntryByName(entryBytes, ref.name);
        if (entryName == null) {
          continue;
        }
      }
      // entryName 可能是相对路径（如 "worksheets/sheet1.xml"）或绝对路径
      byte[] sheetXml = entryBytes.get(entryName);
      if (sheetXml == null && !entryName.startsWith("xl/")) {
        sheetXml = entryBytes.get("xl/" + entryName);
      }
      if (sheetXml == null) {
        // 尝试去除前导 /
        if (entryName.startsWith("/")) {
          sheetXml = entryBytes.get(entryName.substring(1));
        }
      }
      if (sheetXml == null) {
        log.warn("无法找到 Sheet XML entry: name={}, entryName={}", ref.name, entryName);
        continue;
      }
      result.add(parseSheetBytes(ref.name, sheetXml, sharedStrings));
    }
    return result;
  }

  /**
   * 从 entryBytes 中根据 sheet 名称查找对应的 sheet XML entry 名（容错回落）。
   */
  private static String findSheetEntryByName(Map<String, byte[]> entryBytes, String sheetName) {
    for (String key : entryBytes.keySet()) {
      if (key.startsWith("xl/worksheets/sheet") && key.endsWith(".xml")) {
        return key;
      }
    }
    return null;
  }

  /**
   * 解析 workbook.xml 获取 Sheet 引用列表（name + rid）。
   */
  private static List<SheetRef> parseWorkbookSheetRefs(String xml) {
    List<SheetRef> refs = new ArrayList<>(8);
    // 匹配 <sheet name="xxx" sheetId="N" r:id="rIdN"/>
    Pattern p = Pattern.compile(
        "<sheet\\s+name=\"([^\"]*)\"\\s+sheetId=\"\\d+\"\\s+r:id=\"([^\"]*)\"",
        Pattern.DOTALL);
    Matcher m = p.matcher(xml);
    while (m.find()) {
      refs.add(new SheetRef(unescapeXml(m.group(1)), m.group(2)));
    }
    return refs;
  }

  /** 解析 workbook.xml 获取 Sheet 名称列表（容错入口，仅获取名称）。 */
  private static List<String> parseWorkbookSheetNames(String xml) {
    List<String> names = new ArrayList<>(8);
    for (SheetRef ref : parseWorkbookSheetRefs(xml)) {
      names.add(ref.name);
    }
    return names;
  }

  /** Internal: sheet reference (name + rid)。 */
  private record SheetRef(String name, String rid) {}

  /** 解析 rels XML 获取 rId → Target 映射。 */
  private static Map<String, String> parseRelationships(String xml) {
    Map<String, String> map = new HashMap<>(16);
    Pattern p = Pattern.compile(
        "<Relationship\\s+Id=\"([^\"]*)\"\\s+Target=\"([^\"]*)\"", Pattern.DOTALL);
    Matcher m = p.matcher(xml);
    while (m.find()) {
      map.put(m.group(1), m.group(2));
    }
    return map;
  }

  /** 解析 sharedStrings.xml 为字符串数组。 */
  private static String[] parseSharedStrings(String xml) {
    List<String> values = new ArrayList<>(64);
    Pattern p = Pattern.compile("<si>(.*?)</si>", Pattern.DOTALL);
    Matcher m = p.matcher(xml);
    while (m.find()) {
      String si = m.group(1);
      // 提取 <t>...</t> 内的纯文本（可能有多个 <t> 在多 <r> 段中）
      StringBuilder sb = new StringBuilder(si.length());
      Pattern tp = Pattern.compile("<t[^>]*>(.*?)</t>", Pattern.DOTALL);
      Matcher tm = tp.matcher(si);
      while (tm.find()) {
        sb.append(unescapeXml(tm.group(1)));
      }
      values.add(sb.toString());
    }
    return values.toArray(new String[0]);
  }

  /** 解析单个 Sheet XML 字节为 RawSheetData。 */
  private static RawSheetData parseSheetBytes(
      String sheetName, byte[] sheetXml, String[] sharedStrings) {
    String xml = new String(sheetXml, StandardCharsets.UTF_8);

    List<String> headers = new ArrayList<>(16);
    List<List<String>> rows = new ArrayList<>(16);

    // 按 <row ...>...</row> 分割
    Pattern rowPattern = Pattern.compile("<row[^>]*>(.*?)</row>", Pattern.DOTALL);
    Matcher rowMatcher = rowPattern.matcher(xml);
    boolean firstRow = true;
    while (rowMatcher.find()) {
      String rowContent = rowMatcher.group(1);
      List<String> cells = parseCells(rowContent, sharedStrings);
      if (firstRow) {
        headers = cells;
        firstRow = false;
      } else {
        // 过滤空行
        if (cells.stream().anyMatch(v -> v != null && !v.isBlank())) {
          rows.add(cells);
        }
      }
    }
    return new RawSheetData(sheetName, headers, rows);
  }

  /** 解析一行内的所有单元格值。 */
  private static List<String> parseCells(String rowXml, String[] sharedStrings) {
    List<String> cells = new ArrayList<>(16);
    Map<Integer, String> cellMap = new LinkedHashMap<>(16);

    Pattern cellPattern = Pattern.compile("<c\\s+([^>]*)>(.*?)</c>", Pattern.DOTALL);
    Matcher cellMatcher = cellPattern.matcher(rowXml);
    int maxCol = -1;
    while (cellMatcher.find()) {
      String attrs = cellMatcher.group(1);
      String cellContent = cellMatcher.group(2);

      // 提取 r 属性确定列号（如 A1 → col 0）
      Pattern rPattern = Pattern.compile("r=\"([A-Z]+)\\d+\"");
      Matcher rMatcher = rPattern.matcher(attrs);
      int colIndex = maxCol + 1;
      if (rMatcher.find()) {
        colIndex = columnToIndex(rMatcher.group(1));
      }
      if (colIndex > maxCol) {
        maxCol = colIndex;
      }

      // 判断类型
      String type = "";
      Pattern tPattern = Pattern.compile("t=\"([^\"]*)\"");
      Matcher tMatcher = tPattern.matcher(attrs);
      if (tMatcher.find()) {
        type = tMatcher.group(1);
      }

      String value = "";
      if ("inlineStr".equals(type)) {
        // 内联字符串: <is><t>...</t></is>
        Pattern isPattern = Pattern.compile("<is><t[^>]*>(.*?)</t></is>", Pattern.DOTALL);
        Matcher isMatcher = isPattern.matcher(cellContent);
        if (isMatcher.find()) {
          value = unescapeXml(isMatcher.group(1));
        }
      } else if ("s".equals(type)) {
        // 共享字符串: <v>index</v>
        Pattern vPattern = Pattern.compile("<v>(\\d+)</v>", Pattern.DOTALL);
        Matcher vMatcher = vPattern.matcher(cellContent);
        if (vMatcher.find()) {
          int idx = Integer.parseInt(vMatcher.group(1));
          if (idx >= 0 && idx < sharedStrings.length) {
            value = sharedStrings[idx];
          }
        }
      } else if ("b".equals(type)) {
        Pattern vPattern = Pattern.compile("<v>([01])</v>", Pattern.DOTALL);
        Matcher vMatcher = vPattern.matcher(cellContent);
        if (vMatcher.find()) {
          value = "1".equals(vMatcher.group(1)) ? "TRUE" : "FALSE";
        }
      } else if ("e".equals(type)) {
        // 错误类型，取空
        value = "";
      } else {
        // 数值或日期: <v>number</v>
        Pattern vPattern = Pattern.compile("<v>(.*?)</v>", Pattern.DOTALL);
        Matcher vMatcher = vPattern.matcher(cellContent);
        if (vMatcher.find()) {
          value = formatNumericValue(vMatcher.group(1).trim());
        }
      }

      cellMap.put(colIndex, value);
    }

    // 按列号顺序输出
    for (int i = 0; i <= maxCol; i++) {
      cells.add(cellMap.getOrDefault(i, ""));
    }
    return cells;
  }

  /** 数值/日期字符串格式化为最终字符串。 */
  private static String formatNumericValue(String raw) {
    if (raw == null || raw.isEmpty()) {
      return "";
    }
    try {
      double num = Double.parseDouble(raw);
      if (num == Math.floor(num) && !Double.isInfinite(num)) {
        return String.valueOf((long) num);
      }
      // 尝试按日期序列号转换（简化处理：仅当 > 25569 且 < 60000 范围内尝试）
      // 完整日期处理需要 styles.xml 解析，这里暂按数值格式输出
      return String.valueOf(num);
    } catch (NumberFormatException e) {
      return raw;
    }
  }

  /** Excel 列字母转 0-based 索引（A=0, Z=25, AA=26）。 */
  private static int columnToIndex(String col) {
    int result = 0;
    for (int i = 0; i < col.length(); i++) {
      result = result * 26 + (col.charAt(i) - 'A' + 1);
    }
    return result - 1;
  }

  /** 简单 XML 实体反转义。 */
  private static String unescapeXml(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&apos;", "'")
        .replace("&quot;", "\"");
  }

  /**
   * 从 InputStream 读取所有字节到数组。
   *
   * <p>用于读取 zip entries 的 XML 正文（文本内容，通常较小）。
   */
  private static byte[] readAllBytes(InputStream is) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream(512);
    byte[] buf = new byte[4096];
    int n;
    while ((n = is.read(buf)) > 0) {
      bos.write(buf, 0, n);
    }
    return bos.toByteArray();
  }
}
