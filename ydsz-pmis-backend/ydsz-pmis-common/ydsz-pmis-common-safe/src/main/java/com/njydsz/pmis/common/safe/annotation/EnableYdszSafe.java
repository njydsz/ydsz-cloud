package com.njydsz.pmis.common.safe.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.njydsz.pmis.common.safe.config.SafeConfiguration;

/**
 * 启用ydsz系统安全模块注解
 * <p>
 * 在 Spring Boot 应用主类上添加此注解，即可启用以下安全防护能力：
 * <ul>
 *   <li>XSS 跨站脚本攻击防护（基于 OWASP Java Encoder）</li>
 *   <li>CSRF 跨站请求伪造防护（Token 机制）</li>
 *   <li>安全响应头配置（CSP / HSTS / X-Frame-Options 等）</li>
 *   <li>敏感数据脱敏（基于 Jackson 序列化器）</li>
 *   <li>SQL 注入防护（基于过滤器正则拦截）</li>
 *   <li>限流防护（令牌桶 / 滑动窗口）</li>
 *   <li>AES-GCM 加解密工具</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * @SpringBootApplication
 * @EnableYdszSafe
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 * @see SafeConfiguration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SafeConfiguration.class)
public @interface EnableYdszSafe {
}
