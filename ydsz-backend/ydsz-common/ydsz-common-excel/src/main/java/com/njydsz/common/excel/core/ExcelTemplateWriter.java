package com.njydsz.common.excel.core;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.metadata.WriteMetadata;
import com.njydsz.common.excel.core.writer.ValueFormatter;
import com.njydsz.common.excel.exception.ExcelWriteException;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor;
import com.njydsz.common.excel.support.cache.ReflectCache;

/**
 * Excel模板写入器 - 基于模板文件写入数据
 *
 * <p>支持将数据写入已有的Excel模板文件，保留模板中的样式、格式、公式等设置。
 * 参照EasyExcel的模板写入功能设计。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * ExcelFacade.writeWithTemplate("template.xlsx", "output.xlsx", User.class)
 *     .sheet(0)
 *     .dataStartRow(3)
 *     .doWrite(userList);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ExcelTemplateWriter {

    private static final Logger log = LoggerFactory.getLogger(ExcelTemplateWriter.class);

    private final String templatePath;
    private final WriteMetadata metadata;
    private final ValueFormatter valueFormatter;
    private int sheetIndex = 0;
    private int dataStartRow = -1; // -1 means auto-detect

    public ExcelTemplateWriter(String templatePath, String outputPath, Class<?> clazz) {
        this.templatePath = templatePath;
        this.metadata = new WriteMetadata();
        this.metadata.setFilePath(outputPath);
        this.metadata.setClazz(clazz);
        this.valueFormatter = new ValueFormatter(true);
    }

    public ExcelTemplateWriter sheet(int sheetIndex) {
        this.sheetIndex = sheetIndex;
        return this;
    }

    public ExcelTemplateWriter dataStartRow(int row) {
        this.dataStartRow = row;
        return this;
    }

    /**
     * 将数据填充到模板并输出到目标文件。
     *
     * <p><b>列映射</b>：不按字段声明顺序硬填，而是读取模板表头行文本，与
     * {@link ExcelProperty#value()}（为空时回退字段名）做名称匹配后按列下标写入。
     * 因此模板列可任意调序；模板中没有对应表头的字段会被静默跳过，不报错。
     *
     * <p><b>起始行推断</b>：显式设置了 {@code dataStartRow} 时，表头行取其上一行；
     * 否则自动探测前 11 行中第一个非空行作为表头，数据从表头下一行开始写。
     *
     * <p><b>覆盖语义</b>：使用 {@code createRow} 写入，会整行覆盖模板中该位置的原有内容
     * 及其行级样式；模板的表头样式、列宽、公式等不受影响。
     *
     * <p><b>容错</b>：单个字段取值或格式化失败时不中断整体写入，仅记录 warn 日志并将该单元格置空。
     * 入参为空集合时直接返回，不会生成输出文件。
     *
     * @param data 待写入数据；非 {@link List} 时按单条记录处理，空列表则直接返回
     * @throws ExcelWriteException 模板读取或结果落盘发生 IO 失败时抛出，
     *                             由 {@link ExcelWriteException#fileAccessFailed} 构造
     */
    public void doWrite(Object data) {
        List<?> list = data instanceof List ? (List<?>) data : Collections.singletonList(data);
        if (list.isEmpty()) return;

        try (InputStream templateIs = new FileInputStream(templatePath);
             XSSFWorkbook workbook = new XSSFWorkbook(templateIs)) {

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
                        Object value = ASMFieldAccessor.getGetter(clazz, field).get(item);
                        String dateFormat = getDateFormat(field);
                        valueFormatter.setCellValueFast(cell, value, dateFormat);
                    } catch (Exception e) {
                        log.warn("模板写入字段值异常", field.getName(), e);
                        cell.setBlank();
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(metadata.getFilePath())) {
                workbook.write(fos);
                fos.flush();
            }

        } catch (IOException e) {
            throw ExcelWriteException.fileAccessFailed(templatePath, e.getMessage());
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
        Map<Integer, Field> mapping = new LinkedHashMap<>();
        Row row = sheet.getRow(headerRow);
        if (row == null) return mapping;

        Map<String, Field> nameToField = new HashMap<>();
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
        return ExcelConfig.getInstance().getDefaultDateFormat();
    }
}
