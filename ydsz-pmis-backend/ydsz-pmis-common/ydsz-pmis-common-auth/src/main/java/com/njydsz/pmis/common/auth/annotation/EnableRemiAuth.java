package com.njydsz.pmis.common.auth.annotation;

import com.njydsz.pmis.common.auth.config.AuthConfiguration;
import com.njydsz.pmis.common.auth.config.AuthFilterConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用瑞米权限管控模块注解
 *
 * <p>在 Spring Boot 应用主类上添加此注解，启用 RBAC 权限、数据权限、列级权限等能力。
 * 该注解通过 {@link Import} 导入 {@link AuthConfiguration} 和 {@link AuthFilterConfiguration} 配置类，
 * 自动装配权限管控相关的所有 Bean。
 *
 * <p><b>使用示例：</b>
 * <pre>
 * @SpringBootApplication
 * @EnableRemiAuth
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see AuthConfiguration
 * @see AuthFilterConfiguration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({AuthConfiguration.class, AuthFilterConfiguration.class})
public @interface EnableRemiAuth {
}
