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
 * @author ydsz-team
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
