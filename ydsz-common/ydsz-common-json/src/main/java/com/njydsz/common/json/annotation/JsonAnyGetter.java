package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

/**
 * 动态属性 Getter（参考 Jackson 的 @JsonAnyGetter）。
 *
 * <p>标注在返回 Map&lt;String, Object&gt; 的方法上，
 * 序列化时将 Map 的键值对展开为顶层 JSON 属性。</p>
 *
 * <p><b>已实现：</b>在 {@code BeanSerializer} 序列化路径中，
 * 写入所有字段后调用标注方法，将返回的 Map 展开为顶层 JSON 属性。</p>
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
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated REST API 场景中极少使用，建议显式定义字段以提升可维护性
 */
@Deprecated
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface JsonAnyGetter {
}
