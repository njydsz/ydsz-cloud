package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

/**
 * 标注字段值作为原始 JSON 嵌入序列化输出（参考 Jackson 的 @JsonRawValue）。
 *
 * <p>标注在字段或方法上，序列化时将字段值作为原始 JSON 片段直接写入输出，
 * 不做字符串转义。适用于动态 JSON 构建、数据库 JSON 列映射等场景。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * public class Product {
 *     private String name;
 *
 *     {@literal @}JsonRawValue
 *     private String metadata;  // 值为 {"category":"electronics","tags":["new"]}
 * }
 * </pre>
 *
 * <p>序列化结果：</p>
 * <pre>
 * {"name":"Widget","metadata":{"category":"electronics","tags":["new"]}}
 * </pre>
 *
 * <p>而非（无 @JsonRawValue 时）：</p>
 * <pre>
 * {"name":"Widget","metadata":"{\"category\":\"electronics\",\"tags\":[\"new\"]}"}
 * </pre>
 *
 * <p><b>注意：</b>该注解仅影响序列化，反序列化时字段值仍按 String 类型解析。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated REST API 场景中极少使用，如需要嵌入 JSON 字符串可手动构建后序列化
 */
@Deprecated
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface JsonRawValue {
}
