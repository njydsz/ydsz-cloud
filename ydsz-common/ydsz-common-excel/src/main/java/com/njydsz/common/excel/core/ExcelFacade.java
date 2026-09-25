package com.njydsz.common.excel.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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
 * <p>封装读取 / 写入 / 模板填充 / 多 Sheet / Web 下载等全部能力。 参照阿里巴巴 EasyExcel 的设计理念，注重性能优化和低内存占用。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 读取
 * ExcelFacade.read("demo.xlsx", User.class)
 *     .sheet("用户数据")
 *     .doRead(new ReadListener<User>() {
 *         @Override
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
 * @version 26.09.01
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
   * @param fileName Excel 文件的完整路径，支持.xlsx 和.xls 格式
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
  public static ExcelReader read(File file) {
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
  public static <T> ExcelReader read(File file, Class<T> clazz) {
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
  public static ExcelWriter write(File file) {
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
   * 创建 Excel 写入器并指定映射类型
   * @param fileName 目标 Excel 文件的完整路径
   * @param clazz 映射的源类类型
   * @param <T> 泛型参数
   * @return ExcelWriter 写入器实例
   *
   * @param dataSize 数据大小
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
  public static <T> ExcelWriter write(File file, Class<T> clazz) {
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
    return new ExcelTemplateWriter(templatePath, outputPath, clazz);
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
    return new ExcelTemplateWriter(templateStream, outputPath, clazz);
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
   * <p>用于一次性写入多个Sheet，每个Sheet对应一个数据列表。 数据列表的顺序对应Sheet的顺序。 注意: 此方法所有Sheet使用相同的类型clazz。
   *
   * <h3>使用示例</h3>
   *
   * <pre>{@code
   * List<Object> sheet1Data = ...; // 第一个Sheet的数据
   * List<Object> sheet2Data = ...; // 第二个Sheet的数据
   *
   * // 使用Map指定每个Sheet的名称和数据
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
   * <p>每 Sheet 可独立指定数据类型与数据。所有 Sheet 共享 SST、样式表与 theme， 生成符合 ECMA-376 规范的
   * OOXML 包，兼容 Excel / WPS / LibreOffice。
   *
   * <p>示例：
   *
   * <pre>{@code
   * ExcelFacade.writeMultiSheet(outputStream)
   *     .sheet("用户", User.class, users)
   *     .sheet("部门", Department.class, departments)
   *     .finish();
   * }</pre>
   *
   * <p>限制：不支持 WriteLifecycleHandler 回调、@ExcelStyle 样式注解、XLS 格式、追加模式。
   * 需要这些能力时请使用 {@link #write(OutputStream, Class)} + {@link ExcelWriter#newSheet()}。
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
   * 以 Stream API 方式流式读取 Excel 文件，适用于大数据量场景和与 Reactor / Spring Batch 等框架集成。
   *
   * <p>示例：
   *
   * <pre>{@code
   * try (ExcelStream<User> stream = ExcelFacade.readAsStream("demo.xlsx", User.class)) {
   *   List<String> names = stream.stream()
   *       .filter(u -> u.getAge() != null && u.getAge() > 18)
   *       .map(User::getName)
   *       .limit(1000)
   *       .collect(Collectors.toList());
   * }
   * }</pre>
   *
   * <p>内部使用有界阻塞队列（默认容量 256）实现背压控制，解析线程在缓冲区满时自动阻塞，避免内存溢出。
   *
   * @param filePath Excel 文件路径（.xlsx 或 .xls）
   * @param clazz 映射数据类型
   * @param <T> 数据类型
   * @return ExcelStream 实例，须由调用方通过 try-with-resources 关闭
   * @throws ExcelReadException 读取失败时抛出
   * @see ExcelStream
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
  public static <T> ExcelStream<T> readAsStringStream(String filePath, Class<T> clazz, int capacity) {
    ReadMetadata metadata = new ReadMetadata();
    metadata.setFilePath(filePath);
    metadata.setClazz(clazz);
    ExcelReader reader = new ExcelReader(metadata);
    return ExcelStream.of(reader, clazz, capacity);
  }

  // ==================== 无类型全 Sheet 读取 ====================

  /**
   * 无类型读取全部 Sheet — 将 Excel 所有 Sheet 以原始字符串形式读出。
   *
   * <p>适用于无 VO 映射的文档解析场景（如 {@code ydsz-common-docs} 的 Excel 解析器）， 统一处理 HSSF/XSSF
   * 格式识别、空行过滤、单元格值转字符串， 消除业务模块直接使用 {@link WorkbookFactory} 的 POI DOM 模式。
   *
   * <p>第一行作为表头（{@code headers}），后续行作为数据行（{@code rows}）。 全空行自动过滤。单元格值统一转为字符串，日期按 {@code yyyy-MM-dd
   * HH:mm:ss} 格式输出， 数字为整数时去掉小数部分。
   *
   * <p><b>警告：</b>本方法使用 POI DOM 方式（{@link WorkbookFactory#create}）全量载入工作簿，
   * 大文件内存占用约为文件体积的 3-5 倍。超大文件场景请通过
   * {@link #read(InputStream)} + {@link ExcelReader#doRead(ReadListener)} 逐 Sheet 流式消费。
   *
   * <h3>使用示例</h3>
   *
   * <pre>{@code
   * List<RawSheetData> sheets = ExcelFacade.readAllSheets(inputStream);
   * for (RawSheetData sheet : sheets) {
   *     log.debug("Sheet={}, Headers={}", sheet.sheetName(), sheet.headers());
   *     for (List<String> row : sheet.rows()) {
   *         log.debug("row={}", row);
   *     }
   * }
   * }</pre>
   *
   * @param inputStream Excel 字节流，由调用方负责关闭；为 {@code null} 时返回空列表
   * @return 全部 Sheet 数据列表，永不为 {@code null}
   * @throws ExcelReadException 读取失败时抛出
   */
  public static List<RawSheetData> readAllSheets(InputStream inputStream) {
    if (inputStream == null) {
      return new ArrayList<>(0);
    }
    try (Workbook workbook = WorkbookFactory.create(inputStream)) {
      List<RawSheetData> result = new ArrayList<>(16);
      int sheetCount = workbook.getNumberOfSheets();
      for (int i = 0; i < sheetCount; i++) {
        Sheet sheet = workbook.getSheetAt(i);
        result.add(parseSheetData(sheet));
      }
      return result;
    } catch (IOException e) {
      throw new ExcelReadException("无类型全 Sheet 读取失败", e);
    }
  }

  /**
   * 将单个 Sheet 解析为 {@link RawSheetData}。
   *
   * <p>第一行作为表头，后续行作为数据行。全空行自动过滤。
   *
   * @param sheet POI Sheet 对象
   * @return 解析后的 Sheet 数据
   */
  private static RawSheetData parseSheetData(Sheet sheet) {
    String sheetName = sheet.getSheetName();
    List<String> headers = new ArrayList<>(16);
    List<List<String>> rows = new ArrayList<>(16);

    int rowCount = sheet.getPhysicalNumberOfRows();
    if (rowCount == 0) {
      return new RawSheetData(sheetName, headers, rows);
    }

    // 第一行作为表头
    Row headerRow = sheet.getRow(0);
    if (headerRow != null) {
      int lastCol = headerRow.getLastCellNum();
      for (int c = 0; c < lastCol; c++) {
        Cell cell = headerRow.getCell(c);
        headers.add(cell != null ? convertCellValueToString(cell) : "");
      }
    }

    // 后续行作为数据行
    for (int r = 1; r < rowCount; r++) {
      Row row = sheet.getRow(r);
      if (row == null) {
        continue;
      }
      List<String> cells = new ArrayList<>(16);
      int lastCol = row.getLastCellNum();
      for (int c = 0; c < lastCol; c++) {
        Cell cell = row.getCell(c);
        cells.add(cell != null ? convertCellValueToString(cell) : "");
      }
      // 过滤空行
      if (cells.stream().anyMatch(v -> v != null && !v.isBlank())) {
        rows.add(cells);
      }
    }

    return new RawSheetData(sheetName, headers, rows);
  }

  /**
   * 将 POI 单元格值转换为字符串。
   *
   * <p>处理常见单元格类型（字符串、数字、布尔、公式）， 日期按 {@code yyyy-MM-dd HH:mm:ss} 格式输出。数字为整数时去掉小数部分。
   *
   * @param cell POI 单元格对象
   * @return 单元格值的字符串表示；永不为 {@code null}
   */
  private static String convertCellValueToString(Cell cell) {
    if (cell == null) {
      return "";
    }
    CellType cellType = cell.getCellType();
    return switch (cellType) {
      case STRING -> {
        String value = cell.getStringCellValue();
        yield value != null ? value.trim() : "";
      }
      case NUMERIC -> {
        if (DateUtil.isCellDateFormatted(cell)) {
          yield DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
              .format(cell.getLocalDateTimeCellValue());
        }
        double num = cell.getNumericCellValue();
        if (num == Math.floor(num) && !Double.isInfinite(num)) {
          yield String.valueOf((long) num);
        }
        yield String.valueOf(num);
      }
      case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
      case FORMULA -> {
        try {
          String cached = cell.getStringCellValue();
          yield cached != null ? cached : "";
        } catch (Exception e) {
          double num = cell.getNumericCellValue();
          if (num == Math.floor(num) && !Double.isInfinite(num)) {
            yield String.valueOf((long) num);
          }
          yield String.valueOf(num);
        }
      }
      default -> "";
    };
  }
}
