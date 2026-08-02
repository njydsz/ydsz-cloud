package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

/**
 * 动态属性 Setter（参考 Jackson 的 @JsonAnySetter）。
 *
 * <p>标注在接收 (String key, Object value) 参数的方法上，
 * 反序列化时将未匹配的 JSON 属性通过此方法设置。</p>
 *
 * <p><b>注意：此注解已创建但尚未实现序列化/反序列化逻辑。标记为 Roadmap 待实现。</b></p>
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
public @interface JsonAnySetter {
}
