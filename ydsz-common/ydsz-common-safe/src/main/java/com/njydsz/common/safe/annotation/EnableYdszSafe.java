package com.njydsz.common.safe.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.njydsz.common.safe.config.SafeConfiguration;

/**
 * 启用ydsz系统安全模块注解
 *
 * <p>在 Spring Boot 应用主类上添加此注解，即可启用以下安全防护能力：
 *
 * <ul>
 *   <li>XSS 跨站脚本攻击防护（基于 OWASP Java HTML Sanitizer + 可配置策略）
 *   <li>CSRF 跨站请求伪造防护（Synchronizer Token / Double Submit Cookie 双模式）
 *   <li>安全响应头配置（CSP / HSTS / X-Frame-Options 等）
 *   <li>敏感数据脱敏（基于 YdszJson 序列化器 + 角色白名单）
 *   <li>SQL 注入防护（基于过滤器正则拦截 + 运行时热更新）
 *   <li>限流防护（令牌桶 / 滑动窗口 + @RateLimit AOP + 多维度）
 *   <li>IP 黑白名单访问控制（CIDR 网段 + 自动封禁）
 *   <li>API 签名验证（timestamp + nonce + HMAC-SHA256）
 *   <li>密码强度校验 + 滑块验证码
 *   <li>安全事件自动响应（滑动窗口聚合 + 自动 IP 封禁）
 *   <li>AES-256-GCM 加解密 + Micrometer 指标 + 审计日志
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
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
 * @author ydsz-team
 * @since 26.09.01
 * @see SafeConfiguration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SafeConfiguration.class)
public @interface EnableYdszSafe {}
