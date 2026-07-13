package com.njydsz.pmis.common.audit.annotation;

import java.lang.annotation.*;

import org.springframework.context.annotation.Import;

import com.njydsz.pmis.common.audit.aspect.AuditAspect;
import com.njydsz.pmis.common.audit.config.AuditAutoConfiguration;
import com.njydsz.pmis.common.audit.core.AuditRecorder;

/**
 * 启用审计模块的开关注解
 * <p>
 * 添加在 Spring Boot 启动类或配置类上即可启用审计能力，自动注册
 * {@link AuditAspect}、{@link AuditRecorder}
 * 等核心 Bean。
 * </p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * @SpringBootApplication
 * @EnableAudit
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 * @see AuditAutoConfiguration
 */
@Inherited
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(AuditAutoConfiguration.class)
public @interface EnableAudit {

    /**
     * 是否启用审计功能
     * <p>默认启用。关闭后审计切面会直接放行方法调用，不会进行日志记录。
     *
     * @return 启用返回 true
     */
    boolean enabled() default true;
}
