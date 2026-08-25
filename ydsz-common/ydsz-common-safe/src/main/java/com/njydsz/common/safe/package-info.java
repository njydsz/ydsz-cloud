/**
 * 统一安全能力模块。
 *
 * <p>提供企业级安全能力，包括：
 *
 * <ul>
 *   <li>字段级加密（{@code @EncryptField}）
 *   <li>敏感数据脱敏（{@code @Sensitive}、{@link com.njydsz.common.safe.sensitive.SensitiveUtil}）
 *   <li>XSS 防护（{@code XssFilter}、{@code @Xss}）
 *   <li>CSRF 防护（{@code CsrfFilter}）
 *   <li>IP 访问控制（{@code IpAccessService}）
 *   <li>密码强度校验（{@code PasswordStrengthValidator}）
 *   <li>接口幂等（{@code @Idempotent}）
 *   <li>限流熔断（{@code @RateLimit}、{@code CircuitBreaker}）
 *   <li>安全事件上报（{@code SecurityEventPublisher}）
 *   <li>SSRF 防护（{@code HttpConnectionValidator}）
 * </ul>
 *
 * <p>业务模块必须优先使用本模块提供的安全能力，禁止重复造轮子。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
package com.njydsz.common.safe;
