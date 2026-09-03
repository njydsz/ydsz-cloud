package com.njydsz.common.excel.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.njydsz.common.excel.core.metadata.ReadMetadata;
import com.njydsz.common.excel.core.metadata.WriteMetadata;
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
   * 读取 Excel 所有 Sheet 数据，返回 {@link RawSheetData} 列表。
   *
   * <p>使用 POI DOM 模式（{@link Workbook}）一次性载入所有 Sheet， 每张 Sheet 的第一行为表头，后续行为数据行，全空行自动过滤。 适用于小文件的多
   * Sheet 读取场景，大文件注意内存占用。
   *
   * @param inputStream Excel 输入流（调用方负责关闭）
   * @return 所有 Sheet 的原始数据列表，每张非空 Sheet 对应一个 {@link RawSheetData}
   */
  public static List<RawSheetData> readAllSheets(InputStream inputStream) {
    if (inputStream == null) {
      return List.of();
    }
    try (Workbook workbook = createWorkbook(inputStream)) {
      List<RawSheetData> result = new ArrayList<>(workbook.getNumberOfSheets());
      for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
        Sheet sheet = workbook.getSheetAt(i);
        if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
          continue;
        }
        String sheetName = sheet.getSheetName();
        List<String> headers = new ArrayList<>(16);
        List<List<String>> rows = new ArrayList<>(32);

        for (Row row : sheet) {
          if (row == null) {
            continue;
          }
          List<String> cells = new ArrayList<>(row.getLastCellNum());
          boolean allEmpty = true;
          for (int cn = 0; cn < row.getLastCellNum(); cn++) {
            Cell cell = row.getCell(cn, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            String value = cellToString(cell);
            cells.add(value);
            if (value != null && !value.isBlank()) {
              allEmpty = false;
            }
          }
          if (allEmpty) {
            continue;
          }
          if (headers.isEmpty()) {
            headers = cells;
          } else {
            rows.add(cells);
          }
        }
        result.add(new RawSheetData(sheetName, headers, rows));
      }
      return result;
    } catch (IOException e) {
      throw new com.njydsz.common.excel.exception.ExcelReadException(
          "Failed to read Excel sheets from input stream", e);
    }
  }

  /**
   * 根据输入流创建对应格式的 Workbook（.xlsx 或 .xls）。
   */
  private static Workbook createWorkbook(InputStream inputStream) throws IOException {
    try {
      return new XSSFWorkbook(inputStream);
    } catch (Exception e) {
      return new HSSFWorkbook(inputStream);
    }
  }

  /**
   * 将单元格值转为字符串。
   */
  private static String cellToString(Cell cell) {
    if (cell == null) {
      return "";
    }
    CellType cellType = cell.getCellType();
    switch (cellType) {
      case STRING:
        return cell.getStringCellValue();
      case NUMERIC:
        double numeric = cell.getNumericCellValue();
        if (numeric == Math.floor(numeric) && !Double.isInfinite(numeric)) {
          return String.valueOf((long) numeric);
        }
        return String.valueOf(numeric);
      case BOOLEAN:
        return String.valueOf(cell.getBooleanCellValue());
      case FORMULA:
        try {
          return cell.getStringCellValue();
        } catch (Exception e) {
          return String.valueOf(cell.getNumericCellValue());
        }
      default:
        return "";
    }
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
