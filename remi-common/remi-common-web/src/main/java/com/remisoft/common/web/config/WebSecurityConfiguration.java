package com.remisoft.common.web.config;

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

import com.remisoft.common.web.handler.WebAccessDeniedHandler;
import com.remisoft.common.web.handler.WebAuthenticationEntryPoint;

/**
 * Web 端安全配置。
 *
 * <p>注册 Spring Security 异常处理入口点：401 未认证、403 权限不足，统一返回标准 JSON 响应。
 *
 * <p>项目使用自定义 {@code WebAuthFilter}，本配置仅负责异常处理链接入。
 *
 * @author remi-team
 * @since 1.0.0
 */

@AutoConfiguration
@ConditionalOnClass(AccessDeniedHandler.class)
@ConditionalOnProperty(prefix = "remi.web.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebSecurityConfiguration {

    /**
     * 注册 403 访问拒绝处理器。
     *
     * <p>认证用户访问无权限资源时由 Spring Security 回调，统一返回标准 JSON 错误响应。
     * 仅在容器中不存在其他 {@link AccessDeniedHandler} 时装配（{@code @ConditionalOnMissingBean}），
     * 便于业务侧自定义覆盖。
     *
     * @return 已注册的 Web 端 AccessDeniedHandler 实例
     */
    @Bean
    @ConditionalOnMissingBean(AccessDeniedHandler.class)
    public AccessDeniedHandler webAccessDeniedHandler() {
        return new WebAccessDeniedHandler();
    }

    /**
     * 注册 401 未认证入口点。
     *
     * <p>匿名请求触及受保护资源、认证信息缺失或失效时由 Spring Security 回调，
     * 统一返回标准 JSON 错误响应。仅在容器中不存在其他 {@link AuthenticationEntryPoint} 时装配，
     * 便于业务侧自定义覆盖。
     *
     * @return 已注册的 Web 端 AuthenticationEntryPoint 实例
     */
    @Bean
    @ConditionalOnMissingBean(AuthenticationEntryPoint.class)
    public AuthenticationEntryPoint webAuthenticationEntryPoint() {
        return new WebAuthenticationEntryPoint();
    }

    /**
     * 构建 Web 安全过滤器链。
     *
     * <p>关闭 CSRF（项目采用无状态 Bearer/Token 认证，无需 CSRF 防护）；会话策略设为
     * {@code IF_REQUIRED} 保留框架默认；所有请求放行（{@code permitAll}），真正的鉴权由自定义
     * {@code WebAuthFilter} 完成，本链仅负责将 401/403 异常处理器接入。
     * 仅在容器中不存在其他 {@link SecurityFilterChain} 时装配，避免与业务安全配置冲突。
     *
     * @param http                    Spring Security 构建器
     * @param accessDeniedHandler     注入的 403 处理器（见 {@link #webAccessDeniedHandler()}）
     * @param authenticationEntryPoint 注入的 401 入口点（见 {@link #webAuthenticationEntryPoint()}）
     * @return 构建完成的 SecurityFilterChain
     * @throws Exception 配置 HttpSecurity 过程中抛出的异常（如策略冲突）
     */
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
