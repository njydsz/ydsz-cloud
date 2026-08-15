package com.njydsz.common.auth.annotation;

import java.lang.annotation.*;

/**
 * 数据权限注解。
 *
 * <p>标注在 Service/Mapper 方法上，声明当前查询的数据权限规则。
 * 配合 JDBC SQL 拦截器（{@code com.njydsz.common.jdbc.interceptor.DataScopeInterceptor}）
 * 自动追加 WHERE 条件，实现行级数据权限控制。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>声明 SQL 拦截器需要追加的数据范围 WHERE 条件</li>
 *   <li>区分「按部门过滤」与「按用户过滤」两类场景</li>
 *   <li>支持多表 JOIN 场景的别名声明</li>
 * </ul>
 *
 * <p><b>实现原理：</b>
 * <ol>
 *   <li>AOP 拦截带 {@code @DataScope} 的方法</li>
 *   <li>从当前线程上下文（{@code AuthContextUtils}）获取用户的数据权限范围</li>
 *   <li>动态拼接 {@code WHERE dept_id IN (...) } / {@code WHERE user_id = ?} 条件</li>
 *   <li>合并到原始 SQL 尾部，由 MyBatis 执行</li>
 * </ol>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 简单场景：按部门+创建人过滤
 * &#64;DataScope(deptColumn = "department_id", userColumn = "created_by")
 * public List<Employee> listEmployees(PageQuery query) { ... }
 *
 * // JOIN 场景：声明别名
 * &#64;DataScope(deptAlias = "d", userAlias = "u", deptColumn = "dept_id", userColumn = "create_by")
 * public List<OrderVO> listOrdersWithUser(PageQuery query) {
 *     // SQL: SELECT o.* FROM order o JOIN user u ON o.user_id = u.id WHERE u.dept_id IN (...)
 * }
 * }</pre>
 *
 * <p><b>兼容性：</b>与历史 {@code com.njydsz.common.annotation.DataScope} 字段语义完全一致，
 * 新代码统一使用本注解。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.common.auth.aspect.AuthRowPermissionAspect 行级权限 AOP
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /**
     * 部门表别名（用于 JOIN 场景）。
     *
     * <p>在多表 JOIN 场景下，需要显式声明部门表别名以便 SQL 拦截器正确拼接条件。
     * 留空时使用默认表名（适用于单表查询）。
     *
     * @return 部门别名，默认 {@code ""}
     */
    String deptAlias() default "";

    /**
     * 用户表别名（用于 JOIN 场景）。
     *
     * <p>在多表 JOIN 场景下，需要显式声明用户表别名以便 SQL 拦截器正确拼接条件。
     * 留空时使用默认表名（适用于单表查询）。
     *
     * @return 用户别名，默认 {@code ""}
     */
    String userAlias() default "";

    /**
     * 部门 ID 字段名，用于按部门过滤数据。
     *
     * <p>对应数据库表中的部门 ID 字段（如 {@code dept_id} / {@code department_id}）。
     * SQL 拦截器会拼接 {@code WHERE <字段名> IN (<用户可见部门列表>)}。
     *
     * @return 部门字段名，默认 {@code "dept_id"}
     */
    String deptColumn() default "dept_id";

    /**
     * 用户 ID 字段名，用于按用户过滤数据。
     *
     * <p>对应数据库表中的创建人/负责人字段（如 {@code user_id} / {@code created_by}）。
     * 当数据范围配置为「仅本人」时，SQL 拦截器会拼接 {@code WHERE <字段名> = <当前用户ID>}。
     *
     * @return 用户字段名，默认 {@code "user_id"}
     */
    String userColumn() default "user_id";
}
