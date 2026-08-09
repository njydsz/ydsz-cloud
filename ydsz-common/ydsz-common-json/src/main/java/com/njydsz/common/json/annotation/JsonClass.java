package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

/**
 * Json 类级别注解（参考 Jackson 的@JsonIgnoreProperties 和 FastJSON2 的@JSONType）
 *
 * <p>用于标注 Java 类，控制整体序列化和反序列化行为。</p>
 *
 * <p><b>主要功能：</b></p>
 * <ul>
 *   <li>指定字段排序</li>
 *   <li>忽略特定字段</li>
 *   <li>指定包含字段</li>
 *   <li>指定命名策略</li>
 *   <li>类级日期格式 / null 输出 / 枚举序列化方式</li>
 *   <li>AutoType 白名单标记（description）</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * {@literal @}JsonClass(
 *     ordering = {"id", "name", "email"},
 *     ignores = {"password", "secretKey"},
 *     description = "用户实体"
 * )
 * public class User {
 *     private Long id;
 *     private String name;
 *     private String email;
 *     private String password;
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface JsonClass {

    /**
     * 类的描述信息（文档用途，标记类可安全反序列化等）。
     *
     * <p>仅作为元信息存储，不参与序列化/反序列化逻辑。</p>
     *
     * @return 描述字符串，默认空
     */
    String description() default "";

    /**
     * 字段排序（指定字段的输出顺序）
     *
     * @return 字段名称数组
     */
    String[] ordering() default {};

    /**
     * 忽略的字段（不参与序列化和反序列化）
     *
     * @return 字段名称数组
     */
    String[] ignores() default {};

    /**
     * 包含的字段（只有这些字段参与序列化）
     *
     * @return 字段名称数组
     */
    String[] includes() default {};

    /**
     * 命名策略
     *
     * @return 命名策略
     */
    NamingStrategy naming() default NamingStrategy.CAMEL_CASE;

    /**
     * 是否输出类名（序列化时写入 {@code @class} 字段标识实际类型）。
     *
     * <p>用于多态场景下的类型保留，对标 fastjson2 的 serializerFeature WriteClassName。</p>
     *
     * @return 是否输出类名，默认 false
     */
    boolean writeClassName() default false;

    /**
     * 类级别日期格式（覆盖字段级 {@link JsonFormat} 的 pattern）。
     *
     * <p>空字符串表示不启用类级日期格式，使用字段级配置或默认 toString。</p>
     *
     * @return 日期格式字符串，默认空
     */
    String dateFormat() default "";

    /**
     * 是否输出 null 值字段（类级别控制，覆盖全局配置）。
     *
     * @return 是否输出 null，默认 false
     */
    boolean writeNulls() default false;

    /**
     * 枚举是否以 ordinal（序号）形式序列化。
     *
     * <p>默认 false，枚举以 name() 字符串序列化。</p>
     *
     * @return 是否使用 ordinal 序列化枚举，默认 false
     */
    boolean serializeEnumUsingOrdinal() default false;

    /**
     * 命名策略枚举
     */
    enum NamingStrategy {
        /** 驼峰命名（默认） */
        CAMEL_CASE,
        /** 下划线命名 */
        SNAKE_CASE,
        /** 短横线命名 */
        KEBAB_CASE,
        /** 原始名称 */
        ORIGINAL;

        /**
         * 转换为统一的 PropertyNamingStrategy（桥接方法）。
         *
         * @return 对应的 PropertyNamingStrategy，ORIGINAL 返回 null（不做转换）
         */
        public com.njydsz.common.json.naming.PropertyNamingStrategy toPropertyNamingStrategy() {
            switch (this) {
                case SNAKE_CASE: return com.njydsz.common.json.naming.PropertyNamingStrategy.SNAKE_CASE;
                case KEBAB_CASE: return com.njydsz.common.json.naming.PropertyNamingStrategy.KEBAB_CASE;
                case CAMEL_CASE: return com.njydsz.common.json.naming.PropertyNamingStrategy.LOWER_CAMEL_CASE;
                default: return null;
            }
        }
    }
}
