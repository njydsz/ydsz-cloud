package com.njydsz.pmis.common.json.annotation;

import java.lang.annotation.*;

/**
 * 定义字段在反序列化时接受的别名列表。
 *
 * <p>当 JSON 中字段名与 Java 字段名不匹配时，可以通过此注解指定别名，
 * 反序列化引擎将按别名列表依次尝试匹配 JSON 字段。</p>
 *
 * <p>示例：</p>
 * <pre><code>
 * public class User {
 *     &#64;JsonAlias({"userName", "loginName"})
 *     private String username;
 * }
 * </code></pre>
 *
 * <p>上述示例中，JSON 中的 {@code "userName"}、{@code "loginName"}
 * 和 {@code "username"} 都能正确映射到 Java 字段 {@code username}。</p>
 *
 * @since 1.4.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JsonAlias {

    /**
     * 别名列表
     *
     * @return 别名字符串数组
     */
    String[] value();
}
