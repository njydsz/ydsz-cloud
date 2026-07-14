package com.njydsz.pmis.common.excel.core.strategy;

/**
 * UserModelReadStrategy �?
 *
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 */
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;

import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.excel.annotation.ExcelIgnore;
import com.njydsz.pmis.common.excel.annotation.ExcelProperty;
import com.njydsz.pmis.common.excel.core.context.AnalysisContext;
import com.njydsz.pmis.common.excel.core.listener.ReadListener;
import com.njydsz.pmis.common.excel.core.metadata.ReadMetadata;

/**
 * 用户模式读取策略 - POI对象模型
 *
 * <p>使用Apache POI的用户模�?UserModel)进行Excel读取�?
 * 将每个单元格映射为Cell对象,适合中小型文件的读取�?/p>
 *
 * <h3>特点</h3>
 * <ul>
 *   <li>兼容性好 - 使用POI标准API</li>
 *   <li>功能完整 - 支持所有POI支持的格�?/li>
 *   <li>内存占用中等 - 需要在内存中保持Cell对象</li>
 *   <li>适合文件大小 - 建议100MB以下的文�?/li>
 * </ul>
 *
 * <h3>性能数据(参�?</h3>
 * <table border="1">
 *   <tr><th>文件大小</th><th>行数</th><th>耗时</th><th>内存峰�?/th></tr>
 *   <tr><td>1MB</td><td>5�?/td><td>~1s</td><td>~50MB</td></tr>
 *   <tr><td>10MB</td><td>50�?/td><td>~10s</td><td>~200MB</td></tr>
 *   <tr><td>50MB</td><td>250�?/td><td>~60s</td><td>~500MB</td></tr>
 * </table>
 *
 * @see ReadStrategy
 * @see SaxReadStrategy
 */
public class UserModelReadStrategy implements ReadStrategy {

    private static final Logger log = LoggerFactory.getLogger(UserModelReadStrategy.class);

    @Override
    public String getName() {
        return "UserModel";
    }

    @Override
    public void doRead(ReadMetadata metadata, ReadListener<?> listener, AnalysisContext context) {
        String filePath = metadata.getFilePath();
        File file = metadata.getFile();
        InputStream inputStream = metadata.getInputStream();

        try (Workbook workbook = createWorkbook(filePath, file, inputStream)) {
            Sheet sheet = selectSheet(workbook, metadata);

            int headRowNumber = metadata.getHeadRowNumber() != null ? metadata.getHeadRowNumber() : 1;
            Row headRow = sheet.getRow(headRowNumber);
            if (headRow == null) {
                throw new IllegalStateException("表头行不存在");
            }

            List<String> headers = new ArrayList<>();
            Map<Integer, Field> fieldMap = new HashMap<>();

            if (metadata.getClazz() != null) {
                analyzeClassMetadata(headRow, headers, fieldMap, metadata);
            } else {
                analyzeHeaders(headRow, headers);
            }

            int lastRowNum = sheet.getLastRowNum();
            int startRow = headRowNumber + 1;

            for (int rowIndex = startRow; rowIndex <= lastRowNum; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                if (Boolean.TRUE.equals(metadata.getSkipEmptyRows()) && isEmptyRow(row)) {
                    continue;
                }

                context.setCurrentRow(rowIndex);
                Object data = parseRow(row, headers, fieldMap, metadata);

                if (data != null) {
                    
                    ReadListener<Object> typedListener = (ReadListener<Object>) listener;
                    typedListener.onData(context, data);
                }
            }

        } catch (Exception e) {
            log.error("用户模式读取Excel异常", e);
            throw new RuntimeException("Excel读取失败: " + e.getMessage(), e);
        }
    }

    private Workbook createWorkbook(String filePath, File file, InputStream inputStream) throws Exception {
        if (filePath != null) {
            return WorkbookFactory.create(new FileInputStream(filePath));
        } else if (file != null) {
            return WorkbookFactory.create(new FileInputStream(file));
        } else if (inputStream != null) {
            return WorkbookFactory.create(inputStream);
        } else {
            throw new IllegalArgumentException("文件路径和输入流都不能为空");
        }
    }

    private Sheet selectSheet(Workbook workbook, ReadMetadata metadata) {
        Integer sheetIndex = metadata.getSheetIndex();
        String sheetName = metadata.getSheetName();

        if (sheetName != null && !sheetName.isEmpty()) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalStateException("Sheet不存在");
            }
            return sheet;
        }

        if (sheetIndex != null && sheetIndex >= 0) {
            if (sheetIndex >= workbook.getNumberOfSheets()) {
                throw new IllegalStateException("Sheet索引超出范围: " + sheetIndex);
            }
            return workbook.getSheetAt(sheetIndex);
        }

        return workbook.getSheetAt(0);
    }

    private void analyzeHeaders(Row headRow, List<String> headers) {
        short lastCellNum = headRow.getLastCellNum();
        for (int i = 0; i < lastCellNum; i++) {
            Cell cell = headRow.getCell(i);
            String headerName = getCellValueAsString(cell);
            headers.add(headerName != null ? headerName : "");
        }
    }

    private void analyzeClassMetadata(Row headRow, List<String> headers,
                                      Map<Integer, Field> fieldMap, ReadMetadata metadata) {
        Class<?> clazz = metadata.getClazz();
        Field[] fields = clazz.getDeclaredFields();

        int maxCol = headRow.getLastCellNum();
        for (int col = 0; col < maxCol; col++) {
            Cell cell = headRow.getCell(col);
            String headerName = getCellValueAsString(cell);

            for (Field field : fields) {
                if (field.isAnnotationPresent(ExcelIgnore.class)) {
                    continue;
                }

                ExcelProperty ann = field.getAnnotation(ExcelProperty.class);
                if (ann == null) {
                    continue;
                }

                String mappedName = ann.value();
                if (mappedName.isEmpty()) {
                    mappedName = field.getName();
                }

                if (headerName != null && headerName.equals(mappedName)) {
                    field.setAccessible(true);
                    fieldMap.put(col, field);
                    headers.add(headerName);
                    break;
                }
            }
        }
    }

    private Object parseRow(Row row, List<String> headers, Map<Integer, Field> fieldMap, ReadMetadata metadata) {
        Class<?> clazz = metadata.getClazz();
        if (clazz == null || fieldMap.isEmpty()) {
            return null;
        }

        try {
            Object instance = clazz.getDeclaredConstructor().newInstance();

            for (Map.Entry<Integer, Field> entry : fieldMap.entrySet()) {
                int colIndex = entry.getKey();
                Field field = entry.getValue();
                Cell cell = row.getCell(colIndex);

                if (cell == null) {
                    continue;
                }

                Object value = getCellValue(cell, field.getType(), metadata);
                field.set(instance, value);
            }

            return instance;
        } catch (Exception e) {
            log.warn("解析行数据异�?, e);
            return null;
        }
    }

    private Object getCellValue(Cell cell, Class<?> fieldType, ReadMetadata metadata) {
        CellType cellType = cell.getCellType();

        if (cellType == CellType.BLANK) {
            return null;
        }

        switch (cellType) {
            case STRING:
                String strValue = cell.getStringCellValue();
                if (fieldType == String.class) {
                    return strValue;
                }
                return strValue;

            case NUMERIC:
                if (Date.class.isAssignableFrom(fieldType)) {
                    return cell.getDateCellValue();
                }
                if (Number.class.isAssignableFrom(fieldType)) {
                    double numValue = cell.getNumericCellValue();
                    if (fieldType == Integer.class || fieldType == int.class) {
                        return (int) numValue;
                    }
                    if (fieldType == Long.class || fieldType == long.class) {
                        return (long) numValue;
                    }
                    if (fieldType == Short.class || fieldType == short.class) {
                        return (short) numValue;
                    }
                    if (fieldType == Byte.class || fieldType == byte.class) {
                        return (byte) numValue;
                    }
                    return numValue;
                }
                return cell.getNumericCellValue();

            case BOOLEAN:
                return cell.getBooleanCellValue();

            case FORMULA:
                try {
                    return cell.getNumericCellValue();
                } catch (Exception e) {
                    try {
                        return cell.getStringCellValue();
                    } catch (Exception e2) {
                        return null;
                    }
                }

            default:
                return null;
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    try {
                        return cell.getStringCellValue();
                    } catch (Exception e2) {
                        return null;
                    }
                }
            default:
                return null;
        }
    }

    private boolean isEmptyRow(Row row) {
        if (row == null) {
            return true;
        }
        short lastCellNum = row.getLastCellNum();
        if (lastCellNum <= 0) {
            return true;
        }

        for (int i = 0; i < lastCellNum; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellValueAsString(cell);
                if (value != null && !value.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }
}