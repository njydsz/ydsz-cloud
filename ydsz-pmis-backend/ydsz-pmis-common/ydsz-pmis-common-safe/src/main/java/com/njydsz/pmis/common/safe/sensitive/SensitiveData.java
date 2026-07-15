package com.njydsz.pmis.common.safe.sensitive;

import java.lang.annotation.*;

/**
 * 敏感数据注解
 *
 * <p>标注在字段上，用于标记需要进行脱敏处理的敏感数据。
 * 支持多种脱敏策略，由 {@link SensitiveType} 定义。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class UserDTO {
 *     @SensitiveData(SensitiveType.CHINESE_NAME)
 *     private String name;  // "张三" → "张*"
 *
 *     @SensitiveData(SensitiveType.ID_CARD)
 *     private String idCard;  // "110101199001011234" → "110101********1234"
 *
 *     @SensitiveData(SensitiveType.PHONE)
 *     private String phone;  // "13800138000" → "138****8000"
 *
 *     @SensitiveData(SensitiveType.EMAIL)
 *     private String email;  // "test@example.com" → "t**t@example.com"
 *
 *     @SensitiveData(SensitiveType.PASSWORD)
 *     private String password;  // 不返回或返回 "******"
 *
 *     @SensitiveData(value = SensitiveType.CUSTOM, customFormat = "prefix:3,suffix:4,replace:*")
 *     private String customField;  // 自定义脱敏规则
 * }
 * }</pre>
 *
 * <p><b>配合 Jackson 使用：</b>
 * <pre>{@code
 * // 方式一：使用 SensitiveDataSerializer Jackson JsonSerializer
 * public class UserVO {
 *     @JsonSerialize(using = SensitiveDataSerializer.class)
 *     @SensitiveData(SensitiveType.PHONE)
 *     private String phone;
 * }
 *
 * // 方式二：直接使用 SensitiveDataProcessor
 * UserVO sanitized = SensitiveDataProcessor.process(userVO);
 * }</pre>
 *
 * @since 1.0.0
 * 
 * @see SensitiveType
 * @see SensitiveDataSerializer
 * @see SensitiveDataProcessor
 */
@Inherited
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveData {

    /**
     * 脱敏策略类型
     *
     * <p>指定使用哪种脱敏策略对字段值进行处理。
     *
     * @return 脱敏策略类型
     */
    SensitiveType value();

    /**
     * 替换字符
     *
     * <p>用于替换原文的字符，默认值为 "*"。
     * 可以设置为其他字符如 "X"、"#" 等。
     *
     * @return 替换字符
     */
    char replaceChar() default '*';

    /**
     * 是否启用
     *
     * <p>默认为 true，即启用脱敏。
     * 设置为 false 可临时禁用脱敏。
     *
     * @return 是否启用
     */
    boolean enabled() default true;

    /**
     * 自定义脱敏格式
     *
     * <p>当 value 为 {@link SensitiveType#CUSTOM} 时使用。
     * 格式：prefix:N,suffix:M,replace:C
     * <ul>
     *   <li>prefix:N - 保留前 N 个字符</li>
     *   <li>suffix:M - 保留后 M 个字符</li>
     *   <li>replace:C - 替换字符（可选，默认 *）</li>
     * </ul>
     *
     * <p>示例：
     * <ul>
     *   <li>"prefix:3,suffix:4" → "138****8000"</li>
     *   <li>"prefix:2,suffix:2,replace:X" → "张XX三"</li>
     *   <li>"prefix:0,suffix:4" → "****8000"</li>
     * </ul>
     *
     * @return 自定义脱敏格式字符串
     */
    String customFormat() default "";

    /**
     * 角色白名单（不脱敏的角色列表）
     *
     * <p>当前用户拥有此处列出的任一角色时，该字段不脱敏，返回原始值。
     * 默认为空数组，表示所有角色都脱敏。
     *
     * @return 角色白名单数组
     */
    String[] roles() default {};
}
