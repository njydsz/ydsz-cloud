package com.njydsz.common.excel.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 合并单元格注解 — 标记字段参与纵向合并。
 *
 * <p>当连续行的该字段值相同时，自动合并为一个单元格区域。
 *
 * <p>示例：
 *
 * <pre>{@code
 * public class OrderDto {
 *     @ExcelMerge
 *     @ExcelProperty(value = "订单号", index = 0)
 *     String orderId;
 *
 *     @ExcelProperty(value = "商品名", index = 1)
 *     String productName;
 *
 *     @ExcelProperty(value = "金额", index = 2)
 *     BigDecimal amount;
 * }
 * }</pre>
 *
 * <p>仅支持纵向合并（行合并），不支持复杂跨列合并。合并操作须在写入完成后（{@code close()} 时）施加。
 *
 * @author ydsz-team
 * @since 26.09.25
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExcelMerge {

  /**
   * 是否仅合并空白后的连续值。
   *
   * <p>若为空（默认 false），相邻相同值即合并；若为 true，仅合并非空连续值（空白行打断）。
   */
  boolean ignoreEmpty() default false;

  /**
   * Primary level for multi-column merge (simple single-column merge by default).
   *
   * <p>保留给将来多列联动合并使用。
   */
  int primaryLevel() default 0;
}
