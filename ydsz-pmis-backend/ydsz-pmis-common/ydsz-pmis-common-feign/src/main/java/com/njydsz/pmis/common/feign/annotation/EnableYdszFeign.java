package com.njydsz.pmis.common.feign.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.core.annotation.AliasFor;

/**
 * 自定义 Feign 客户端启用注解，封装了 {@link EnableFeignClients} 并提供默认配置。
 *
 * <p>此注解用于简化 Feign 客户端的配置，提供统一的包扫描路径和默认配置。
 * 在 Spring Boot 主类上添加此注解后，将自动扫描并注册所有标注了
 * {@code @FeignClient} 的接口。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @SpringBootApplication
 * @EnableYdszFeign(basePpackages = "com.njydsz.pmis.order.client")
 * public class OrderApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(OrderApplication.class, args);
 *     }
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableFeignClients
public @interface EnableYdszFeign {

    /**
     * 等同于 {@link #basePpackages}，指定 Feign 客户端扫描包路径。
     */
    @AliasFor(annotation = EnableFeignClients.class, attribute = "value")
    String[] value() default {};

    /**
     * Feign 客户端扫描的基础包路径，默认为 {@code com.njydsz.pmis}。
     */
    @AliasFor(annotation = EnableFeignClients.class, attribute = "basePpackages")
    String[] basePpackages() default {"com.njydsz.pmis"};

    /**
     * 指定类所在的包作为 Feign 客户端扫描路径。
     */
    @AliasFor(annotation = EnableFeignClients.class, attribute = "basePpackageClasses")
    Class<?>[] basePpackageClasses() default {};

    /**
     * Feign 客户端的默认配置类。
     */
    @AliasFor(annotation = EnableFeignClients.class, attribute = "defaultConfiguration")
    Class<?>[] defaultConfiguration() default {};

    /**
     * 显式指定的 Feign 客户端接口类列表，指定后将禁用包扫描。
     */
    @AliasFor(annotation = EnableFeignClients.class, attribute = "clients")
    Class<?>[] clients() default {};
}
