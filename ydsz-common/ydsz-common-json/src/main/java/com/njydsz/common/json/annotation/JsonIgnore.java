package com.njydsz.common.json.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Jackson 兼容注解：标记字段在序列化/反序列化时被忽略。
 *
 * <p>当项目中同时存在 Jackson 和 YdszJson 时，可使用此注解替代
 * {@code @JsonIgnore(com.fasterxml.jackson.annotation.JsonIgnore)}， 避免引入 Jackson 依赖。
 *
 * <p>使用示例：
 *
 * <pre><code>
 * public class User {
 *     private String name;
 *
 *     &#64;JsonIgnore
 *     private String password;
 * }
 * </code></pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JsonIgnore {}
