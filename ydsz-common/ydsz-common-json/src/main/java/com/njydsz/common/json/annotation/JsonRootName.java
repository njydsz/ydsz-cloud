package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

/**
 * 指定序列化/反序列化时的根包装名称（参考 Jackson 的 @JsonRootName）。
 *
 * <p>标注在类上，序列化时将对象包裹在指定根名中，反序列化时自动解包。</p>
 *
 * <p>需配合 {@link com.njydsz.common.json.internal.JsonConfig} 的 wrapRootValue
 * 或 {@link com.njydsz.common.json.writer.JSONWriter.Feature} WriteRootValue 使用。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * {@literal @}JsonRootName("user")
 * public class User {
 *     private String name;
 *     private int age;
 * }
 *
 * // 序列化（启用 wrapRootValue）:
 * // {"user":{"name":"John","age":30}}
 *
 * // 反序列化（启用 unwrapRootValue）:
 * // 从 {"user":{"name":"John","age":30}} 中解包 "user" 键
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated REST API 场景中极少使用，如需包装返回结构建议使用统一的 Response 包装类
 */
@Deprecated
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JsonRootName {

    /**
     * 根包装名称。
     *
     * @return 根名称
     */
    String value();
}
