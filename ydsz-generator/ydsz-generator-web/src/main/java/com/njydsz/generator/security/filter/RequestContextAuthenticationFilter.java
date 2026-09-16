package com.njydsz.generator.security.filter;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.common.auth.model.AuthInfo;
import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.RequestContext;

import lombok.extern.slf4j.Slf4j;

/**
 * 请求上下文 → Spring Security 认证桥接过滤器（P0-1 鉴权底层支撑）。
 *
 * <p>在 {@code WebAuthFilter}（优先级 {@link Ordered#HIGHEST_PRECEDENCE} + 3）之后执行，
 * 从 {@link RequestContext} 读取 {@link AuthInfo} 并转换为 Spring Security 的
 * {@link UsernamePasswordAuthenticationToken} 写入 {@link SecurityContextHolder}，
 * 使 {@code @Secured} / {@code @PreAuthorize} 等注解在 Controller 层生效。
 *
 * <p><b>执行顺序：</b>WebAuthFilter (+3) → <b>本过滤器 (+4)</b> → Spring Security FilterChain。
 *
 * <p><b>线程安全：</b>请求结束后清理 {@link SecurityContextHolder}，避免上下文泄漏。
 *
 * @author ydsz-team
 * @since 26.09.16
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
public class RequestContextAuthenticationFilter extends OncePerRequestFilter {

  /** 默认角色前缀（Spring Security @Secured 规范要求）。 */
  private static final String ROLE_PREFIX = "ROLE_";

  /** 代码生成器模块基础访问角色。 */
  private static final SimpleGrantedAuthority ROLE_GENERATOR_USER =
      new SimpleGrantedAuthority(ROLE_PREFIX + "GENERATOR_USER");

  /** 代码生成器模块管理员角色。 */
  private static final SimpleGrantedAuthority ROLE_GENERATOR_ADMIN =
      new SimpleGrantedAuthority(ROLE_PREFIX + "GENERATOR_ADMIN");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Object authObj = RequestContext.get(BizContextKeys.KEY_AUTH_INFO);
    if (authObj instanceof AuthInfo authInfo && authInfo.getUniqueId() != null) {
      String servletPath = request.getServletPath();
      List<SimpleGrantedAuthority> authorities = buildAuthorities(servletPath);
      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(
              authInfo.getUniqueId(), null, authorities);
      SecurityContextHolder.getContext().setAuthentication(authentication);
      log.debug("[RequestContextAuthenticationFilter] 认证信息已桥接到 SecurityContext, user={}",
          authInfo.getUniqueId());
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  /**
   * 根据模块角色策略构建授权列表。
   *
   * <p>当前策略：所有已认证用户默认具备 {@code GENERATOR_USER} 角色，当访问数据源管理等
   * 敏感路径时额外授予 {@code GENERATOR_ADMIN} 角色（后续可扩展为 RBAC 动态查询）。
   *
   * @param servletPath 请求路径
   * @return 授权列表
   */
  private List<SimpleGrantedAuthority> buildAuthorities(String servletPath) {
    if (servletPath != null && servletPath.contains("/generator/datasources")) {
      return List.of(ROLE_GENERATOR_USER, ROLE_GENERATOR_ADMIN);
    }
    return List.of(ROLE_GENERATOR_USER);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // 仅对 /generator/** 路径生效，避免影响其他模块的 Spring Security 上下文
    String path = request.getServletPath();
    return path == null || !path.startsWith("/generator/");
  }
}
