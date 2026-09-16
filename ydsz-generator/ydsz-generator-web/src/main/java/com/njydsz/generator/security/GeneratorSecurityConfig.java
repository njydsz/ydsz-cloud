package com.njydsz.generator.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import lombok.extern.slf4j.Slf4j;

/**
 * 代码生成器模块安全配置（P0-1 鉴权缺口整改）。
 *
 * <p>覆盖 ydz-common-web 的默认 {@code SecurityFilterChain}（通过 {@code @ConditionalOnMissingBean} 自动让位逻辑），
 * 对 {@code /generator/**} 路由要求 Spring Security 已认证，配合 {@link RequestContextAuthenticationFilter}
 * 将 {@code WebAuthFilter} 写入 {@code RequestContext} 的认证态桥接到 {@code SecurityContextHolder}。
 *
 * <p><b>注解方法级鉴权：</b>同时启用 {@code @EnableMethodSecurity}，使 Controller 层的
 * {@code @Secured("ROLE_GENERATOR_*")} 注解生效。
 *
 * @author ydsz-team
 * @since 26.09.16
 */
@Slf4j
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@EnableMethodSecurity(securedEnabled = true, prePostEnabled = true)
public class GeneratorSecurityConfig {

  /**
   * 构建代码生成器模块安全过滤器链。
   *
   * <p>规则：
   *
   * <ul>
   *   <li>{@code /generator/**}：要求已认证（由 {@link RequestContextAuthenticationFilter} 提供 SecurityContext）
   *   <li>关闭 CSRF（无状态 JWT 模型）
   *   <li>无 HttpSession（STATELESS）
   * </ul>
   *
   * @param http HttpSecurity 构建器
   * @return 安全过滤器链
   * @throws Exception 配置过程中抛出的异常
   */
  @Bean
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  public SecurityFilterChain generatorSecurityFilterChain(HttpSecurity http) throws Exception {
    log.info("[GeneratorSecurityConfig] 注册代码生成器模块安全过滤器链（@Secured 方法级鉴权已启用）");
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/generator/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll());
    return http.build();
  }
}
