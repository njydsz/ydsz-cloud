package com.njydsz.common.excel.annotation;

/**
 * ExcelProperty 类
 *
 * @author ydsz-team
 * @email ydsz-dev@ydszsoft.com
 * @version 1.0.0
 */
import java.lang.annotation.*;

/**
 * Excel属性注解 - 核心映射注解
 *
 * <p>用于标注Java类字段与Excel列的映射关系,是ExcelFacade最核心的注解之一。
 * 支持灵活的配置方式,可指定列名、列索引、日期格式、宽度等属性。</p>
 *
 * <h3>使用位置</h3>
 * <ul>
 *   <li>字段级别 - 标注具体字段与Excel列的映射</li>
 *   <li>类型级别 - 可用于标注类本身(预留扩展)</li>
 * </ul>
 *
 * <h3>映射规则</h3>
 * <ol>
 *   <li>优先使用index指定列索引</li>
 *   <li>其次使用value作为列名进行表头匹配</li>
 *   <li>最后使用字段名作为列名</li>
 * </ol>
 *
 * <h3>示例</h3>
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
 * @see ExcelSheet
 * @see ExcelIgnore
 * @author ydsz-team
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface ExcelProperty {

    /**
     * Excel列名称
     *
     * <p>用于与表头进行精确匹配。
     * 若未指定,则使用字段名作为列名</p>
     *
     * @return 列名称
     */
    String value() default "";

    /**
     * 多列名称支持(用于合并单元格等场景)
     *
     * @return 多列名称数组
     */
    String[] valueArr() default {};

    /**
     * 列索引(从0开始)
     *
     * <p>指定后优先级最高,会忽略value的匹配逻辑。
     * 设置为-1表示未指定,会根据value或字段名自动匹配。</p>
     *
     * @return 列索引,-1表示自动
     */
    int index() default -1;

    /**
     * 多列索引支持
     *
     * @return 多列索引数组
     */
    int[] indexArr() default {};

    /**
     * 格式化格式(预留)
     *
     * @return 格式化字符串
     */
    String format() default "";

    /**
     * 日期格式
     *
     * <p>仅在字段类型为Date或Calendar时生效。
     * 支持的格式如:"yyyy-MM-dd"、"yyyy/MM/dd HH:mm:ss"等</p>
     *
     * @return 日期格式字符串
     */
    String dateFormat() default "";

    /**
     * 数字格式
     *
     * <p>用于格式化数字类型字段的输出。
     * 支持的格式如:"#,##0.00"、".00"等</p>
     *
     * @return 数字格式字符串
     */
    String numberFormat() default "";

    /**
     * 默认值
     *
     * <p>当单元格值为空时使用的默认值</p>
     *
     * @return 默认值字符串
     */
    String defaultValue() default "";

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
     * 列排序顺序
     *
     * <p>数值越小越靠前,默认值为0。
     * 用于控制多字段时的输出顺序</p>
     *
     * @return 排序顺序
     */
    int order() default 0;

    /**
     * 是否必填
     *
     * <p>用于数据验证(预留功能)</p>
     *
     * @return true表示必填
     */
    boolean required() default false;

    /**
     * 是否忽略该字段
     *
     * <p>设置为true时,该字段不会参与Excel映射</p>
     *
     * @return true表示忽略
     */
    boolean ignore() default false;

    /**
     * 自定义转换器名称(预留)
     *
     * @return 转换器名称
     */
    String converter() default "";

    /**
     * 自定义转换器类
     *
     * <p>指定一个实现Converter接口的转换器类,
     * 用于自定义类型转换逻辑</p>
     *
     * @return 转换器类
     */
    Class<?> converterClass() default void.class;

    /**
     * 多列名称(用于复杂映射场景)
     *
     * @return 多列名称数组
     */
    String[] names() default {};

    /**
     * 多列排序顺序
     *
     * @return 多列顺序数组
     */
    int[] orders() default {};

    /**
     * 公式表达式
     *
     * <p>用于设置单元格的公式。
     * 支持Excel公式语法,如:"SUM(A1:A10)", "IF(B1>60,\"及格\",\"不及格\")"等。
     * 公式会在写入时设置到单元格中,读取时被忽略。</p>
     *
     * <h3>使用示例</h3>
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
     * @ExcelProperty(value = "等级", formula = "IF(E2>=90,\"A\",IF(E2>=80,\"B\",IF(E2>=60,\"C\",\"D\")))")
     * private String grade;
     * }</pre>
     *
     * @return 公式表达式字符串
     */
    String formula() default "";

    // ==================== 数据验证属性 ====================

    /**
     * 字符串最大长度
     *
     * <p>仅对 String 类型字段生效。设置为 -1 表示不限制。</p>
     *
     * @return 最大长度
     */
    int maxLength() default -1;

    /**
     * 数值最小值（字符串表示）
     *
     * <p>仅对 Number 类型字段生效。为空字符串表示不限制。</p>
     *
     * @return 最小值
     */
    String minValue() default "";

    /**
     * 数值最大值（字符串表示）
     *
     * <p>仅对 Number 类型字段生效。为空字符串表示不限制。</p>
     *
     * @return 最大值
     */
    String maxValue() default "";

    /**
     * 正则表达式验证
     *
     * <p>仅对 String 类型字段生效。为空字符串表示不验证。</p>
     *
     * @return 正则表达式
     */
    String pattern() default "";

    /**
     * 自定义验证错误消息
     *
     * <p>为空时使用默认错误消息。</p>
     *
     * @return 错误消息
     */
    String errorMessage() default "";
}
