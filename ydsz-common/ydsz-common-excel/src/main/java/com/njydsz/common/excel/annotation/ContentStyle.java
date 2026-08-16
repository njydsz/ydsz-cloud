package com.njydsz.common.excel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ContentStyle 注解：声明式标记单元格的内容样式。
 *
 * <p>标注在导出模型的字段上，控制生成 Excel 时该单元格的隐藏、锁定、
 * 对齐、背景色、数字格式、自动换行等样式。所有数值型属性使用
 * {@code -1} 表示「不设置」（沿用默认样式）。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ContentStyle {

    /**
     * 是否隐藏该列。
     *
     * @return {@code true} 表示隐藏列，默认 false
     */
    boolean hidden() default false;

    /**
     * 单元格是否锁定（受工作表保护时生效）。
     *
     * @return {@code true} 表示锁定，默认 true
     */
    boolean locked() default true;

    /**
     * 水平对齐方式（POI HorizontalAlignment 的 short 值）。
     *
     * @return 对齐值，-1 表示不设置
     */
    short horizontalAlignment() default -1;

    /**
     * 垂直对齐方式（POI VerticalAlignment 的 short 值）。
     *
     * @return 对齐值，-1 表示不设置
     */
    short verticalAlignment() default -1;

    /**
     * 背景色（POI IndexedColors 的 short 值）。
     *
     * @return 颜色值，-1 表示不设置
     */
    short backgroundColor() default -1;

    /**
     * 数字格式字符串（如 {@code "0.00%"}、{@code "yyyy-mm-dd"}）。
     *
     * @return 格式串，空字符串表示不设置
     */
    String dataFormat() default "";

    /**
     * 是否自动换行。
     *
     * @return {@code true} 表示自动换行，默认 false
     */
    boolean wrapText() default false;

    /**
     * 是否收缩适应（文本过多时缩小字体）。
     *
     * @return {@code true} 表示收缩适应，默认 false
     */
    boolean shrinkToFit() default false;
}
