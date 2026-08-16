package com.njydsz.common.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;
import com.njydsz.common.audit.config.AuditAutoConfiguration;

/**
 * 启用审计模块的开关注解
 * <p>
 * 添加在 Spring Boot 启动类或配置类上即可启用审计能力，自动注册
 * {@link com.njydsz.common.audit.aspect.AuditAspect}、{@link com.njydsz.common.audit.core.AuditRecorder}
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
 * @author ydsz-team
 * @since 1.0.0
 * @see AuditAutoConfiguration
 */
@Inherited
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(AuditAutoConfiguration.class)
public @interface EnableYdszAudit {

    /**
     * 是否启用审计功能
     * <p>默认启用。关闭后审计切面会直接放行方法调用，不会进行日志记录。
     *
     * @return 启用返回 true
     */
    boolean enabled() default true;
}
