package com.njydsz.pmis.common.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.njydsz.pmis.common.web.handler.WebAccessDeniedHandler;
import com.njydsz.pmis.common.web.handler.WebAuthenticationEntryPoint;

/**
 * Web 端安全配置类
 *
 * <p>注册认证失败和权限不足的统一处理入口点。
 * <ul>
 *   <li>{@link WebAuthenticationEntryPoint}：处理未认证请求，返回 401</li>
 *   <li>{@link WebAccessDeniedHandler}：处理权限不足请求，返回 403</li>
 * </ul>
 *
 * <p>注意：此配置仅注册 Bean，不干预已有的认证过滤器链。
 * 项目使用自定义的 {@link com.njydsz.pmis.common.web.filter.WebAuthFilter} 进行认证，
 * 不启用 Spring Security 默认的登录/表单/HTTP Basic 认证机制。
 *
 * <p><b>配置开关：</b>
 * <ul>
 *   <li>{@code ydsz.web.security.enabled=false} 可完全禁用此配置</li>
 *   <li>默认启用（matchIfMissing = true）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@AutoConfiguration
@ConditionalOnClass(AccessDeniedHandler.class)
@ConditionalOnProperty(prefix = "ydsz.web.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebSecurityConfiguration {

    /**
     * 注册权限不足处理入口点
     *
     * <p>当已认证用户访问无权限资源时（403 Forbidden），
     * 由该处理器统一返回标准错误响应。
     *
     * @return AccessDeniedHandler 实例
     */
    @Bean
    public AccessDeniedHandler webAccessDeniedHandler() {
        return new WebAccessDeniedHandler();
    }

    /**
     * 注册认证失败处理入口点
     *
     * <p>当未认证用户访问受保护资源时（401 Unauthorized），
     * 由该处理器统一返回标准错误响应。
     *
     * @return AuthenticationEntryPoint 实例
     */
    @Bean
    public AuthenticationEntryPoint webAuthenticationEntryPoint() {
        return new WebAuthenticationEntryPoint();
    }

    /**
     * 注册 SecurityFilterChain
     *
     * <p>允许所有请求通过，认证由自定义 {@link com.njydsz.pmis.common.web.filter.WebAuthFilter} 处理，
     * Spring Security 仅负责提供异常处理等辅助能力。
     *
     * @param http HttpSecurity 构建器
     * @return SecurityFilterChain 实例
     * @throws Exception 配置异常
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
