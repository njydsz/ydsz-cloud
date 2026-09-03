package com.njydsz.common.excel.core;

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

    WriteMetadata metadata = new WriteMetadata();
    metadata.setClazz(clazz);
    metadata.setFilePath(fileName);

    ExcelWriter writer = new ExcelWriter(metadata);
    int index = 0;
    for (Map.Entry<String, ?> entry : sheets.entrySet()) {
      ExcelWriter currentWriter =
          (index == 0)
              ? writer.sheet(entry.getKey())
              : writer.newSheet(entry.getKey());
      currentWriter.doWrite(entry.getValue());
      index++;
    }
    writer.finish();
  }
}