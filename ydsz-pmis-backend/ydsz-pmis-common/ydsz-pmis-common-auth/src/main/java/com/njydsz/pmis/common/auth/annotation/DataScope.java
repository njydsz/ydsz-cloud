package com.njydsz.pmis.common.auth.annotation;

import java.lang.annotation.*;

/**
 * 数据权限注解（兼容旧 com.njydsz.pmis.common.annotation.DataScope）。
 *
 * <p>标注在 Service/Mapper 方法上，声明当前查询的数据权限规则。
 * 配合 JDBC SQL 拦截器自动追加 WHERE 条件，实现行级数据权限控制。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @DataScope(deptColumn = "department_id", userColumn = "created_by")
 * public List<Employee> listEmployees(PageQuery query) { ... }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /**
     * 部门表别名（用于 JOIN 场景），为空时使用默认表名。
     *
     * @return 部门别名
     */
    String deptAlias() default "";

    /**
     * 用户表别名（用于 JOIN 场景），为空时使用默认表名。
     *
     * @return 用户别名
     */
    String userAlias() default "";

    /**
     * 部门 ID 字段名，用于按部门过滤数据。
     *
     * @return 部门字段名
     */
    String deptColumn() default "dept_id";

    /**
     * 用户 ID 字段名，用于按用户过滤数据。
     *
     * @return 用户字段名
     */
    String userColumn() default "user_id";
}
