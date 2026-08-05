package com.remisoft.common.notify.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.remisoft.common.notify.config.NotifyConfiguration;

/**
 * 启用remi统一消息通知模块
 *
 * <p>在 Spring Boot 应用的启动类或配置类上添加此注解，
 * 即可启用通知模块的全部功能（包括定时重试消费）。
 *
 * @author remi-team
 * @since 1.0.0
 * @see NotifyConfiguration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(NotifyConfiguration.class)
public @interface EnableRemiNotify {
}
