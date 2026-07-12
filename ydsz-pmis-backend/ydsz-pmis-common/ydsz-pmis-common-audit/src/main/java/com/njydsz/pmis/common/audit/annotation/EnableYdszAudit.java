package com.njydsz.pmis.common.audit.annotation;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用审计模块的开关注解
 * <p>
 * 添加在 Spring Boot 启动类或配置类上即可启用审计能力，自动注册
 * {@link com.njydsz.pmis.common.audit.aspect.AuditAspect}、{@link com.njydsz.pmis.common.audit.core.AuditRecorder}
 * 等核心 Bean。
 * </p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * @SpringBootApplication
 * @EnableYdszAudit
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see com.njydsz.pmis.common.audit.config.AuditAutoConfiguration
 */
@Inherited
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(com.njydsz.pmis.common.audit.config.AuditAutoConfiguration.class)
public @interface EnableYdszAudit {

    /**
     * 是否启用审计功能
     * <p>默认启用。关闭后审计切面会直接放行方法调用，不会进行日志记录。
     *
     * @return 启用返回 true
     */
    boolean enabled() default true;
}
