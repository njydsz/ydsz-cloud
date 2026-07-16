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
 *   <li>指定命名策略</li>
 *   <li>配置循环引用处理</li>
 *   <li>支持多态类型（seeAlso）</li>
 *   <li>自动类型识别（autoType）</li>
 * </ul>
 * 
 * <p><b>使用示例：</b></p>
 * <pre>
 * {@literal @}JsonClass(
 *     ordering = {"id", "name", "email"},
 *     ignores = {"password", "secretKey"},
 *     typeKey = "@type",
 *     seeAlso = {UserAdmin.class, UserGuest.class}
 * )
 * public class User {
 *     private Long id;
 *     private String name;
 *     private String email;
 *     private String password;
 * }
 * </pre>
 * 
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface JsonClass {
    
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
     * 是否处理循环引用
     * 
     * @return 是否处理循环引用
     */
    boolean handleCircularReference() default true;
    
    /**
     * 是否输出 null 值
     *
     * @return 是否输出 null 值
     */
    boolean writeNulls() default false;

    /**
     * 是否输出类名
     *
     * <p>如果为 true，则在序列化时输出类的全限定名</p>
     *
     * @return 是否输出类名
     */
    boolean writeClassName() default false;

    /**
     * 日期格式
     *
     * @return 日期格式字符串
     */
    String dateFormat() default "";
    
    /**
     * 是否启用快速模式（跳过某些检查以提升性能）
     * 
     * @return 是否启用快速模式
     */
    boolean fastMode() default false;
    
    // ==================== 多态类型支持（参考@JSONType） ====================
    
    /**
     * 类型标识字段名称
     * 
     * <p>用于多态反序列化时标识实际类型。</p>
     * 
     * <p>默认使用 "@type" 作为类型键。</p>
     * 
     * @return 类型标识字段名称
     */
    String typeKey() default "@type";
    
    /**
     * 可见的子类型
     * 
     * <p>指定反序列化时可以识别的子类型。</p>
     * 
     * <p>用于多态反序列化，类似 Jackson 的 {@code @JsonSubTypes}。</p>
     * 
     * @return 子类型数组
     */
    Class<?>[] seeAlso() default {};
    
    /**
     * 子类型的类型名称
     * 
     * <p>与 {@link #seeAlso()} 配合使用，指定每个子类型的类型名称。</p>
     * 
     * @return 类型名称数组
     */
    String[] seeAlsoNames() default {};
    
    /**
     * 是否启用 autoType
     * 
     * <p>如果为 true，则允许反序列化时自动识别类型。</p>
     * 
     * <p><b>注意：</b>启用 autoType 可能存在安全风险，请谨慎使用。</p>
     * 
     * @return 是否启用 autoType
     */
    boolean autoType() default false;
    
    /**
     * 是否使用 ordinal 序列化枚举
     * 
     * <p>如果为 true，则枚举使用 ordinal 值（数字）序列化，否则使用 name（字符串）。</p>
     * 
     * @return 是否使用 ordinal 序列化枚举
     */
    boolean serializeEnumUsingOrdinal() default false;
    
    /**
     * 序列化特性
     * 
     * @return 序列化特性数组
     */
    SerializeFeature[] features() default {};
    
    /**
     * 反序列化特性
     * 
     * @return 反序列化特性数组
     */
    DeserializeFeature[] deserializeFeatures() default {};
    
    /**
     * 序列化特性枚举
     */
    enum SerializeFeature {
        /** 使用 Bean 名称 */
        UseBeanName,
        /** 禁用循环引用检测 */
        DisableCircularReferenceDetect,
        /** 输出 null 值 */
        WriteMapNullValue,
        /** 空集合输出为 null */
        WriteNullListAsEmpty,
        /** 空字符串输出为 null */
        WriteNullStringAsEmpty,
        /** 空布尔输出为 null */
        WriteNullBooleanAsEmpty,
        /** 空数字输出为 null */
        WriteNullNumberAsEmpty,
        /** 使用 ISO-8601 格式输出日期 */
        UseISO8601DateFormat,
        /** 单引号格式 */
        UseSingleQuotes,
        /** 使用下划线命名 */
        SnakeCase,
        /** 使用短横线命名 */
        KebabCase,
        /** 排序输出 */
        SortField,
        /** 使用对象池 */
        UseObjectPool,
        /** 非字段字段也输出 */
        NotWriteDefaultValue,
        /** 浏览器兼容模式 */
        BrowserCompatible,
        /** 输出类的 Class 对象 */
        WriteClassName,
        /** 忽略 getter */
        IgnoreGetters,
        /** 忽略 setter */
        IgnoreSetters,
        /** 忽略非字段 getter */
        IgnoreNonFieldGetter,
        /** 不输出 root 对象 */
        NotWriteRootClassName,
        /** 不输出 null 值 */
        NotWriteNullValue,
        /** 不输出空数组 */
        NotWriteEmptyArray,
        /** 枚举使用 ordinal 序列化 */
        SerializeEnumUsingOrdinal
    }
    
    /**
     * 反序列化特性枚举
     */
    enum DeserializeFeature {
        /** 支持注释 */
        SupportComment,
        /** 支持单引号 */
        SupportSingleQuote,
        /** 支持非标准引号 */
        SupportNonStandardQuote,
        /** 支持数组分隔符 */
        SupportArbitraryComma,
        /** 忽略不匹配的字段 */
        IgnoreNotMatch,
        /** 禁用 ASM */
        DisableASM,
        /** 禁用 autoType */
        DisableAutoType,
        /** 启用字段类型推断 */
        EnableFieldBased,
        /** 使用对象池 */
        UseObjectPool,
        /** 忽略控制字符 */
        IgnoreControlChars,
        /** 支持 JSON5 */
        SupportJSON5
    }
    
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
        ORIGINAL
    }
}
