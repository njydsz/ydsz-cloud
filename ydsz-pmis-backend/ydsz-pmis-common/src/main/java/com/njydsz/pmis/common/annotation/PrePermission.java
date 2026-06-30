package com.njydsz.pmis.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解
 *
 * <p>用于 Controller 方法上，由 PermissionAspect 拦截校验。
 *
 * <p>用法：
 * <pre>
 *   {@code @PrePermission("system:user:create")}
 *   {@code @PrePermission(value = {"system:user:update", "system:user:delete"}, mode = PrePermission.Mode.OR)}
 *   {@code @PrePermission(value = "system:user:query", requireLogin = false)}
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PrePermission {

    /**
     * 权限编码
     */
    String[] value();

    /**
     * 校验模式: AND / OR
     */
    Mode mode() default Mode.AND;

    /**
     * 是否要求登录（默认 true）
     */
    boolean requireLogin() default true;

    enum Mode {
        /** 全部满足 */
        AND,
        /** 任一满足 */
        OR
    }
}
