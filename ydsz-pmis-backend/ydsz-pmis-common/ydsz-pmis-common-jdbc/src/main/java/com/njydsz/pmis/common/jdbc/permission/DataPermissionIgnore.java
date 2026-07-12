package com.njydsz.pmis.common.jdbc.permission;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限忽略注解
 *
 * <p>标注在 Mapper 方法上，跳过数据权限拦截（行级 + 列级）。
 * 适用于需要访问全量数据的场景，如全局统计、系统级查询等。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @DataPermissionIgnore
 * @Select("SELECT COUNT(*) FROM t_user")
 * long countAllUsers();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * 
 * 
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataPermissionIgnore {
}
