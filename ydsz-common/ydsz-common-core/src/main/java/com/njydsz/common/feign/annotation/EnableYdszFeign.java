package com.njydsz.common.feign.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用 Ydsz Feign 客户端自动配置
 *
 * <p>在 Spring Boot 应用入口类上添加此注解，启用 Feign 客户端相关能力。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnableYdszFeign {
}