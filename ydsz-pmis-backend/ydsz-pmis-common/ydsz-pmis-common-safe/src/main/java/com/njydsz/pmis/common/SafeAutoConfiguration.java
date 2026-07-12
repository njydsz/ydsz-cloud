package com.njydsz.pmis.common;

import com.njydsz.pmis.common.aspect.IdempotentAspect;
import com.njydsz.pmis.common.aspect.RateLimiterAspect;
import com.njydsz.pmis.common.aspect.RequireReAuthAspect;
import com.njydsz.pmis.common.config.AuditFieldFiller;
import com.njydsz.pmis.common.filter.ApiRequestLogFilter;
import com.njydsz.pmis.common.filter.TraceIdFilter;
import com.njydsz.pmis.common.filter.XssFilter;
import com.njydsz.pmis.common.security.event.SecurityEventPublisher;
import com.njydsz.pmis.common.security.nonce.NonceVerifier;
import com.njydsz.pmis.common.security.token.TokenBlacklist;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 安全防护层自动配置
 *
 * <p>聚合 safe 模块所有组件，通过 Spring Boot 3 自动装配机制注册。
 *
 * <p>包含：
 * <ul>
 *   <li>3 类 AOP 切面：幂等/限流/二次认证</li>
 *   <li>3 类过滤器：链路追踪/XSS/请求日志</li>
 *   <li>安全组件：Nonce 校验/Token 黑名单/安全事件</li>
 *   <li>审计字段填充器 {@link AuditFieldFiller}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({
    // AOP 切面
    IdempotentAspect.class,
    RateLimiterAspect.class,
    RequireReAuthAspect.class,
    // 过滤器
    TraceIdFilter.class,
    XssFilter.class,
    ApiRequestLogFilter.class,
    // 安全组件
    NonceVerifier.class,
    TokenBlacklist.class,
    SecurityEventPublisher.class,
    // 审计
    AuditFieldFiller.class
})
public class SafeAutoConfiguration {
}
