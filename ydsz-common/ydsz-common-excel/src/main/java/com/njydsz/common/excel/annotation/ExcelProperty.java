package com.njydsz.common.excel.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excel 属性注解 — Java 字段与 Excel 列的映射关系。
 *
 * <p>是 {@code ydsz-common-excel} 最核心的注解，支持指定列名、列索引、 日期格式、数字格式、列宽、排序顺序及公式等属性。
 *
 * <h3>映射优先级</h3>
 *
 * <ol>
 *   <li>{@link #index()} — 显式列索引（最高优先级）
 *   <li>{@link #value()} — 列名，用于表头匹配
 *   <li>字段名 — 兜底匹配
 * </ol>
 *
 * <h3>示例</h3>
 *
 * <pre>{@code
 * public class User {
 *     @ExcelProperty(value = "姓名", order = 1)
 *     private String name;
 *
 *     @ExcelProperty(value = "年龄", index = 2, width = 10)
 *     private Integer age;
 *
 *     @ExcelProperty(value = "生日", dateFormat = "yyyy-MM-dd")
 *     private Date birthday;
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExcelSheet
 * @see ExcelIgnore
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface ExcelProperty {

  /**
   * Excel 列名称。
   *
   * <p>用于与表头进行精确匹配。若未指定，则使用字段名作为列名。
   *
   * @return 列名称
   */
  String value() default "";

  /**
   * 列索引（从 0 开始）。
   *
   * <p>指定后优先级最高，会忽略 {@link #value()} 的匹配逻辑。 设置为 {@code -1} 表示未指定，会根据列名或字段名自动匹配。
   *
   * @return 列索引，-1 表示自动
   */
  int index() default -1;

  /**
   * 日期格式。
   *
   * <p>仅在字段类型为 {@code Date} 或 {@code Calendar} 时生效。 支持的格式如：{@code yyyy-MM-dd}、{@code yyyy/MM/dd
   * HH:mm:ss} 等。
   *
   * @return 日期格式字符串
   */
  String dateFormat() default "";

  /**
   * 数字格式。
   *
   * <p>用于格式化数字类型字段的输出。 支持的格式如：{@code #,##0.00}、{@code .00} 等。
   *
   * @return 数字格式字符串
   */
  String numberFormat() default "";

  /**
   * 默认值。
   *
   * <p>当单元格值为空时使用的默认值。
   *
   * @return 默认值字符串
   */
  String defaultValue() default "";

  /**
   * 列宽度。
   *
   * <p>指定列的宽度（单位：字符）。设置为 {@code -1} 表示自动宽度。
   *
   * @return 列宽度
   */
  int width() default -1;

  /**
   * 列排序顺序。
   *
   * <p>数值越小越靠前，默认值为 0。用于控制多字段时的输出顺序。
   *
   * @return 排序顺序
   */
  int order() default 0;

  /**
   * 是否忽略该字段。
   *
   * <p>设置为 {@code true} 时，该字段不参与 Excel 映射。
   *
   * @return {@code true} 表示忽略
   */
  boolean ignore() default false;

  /**
   * 公式表达式。
   *
   * <p>用于设置单元格的公式，支持 Excel 公式语法，如 {@code SUM(A1:A10)}、 {@code IF(B1>60,"及格","不及格")}
   * 等。公式在写入时设置到单元格中，读取时被忽略。
   *
   * <h3>使用示例</h3>
   *
   * <pre>{@code
   * // 总成绩 = 数学 + 英语 + 语文
   * @ExcelProperty(value = "总成绩", formula = "B2+C2+D2")
   * private Double totalScore;
   *
   * // 平均分 = 总分 / 3
   * @ExcelProperty(value = "平均分", formula = "AVERAGE(B2:D2)")
   * private Double avgScore;
   *
   * // 等级判定
   * @ExcelProperty(value = "等级", formula = "IF(E2>=90,"A",IF(E2>=80,"B",IF(E2>=60,"C","D")))")
   * private String grade;
   * }</pre>
   *
   * @return 公式表达式字符串
   */
  String formula() default "";
}
