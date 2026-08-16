package com.njydsz.gateway.filter;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.json.YdszJson;
import com.njydsz.gateway.config.AuthorizationProperties;
import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayFilterOrder;
import com.njydsz.gateway.config.PathGuard;

/**
 * P3-7: 网关层粗粒度鉴权过滤器（RBAC）
 *
 * <p>在认证过滤器之后、API Key 过滤器之前执行，基于请求路径和用户角色进行粗粒度权限校验。
 *
 * <h3>工作机制</h3>
 *
 * <ol>
 *   <li>从 X-User-Roles 头获取用户角色列表
 *   <li>按 Path 匹配找到该路径所需的角色列表
 *   <li>检查用户角色是否满足路径要求（any=OR / all=AND）
 *   <li>不满足返回 403 FORBIDDEN
 * </ol>
 *
 * <h3>路径匹配</h3>
 *
 * <p>使用 Spring {@link AntPathMatcher} 支持 Ant 风格通配符：
 *
 * <ul>
 *   <li>{@code /api/admin/**} — 匹配 /api/admin/ 开头的所有路径
 *   <li>{@code /api/user/*} — 匹配 /api/user/ 下一级路径
 *   <li><code>/api/**&#47;list</code> — 匹配任意层级下的 list 端点
 * </ul>
 *
 * <h3>执行顺序</h3>
 *
 * <p>{@code HIGHEST_PRECEDENCE + 12}，在认证（+10）之后、API Key（+15）之前执行。 确保已获取用户角色后再进行权限校验。
 *
 * @since 3.7.0
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ydsz.gateway.authorization",
    name = "enabled",
    havingValue = "true")
public class AuthorizationFilter implements GlobalFilter, Ordered {

  /** 403 响应消息 */
  private static final String FORBIDDEN_MESSAGE = "权限不足，无法访问该资源";

  private final AuthorizationProperties properties;

  /** Ant 路径匹配器 */
  private static final AntPathMatcher pathMatcher = new AntPathMatcher();

  /**
   * P3-7: 网关层鉴权核心过滤器
   *
   * <p>检查用户角色是否满足路径要求，不满足时返回 403。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param chain 网关过滤器链
   * @return 放行或拒绝（403）的完成信号 Mono
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!properties.isEnabled()) {
      return chain.filter(exchange);
    }

    ServerHttpRequest request = exchange.getRequest();
    String path = request.getURI().getPath();

    // 白名单路径跳过鉴权
    if (PathGuard.isWhiteList(path)) {
      return chain.filter(exchange);
    }

    // 获取路径所需角色
    List<String> requiredRoles = resolveRequiredRoles(path);
    if (requiredRoles == null || requiredRoles.isEmpty()) {
      // 路径未配置角色要求，放行
      return chain.filter(exchange);
    }

    // 获取用户角色
    String rolesHeader = request.getHeaders().getFirst(GatewayConstants.HEADER_USER_ROLES);
    List<String> userRoles = parseRoles(rolesHeader);

    // 检查权限
    boolean authorized;
    if (properties.isAnyRoleMatch()) {
      // OR 模式：用户拥有任意一个所需角色即可
      authorized = requiredRoles.stream().anyMatch(userRoles::contains);
    } else {
      // AND 模式：用户必须拥有所有所需角色
      authorized = userRoles.containsAll(requiredRoles);
    }

    if (!authorized) {
      if (properties.isLogFailures()) {
        log.warn(
            "[Authorization] 鉴权失败: path={} requiredRoles={} userRoles={}",
            path,
            requiredRoles,
            userRoles);
      }
      return rejectForbidden(exchange, requiredRoles);
    }

    return chain.filter(exchange);
  }

  /**
   * P3-7: 解析路径所需的角色列表
   *
   * <p>按 Ant 风格匹配路径，返回匹配到的第一个路径模式的角色要求。
   *
   * @param path 请求路径
   * @return 所需角色列表，未匹配返回空
   */
  private List<String> resolveRequiredRoles(String path) {
    Map<String, List<String>> pathRolesMap = properties.getPathRoles();
    if (pathRolesMap == null || pathRolesMap.isEmpty()) {
      return List.of();
    }

    // 精确匹配优先
    if (pathRolesMap.containsKey(path)) {
      return pathRolesMap.get(path);
    }

    // Ant 模式匹配（最长路径优先）
    for (Map.Entry<String, List<String>> entry : pathRolesMap.entrySet()) {
      String pattern = entry.getKey();
      if (pathMatcher.match(pattern, path)) {
        return entry.getValue();
      }
    }

    return List.of();
  }

  /**
   * P3-7: 解析用户角色字符串（CSV 格式）
   *
   * @param rolesHeader 角色头字符串（如 "ROLE_ADMIN,ROLE_USER"）
   * @return 角色列表
   */
  private List<String> parseRoles(String rolesHeader) {
    if (rolesHeader == null || rolesHeader.isBlank()) {
      return List.of();
    }
    return List.of(rolesHeader.split(",")).stream()
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  /**
   * P3-7: 返回 403 禁止访问响应
   *
   * @param exchange 服务器 Web 交换上下文
   * @param requiredRoles 所需角色列表（用于日志）
   * @return 完成信号 Mono
   */
  private Mono<Void> rejectForbidden(ServerWebExchange exchange, List<String> requiredRoles) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.FORBIDDEN);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    BaseResponse<Void> body =
        BaseResponse.error(
            BaseResultCode.FORBIDDEN,
            FORBIDDEN_MESSAGE + " (需要角色: " + String.join(",", requiredRoles) + ")");
    byte[] bytes = YdszJson.toJsonBytes(body);
    DataBuffer buffer = response.bufferFactory().wrap(bytes);
    return response.writeWith(Mono.just(buffer));
  }

  /**
   * 过滤器顺序：{@code HIGHEST_PRECEDENCE + 12}。
   *
   * <p>在认证（+10）之后、API Key（+15）之前执行。
   *
   * @return 顺序值
   */
  @Override
  public int getOrder() {
    return GatewayFilterOrder.AUTHORIZATION.getOrder();
  }
}
