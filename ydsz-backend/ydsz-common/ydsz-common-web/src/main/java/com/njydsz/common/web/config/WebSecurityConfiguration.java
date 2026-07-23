package com.njydsz.common.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.njydsz.common.web.handler.WebAccessDeniedHandler;
import com.njydsz.common.web.handler.WebAuthenticationEntryPoint;

/**
 * Web 端安全配置类
 *
 * <p>注册认证失败和权限不足的统一处理入口点，并将其接入 Spring Security 过滤器链。
 * <ul>
 *   <li>{@link WebAuthenticationEntryPoint}：处理未认证请求，返回 401 JSON 响应</li>
 *   <li>{@link WebAccessDeniedHandler}：处理权限不足请求，返回 403 JSON 响应</li>
 * </ul>
 *
 * <p>注意：此配置仅注册 Bean 和异常处理链，不启用 Spring Security 默认的登录/表单/HTTP Basic 认证机制。
 * 项目使用自定义的 {@link com.njydsz.common.web.filter.WebAuthFilter} 进行认证。
 *
 * <p><b>安全策略：</b>
 * <ul>
 *   <li>CSRF 禁用（REST API 无状态场景无需 CSRF Token）</li>
 *   <li>Session 策略：IF_REQUIRED（按需创建，兼顾 Redis Session 共享和无状态 API）</li>
 *   <li>所有请求 permitAll（认证由 WebAuthFilter 独立处理）</li>
 *   <li>异常处理：401/403 返回标准 JSON 响应</li>
 * </ul>
 *
 * <p><b>配置开关：</b>
 * <ul>
 *   <li>{@code ydsz.web.security.enabled=false} 可完全禁用此配置</li>
 *   <li>默认启用（matchIfMissing = true）</li>
 * </ul>
 *
 * @author ydsz-team
 * @see WebAuthenticationEntryPoint
 * @see WebAccessDeniedHandler
 * @see WebAuthFilter
 */
@AutoConfiguration
@ConditionalOnClass(AccessDeniedHandler.class)
@ConditionalOnProperty(prefix = "ydsz.web.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebSecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean(AccessDeniedHandler.class)
    public AccessDeniedHandler webAccessDeniedHandler() {
        return new WebAccessDeniedHandler();
    }

    @Bean
    @ConditionalOnMissingBean(AuthenticationEntryPoint.class)
    public AuthenticationEntryPoint webAuthenticationEntryPoint() {
        return new WebAuthenticationEntryPoint();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    AccessDeniedHandler accessDeniedHandler,
                                                    AuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
