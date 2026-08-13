package com.njydsz.common.jdbc.permission;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限忽略注解
 *
 * <p><b>仅支持标注在 Mapper 接口方法上</b>，跳过数据权限拦截（行级 + 列级）。
 * 适用于需要访问全量数据的场景，如全局统计、系统级查询等。
 *
 * <p>Service 层方法标注本注解<b>不会生效</b>——拦截器通过 {@code MappedStatement.id}
 * 反射检查的是 Mapper 接口方法上的注解。后台批处理 / MQ 消费等无 HTTP 请求上下文
 * 的场景，请使用 {@link DataPermissionBypass#runWithoutCheck(Runnable)} 编程式绕过。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @DataPermissionIgnore
 * @Select("SELECT COUNT(*) FROM t_user")
 * long countAllUsers();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see DataPermissionBypass
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataPermissionIgnore {
}
