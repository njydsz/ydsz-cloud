new ArrayList<>(16).excel.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

import com.njydsz.common.excel.core.metadata.ReadMetadata;
import com.njydsz.common.excel.core.metadata.WriteMetadata;
import com.njydsz.common.excel.core.model.SheetData;
import com.njydsz.common.excel.exception.ExcelReadException;
import com.njydsz.common.excel.exception.ExcelWriteException;

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

    List<SheetData> sheetDataList = new ArrayList<>();
    for (Map.Entry<String, ?> entry : sheets.entrySet()) {
      SheetData sheetData = new SheetData(entry.getKey(), clazz, entry.getValue());
      sheetDataList.add(sheetData);
    }

    writeMultiple(fileName, sheetDataList);
  }

  /**
   * 多Sheet写入 - 使用SheetData列表
   *
   * <p>每个SheetData可以单独指定:
   *
   * <ul>
   *   <li>Sheet名称
   *   <li>数据类型
   *   <li>数据列表
   *   <li>表头行号
   * </ul>
   *
   * @param fileName 目标文件路径
   * @param sheetDataList Sheet数据列表
   */
  public static void writeMultiple(String fileName, List<SheetData> sheetDataList) {
    if (sheetDataList == null || sheetDataList.isEmpty()) {
      return;
    }

    ExcelWriter firstWriter = null;
    int sheetIndex = 0;

    for (SheetData sheetData : sheetDataList) {
      ExcelWriter writer;
      if (sheetIndex == 0) {
        firstWriter = write(fileName, sheetData.getClazz());
        firstWriter.setMultiSheetWriting(true);
        firstWriter.sheet(sheetData.getSheetName() != null ? sheetData.getSheetName() : "sheet1");
        writer = firstWriter;
      } else {
        if (firstWriter == null) {
          throw new IllegalStateException("ExcelWriter 未初始化，多Sheet写入必须从第一个Sheet开始");
        }
        writer =
            firstWriter.newSheet(
                sheetData.getSheetName() != null
                    ? sheetData.getSheetName()
                    : "sheet" + (sheetIndex + 1));
      }

      if (sheetData.getHeadRowNumber() != null) {
        writer.headRowNumber(sheetData.getHeadRowNumber());
      }

      writer.doWrite(sheetData.getData(), sheetIndex);
      sheetIndex++;
    }

    if (firstWriter != null) {
      try {
        firstWriter.finish();
      } catch (IOException e) {
        throw new ExcelWriteException(
            "多Sheet写入失败: error=" + e.getMessage(), e);
      }
    }
  }

  // ==================== 模板写入 ====================

  /**
   * 基于模板写入Excel
   *
   * <p>将数据写入已有的Excel模板文件，保留模板中的样式、格式、公式等设置
   *
   * @param templatePath 模板文件路径
   * @param outputPath 输出文件路径
   * @param clazz 数据类型
   * @return 模板写入器实例
   */
  public static ExcelTemplateWriter writeWithTemplate(
      String templatePath, String outputPath, Class<?> clazz) {
    return new ExcelTemplateWriter(templatePath, outputPath, clazz);
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
   * <p><b>注意：</b>本方法使用 DOM 方式载入工作簿，大文件内存占用约为文件体积的数倍。 调用方应按 {@code maxFileSizeMb} 提前拦截超大文件。
   *
   * <h3>使用示例</h3>
   *
   * <pre>{@code
   * List<RawSheetData> sheets = ExcelFacade.readAllSheets(inputStream);
   * for (RawSheetData sheet : sheets) {
   *     System.out.println("Sheet: " + sheet.sheetName());
   *     System.out.println("Headers: " + sheet.headers());
   *     for (List<String> row : sheet.rows()) {
   *         System.out.println(row);
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
