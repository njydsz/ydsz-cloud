package com.njydsz.literule.infra.excel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.exception.ExcelWriteException;
import com.njydsz.literule.api.DecisionTableDefinition;
import com.njydsz.literule.api.HitPolicy;
import com.njydsz.literule.domain.service.DecisionTableExcelService;

/**
 * 决策表 Excel 导入导出器（P0-3）
 *
 * <p>将 {@link DecisionTableDefinition} 与 Excel（.xlsx）双向转换， 对标 Drools/URule 的决策表 Excel 格式，便于业务人员通过
 * Excel 维护决策表。
 *
 * <h3>Excel 结构</h3>
 *
 * <pre>
 * | HitPolicy: FIRST  | TableCode: DT_PROJECT_RISK | TableName: 项目风险等级决策表 |
 * | Category: RISK    | Description: ...          | Priority: 100              |
 * |----条件列----|----条件列----|----动作列----|----动作列----|
 * | C:evmRedCount | C:metricValue | A:severity   | A:title      |
 * | EVM 红灯数    | 指标值        | 严重度       | 标题         |
 * | number        | number        | string       | string       |
 * | &gt;=3          |               | RED          | EVM 严重偏离  |
 * |               | &lt;0.05       | YELLOW       | 指标值过低    |
 * | DEFAULT       |               | INFO         | 正常         |
 * </pre>
 *
 * <ul>
 *   <li>第 1-2 行：元数据（HitPolicy/TableCode/TableName/Category/Description/Priority/Scope）
 *   <li>第 3 行：列头（条件列用 "C:" 前缀，动作列用 "A:" 前缀）
 *   <li>第 4 行：列显示名（label）
 *   <li>第 5 行：列类型（number/string/boolean）
 *   <li>第 6 行起：决策行（空单元格表示该列不参与该行条件）
 *   <li>最后行：默认动作（第一个单元格标记 "DEFAULT"）
 * </ul>
 *
 * <h3>异常约定</h3>
 *
 * <ul>
 *   <li>导出失败抛 {@link RuntimeException}
 *   <li>导入失败抛 {@link IllegalArgumentException}
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class DecisionTableExcelExporter implements DecisionTableExcelService {

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
      throw new IllegalArgumentException("决策表定义不能为 null");
    }
    try {
      List<DecisionTableDefinition.Column> conditionColumns =
          nullToEmpty(definition.getConditionColumns());
      List<DecisionTableDefinition.Column> actionColumns =
          nullToEmpty(definition.getActionColumns());
      int totalCols = Math.max(conditionColumns.size() + actionColumns.size(), 4);

      List<List<Object>> allRows = new ArrayList<>();

      // 第 1 行：HitPolicy | TableCode | TableName
      List<Object> metaRow1 = padRow(new ArrayList<>(), totalCols);
      metaRow1.set(0, "HitPolicy: " + hitPolicyName(definition.getHitPolicy()));
      metaRow1.set(1, "TableCode: " + nullToEmpty(definition.getTableCode()));
      metaRow1.set(2, "TableName: " + nullToEmpty(definition.getTableName()));
      allRows.add(metaRow1);

      // 第 2 行：Category | Description | Priority | Scope
      List<Object> metaRow2 = padRow(new ArrayList<>(), totalCols);
      metaRow2.set(0, "Category: " + nullToEmpty(definition.getCategory()));
      metaRow2.set(1, "Description: " + nullToEmpty(definition.getDescription()));
      metaRow2.set(2, "Priority: " + definition.getPriority());
      if (definition.getScope() != null && !definition.getScope().isBlank()) {
        metaRow2.set(3, "Scope: " + definition.getScope());
      }
      allRows.add(metaRow2);

      // 第 3 行：列头（C:name / A:name）
      List<Object> headerRow = padRow(new ArrayList<>(), totalCols);
      int colIdx = 0;
      for (DecisionTableDefinition.Column col : conditionColumns) {
        headerRow.set(colIdx, CONDITION_PREFIX + nullToEmpty(col.getName()));
        colIdx++;
      }
      for (DecisionTableDefinition.Column col : actionColumns) {
        headerRow.set(colIdx, ACTION_PREFIX + nullToEmpty(col.getName()));
        colIdx++;
      }
      allRows.add(headerRow);

      // 第 4 行：列显示名（label）
      List<Object> labelRow = padRow(new ArrayList<>(), totalCols);
      colIdx = 0;
      for (DecisionTableDefinition.Column col : conditionColumns) {
        labelRow.set(colIdx, nullToEmpty(col.getLabel()));
        colIdx++;
      }
      for (DecisionTableDefinition.Column col : actionColumns) {
        labelRow.set(colIdx, nullToEmpty(col.getLabel()));
        colIdx++;
      }
      allRows.add(labelRow);

      // 第 5 行：列类型
      List<Object> typeRow = padRow(new ArrayList<>(), totalCols);
      colIdx = 0;
      for (DecisionTableDefinition.Column col : conditionColumns) {
        typeRow.set(colIdx, nullToEmpty(col.getType()));
        colIdx++;
      }
      for (DecisionTableDefinition.Column col : actionColumns) {
        typeRow.set(colIdx, nullToEmpty(col.getType()));
        colIdx++;
      }
      allRows.add(typeRow);

      // 第 6 行起：决策行
      List<DecisionTableDefinition.Row> rows = nullToEmpty(definition.getRows());
      for (DecisionTableDefinition.Row row : rows) {
        allRows.add(buildDataRow(row, conditionColumns, actionColumns, totalCols));
      }

      // 默认动作行
      Map<String, Object> defaultActions = definition.getDefaultActions();
      if (defaultActions != null && !defaultActions.isEmpty()) {
        List<Object> defaultRow = padRow(new ArrayList<>(), totalCols);
        defaultRow.set(0, DEFAULT_MARKER);
        int actionStart = conditionColumns.size();
        for (int i = 0; i < actionColumns.size(); i++) {
          Object val = defaultActions.get(actionColumns.get(i).getName());
          defaultRow.set(actionStart + i, val == null ? "" : val.toString());
        }
        allRows.add(defaultRow);
      }

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ExcelFacade.write(out)
          .headRowNumber(0)
          .sheet(safeSheetName(definition.getTableCode()))
          .doWrite(allRows);

      log.debug("[Excel导出] 决策表 {} 导出完成，共 {} 行", definition.getTableCode(), rows.size());
      return out.toByteArray();
    } catch (Exception e) {
      throw new ExcelWriteException(
          "导出决策表 Excel 失败: tableCode=" + definition.getTableCode() + ", error=" + e.getMessage(),
          e);
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
    try {
      List<?> rawRows =
          ExcelFacade.read(new ByteArrayInputStream(excelBytes))
              .sheet(0)
              .headRowNumber(0)
              .doReadAll();

      if (rawRows.isEmpty()) {
        throw new IllegalArgumentException("Excel 文件不包含任何数据行");
      }

      // 将每行转为 List<String>，同时提取 header 行（row 0）的值
      List<List<String>> stringRows = new ArrayList<>();
      List<String> headerValues = null;
      for (Object rawRow : rawRows) {
        if (rawRow instanceof Map) {
          Map<?, ?> map = (Map<?, ?>) rawRow;
          if (headerValues == null) {
            headerValues = new ArrayList<>();
            for (Object key : map.keySet()) {
              headerValues.add(key == null ? "" : key.toString());
            }
          }
          List<String> values = new ArrayList<>();
          for (Object val : map.values()) {
            values.add(val == null ? "" : val.toString());
          }
          stringRows.add(values);
        }
      }

      // row 0 的值 = headerValues（从 Map keySet 提取）
      // row 1+ 的值 = stringRows 各行的 values
      List<String> row0 = headerValues != null ? headerValues : List.of();
      List<String> row1 = stringRows.isEmpty() ? List.of() : stringRows.get(0);
      List<String> row2 = stringRows.size() > 1 ? stringRows.get(1) : List.of();
      List<String> row3 = stringRows.size() > 2 ? stringRows.get(2) : List.of();
      List<String> row4 = stringRows.size() > 3 ? stringRows.get(3) : List.of();

      // 解析元数据
      Map<String, String> meta = new LinkedHashMap<>();
      parseMetaValues(row0, meta);
      parseMetaValues(row1, meta);
      String tableCode = meta.getOrDefault("TableCode", "");
      String tableName = meta.getOrDefault("TableName", "");
      String category = meta.getOrDefault("Category", "");
      String description = meta.getOrDefault("Description", "");
      String scope = meta.getOrDefault("Scope", null);
      HitPolicy hitPolicy = HitPolicy.fromCode(meta.getOrDefault("HitPolicy", "FIRST"));
      int priority = parseIntOrDefault(meta.getOrDefault("Priority", "100"), 100);

      // 解析列定义
      if (row2.isEmpty()) {
        throw new IllegalArgumentException("Excel 缺少列头行（第 3 行）");
      }

      int totalCols = row2.size();
      if (totalCols == 0) {
        throw new IllegalArgumentException("Excel 未定义任何列");
      }

      List<DecisionTableDefinition.Column> conditionColumns = new ArrayList<>();
      List<DecisionTableDefinition.Column> actionColumns = new ArrayList<>();
      for (int i = 0; i < totalCols; i++) {
        String header = getOrEmpty(row2, i);
        String label = getOrEmpty(row3, i);
        String type = getOrEmpty(row4, i);
        if (header.isBlank()) {
          throw new IllegalArgumentException("第 " + (i + 1) + " 列头为空");
        }
        DecisionTableDefinition.Column column =
            DecisionTableDefinition.Column.builder()
                .name(stripPrefix(header))
                .label(label)
                .type(type.isBlank() ? "string" : type)
                .build();
        if (header.startsWith(CONDITION_PREFIX)) {
          conditionColumns.add(column);
        } else if (header.startsWith(ACTION_PREFIX)) {
          actionColumns.add(column);
        } else {
          throw new IllegalArgumentException(
              "第 " + (i + 1) + " 列头 '" + header + "' 缺少 C:/A: 前缀，无法识别列类型");
        }
      }

      if (conditionColumns.isEmpty()) {
        throw new IllegalArgumentException("决策表至少需要一个条件列");
      }
      if (actionColumns.isEmpty()) {
        throw new IllegalArgumentException("决策表至少需要一个动作列");
      }

      // 解析决策行 + 默认动作
      List<DecisionTableDefinition.Row> decisionRows = new ArrayList<>();
      Map<String, Object> defaultActions = new LinkedHashMap<>();
      for (int r = DATA_ROW_START - 1; r < stringRows.size(); r++) {
        List<String> rowValues = stringRows.get(r);
        String firstCell = getOrEmpty(rowValues, 0);
        if (DEFAULT_MARKER.equalsIgnoreCase(firstCell)) {
          int actionStart = conditionColumns.size();
          for (int i = 0; i < actionColumns.size(); i++) {
            String val = getOrEmpty(rowValues, actionStart + i);
            if (!val.isEmpty()) {
              defaultActions.put(actionColumns.get(i).getName(), val);
            }
          }
          continue;
        }

        DecisionTableDefinition.Row decisionRow =
            parseDataRow(rowValues, conditionColumns, actionColumns);
        if (decisionRow != null) {
          decisionRows.add(decisionRow);
        }
      }

      DecisionTableDefinition def =
          DecisionTableDefinition.builder()
              .tableCode(tableCode)
              .tableName(tableName)
              .description(description)
              .category(category)
              .hitPolicy(hitPolicy)
              .conditionColumns(conditionColumns)
              .actionColumns(actionColumns)
              .rows(decisionRows)
              .defaultActions(defaultActions.isEmpty() ? null : defaultActions)
              .enabled(true)
              .priority(priority)
              .scope(scope == null || scope.isBlank() ? null : scope)
              .version(1)
              .build();
      log.debug(
          "[Excel导入] 决策表 {} 导入完成，条件列={} 动作列={} 行数={}",
          tableCode,
          conditionColumns.size(),
          actionColumns.size(),
          decisionRows.size());
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
    DecisionTableDefinition template =
        DecisionTableDefinition.builder()
            .tableCode("DT_TEMPLATE")
            .tableName("决策表模板")
            .description("请在此填写决策表内容")
            .category("TEMPLATE")
            .hitPolicy(HitPolicy.FIRST)
            .conditionColumns(
                List.of(
                    DecisionTableDefinition.Column.builder()
                        .name("cond1")
                        .label("条件1")
                        .type("string")
                        .build()))
            .actionColumns(
                List.of(
                    DecisionTableDefinition.Column.builder()
                        .name("action1")
                        .label("动作1")
                        .type("string")
                        .build()))
            .rows(Collections.emptyList())
            .defaultActions(Collections.emptyMap())
            .priority(100)
            .build();
    return exportToExcel(template);
  }

  // ============================== 私有方法 ==============================

  /** 构建决策数据行 */
  private List<Object> buildDataRow(
      DecisionTableDefinition.Row row,
      List<DecisionTableDefinition.Column> conditionColumns,
      List<DecisionTableDefinition.Column> actionColumns,
      int totalCols) {
    List<Object> dataRow = padRow(new ArrayList<>(), totalCols);
    Map<String, String> conditions = row.getConditions();
    Map<String, Object> actions = row.getActions();

    int colIdx = 0;
    for (DecisionTableDefinition.Column col : conditionColumns) {
      if (conditions != null && conditions.containsKey(col.getName())) {
        dataRow.set(colIdx, conditions.get(col.getName()));
      }
      colIdx++;
    }
    for (DecisionTableDefinition.Column col : actionColumns) {
      if (actions != null && actions.containsKey(col.getName())) {
        Object val = actions.get(col.getName());
        dataRow.set(colIdx, val == null ? "" : val.toString());
      }
      colIdx++;
    }
    return dataRow;
  }

  /** 将行填充到指定列数 */
  private List<Object> padRow(List<Object> row, int totalCols) {
    while (row.size() < totalCols) {
      row.add("");
    }
    return row;
  }

  /** 解析元数据值 */
  private void parseMetaValues(List<String> values, Map<String, String> meta) {
    for (String text : values) {
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

  /** 解析一行为 DecisionTableDefinition.Row */
  private DecisionTableDefinition.Row parseDataRow(
      List<String> rowValues,
      List<DecisionTableDefinition.Column> conditionColumns,
      List<DecisionTableDefinition.Column> actionColumns) {
    Map<String, String> conditions = new LinkedHashMap<>();
    Map<String, Object> actions = new LinkedHashMap<>();
    int colIdx = 0;

    for (DecisionTableDefinition.Column col : conditionColumns) {
      String val = getOrEmpty(rowValues, colIdx);
      if (!val.isEmpty()) {
        conditions.put(col.getName(), val);
      }
      colIdx++;
    }
    for (DecisionTableDefinition.Column col : actionColumns) {
      String val = getOrEmpty(rowValues, colIdx);
      if (!val.isEmpty()) {
        actions.put(col.getName(), val);
      }
      colIdx++;
    }

    if (conditions.isEmpty() && actions.isEmpty()) {
      return null;
    }
    return DecisionTableDefinition.Row.builder()
        .conditions(conditions)
        .actions(actions)
        .priority(100)
        .build();
  }

  private String getOrEmpty(List<String> list, int index) {
    if (list == null || index < 0 || index >= list.size()) {
      return "";
    }
    String val = list.get(index);
    return val == null ? "" : val;
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
