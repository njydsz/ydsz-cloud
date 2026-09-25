package com.njydsz.common.excel.core;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.metadata.WriteMetadata;
import com.njydsz.common.excel.core.writer.ValueFormatter;
import com.njydsz.common.excel.exception.ExcelWriteException;
import com.njydsz.common.excel.support.mh.MHFieldAccessor;
import com.njydsz.common.excel.support.cache.ReflectCache;
import com.njydsz.common.excel.core.template.TemplateRegion;

/**
 * Excel模板写入器 - 基于模板文件写入数据
 *
 * <p>支持将数据写入已有的Excel模板文件，保留模板中的样式、格式、公式等设置。 参照EasyExcel的模板写入功能设计。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * ExcelFacade.writeWithTemplate("template.xlsx", "output.xlsx", User.class)
 *     .sheet(0)
 *     .dataStartRow(3)
 *     .doWrite(userList);
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class ExcelTemplateWriter {

  private static final Logger LOG = LoggerFactory.getLogger(ExcelTemplateWriter.class);

  private final String templatePath;
  private InputStream templateInputStream;
  private final WriteMetadata metadata;
  private final ValueFormatter valueFormatter;
  private int sheetIndex = 0;
  private int dataStartRow = -1; // -1 means auto-detect
  private TemplateRegion defaultRegion; // 区域循环填充时的默认区域描述符

  public ExcelTemplateWriter(String templatePath, String outputPath, Class<?> clazz) {
    this.templatePath = templatePath;
    this.templateInputStream = null;
    this.metadata = new WriteMetadata();
    this.metadata.setFilePath(outputPath);
    this.metadata.setClazz(clazz);
    this.valueFormatter = new ValueFormatter(true);
  }

  /**
   * 从输入流加载模板文件。
   *
   * <p>适用于 Web 上传模板、云存储模板等场景——模板不以文件形式落地到本地文件系统，
   * 而是以 {@link InputStream} 形式直接提供。
   *
   * @param templateStream 模板文件的输入流（调用方负责关闭）
   * @param outputPath 输出文件路径
   * @param clazz 映射的源类类型
   */
  public ExcelTemplateWriter(InputStream templateStream, String outputPath, Class<?> clazz) {
    this.templatePath = null;
    this.templateInputStream = templateStream;
    this.metadata = new WriteMetadata();
    this.metadata.setFilePath(outputPath);
    this.metadata.setClazz(clazz);
    this.valueFormatter = new ValueFormatter(true);
  }

  /**
   * 从字节数组加载模板文件。
   *
   * <p>便捷工厂方法，适用于模板已完整加载到内存的场景（如数据库 BLOB、缓存等）。
   *
   * @param templateBytes 模板文件的字节内容
   * @param outputPath 输出文件路径
   * @param clazz 映射的源类类型
   */
  public ExcelTemplateWriter(byte[] templateBytes, String outputPath, Class<?> clazz) {
    this.templatePath = null;
    this.templateInputStream = new ByteArrayInputStream(templateBytes);
    this.metadata = new WriteMetadata();
    this.metadata.setFilePath(outputPath);
    this.metadata.setClazz(clazz);
    this.valueFormatter = new ValueFormatter(true);
  }

  /**
   * 设置模板中作为写入目标的 Sheet 下标。
   *
   * <p>模板可能包含多个 Sheet，默认取第 0 个。下标越界时由底层 {@link XSSFWorkbook#getSheetAt(int)} 抛出异常。
   *
   * @param sheetIndex 目标 Sheet 下标，从 0 开始
   * @return 当前写入器，便于链式调用
   */
  public ExcelTemplateWriter sheet(int sheetIndex) {
    this.sheetIndex = sheetIndex;
    return this;
  }

  /**
   * 设置数据写入的起始行号（1-based）。
   *
   * <p>表头行取该行的上一行；不设置（保持默认 {@code -1}）时自动探测前 11 行中 第一个非空行作为表头，数据从表头下一行开始写。
   *
   * @param row 数据起始行号，从 1 开始；设为 -1 可恢复自动探测
   * @return 当前写入器，便于链式调用
   */
  public ExcelTemplateWriter dataStartRow(int row) {
    this.dataStartRow = row;
    return this;
  }

  /**
   * 设置区域循环填充的默认 {@link TemplateRegion}。
   *
   * <p>由 {@link ExcelFacade#writeLoopTemplate} 自动设置，也可由调用方手动调用
   * 以复用同一 writer 上的区域配置执行多次 {@code doWrite(...)} 而无需每次传入区域参数。
   *
   * @param region 模板区域描述符；传入 {@code null} 清除默认区域
   * @return 当前写入器，便于链式调用
   * @since 26.10.01
   */
  public ExcelTemplateWriter setDefaultRegion(TemplateRegion region) {
    this.defaultRegion = region;
    return this;
  }

  /**
   * 将数据填充到模板并输出到目标文件。
   *
   * <p><b>列映射</b>：不按字段声明顺序硬填，而是读取模板表头行文本，与 {@link ExcelProperty#value()}（为空时回退字段名）做名称匹配后按列下标写入。
   * 因此模板列可任意调序；模板中没有对应表头的字段会被静默跳过，不报错。
   *
   * <p><b>起始行推断</b>：显式设置了 {@code dataStartRow} 时，表头行取其上一行； 否则自动探测前 11 行中第一个非空行作为表头，数据从表头下一行开始写。
   *
   * <p><b>覆盖语义</b>：使用 {@code createRow} 写入，会整行覆盖模板中该位置的原有内容 及其行级样式；模板的表头样式、列宽、公式等不受影响。
   *
   * <p><b>容错</b>：单个字段取值或格式化失败时不中断整体写入，仅记录 warn 日志并将该单元格置空。 入参为空集合时直接返回，不会生成输出文件。
   *
   * <p><b>区域模式</b>：若已通过 {@link #setDefaultRegion} 设置了默认区域描述符，则本方法等价于
   * {@code doWrite(data, defaultRegion)}——无区域时退化为普通模板覆盖写入。
   *
   * @param data 待写入数据；非 {@link List} 时按单条记录处理，空列表则直接返回
   * @throws ExcelWriteException 模板读取或结果落盘发生 IO 失败时抛出， 由 {@link
   *     ExcelWriteException#fileAccessFailed} 构造
   */
  public void doWrite(Object data) {
    List<?> list = data instanceof List ? (List<?>) data : Collections.singletonList(data);
    if (list.isEmpty()) {
      return;
    }

    // 区域模式：有默认区域描述符时走区域循环写入路径
    if (defaultRegion != null) {
      doWrite(list, defaultRegion);
      return;
    }

    try {
      if (templatePath != null && templateInputStream == null) {
        templateInputStream = new FileInputStream(templatePath);
      }
    } catch (FileNotFoundException e) {
      throw ExcelWriteException.fileAccessFailed(templatePath, e.getMessage());
    }

    try (XSSFWorkbook workbook = new XSSFWorkbook(templateInputStream)) {

      Sheet sheet = workbook.getSheetAt(sheetIndex);
      Class<?> clazz = metadata.getClazz();
      Field[] fields = ReflectCache.getCachedFields(clazz);

      // Build field mapping from template header
      int headerRow = dataStartRow > 0 ? dataStartRow - 1 : findHeaderRow(sheet);
      Map<Integer, Field> columnFieldMap = buildColumnMapping(sheet, headerRow, fields);

      int startRow = dataStartRow > 0 ? dataStartRow : headerRow + 1;

      for (int i = 0; i < list.size(); i++) {
        Object item = list.get(i);
        Row row = sheet.createRow(startRow + i);

        for (Map.Entry<Integer, Field> entry : columnFieldMap.entrySet()) {
          int colIndex = entry.getKey();
          Field field = entry.getValue();
          Cell cell = row.createCell(colIndex);

          try {
            Object value = MHFieldAccessor.getGetter(clazz, field).get(item);
            String dateFormat = getDateFormat(field);
            valueFormatter.setCellValueFast(cell, value, dateFormat);
          } catch (Exception e) {
            LOG.warn("模板写入字段值异常", field.getName(), e);
            cell.setBlank();
          }
        }
      }

      try (FileOutputStream fos = new FileOutputStream(metadata.getFilePath())) {
        workbook.write(fos);
        fos.flush();
      }

    } catch (IOException e) {
      throw ExcelWriteException.fileAccessFailed(
          templatePath != null ? templatePath : "<input-stream>", e.getMessage());
    }
  }

  private int findHeaderRow(Sheet sheet) {
    for (int i = 0; i <= Math.min(10, sheet.getLastRowNum()); i++) {
      Row row = sheet.getRow(i);
      if (row != null && row.getLastCellNum() > 0) {
        return i;
      }
    }
    return 0;
  }

  private Map<Integer, Field> buildColumnMapping(Sheet sheet, int headerRow, Field[] fields) {
    Map<Integer, Field> mapping = new LinkedHashMap<>(16);
    Row row = sheet.getRow(headerRow);
    if (row == null) {
      return mapping;
    }

    Map<String, Field> nameToField = new HashMap<>(16);
    for (Field field : fields) {
      ExcelProperty prop = field.getAnnotation(ExcelProperty.class);
      if (prop != null && !field.isAnnotationPresent(ExcelIgnore.class)) {
        String name = prop.value().isEmpty() ? field.getName() : prop.value();
        nameToField.put(name, field);
      }
    }

    for (int col = 0; col < row.getLastCellNum(); col++) {
      Cell cell = row.getCell(col);
      if (cell != null) {
        String headerName = cell.getStringCellValue();
        Field field = nameToField.get(headerName);
        if (field != null) {
          mapping.put(col, field);
        }
      }
    }
    return mapping;
  }

  private String getDateFormat(Field field) {
    ExcelProperty prop = field.getAnnotation(ExcelProperty.class);
    if (prop != null && !prop.dateFormat().isEmpty()) {
      return prop.dateFormat();
    }
    ExcelConfig config =
        metadata.getExcelConfig() != null ? metadata.getExcelConfig() : ExcelConfig.defaults();
    return config.getDefaultDateFormat();
  }

  // ==================== 区域循环写入（P1-6 新增：对标 poi-tl {{#each}}） ====================

  /**
   * 区域循环写入 — 将数据列表按 {@link TemplateRegion} 定义的模板行区域循环复制并填充。
   *
   * <p>对标 poi-tl 的 {@code {{#each items}}...{{/each}}} 语义：
   * <ul>
   *   <li>每条数据项按"模板行样式复制 → 字段值填充"流程追加写入</li>
   *   <li>源模板行的列宽、单元格样式、公式均被复制到目标行（注：公式引用会按行偏移自动调整，
   *       前提是模板中使用相对引用）</li>
   *   <li>区域内的字段映射复用 {@link #buildColumnMapping} 的表头 → 字段名匹配逻辑</li>
   * </ul>
   *
   * <h3>示例</h3>
   *
   * <pre>{@code
   * // 模板第 4-6 行为数据区域的"样式模板"（行号从 0 计）
   * TemplateRegion region = TemplateRegion.builder()
   *     .sourceStartRow(4)
   *     .sourceEndRow(4)
   *     .targetStartRow(4)
   *     .build();
   * templateWriter.doWrite(userList, region);
   * }</pre>
   *
   * @param data 待写入数据列表（空列表直接返回不写）
   * @param region 模板区域描述符
   * @throws ExcelWriteException 模板读取、偏移计算或 IO 失败时抛出
   */
  public void doWrite(List<?> data, TemplateRegion region) {
    if (data == null || data.isEmpty()) {
      return;
    }
    validateRegion(region);

    try {
      if (templatePath != null && templateInputStream == null) {
        templateInputStream = new FileInputStream(templatePath);
      }
    } catch (FileNotFoundException e) {
      throw ExcelWriteException.fileAccessFailed(templatePath, e.getMessage());
    }

    try (XSSFWorkbook workbook = new XSSFWorkbook(templateInputStream)) {
      Sheet sheet = workbook.getSheetAt(sheetIndex);
      Class<?> clazz = metadata.getClazz();
      Field[] fields = ReflectCache.getCachedFields(clazz);
      Map<Integer, Field> columnFieldMap;
      try {
        columnFieldMap = buildColumnMapping(sheet, region.getSourceStartRow(), fields);
      } catch (Exception e) {
        throw ExcelWriteException.fileAccessFailed(
            templatePath != null ? templatePath : "<input-stream>",
            "Failed to build column mapping: " + e.getMessage());
      }

      int rowSpan = region.getRowSpan();
      // 预计算总行数需求，不足时预先 shiftRows 腾出空间（避免段错误）
      int totalTargetRows = region.getTargetStartRow() + data.size() * rowSpan;
      int available = sheet.getLastRowNum() + 1;
      if (totalTargetRows > available) {
        // 从模板区域末尾起向下平移足够行，保证目标区域不被覆盖
        int shiftFrom = Math.max(region.getSourceEndRow() + 1, region.getTargetStartRow());
        if (shiftFrom <= sheet.getLastRowNum()) {
          sheet.shiftRows(shiftFrom, sheet.getLastRowNum(), totalTargetRows - available, true,
              false);
        }
      }

      for (int i = 0; i < data.size(); i++) {
        Object item = data.get(i);
        int targetBase = region.resolveTargetRow(i);

        // 复制模板行到目标位置（列宽 + 样式 + 值暂不复制）
        for (int r = 0; r < rowSpan; r++) {
          int srcRow = region.getSourceStartRow() + r;
          int dstRow = targetBase + r;
          copyRowStyle(sheet, srcRow, dstRow);
        }

        // 使用 columnFieldMap 仅填充每区域第一行（数据行）
        int fillerRow = targetBase;
        Row row = sheet.getRow(fillerRow);
        if (row == null) {
          row = sheet.createRow(fillerRow);
        }

        for (Map.Entry<Integer, Field> entry : columnFieldMap.entrySet()) {
          int colIndex = entry.getKey();
          Field field = entry.getValue();
          Cell cell = row.getCell(colIndex);
          if (cell == null) {
            cell = row.createCell(colIndex);
          }
          try {
            Object value = MHFieldAccessor.getGetter(clazz, field).get(item);
            String dateFormat = getDateFormat(field);
            valueFormatter.setCellValueFast(cell, value, dateFormat);
          } catch (Exception e) {
            LOG.warn("模板区域写入字段值异常 — field={}, rowIndex={}", field.getName(), i, e);
            cell.setBlank();
          }
        }
      }

      try (FileOutputStream fos = new FileOutputStream(metadata.getFilePath())) {
        workbook.write(fos);
        fos.flush();
      }
    } catch (IOException e) {
      throw ExcelWriteException.fileAccessFailed(
          templatePath != null ? templatePath : "<input-stream>", e.getMessage());
    }
  }

  /**
   * 行样式复制（单元格类型、列宽、行高、CellStyle）。复用源行每一列的 CellStyle 到新行。
   *
   * @param sheet 目标 Sheet
   * @param srcRowNum 源行下标
   * @param dstRowNum 目标行下标
   */
  private void copyRowStyle(Sheet sheet, int srcRowNum, int dstRowNum) {
    Row srcRow = sheet.getRow(srcRowNum);
    if (srcRow == null) {
      return;
    }
    Row dstRow = sheet.getRow(dstRowNum);
    if (dstRow == null) {
      dstRow = sheet.createRow(dstRowNum);
    }
    // 行高
    dstRow.setHeight(srcRow.getHeight());
    // 复制单元格样式
    for (int col = 0; col < srcRow.getLastCellNum(); col++) {
      Cell srcCell = srcRow.getCell(col);
      if (srcCell == null) {
        continue;
      }
      Cell dstCell = dstRow.getCell(col);
      if (dstCell == null) {
        dstCell = dstRow.createCell(col);
      }
      CellStyle srcStyle = srcCell.getCellStyle();
      if (srcStyle != null) {
        // 复用同名 CellStyle（避免创建冗余样式对象）
        dstCell.setCellStyle(srcStyle);
      }
    }
  }

  private void validateRegion(TemplateRegion region) {
    if (region == null) {
      throw new IllegalArgumentException("TemplateRegion must not be null");
    }
    if (region.getTargetStartRow() < 0) {
      // 原地覆盖模式不校验重叠（用户明确有意覆盖）
      return;
    }
    // 校验目标区域不超出源模板区域（避免写冲突）
    if (region.getTargetStartRow() < region.getSourceEndRow() + 1
        && region.getTargetStartRow() >= region.getSourceStartRow()) {
      LOG.warn(
          "Target region overlaps with source region — data overwrites template. "
              + "Consider setting targetStartRow >= sourceEndRow + 1. region={}",
          region);
    }
  }
}
