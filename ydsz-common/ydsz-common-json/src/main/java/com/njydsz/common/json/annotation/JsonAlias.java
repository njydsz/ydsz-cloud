package com.njydsz.common.json.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为字段声明一个或多个备用 JSON 名称（反序列化匹配）。
 *
 * <p>对标 Jackson {@code com.fasterxml.jackson.annotation.JsonAlias}：
 * 序列化时仍输出主名称（{@code @JsonProperty} 值或字段名）， 反序列化时 JSON 中出现主名称或任一别名均可匹配到该字段。 适用于对接外部系统的多命名兼容（如 {@code
 * user_id} / {@code userId}）。
 *
 * <p><b>示例：</b>
 *
 * <pre>
 * public class User {
 *     &#064;JsonAlias({"user_id", "uid"})
 *     private String userId;
 * }
 *
 * // 以下三种 JSON 均可正确反序列化：
 * // {"userId":"A"} / {"user_id":"A"} / {"uid":"A"}
 * </pre>
 *
 * <p><b>F-3 恢复说明：</b>v1.2.x 曾将该注解标记废弃并移除了实现，因外部 API 多命名兼容为刚需，v1.2.2 恢复支持（见 BeanReader 别名字段匹配）。
 *
 * @author ydsz-team
 * @since 1.2.2
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonAlias {

  /**
   * 备用名称列表。
   *
   * @return 备用 JSON 名称，空数组表示无别名
   */
  String[] value() default {};
}
