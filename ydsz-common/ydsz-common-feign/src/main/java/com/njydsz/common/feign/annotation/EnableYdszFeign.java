package com.njydsz.common.feign.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.njydsz.common.feign.assembler.NameAssemblerAutoConfiguration;
import com.njydsz.common.feign.config.FeignConfiguration;

/**
 * 启用 YdszFeign 自动配置的注解。
 *
 * <p>在 Spring Boot 启动类上添加此注解即可启用 feign 模块的全部公共能力，包括：
 * <ul>
 *   <li>统一请求头透传</li>
 *   <li>错误解码映射</li>
 *   <li>YdszJson 编解码</li>
 *   <li>链路追踪注入</li>
 *   <li>监控指标采集</li>
 *   <li>名称富化（NameAssembler）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * @SpringBootApplication
 * @EnableYdszFeign
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FeignConfiguration
 * @see NameAssemblerAutoConfiguration
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({FeignConfiguration.class, NameAssemblerAutoConfiguration.class})
public @interface EnableYdszFeign {
}
