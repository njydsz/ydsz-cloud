package com.remisoft.common.queue.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.remisoft.common.queue.config.QueueConfiguration;

/**
 * 启用remi消息队列模块
 * <p>在Spring Boot应用主类上添加此注解，启用多消息中间件适配能力
 *
 * @author remi-team
 * 
 * 
 * @since 1.0.0
 * @see QueueConfiguration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(QueueConfiguration.class)
public @interface EnableQueue {
}
