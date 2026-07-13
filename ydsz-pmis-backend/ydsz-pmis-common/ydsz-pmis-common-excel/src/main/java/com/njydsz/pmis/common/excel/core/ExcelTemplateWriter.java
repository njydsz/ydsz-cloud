package com.njydsz.pmis.common.excel.core;

import com.njydsz.pmis.common.excel.annotation.ExcelProperty;
import com.njydsz.pmis.common.excel.exception.ExcelWriteException;
import com.njydsz.pmis.common.excel.core.config.ExcelConfig;
import com.njydsz.pmis.common.excel.core.metadata.WriteMetadata;
import com.njydsz.pmis.common.excel.core.writer.ValueFormatter;
import com.njydsz.pmis.common.excel.support.asm.ASMFieldAccessor;
import com.njydsz.pmis.common.excel.support.cache.ReflectCache;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import com.njydsz.pmis.common.excel.annotation.ExcelIgnore;

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
                        log.warn("模板写入字段值异常: {}.{}", clazz.getSimpleName(), field.getName(), e);
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
