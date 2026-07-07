package com.njydsz.pmis.literule.excel;

import com.njydsz.pmis.literule.api.DecisionTableDefinition;
import com.njydsz.pmis.literule.api.HitPolicy;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 决策表 Excel 导入导出器（P0-3）
 *
 * <p>将 {@link DecisionTableDefinition} 与 Excel（.xlsx）双向转换，
 * 对标 Drools/URule 的决策表 Excel 格式，便于业务人员通过 Excel 维护决策表。
 *
 * <h3>Excel 结构</h3>
 * <pre>
 * | HitPolicy: FIRST  | TableCode: DT_PROJECT_RISK | TableName: 项目风险等级决策表 |
 * | Category: RISK    | Description: ...          | Priority: 100              |
 * |----条件列----|----条件列----|----动作列----|----动作列----|
 * | C:evmRedCount | C:grossMargin | A:severity   | A:title      |
 * | EVM 红灯数    | 毛利率        | 严重度       | 标题         |
 * | number        | number        | string       | string       |
 * | &gt;=3          |               | RED          | EVM 严重偏离  |
 * |               | &lt;0.05       | YELLOW       | 毛利率过低    |
 * | DEFAULT       |               | INFO         | 正常         |
 * </pre>
 *
 * <ul>
 *   <li>第 1-2 行：元数据（HitPolicy/TableCode/TableName/Category/Description/Priority/Scope）</li>
 *   <li>第 3 行：列头（条件列用 "C:" 前缀，动作列用 "A:" 前缀）</li>
 *   <li>第 4 行：列显示名（label）</li>
 *   <li>第 5 行：列类型（number/string/boolean）</li>
 *   <li>第 6 行起：决策行（空单元格表示该列不参与该行条件）</li>
 *   <li>最后行：默认动作（第一个单元格标记 "DEFAULT"）</li>
 * </ul>
 *
 * <h3>异常约定</h3>
 * <ul>
 *   <li>导出失败抛 {@link RuntimeException}</li>
 *   <li>导入失败抛 {@link IllegalArgumentException}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Slf4j
public class DecisionTableExcelExporter {

    /** 元数据行数（HitPolicy/TableCode 等占 2 行） */
    private static final int METADATA_ROWS = 2;
    /** 列头行、显示名行、类型行 */
    private static final int HEADER_ROWS = 3;
    /** 决策行起始索引（0-based，第 6 行对应 rowIdx=5） */
    private static final int DATA_ROW_START = METADATA_ROWS + HEADER_ROWS;

    /** 默认动作标记 */
    private static final String DEFAULT_MARKER = "DEFAULT";
    /** 条件列前缀 */
    private static final String CONDITION_PREFIX = "C:";
    /** 动作列前缀 */
    private static final String ACTION_PREFIX = "A:";

    /**
     * 导出决策表为 Excel 字节数组
     *
     * @param definition 决策表定义
     * @return xlsx 字节数组
     * @throws RuntimeException 导出失败
     */
    public byte[] exportToExcel(DecisionTableDefinition definition) {
        if (definition == null) {
            throw new RuntimeException("决策表定义不能为 null");
        }
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(safeSheetName(definition.getTableCode()));
            CellStyle headerStyle = createHeaderStyle(workbook);

            List<DecisionTableDefinition.Column> conditionColumns =
                    nullToEmpty(definition.getConditionColumns());
            List<DecisionTableDefinition.Column> actionColumns =
                    nullToEmpty(definition.getActionColumns());
            int totalCols = conditionColumns.size() + actionColumns.size();

            // 第 1 行：HitPolicy | TableCode | TableName
            Row metaRow1 = sheet.createRow(0);
            setCell(metaRow1, 0, "HitPolicy: " + hitPolicyName(definition.getHitPolicy()), headerStyle);
            setCell(metaRow1, 1, "TableCode: " + nullToEmpty(definition.getTableCode()), headerStyle);
            setCell(metaRow1, 2, "TableName: " + nullToEmpty(definition.getTableName()), headerStyle);

            // 第 2 行：Category | Description | Priority | Scope
            Row metaRow2 = sheet.createRow(1);
            setCell(metaRow2, 0, "Category: " + nullToEmpty(definition.getCategory()), headerStyle);
            setCell(metaRow2, 1, "Description: " + nullToEmpty(definition.getDescription()), headerStyle);
            setCell(metaRow2, 2, "Priority: " + definition.getPriority(), headerStyle);
            if (definition.getScope() != null && !definition.getScope().isBlank()) {
                setCell(metaRow2, 3, "Scope: " + definition.getScope(), headerStyle);
            }

            // 第 3 行：列头（C:name / A:name）
            Row headerRow = sheet.createRow(2);
            int colIdx = 0;
            for (DecisionTableDefinition.Column col : conditionColumns) {
                setCell(headerRow, colIdx, CONDITION_PREFIX + nullToEmpty(col.getName()), headerStyle);
                colIdx++;
            }
            for (DecisionTableDefinition.Column col : actionColumns) {
                setCell(headerRow, colIdx, ACTION_PREFIX + nullToEmpty(col.getName()), headerStyle);
                colIdx++;
            }

            // 第 4 行：列显示名（label）
            Row labelRow = sheet.createRow(3);
            colIdx = 0;
            for (DecisionTableDefinition.Column col : conditionColumns) {
                setCell(labelRow, colIdx, nullToEmpty(col.getLabel()));
                colIdx++;
            }
            for (DecisionTableDefinition.Column col : actionColumns) {
                setCell(labelRow, colIdx, nullToEmpty(col.getLabel()));
                colIdx++;
            }

            // 第 5 行：列类型
            Row typeRow = sheet.createRow(4);
            colIdx = 0;
            for (DecisionTableDefinition.Column col : conditionColumns) {
                setCell(typeRow, colIdx, nullToEmpty(col.getType()));
                colIdx++;
            }
            for (DecisionTableDefinition.Column col : actionColumns) {
                setCell(typeRow, colIdx, nullToEmpty(col.getType()));
                colIdx++;
            }

            // 第 6 行起：决策行
            List<DecisionTableDefinition.Row> rows = nullToEmpty(definition.getRows());
            int rowIdx = DATA_ROW_START;
            for (DecisionTableDefinition.Row row : rows) {
                writeDataRow(sheet, rowIdx, row, conditionColumns, actionColumns);
                rowIdx++;
            }

            // 默认动作行（第一个单元格标记 DEFAULT，后续单元格为动作值）
            Map<String, Object> defaultActions = definition.getDefaultActions();
            if (defaultActions != null && !defaultActions.isEmpty()) {
                Row defaultRow = sheet.createRow(rowIdx);
                setCell(defaultRow, 0, DEFAULT_MARKER);
                int actionStart = conditionColumns.size();
                for (int i = 0; i < actionColumns.size(); i++) {
                    DecisionTableDefinition.Column col = actionColumns.get(i);
                    Object val = defaultActions.get(col.getName());
                    setCell(defaultRow, actionStart + i, val == null ? "" : val.toString());
                }
            }

            // 自适应列宽
            for (int i = 0; i < Math.max(totalCols, 4); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            log.debug("[Excel导出] 决策表 {} 导出完成，共 {} 行", definition.getTableCode(), rows.size());
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("导出决策表 Excel 失败: " + definition.getTableCode(), e);
        }
    }

    /**
     * 从 Excel 字节数组导入决策表
     *
     * @param excelBytes xlsx 字节数组
     * @return 决策表定义
     * @throws IllegalArgumentException 导入失败（格式错误/数据缺失）
     */
    public DecisionTableDefinition importFromExcel(byte[] excelBytes) {
        if (excelBytes == null || excelBytes.length == 0) {
            throw new IllegalArgumentException("Excel 数据不能为空");
        }
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Excel 文件不包含任何工作表");
            }

            // 解析元数据
            Map<String, String> meta = parseMetadata(sheet);
            String tableCode = meta.getOrDefault("TableCode", "");
            String tableName = meta.getOrDefault("TableName", "");
            String category = meta.getOrDefault("Category", "");
            String description = meta.getOrDefault("Description", "");
            String scope = meta.getOrDefault("Scope", null);
            HitPolicy hitPolicy = HitPolicy.fromCode(meta.getOrDefault("HitPolicy", "FIRST"));
            int priority = parseIntOrDefault(meta.getOrDefault("Priority", "100"), 100);

            // 解析列定义
            Row headerRow = sheet.getRow(2);
            Row labelRow = sheet.getRow(3);
            Row typeRow = sheet.getRow(4);
            if (headerRow == null || labelRow == null || typeRow == null) {
                throw new IllegalArgumentException("Excel 缺少列定义行（第 3-5 行）");
            }

            List<DecisionTableDefinition.Column> conditionColumns = new ArrayList<>();
            List<DecisionTableDefinition.Column> actionColumns = new ArrayList<>();
            int totalCols = 0;
            // 统计有效列数（以 headerRow 为准，遇到空单元格停止）
            while (totalCols < headerRow.getLastCellNum()) {
                Cell cell = headerRow.getCell(totalCols);
                if (cell == null || cell.getStringCellValue() == null || cell.getStringCellValue().isBlank()) {
                    break;
                }
                totalCols++;
            }
            if (totalCols == 0) {
                throw new IllegalArgumentException("Excel 未定义任何列");
            }

            for (int i = 0; i < totalCols; i++) {
                String header = getCellAsString(headerRow.getCell(i));
                String label = getCellAsString(labelRow.getCell(i));
                String type = getCellAsString(typeRow.getCell(i));
                if (header == null || header.isBlank()) {
                    throw new IllegalArgumentException("第 " + (i + 1) + " 列头为空");
                }
                DecisionTableDefinition.Column column = DecisionTableDefinition.Column.builder()
                        .name(stripPrefix(header))
                        .label(label == null ? "" : label)
                        .type(type == null ? "string" : type)
                        .build();
                if (header.startsWith(CONDITION_PREFIX)) {
                    conditionColumns.add(column);
                } else if (header.startsWith(ACTION_PREFIX)) {
                    actionColumns.add(column);
                } else {
                    throw new IllegalArgumentException("第 " + (i + 1) + " 列头 '" + header
                            + "' 缺少 C:/A: 前缀，无法识别列类型");
                }
            }

            if (conditionColumns.isEmpty()) {
                throw new IllegalArgumentException("决策表至少需要一个条件列");
            }
            if (actionColumns.isEmpty()) {
                throw new IllegalArgumentException("决策表至少需要一个动作列");
            }

            // 解析决策行 + 默认动作
            List<DecisionTableDefinition.Row> rows = new ArrayList<>();
            Map<String, Object> defaultActions = new LinkedHashMap<>();
            int lastRowIdx = sheet.getLastRowNum();
            for (int r = DATA_ROW_START; r <= lastRowIdx; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String firstCell = getCellAsString(row.getCell(0));
                if (DEFAULT_MARKER.equalsIgnoreCase(firstCell)) {
                    // 默认动作行
                    int actionStart = conditionColumns.size();
                    for (int i = 0; i < actionColumns.size(); i++) {
                        Cell cell = row.getCell(actionStart + i);
                        String val = getCellAsString(cell);
                        if (val != null && !val.isEmpty()) {
                            defaultActions.put(actionColumns.get(i).getName(), val);
                        }
                    }
                    continue;
                }

                DecisionTableDefinition.Row decisionRow = parseDataRow(row, conditionColumns, actionColumns);
                if (decisionRow != null) {
                    rows.add(decisionRow);
                }
            }

            DecisionTableDefinition def = DecisionTableDefinition.builder()
                    .tableCode(tableCode)
                    .tableName(tableName)
                    .description(description)
                    .category(category)
                    .hitPolicy(hitPolicy)
                    .conditionColumns(conditionColumns)
                    .actionColumns(actionColumns)
                    .rows(rows)
                    .defaultActions(defaultActions.isEmpty() ? null : defaultActions)
                    .enabled(true)
                    .priority(priority)
                    .scope(scope == null || scope.isBlank() ? null : scope)
                    .version(1)
                    .build();
            log.debug("[Excel导入] 决策表 {} 导入完成，条件列={} 动作列={} 行数={}",
                    tableCode, conditionColumns.size(), actionColumns.size(), rows.size());
            return def;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("导入决策表 Excel 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 导出空白模板（供用户填写后导入）
     *
     * @return xlsx 字节数组
     */
    public byte[] exportTemplate() {
        DecisionTableDefinition template = DecisionTableDefinition.builder()
                .tableCode("DT_TEMPLATE")
                .tableName("决策表模板")
                .description("请在此填写决策表内容")
                .category("TEMPLATE")
                .hitPolicy(HitPolicy.FIRST)
                .conditionColumns(List.of(
                        DecisionTableDefinition.Column.builder().name("cond1").label("条件1").type("string").build()))
                .actionColumns(List.of(
                        DecisionTableDefinition.Column.builder().name("action1").label("动作1").type("string").build()))
                .rows(Collections.emptyList())
                .defaultActions(Collections.emptyMap())
                .priority(100)
                .build();
        return exportToExcel(template);
    }

    // ============================== 私有方法 ==============================

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void setCell(Row row, int colIdx, String value) {
        setCell(row, colIdx, value, null);
    }

    private void setCell(Row row, int colIdx, String value, CellStyle style) {
        Cell cell = row.createCell(colIdx);
        cell.setCellValue(value == null ? "" : value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    /**
     * 写入一行决策数据
     */
    private void writeDataRow(Sheet sheet, int rowIdx, DecisionTableDefinition.Row row,
                              List<DecisionTableDefinition.Column> conditionColumns,
                              List<DecisionTableDefinition.Column> actionColumns) {
        Row excelRow = sheet.createRow(rowIdx);
        Map<String, String> conditions = row.getConditions();
        Map<String, Object> actions = row.getActions();

        int colIdx = 0;
        // 条件列
        for (DecisionTableDefinition.Column col : conditionColumns) {
            if (conditions != null && conditions.containsKey(col.getName())) {
                setCell(excelRow, colIdx, conditions.get(col.getName()));
            }
            colIdx++;
        }
        // 动作列
        for (DecisionTableDefinition.Column col : actionColumns) {
            if (actions != null && actions.containsKey(col.getName())) {
                Object val = actions.get(col.getName());
                setCell(excelRow, colIdx, val == null ? "" : val.toString());
            }
            colIdx++;
        }
    }

    /**
     * 解析元数据（前两行）
     */
    private Map<String, String> parseMetadata(Sheet sheet) {
        Map<String, String> meta = new LinkedHashMap<>();
        parseMetaRow(sheet.getRow(0), meta);
        parseMetaRow(sheet.getRow(1), meta);
        return meta;
    }

    private void parseMetaRow(Row row, Map<String, String> meta) {
        if (row == null) {
            return;
        }
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            String text = getCellAsString(cell);
            if (text == null || text.isBlank()) {
                continue;
            }
            int colonIdx = text.indexOf(':');
            if (colonIdx > 0) {
                String key = text.substring(0, colonIdx).trim();
                String value = text.substring(colonIdx + 1).trim();
                meta.put(key, value);
            }
        }
    }

    /**
     * 解析一行为 DecisionTableDefinition.Row
     */
    private DecisionTableDefinition.Row parseDataRow(Row row,
                                                     List<DecisionTableDefinition.Column> conditionColumns,
                                                     List<DecisionTableDefinition.Column> actionColumns) {
        Map<String, String> conditions = new LinkedHashMap<>();
        Map<String, Object> actions = new LinkedHashMap<>();
        int colIdx = 0;

        // 条件列
        for (DecisionTableDefinition.Column col : conditionColumns) {
            Cell cell = row.getCell(colIdx);
            String val = getCellAsString(cell);
            if (val != null && !val.isEmpty()) {
                conditions.put(col.getName(), val);
            }
            colIdx++;
        }
        // 动作列
        for (DecisionTableDefinition.Column col : actionColumns) {
            Cell cell = row.getCell(colIdx);
            String val = getCellAsString(cell);
            if (val != null && !val.isEmpty()) {
                actions.put(col.getName(), val);
            }
            colIdx++;
        }

        // 空行跳过
        if (conditions.isEmpty() && actions.isEmpty()) {
            return null;
        }
        return DecisionTableDefinition.Row.builder()
                .conditions(conditions)
                .actions(actions)
                .priority(100)
                .build();
    }

    private String getCellAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num)) {
                    return String.valueOf((long) num);
                }
                return String.valueOf(num);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BLANK:
            case _NONE:
                return null;
            default:
                return null;
        }
    }

    private String stripPrefix(String header) {
        if (header == null) {
            return "";
        }
        if (header.startsWith(CONDITION_PREFIX) || header.startsWith(ACTION_PREFIX)) {
            return header.substring(2);
        }
        return header;
    }

    private String hitPolicyName(HitPolicy hitPolicy) {
        return hitPolicy == null ? HitPolicy.FIRST.name() : hitPolicy.name();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private String safeSheetName(String tableCode) {
        if (tableCode == null || tableCode.isBlank()) {
            return "DecisionTable";
        }
        // Excel sheet 名称禁止字符: / \ ? * [ ]
        return tableCode.replaceAll("[/\\\\?*\\[\\]]", "_");
    }

    private int parseIntOrDefault(String s, int defaultValue) {
        if (s == null || s.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
