package com.njydsz.pmis.common.queue.annotation;

import com.njydsz.pmis.common.queue.config.QueueConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用ydsz消息队列模块
 * <p>在Spring Boot应用主类上添加此注解，启用多消息中间件适配能力
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see QueueConfiguration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(QueueConfiguration.class)
public @interface EnableYdszQueue {
}
