package com.njydsz.pmis.common.json.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JSON 视图注解
 *
 * <p>用于按场景过滤字段，对标 Jackson @JsonView。</p>
 *
 * <p><b>使用场景：</b></p>
 * <ul>
 *   <li>列表视图：仅返回 ID 和名称</li>
 *   <li>详情视图：返回所有字段</li>
 *   <li>管理视图：返回敏感字段</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 定义视图类
 * public class UserViews {
 *     public static class List { }      // 列表视图
 *     public static class Detail { }    // 详情视图
 * }
 *
 * // 使用注解
 * public class User {
 *     &#064;JsonView(UserViews.List.class)
 *     private Long id;
 *
 *     &#064;JsonView(UserViews.List.class)
 *     private String name;
 *
 *     &#064;JsonView(UserViews.Detail.class)
 *     private String email;
 *
 *     &#064;JsonView(UserViews.Detail.class)
 *     private String phone;
 * }
 *
 * // 序列化 - 列表视图
 * String json = Json.toJson(user, UserViews.List.class);
 * // 输出：{"id":1,"name":"John"}
 *
 * // 序列化 - 详情视图
 * String json = Json.toJson(user, UserViews.Detail.class);
 * // 输出：{"id":1,"name":"John","email":"john@example.com","phone":"1234567890"}
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 * @since 1.3.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface JsonView {

    /**
     * 视图类
     *
     * <p>指定该字段在哪些视图下可见。支持继承关系，
     * 如果指定了父视图，子视图也会继承该可见性。</p>
     */
    Class<?>[] value();
}
