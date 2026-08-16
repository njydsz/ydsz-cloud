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
 * Web 端安全配置。
 *
 * <p>注册 Spring Security 异常处理入口点：401 未认证、403 权限不足，统一返回标准 JSON 响应。
 *
 * <p>项目使用自定义 {@code WebAuthFilter}，本配置仅负责异常处理链接入。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@AutoConfiguration
@ConditionalOnClass(AccessDeniedHandler.class)
@ConditionalOnProperty(prefix = "ydsz.web.security", name = "enabled", havingValue = "true", matchIfMissing = true)
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
     * <p>项目采用<b>无状态 JWT Bearer 认证</b>（由自定义 {@code WebAuthFilter} 完成鉴权），
     * 因此本链：
     * <ul>
     *   <li>关闭 CSRF（无 Cookie 会话，无 CSRF 攻击面）</li>
     *   <li>会话策略设为 {@code STATELESS}，与 JWT 无状态模型一致，不创建/使用 HttpSession</li>
     *   <li>所有请求放行（{@code permitAll}）：真正的鉴权与 401/403 响应由自定义
     *       {@code WebAuthFilter} 在链内完成，本链仅兜底接入异常处理器，避免业务侧
     *       自定义 {@link SecurityFilterChain} 被覆盖</li>
     * </ul>
     *
     * <p><b>注意：</b>若业务需接入 Spring Security 原生鉴权（如注解 {@code @PreAuthorize}），
     * 应自行提供 {@link SecurityFilterChain}（本 Bean 通过 {@code @ConditionalOnMissingBean} 自动让位），
     * 并在自定义链中配置真正的 {@code authorizeHttpRequests} 规则。
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
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
