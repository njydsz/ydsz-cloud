package com.njydsz.common.excel.annotation;

/**
 * ExcelHead 类
 *
 * @author ydsz-team
 * @email ydsz-dev@ydszsoft.com
 * @version 1.0.0
 */
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ExcelHead注解 - 表头样式配置
 *
 * <p>用于精细控制Excel表头单元格的样式,包括字体、颜色、宽度等。
 * 该注解标注在Java类的字段级别。</p>
 *
 * <h3>样式说明</h3>
 * <ul>
 *   <li>字体 - 名称、大小、加粗、斜体、颜色</li>
 *   <li>背景 - 填充颜色</li>
 *   <li>对齐 - 水平和垂直对齐方式</li>
 *   <li>换行 - 是否自动换行</li>
 * </ul>
 *
 * <h3>示例</h3>
 * <pre>{@code
 * public class User {
 *     @ExcelHead(
 *         value = "姓名",
 *         width = 15,
 *         isBold = true,
 *         fontColor = 0xFF0000,
 *         backgroundColor = 0xFFFF00
 *     )
 *     private String name;
 * }
 * }</pre>
 *
 * @see ExcelProperty
 * @author ydsz-team
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExcelHead {

    /**
     * 表头名称
     *
     * <p>指定表头显示的名称。
     * 若未指定,使用字段名作为默认值</p>
     *
     * @return 表头名称
     */
    String value() default "";

    /**
     * 列宽度
     *
     * <p>指定列的宽度(单位:字符)。
     * 设置为-1表示使用自动宽度</p>
     *
     * @return 列宽度
     */
    int width() default -1;

    /**
     * 排序顺序
     *
     * <p>数值越小越靠前。
     * 默认值为0</p>
     *
     * @return 排序顺序
     */
    int order() default 0;

    /**
     * 是否加粗
     *
     * @return true表示加粗
     */
    boolean isBold() default false;

    /**
     * 字体名称
     *
     * <p>支持的字体如:"Calibri"、"Arial"、"宋体"等。
     * 若未指定,使用默认字体</p>
     *
     * @return 字体名称
     */
    String fontName() default "";

    /**
     * 字体大小
     *
     * <p>单位:磅(point)。默认值为11</p>
     *
     * @return 字体大小
     */
    int fontSize() default 11;

    /**
     * 字体颜色
     *
     * <p>使用POI的短整型颜色值,格式为0xRRGGBB。
     * 例如:0xFF0000表示红色</p>
     *
     * @return 字体颜色
     */
    short fontColor() default 0;

    /**
     * 背景颜色
     *
     * <p>使用POI的短整型颜色值。
     * 需要配合相应的填充模式使用</p>
     *
     * @return 背景颜色
     */
    short backgroundColor() default 0;

    /**
     * 是否自动换行
     *
     * @return true表示自动换行
     */
    boolean wrapText() default false;
}
