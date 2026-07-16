package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

/**
 * 动态属性 Getter（参考 Jackson 的 @JsonAnyGetter）。
 *
 * <p>标注在返回 Map&lt;String, Object&gt; 的方法上，
 * 序列化时将 Map 的键值对展开为顶层 JSON 属性。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * public class DynamicBean {
 *     private Map&lt;String, Object&gt; extras = new HashMap&lt;&gt;();
 *
 *     {@literal @}JsonAnyGetter
 *     public Map&lt;String, Object&gt; getExtras() {
 *         return extras;
 *     }
 * }
 * </pre>
 *
 * @since 1.4.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface JsonAnyGetter {
}
