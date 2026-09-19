package com.njydsz.common.feign.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.njydsz.common.feign.config.FeignConfiguration;

/**
 * 启用 Ydsz Feign 客户端自动配置
 *
 * <p>在 Spring Boot 应用入口类上添加此注解，启用 Feign 客户端相关的增强能力（头透传 / 编解码 / 重试 / 追踪 / 熔断 / 隔离）。
 *
 * <p>内部通过 {@link Import} 导入 {@link FeignConfiguration} 配置类，注册 {@link
 * com.njydsz.common.feign.aspect.FeignRequestInterceptor} 核心 Bean。
 *
 * <p><b>注意：</b>通常无需显式使用此注解——Spring Boot 自动配置通过 {@code META-INF/spring/}
 * {@code org.springframework.boot.autoconfigure.AutoConfiguration.imports} 已能正确加载。
 * 仅在需要精确控制配置加载顺序时使用此注解。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FeignConfiguration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(FeignConfiguration.class)
public @interface EnableYdszFeign {
}
