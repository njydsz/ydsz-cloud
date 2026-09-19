/**
 * 统一安全能力模块。
 *
 * <p><b>模块定位：</b>API 层安全增强中间件，专注于请求/响应链路的安全防护。
 * 本模块<b>不提供认证授权能力</b>，认证（身份校验、Token 签发）与授权（RBAC/ABAC 权限判断）
 * 由外部 JWT 网关和 {@code ydzsz-userinfo} 身份引擎承担。
 *
 * <p><b>提供能力：</b>
 *
 * <ul>
 *   <li>字段级加密（{@code @EncryptField} + {@code EncryptTypeHandler}，AES-256-GCM 随机 IV）
 *   <li>敏感数据脱敏（{@code @Sensitive}、{@code SensitiveDataAdvice} 全局拦截器）
 *   <li>XSS 防护（{@code XssFilter} + OWASP Java HTML Sanitizer）
 *   <li>CSRF 防护（{@code CsrfFilter}，JWT 架构下默认关闭）
 *   <li>IP 访问控制（{@code IpAccessService} + {@code IpAccessFilter} 自动拦截）
 *   <li>密码强度校验（{@code PasswordStrengthValidator}，可配置强度级别）
 *   <li>限流熔断（{@code @RateLimit} AOP、{@code SafeCircuitBreaker} 三态保护）
 *   <li>安全事件上报（{@code SecurityEventPublisher} → 聚合器 → 自动 IP 封禁闭环）
 *   <li>SSRF 防护（{@code HttpConnectionValidator} 内网地址拦截）
 *   <li>API 签名校验（{@code ApiSignatureFilter}，HMAC-SHA256）
 *   <li>图形验证码（{@code CaptchaGenerator}）
 *   <li>安全响应头注入（{@code SecurityHeaderFilter}，CSP/HSTS/X-Frame-Options 等）
 * </ul>
 *
 * <p><b>与外部框架分工：</b>
 *
 * <ul>
 *   <li>与 Spring Security / Sa-Token：互补。本模块不提供会话管理、OAuth2/OIDC、RBAC 权限 SPI，
 *       认证授权由网关层和身份引擎负责；本模块专注 API 层（过滤器链层）的 XSS/限流/IP/签名/SSRF 防护
 *   <li>与 ydsz-userinfo（身份引擎）：分工明确。ydsz-userinfo 处理认证/授权/用户生命周期；
 *       本模块处理请求链路安全控制（入站过滤、出站脱敏）
 * </ul>
 *
 * <p><b>业务模块使用规范：</b>业务模块必须优先使用本模块提供的安全能力，禁止重复造轮子。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.common.safe;
