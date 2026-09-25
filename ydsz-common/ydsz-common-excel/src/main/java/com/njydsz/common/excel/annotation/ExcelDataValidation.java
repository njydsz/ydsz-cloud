package com.njydsz.common.excel.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据验证注解 — 在 Excel 中生成 DataValidation（数据有效性校验）。
 *
 * <p>支持的验证类型：
 *
 * <ul>
 *   <li>{@link ValidationType#LIST} — 下拉列表（公式引用或内联值）</li>
 *   <li>{@link ValidationType#INTEGER} / {@link ValidationType#DECIMAL} — 数值范围</li>
 *   <li>{@link ValidationType#DATE} / {@link ValidationType#TIME} — 日期/时间范围</li>
 *   <li>{@link ValidationType#LENGTH} — 文本长度限制</li>
 *   <li>{@link ValidationType#CUSTOM} — 自定义公式</li>
 * </ul>
 *
 * <pre>{@code
 * public class UserInputDto {
 *     @ExcelDataValidation(type = ValidationType.LIST, formula = "\"男,女\"", showDropdown = true)
 *     @ExcelProperty(value = "性别", index = 2)
 *     String gender;
 *
 *     @ExcelDataValidation(type = ValidationType.INTEGER, operator = ValidationOperator.BETWEEN,
 *         formula1 = "0", formula2 = "150", errorMessage = "年龄必须在 0-150 之间")
 *     @ExcelProperty(value = "年龄", index = 3)
 *     Integer age;
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExcelDataValidation {

  /** 验证类型 */
  ValidationType type() default ValidationType.ANY;

  /** 验证操作符（用于 INTEGER / DECIMAL / DATE 的范围） */
  ValidationOperator operator() default ValidationOperator.NONE;

  /** 公式 / 值 1（列表内联值用引号包围的字符串如 {@code "\"A,B,C\"}，范围起始值） */
  String formula1() default "";

  /** 公式 / 值 2（范围结束值） */
  String formula2() default "";

  /** 是否忽略空白单元格 */
  boolean ignoreBlank() default true;

  /** 是否下拉列表（仅 LIST 类型有效） */
  boolean showDropdown() default true;

  /** 输入提示标题 */
  String promptTitle() default "";

  /** 输入提示内容 */
  String promptContent() default "";

  /** 错误提示标题 */
  String errorTitle() default "输入错误";

  /** 错误提示内容 */
  String errorMessage() default "输入值无效";

  /** 错误提示样式（stop / warning / information） */
  ErrorStyle errorStyle() default ErrorStyle.STOP;

  /** 验证类型枚举 */
  enum ValidationType {
    ANY(""),
    INTEGER("whole"),
    DECIMAL("decimal"),
    LIST("list"),
    DATE("date"),
    TIME("time"),
    LENGTH("textLength"),
    CUSTOM("custom");

    public final String ooxmlType;

    ValidationType(String ooxmlType) {
      this.ooxmlType = ooxmlType;
    }
  }

  /** 验证操作符 */
  enum ValidationOperator {
    NONE(""),
    BETWEEN("between"),
    NOT_BETWEEN("notBetween"),
    EQUAL("equal"),
    NOT_EQUAL("notEqual"),
    GREATER_THAN("greaterThan"),
    GREATER_EQUAL("greaterOrEqual"),
    LESS_THAN("lessThan"),
    LESS_EQUAL("lessOrEqual");

    public final String ooxmlOperator;

    ValidationOperator(String ooxmlOperator) {
      this.ooxmlOperator = ooxmlOperator;
    }
  }

  /** 错误提示样式 */
  enum ErrorStyle {
    STOP("stop"),
    WARNING("warning"),
    INFORMATION("information");

    public final String value;

    ErrorStyle(String value) {
      this.value = value;
    }
  }
}
