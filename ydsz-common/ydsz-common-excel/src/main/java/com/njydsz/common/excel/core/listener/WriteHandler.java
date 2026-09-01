package com.njydsz.common.excel.core.listener;

import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.ConditionalFormattingRule;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PatternFormatting;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Excel 条件格式与数据验证工具类。
 *
 * <p>提供静态工厂方法构造条件格式配置（{@link ConditionalFormattingConfig}）与 数据验证配置（{@link
 * DataValidationConfig}），以及将它们应用到指定 Sheet 范围的方法。 支持下拉列表、数字范围、日期区间等多种 POI 验证类型。
 *
 * <h3>典型用法</h3>
 *
 * <pre>{@code
 * // 下拉列表验证
 * DataValidationConfig listConfig = WriteHandler.createListValidation("是,否,待定");
 * WriteHandler.applyDataValidation(sheet, 1, 1000, 1, 1, listConfig);
 *
 * // 数字范围验证
 * DataValidationConfig numConfig = WriteHandler.createNumberBetweenValidation(0, 100);
 * WriteHandler.applyDataValidation(sheet, 1, 1000, 2, 2, numConfig);
 *
 * // 条件格式（值大于 100 标红）
 * List<ConditionalFormattingConfig> configs =
 *     WriteHandler.buildConditionalFormatting("A1>100", "RED", null);
 * WriteHandler.applyConditionalFormatting(sheet, 1, 1000, 0, 0, configs);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class WriteHandler {

  private WriteHandler() {}

  private static final Logger LOG = LoggerFactory.getLogger(WriteHandler.class);

  /** DATE 类型验证约束使用的日期格式（formula1/formula2 的解析格式） */
  private static final String DEFAULT_VALIDATION_DATE_FORMAT = "yyyy-MM-dd";

  /**
   * 单元格数据格式化器接口
   *
   * <p>用于自定义单元格的格式化逻辑
   */
  public interface CellDataFormatter {
    void format(Cell cell, Object value);
  }

  /**
   * 条件格式配置
   *
   * <p>封装条件格式的公式、字体颜色、背景颜色等属性
   */
  public static class ConditionalFormattingConfig {
    private String formula;
    private String fontColor;
    private String backgroundColor;
    private boolean bold;

    public ConditionalFormattingConfig(String formula) {
      this.formula = formula;
    }

    /**
     * 设置条件格式命中的字体颜色。
     *
     * @param fontColor 颜色名称（如 {@code "RED"}），可为 {@code null} 表示沿用默认色
     * @return 当前配置，便于链式调用
     */
    public ConditionalFormattingConfig setFontColor(String fontColor) {
      this.fontColor = fontColor;
      return this;
    }

    /**
     * 设置条件格式命中的背景填充色。
     *
     * @param backgroundColor 颜色名称（如 {@code "YELLOW"}），可为 {@code null} 表示不填充
     * @return 当前配置，便于链式调用
     */
    public ConditionalFormattingConfig setBackgroundColor(String backgroundColor) {
      this.backgroundColor = backgroundColor;
      return this;
    }

    /**
     * 设置条件格式命中的单元格是否加粗显示。
     *
     * @param bold {@code true} 表示加粗
     * @return 当前配置，便于链式调用
     */
    public ConditionalFormattingConfig setBold(boolean bold) {
      this.bold = bold;
      return this;
    }

    public String getFormula() {
      return formula;
    }

    public String getFontColor() {
      return fontColor;
    }

    public String getBackgroundColor() {
      return backgroundColor;
    }

    public boolean isBold() {
      return bold;
    }
  }

  /**
   * 数据验证配置
   *
   * <p>封装数据验证的类型、公式、错误提示等属性
   */
  public static class DataValidationConfig {
    private String formula1;
    private String formula2;
    private int operatorType;
    private int validationType;
    private String errorStyle;
    private String errorTitle;
    private String error;
    private boolean showErrorMessage;

    public DataValidationConfig(int validationType) {
      this.validationType = validationType;
      this.operatorType = DataValidationConstraint.OperatorType.IGNORED;
      this.showErrorMessage = false;
    }

    /**
     * 设置数据验证的公式 1（列表内容或数值区间下界）。
     *
     * <p>对 LIST 类型为逗号分隔的候选值；对 BETWEEN 等区间类型为下界公式。
     *
     * @param formula1 验证公式，可为 {@code null}
     * @return 当前配置，便于链式调用
     */
    public DataValidationConfig setFormula1(String formula1) {
      this.formula1 = formula1;
      return this;
    }

    /**
     * 设置数据验证的公式 2（数值区间上界）。
     *
     * <p>仅对 BETWEEN 等需要双边界的运算符类型生效。
     *
     * @param formula2 上界公式，可为 {@code null}
     * @return 当前配置，便于链式调用
     */
    public DataValidationConfig setFormula2(String formula2) {
      this.formula2 = formula2;
      return this;
    }

    /**
     * 设置数据验证的运算符类型。
     *
     * <p>取值参考 {@link DataValidationConstraint.OperatorType}（如 BETWEEN、GREATER_THAN）， 默认
     * IGNORED（仅限制类型、不限范围）。
     *
     * @param operatorType 运算符类型编码
     * @return 当前配置，便于链式调用
     */
    public DataValidationConfig setOperatorType(int operatorType) {
      this.operatorType = operatorType;
      return this;
    }

    /**
     * 设置校验失败时的错误提示样式。
     *
     * <p>取值如 {@code "stop"}（阻止非法输入）、{@code "warning"}（警告但允许）等 POI 错误样式标识。
     *
     * @param errorStyle 错误样式标识，可为 {@code null}
     * @return 当前配置，便于链式调用
     */
    public DataValidationConfig setErrorStyle(String errorStyle) {
      this.errorStyle = errorStyle;
      return this;
    }

    /**
     * 设置校验失败时的错误弹窗标题。
     *
     * @param errorTitle 标题文本，可为 {@code null}
     * @return 当前配置，便于链式调用
     */
    public DataValidationConfig setErrorTitle(String errorTitle) {
      this.errorTitle = errorTitle;
      return this;
    }

    /**
     * 设置校验失败时展示给用户的错误提示内容。
     *
     * @param error 错误提示文本，可为 {@code null}
     * @return 当前配置，便于链式调用
     */
    public DataValidationConfig setError(String error) {
      this.error = error;
      return this;
    }

    /**
     * 设置校验失败时是否弹出错误提示框。
     *
     * <p>默认 {@code false}，即静默拦截非法输入但不向用户提示原因； 设置为 {@code true} 后配合 {@link #setErrorTitle} / {@link
     * #setError} 展示细节。
     *
     * @param showErrorMessage {@code true} 显示错误弹窗
     * @return 当前配置，便于链式调用
     */
    public DataValidationConfig setShowErrorMessage(boolean showErrorMessage) {
      this.showErrorMessage = showErrorMessage;
      return this;
    }

    public String getFormula1() {
      return formula1;
    }

    public String getFormula2() {
      return formula2;
    }

    public int getOperatorType() {
      return operatorType;
    }

    public int getValidationType() {
      return validationType;
    }

    public String getErrorStyle() {
      return errorStyle;
    }

    public String getErrorTitle() {
      return errorTitle;
    }

    public String getError() {
      return error;
    }

    public boolean isShowErrorMessage() {
      return showErrorMessage;
    }
  }

  /**
   * 构建条件格式配置列表
   *
   * @param condition 条件公式
   * @param trueColor 条件为真时的颜色
   * @param falseColor 条件为假时的颜色
   * @return 条件格式配置列表
   */
  public static List<ConditionalFormattingConfig> buildConditionalFormatting(
      String condition, String trueColor, String falseColor) {
    List<ConditionalFormattingConfig> configs = new ArrayList<>();
    if (condition != null && !condition.isEmpty()) {
      configs.add(new ConditionalFormattingConfig(condition).setFontColor(trueColor).setBold(true));
    }
    return configs;
  }

  /**
   * 应用条件格式到指定区域
   *
   * <p>根据配置的公式设置条件格式规则
   *
   * @param sheet 目标Sheet
   * @param firstRow 起始行
   * @param lastRow 结束行
   * @param firstCol 起始列
   * @param lastCol 结束列
   * @param configs 条件格式配置列表
   */
  public static void applyConditionalFormatting(
      Sheet sheet,
      int firstRow,
      int lastRow,
      int firstCol,
      int lastCol,
      List<ConditionalFormattingConfig> configs) {
    if (configs == null || configs.isEmpty()) {
      return;
    }

    CellRangeAddress range = new CellRangeAddress(firstRow, lastRow, firstCol, lastCol);

    for (ConditionalFormattingConfig config : configs) {
      try {
        ConditionalFormattingRule rule =
            sheet
                .getSheetConditionalFormatting()
                .createConditionalFormattingRule(config.getFormula());

        PatternFormatting patternFmt = rule.createPatternFormatting();
        if (config.getBackgroundColor() != null) {
          patternFmt.setFillBackgroundColor(parseColor(config.getBackgroundColor()));
        }

        sheet
            .getSheetConditionalFormatting()
            .addConditionalFormatting(new CellRangeAddress[] {range}, rule);
      } catch (Exception e) {
        LOG.warn("应用条件格式失败: {}", config.getFormula(), e);
      }
    }
  }

  /**
   * 应用数据验证到指定区域
   *
   * <p>支持数字范围验证、下拉列表验证等
   *
   * <p>P2-12 修复：此前无视 {@code config.getValidationType()} 一律
   * {@code createFormulaListConstraint}——下拉列表的候选值被当作公式（下拉失效），
   * 数字区间/日期验证全部退化为公式列表。现按验证类型分派构造约束， 并接线此前从未生效的
   * errorStyle / errorTitle / error 提示配置。
   *
   * @param sheet 目标Sheet
   * @param firstRow 起始行
   * @param lastRow 结束行
   * @param firstCol 起始列
   * @param lastCol 结束列
   * @param config 数据验证配置
   */
  public static void applyDataValidation(
      Sheet sheet,
      int firstRow,
      int lastRow,
      int firstCol,
      int lastCol,
      DataValidationConfig config) {
    try {
      CellRangeAddressList addressList =
          new CellRangeAddressList(firstRow, lastRow, firstCol, lastCol);
      DataValidationHelper dvHelper = sheet.getDataValidationHelper();
      DataValidationConstraint constraint = createConstraint(dvHelper, config);

      DataValidation validation = dvHelper.createValidation(constraint, addressList);
      validation.setShowErrorBox(config.isShowErrorMessage());
      if (config.getErrorTitle() != null || config.getError() != null) {
        validation.createErrorBox(
            config.getErrorTitle() != null ? config.getErrorTitle() : "",
            config.getError() != null ? config.getError() : "");
      }
      applyErrorStyle(validation, config.getErrorStyle());

      sheet.addValidationData(validation);
    } catch (Exception e) {
      LOG.warn("应用数据验证失败", e);
    }
  }

  /**
   * 按验证类型分派构造约束。
   *
   * <ul>
   *   <li>LIST → 显式列表（逗号分隔候选值拆分，Excel 展示为下拉框）
   *   <li>DATE → 日期约束（yyyy-MM-dd 格式）
   *   <li>INTEGER / DECIMAL / TEXT_LENGTH 及其他 → 数值/文本长度约束
   *       （POI ValidationType 未定义 CUSTOM 常量，自定义公式约束请经 helper 直接构造）
   * </ul>
   *
   * @param dvHelper Sheet 的验证助手
   * @param config 数据验证配置
   * @return 对应类型的 POI 验证约束
   */
  private static DataValidationConstraint createConstraint(
      DataValidationHelper dvHelper, DataValidationConfig config) {
    int type = config.getValidationType();
    if (type == DataValidationConstraint.ValidationType.LIST) {
      // 显式列表：formula1 为逗号分隔的候选值（旧实现误作公式，下拉失效）
      return dvHelper.createExplicitListConstraint(splitListValues(config.getFormula1()));
    }
    if (type == DataValidationConstraint.ValidationType.DATE) {
      return dvHelper.createDateConstraint(
          config.getOperatorType(),
          config.getFormula1(),
          config.getFormula2(),
          DEFAULT_VALIDATION_DATE_FORMAT);
    }
    // INTEGER / DECIMAL / TEXT_LENGTH
    return dvHelper.createNumericConstraint(
        type, config.getOperatorType(), config.getFormula1(), config.getFormula2());
  }

  /** 逗号分隔的候选值拆分（去空白，忽略空段）。 */
  private static String[] splitListValues(String listValues) {
    if (listValues == null || listValues.isEmpty()) {
      return new String[0];
    }
    String[] parts = listValues.split(",");
    List<String> values = new ArrayList<>(parts.length);
    for (String part : parts) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        values.add(trimmed);
      }
    }
    return values.toArray(new String[0]);
  }

  /** 应用错误提示样式（stop 阻止输入 / warning 警告允许 / information 信息提示）。 */
  private static void applyErrorStyle(DataValidation validation, String errorStyle) {
    if (errorStyle == null) {
      return;
    }
    if ("warning".equalsIgnoreCase(errorStyle)) {
      validation.setErrorStyle(DataValidation.ErrorStyle.WARNING);
    } else if ("information".equalsIgnoreCase(errorStyle)) {
      validation.setErrorStyle(DataValidation.ErrorStyle.INFO);
    } else {
      validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
    }
  }

  /**
   * 创建下拉列表验证配置
   *
   * @param listValues 下拉列表值，使用逗号分隔
   * @return 数据验证配置
   */
  public static DataValidationConfig createListValidation(String listValues) {
    DataValidationConfig config =
        new DataValidationConfig(DataValidationConstraint.ValidationType.LIST);
    config.setFormula1(listValues);
    return config;
  }

  /**
   * 创建数字范围验证配置
   *
   * @param min 最小值
   * @param max 最大值
   * @return 数据验证配置
   */
  public static DataValidationConfig createNumberBetweenValidation(double min, double max) {
    DataValidationConfig config =
        new DataValidationConfig(DataValidationConstraint.ValidationType.DECIMAL);
    config.setFormula1(String.valueOf(min));
    config.setFormula2(String.valueOf(max));
    return config;
  }

  /**
   * 创建日期验证配置
   *
   * @return 数据验证配置
   */
  public static DataValidationConfig createDateValidation() {
    return new DataValidationConfig(DataValidationConstraint.ValidationType.DATE);
  }

  /**
   * 解析颜色名称为POI颜色索引
   *
   * <p>支持的颜色名称: RED, BLUE, GREEN, YELLOW, WHITE, BLACK 等
   *
   * @param colorName 颜色名称
   * @return POI颜色索引
   */
  private static short parseColor(String colorName) {
    if (colorName == null) {
      return IndexedColors.AUTOMATIC.getIndex();
    }
    try {
      return IndexedColors.valueOf(colorName.toUpperCase().replace(" ", "_")).getIndex();
    } catch (IllegalArgumentException e) {
      return IndexedColors.AUTOMATIC.getIndex();
    }
  }
}
