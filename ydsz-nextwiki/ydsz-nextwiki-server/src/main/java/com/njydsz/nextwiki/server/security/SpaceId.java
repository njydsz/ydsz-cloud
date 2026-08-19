package com.njydsz.nextwiki.server.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 空间 ID 参数标记注解。
 *
 * <p>用于标记方法参数中哪个是空间 ID，供 {@link SpacePermissionAspect} 提取并校验权限。
 *
 * <p>若方法参数名就是 {@code spaceId} 且类型是 {@link String}，可省略此注解。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see SpacePermission
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SpaceId {
}
