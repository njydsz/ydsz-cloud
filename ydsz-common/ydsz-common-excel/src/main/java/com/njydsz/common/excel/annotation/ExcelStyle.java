package com.njydsz.common.excel.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.njydsz.common.excel.core.ExcelWriter;

/**
 * Excel样式注解 - 用于自定义单元格样式
 *
 * <p>可应用于JavaBean的字段上,控制Excel单元格的样式显示。 支持表头样式和数据样式两种配置。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * public class User {
 *     @ExcelProperty("姓名")
 *     @ExcelStyle(fontColor = "RED", backgroundColor = "YELLOW")
 *     private String name;
 *
 *     @ExcelProperty("年龄")
 *     @ExcelStyle(dataStyle = DataStyle.class)
 *     private Integer age;
 * }
 * }</pre>
 *
 * @author ydsz-team

 * @version 26.09.01
 * @see ExcelWriter
 * @since 26.09.01
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExcelStyle {

  /**
   * 表头是否加粗
   *
   * @return true表示表头加粗
   */
  boolean headBold() default true;

  /**
   * 表头字体颜色
   *
   * <p>支持颜色值:
   *
   * <ul>
   *   <li>RED, BLUE, GREEN, YELLOW, WHITE, BLACK等标准颜色
   *   <li>或使用ARGB格式如"#FF0000"
   * </ul>
   *
   * @return 字体颜色名称或十六进制值
   */
  String headFontColor() default "BLACK";

  /**
   * 表头背景填充颜色
   *
   * @return 背景填充颜色名称或十六进制值
   */
  String headBackgroundColor() default "GRAY_25_PERCENT";

  /**
   * 表头字号
   *
   * @return 字体大小(磅值)
   */
  short headFontSize() default 11;

  /**
   * 表头文字水平对齐方式
   *
   * @return 对齐方式:CENTER, LEFT, RIGHT
   */
  String headHorizontalAlignment() default "CENTER";

  /**
   * 数据行是否加粗
   *
   * @return true表示数据行加粗
   */
  boolean dataBold() default false;

  /**
   * 数据字体颜色
   *
   * @return 字体颜色名称或十六进制值
   */
  String dataFontColor() default "BLACK";

  /**
   * 数据背景填充颜色
   *
   * @return 背景填充颜色名称或十六进制值
   */
  String dataBackgroundColor() default "NO_FILL";

  /**
   * 数据字号
   *
   * @return 字体大小(磅值)
   */
  short dataFontSize() default 10;

  /**
   * 数据文字水平对齐方式
   *
   * @return 对齐方式:CENTER, LEFT, RIGHT
   */
  String dataHorizontalAlignment() default "LEFT";

  /**
   * 数据行垂直对齐方式
   *
   * @return 对齐方式:CENTER, TOP, BOTTOM
   */
  String dataVerticalAlignment() default "CENTER";

  /**
   * 是否自动换行
   *
   * @return true表示启用自动换行
   */
  boolean wrapText() default false;

  /**
   * 是否设置边框
   *
   * @return true表示显示边框
   */
  boolean border() default false;

  /**
   * 边框样式
   *
   * @return 边框样式:THIN, MEDIUM, THICK等
   */
  String borderStyle() default "THIN";

  /**
   * 上边框颜色
   *
   * @return 边框颜色
   */
  String borderTopColor() default "BLACK";

  /**
   * 下边框颜色
   *
   * @return 边框颜色
   */
  String borderBottomColor() default "BLACK";

  /**
   * 左边框颜色
   *
   * @return 边框颜色
   */
  String borderLeftColor() default "BLACK";

  /**
   * 右边框颜色
   *
   * @return 边框颜色
   */
  String borderRightColor() default "BLACK";
}
