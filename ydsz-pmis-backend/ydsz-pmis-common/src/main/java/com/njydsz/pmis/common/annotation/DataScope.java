package com.njydsz.pmis.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解
 *
 * <p>标注在 Service / Controller 方法上，配合 DataScopeAspect 自动注入数据范围 SQL 条件。
 *
 * <p>用法：
 * <pre>
 *   {@code @DataScope(deptAlias = "t", userAlias = "t")}
 *   public Page<ProjectDO> page(ProjectQueryDTO q) { ... }
 * </pre>
 *
 * <p>触发条件：当前登录用户的 {@code dataScope != ALL} 且未在白名单中。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /**
     * 部门字段别名（SQL 别名，例如 "t" / "p"）
     */
    String deptAlias() default "";

    /**
     * 创建人字段别名
     */
    String userAlias() default "";

    /**
     * 自定义部门字段（用于 DEPT_AND_CHILD 递归）
     */
    String deptColumn() default "dept_id";

    /**
     * 自定义创建人字段
     */
    String userColumn() default "creator_id";

    /**
     * 是否对子查询生效（List/Page 自动注入）
     */
    boolean applyToChildren() default false;
}
