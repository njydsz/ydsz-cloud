package com.njydsz.pmis.common.notify.annotation;

import com.njydsz.pmis.common.notify.config.NotifyConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用瑞米统一消息通知模块
 *
 * <p>�?Spring Boot 应用的启动类或配置类上添加此注解�?
 * 即可启用通知模块的全部功能（包括定时重试消费）�?
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 * @see com.njydsz.pmis.common.notify.config.NotifyConfiguration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(NotifyConfiguration.class)
public @interface EnableNotify {
}
