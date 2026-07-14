package com.njydsz.pmis.common.json.annotation;

import java.lang.annotation.*;

/**
 * YdszJson 字段注解（参考 fastjson2 的@JSONField 和 Jackson 的@JsonProperty）
 *
 * <p>用于标注 Java 字段，控制序列化和反序列化行为。</p>
 *
 * <p><b>主要功能：</b></p>
 * <ul>
 *   <li>指定 JSON 字段名称</li>
 *   <li>忽略字段（不参与序列化/反序列化）</li>
 *   <li>指定日期格式</li>
 *   <li>控制 null 值输出</li>
 *   <li>指定序列化/反序列化序号</li>
 *   <li>使用 Bean 名称</li>
 *   <li>直接序列化字段</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * public class User {
 *     {@literal @}YdszJsonField("user_id")
 *     private Long id;
 *
 *     {@literal @}YdszJsonField(value = "user_name", required = true)
 *     private String name;
 *
 *     {@literal @}YdszJsonField(ignore = true)
 *     private String password;
 *
 *     {@literal @}YdszJsonField(format = "yyyy-MM-dd")
 *     private Date birthday;
 *
 *     {@literal @}YdszJsonField(ordinal = 1)
 *     private Integer priority;
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @email limw1888@126.com
 * @since 1.3.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
public @interface YdszJsonField {

    /**
     * JSON 字段名称
     *
     * <p>指定序列化后在 JSON 中的字段名。</p>
     *
     * <p>默认使用 Java 字段名。</p>
     *
     * @return JSON 字段名称
     */
    String value() default "";

    /**
     * JSON 字段名称（别名，同 value）
     *
     * @return JSON 字段名称
     */
    String name() default "";

    /**
     * 是否忽略该字段
     *
     * <p>如果为 true，则该字段不参与序列化和反序列化。</p>
     *
     * <p>类似于 Jackson 的 {@code @JsonIgnore}。</p>
     *
     * @return 是否忽略
     */
    boolean ignore() default false;

    /**
     * 是否忽略 getter
     *
     * <p>如果为 true，则序列化时忽略该字段的 getter 方法。</p>
     *
     * @return 是否忽略 getter
     */
    boolean ignoreGetters() default false;

    /**
     * 是否忽略 setter
     *
     * <p>如果为 true，则反序列化时忽略该字段的 setter 方法。</p>
     *
     * @return 是否忽略 setter
     */
    boolean ignoreSetters() default false;

    /**
     * 是否必需
     *
     * <p>如果为 true，则反序列化时该字段不能为 null。</p>
     *
     * <p>如果为 null 会抛出异常。</p>
     *
     * @return 是否必需
     */
    boolean required() default false;

    /**
     * 日期格式
     *
     * <p>指定日期字段的格式。</p>
     *
     * <p>仅对 Date、LocalDateTime 等日期类型有效。</p>
     *
     * @return 日期格式字符串
     */
    String format() default "";

    /**
     * 序列化时的默认值
     *
     * <p>当字段值为 null 时，使用此默认值。</p>
     *
     * @return 默认值
     */
    String defaultValue() default "";

    /**
     * 是否输出 null 值
     *
     * <p>如果为 true，则即使字段值为 null 也会输出。</p>
     *
     * @return 是否输出 null 值
     */
    boolean writeNull() default false;

    /**
     * 字段描述
     *
     * <p>用于文档生成或调试。</p>
     *
     * @return 字段描述
     */
    String description() default "";

    /**
     * 字段的序号（控制输出顺序）
     *
     * <p>序号越小，越先输出。</p>
     *
     * <p>未指定序号的字段按默认顺序输出。</p>
     *
     * @return 字段序号
     */
    int ordinal() default 0;

    /**
     * 是否使用 Bean 名称
     *
     * <p>如果为 true，则使用 Bean 的名称作为 JSON 字段名。</p>
     *
     * @return 是否使用 Bean 名称
     */
    boolean useBeanName() default false;

    /**
     * 是否直接序列化字段
     *
     * <p>如果为 true，则直接序列化字段值，而不是调用 getter 方法。</p>
     *
     * @return 是否直接序列化字段
     */
    boolean direct() default false;

    /**
     * 序列化时使用的方法
     *
     * <p>指定序列化时调用的方法名。</p>
     *
     * @return 方法名
     */
    String serializeUsing() default "";

    /**
     * 反序列化时使用的方法
     *
     * <p>指定反序列化时调用的方法名。</p>
     *
     * @return 方法名
     */
    String deserializeUsing() default "";

    /**
     * 是否使用 fastMode
     *
     * <p>如果为 true，则使用快速模式序列化该字段。</p>
     *
     * @return 是否使用 fastMode
     */
    boolean fastMode() default false;

    /**
     * 是否输出为 JSON 对象
     *
     * <p>如果为 true，则将该字段值输出为 JSON 对象。</p>
     *
     * @return 是否输出为 JSON 对象
     */
    boolean jsonDirect() default false;

    /**
     * 双精度浮点数格式
     *
     * <p>指定 double/BigDecimal 等类型的输出格式。</p>
     *
     * <p>例如："0.00" 表示保留两位小数。</p>
     *
     * @return 数字格式
     */
    String numberFormat() default "";

    /**
     * 是否使用 HTML 安全
     *
     * <p>如果为 true，则对字符串进行 HTML 转义。</p>
     *
     * @return 是否使用 HTML 安全
     */
    boolean htmlSafe() default false;

    /**
     * 最大深度
     *
     * <p>嵌套对象的最大序列化深度。</p>
     *
     * @return 最大深度
     */
    int maxDepth() default 2048;

    /**
     * 是否不输出
     *
     * <p>如果为 true，则不输出该字段。</p>
     *
     * @return 是否不输出
     */
    boolean notWrite() default false;

    /**
     * 是否不输出 null 值
     *
     * <p>如果为 true，则当值为 null 时不输出。</p>
     *
     * @return 是否不输出 null 值
     */
    boolean notWriteNullValue() default false;

    /**
     * 是否不输出默认值
     *
     * <p>如果为 true，则当值为默认值时不输出。</p>
     *
     * @return 是否不输出默认值
     */
    boolean notWriteDefaultValue() default false;

    /**
     * 是否输出为类名
     *
     * <p>如果为 true，则输出类的完整名称。</p>
     *
     * @return 是否输出为类名
     */
    boolean writeClassName() default false;
}
