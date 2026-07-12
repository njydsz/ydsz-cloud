package com.njydsz.pmis.common.auth.annotation;

import java.lang.annotation.*;

/**
 * 行级数据权限校验与注入注解。
 *
 * <p>用于控制接口方法的数据权限范围，会将当前用户可访问的数据范围注入到方法参数中。
 * 与 {@link com.njydsz.pmis.common.auth.model.DataScopeAware} 或 Map 类型参数配合使用，
 * 实现行级数据权限的动态过滤。
 *
 * <p><b>工作原理：</b>
 * <ol>
 *   <li>切面拦截标注了本注解的方法</li>
 *   <li>从请求上下文解析当前用户的行级数据权限（来自 Redis role-row-key）</li>
 *   <li>根据用户角色合并多角色的数据权限范围</li>
 *   <li>将数据权限信息 {@link com.njydsz.pmis.common.auth.model.DataScopeInfo} 注入到方法参数</li>
 *   <li>同时将数据范围信息以 header 形式透传给下游服务（如 SQL 拦截器）</li>
 * </ol>
 *
 * <p><b>数据权限维度：</b>
 * <ul>
 *   <li>租户维度（TENANT）：按租户隔离数据</li>
 *   <li>集团维度（GROUP）：可访问集团下所有公司数据</li>
 *   <li>公司维度（COMPANY）：可访问公司及下属部门数据</li>
 *   <li>部门维度（DEPT）：可访问本部门及下级部门数据</li>
 *   <li>用户维度（USER）：仅可访问自己的数据</li>
 *   <li>项目维度（PROJECT）：可访问有权限的项目数据</li>
 *   <li>区域维度（REGION）：可访问有权限的区域数据</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // 方式一：自动注入到实现 DataScopeAware 接口的参数
 * &#64;AuthRowPermission
 * public PageResult&lt;UserVO&gt; queryUsers(UserQuery query) {
 *     // query 对象已自动注入 DataScopeInfo
 *     return userService.queryWithScope(query);
 * }
 *
 * // 方式二：注入到 Map 类型参数
 * &#64;AuthRowPermission(mapKey = "rowPermission")
 * public PageResult&lt;UserVO&gt; queryUsers(Map&lt;String, Object&gt; params) {
 *     // params["rowPermission"] 已注入 DataScopeInfo
 *     return userService.queryWithScope(params);
 * }
 *
 * // 方式三：精确指定注入目标参数
 * &#64;AuthRowPermission(targetParamName = "query")
 * public PageResult&lt;UserVO&gt; queryUsers(UserQuery query, OtherDTO other) {
 *     // 仅注入到 query 参数
 *     return userService.queryWithScope(query);
 * }
 *
 * // 方式四：必须包含数据权限（无权限则抛异常）
 * &#64;AuthRowPermission(required = true)
 * public UserVO getUserData(Long id) {
 *     return userService.getById(id);
 * }
 * </pre>
 *
 * <p><b>与 ydsz-pmis-common-jdbc 联动：</b>
 * <p>行级数据权限会自动透传到 SQL 拦截层，实现自动的数据过滤。
 * 详见 {@link com.njydsz.pmis.common.jdbc.permission.DataPermissionContext}
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see com.njydsz.pmis.common.auth.model.DataScopeInfo
 * @see com.njydsz.pmis.common.auth.model.DataScopeAware
 * @see com.njydsz.pmis.common.core.enums.DataScopeType
 */
@Inherited
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthRowPermission {

    /**
     * 注入到 Map 类型参数时的 key 名称。
     *
     * <p>当方法参数为 Map 类型时，数据权限信息会以此 key 存入 Map。
     * 默认值为 {@code rowPermission}。
     *
     * @return Map key 名称
     */
    String mapKey() default "rowPermission";

    /**
     * 是否必须包含有效的数据权限。
     *
     * <ul>
     *   <li>{@code true}：若解析不到有效的数据权限，抛出业务异常</li>
     *   <li>{@code false}：若解析不到数据权限，静默跳过（默认）</li>
     * </ul>
     *
     * <p>适用于必须依赖数据权限才能正常执行的业务场景。
     *
     * @return 是否必须
     */
    boolean required() default false;

    /**
     * 目标方法参数名称。
     *
     * <p>用于精确指定需要注入数据权限信息的参数名称。
     * 若方法中存在多个可注入参数（实现 {@link com.njydsz.pmis.common.auth.model.DataScopeAware} 或 Map），
     * 通过此属性定位目标参数。
     *
     * @return 方法参数名称
     */
    String targetParamName() default "";
}