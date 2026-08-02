package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

/**
 * 动态属性 Setter（参考 Jackson 的 @JsonAnySetter）。
 *
 * <p>标注在接收 (String key, Object value) 参数的方法上，
 * 反序列化时将未匹配的 JSON 属性通过此方法设置。</p>
 *
 * <p><b>已实现：</b>在 {@code BeanReader} 反序列化路径中，
 * 当 JSON 属性未匹配到任何字段时，调用标注方法将键值对写入。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * public class DynamicBean {
 *     private Map&lt;String, Object&gt; extras = new HashMap&lt;&gt;();
 *
 *     {@literal @}JsonAnySetter
 *     public void addExtra(String key, Object value) {
 *         extras.put(key, value);
 *     }
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Deprecated(since = "1.0.0", forRemoval = true)
public @interface JsonAnySetter {
}
