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
