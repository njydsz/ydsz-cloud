package com.njydsz.userinfo.domain.auth;

import java.util.Optional;

/**
 * 认证上下文提供者接口（P2-1 Dubbo3 上下文传递适配）。
 *
 * <p><b>设计目标：</b>为未来升级到 Dubbo3 Triple 协议时，认证信息从 gateway → user-service 的
 * 跨服务传递提供标准化的接口契约。当前 Spring Cloud OpenFeign 场景下基于
 * {@link com.njydsz.common.core.context.RequestContext} ThreadLocal 实现，
 * Dubbo3 升级时需改用 {@code ServiceContext} 传递。
 *
 * <p><b>使用方式：</b>业务服务通过 {@link #getCurrentContext()} 获取当前请求的认证上下文，
 * 而无需关心上下文是通过 ThreadLocal 还是 RPC 透传获取的。
 *
 * <p><b>Dubbo3 升级路线：</b>
 *
 * <ol>
 *   <li><b>当前阶段</b>：实现类基于 ThreadLocal（{@code RequestContext}）</li>
 *   <li><b>网关改造</b>：在 gateway 中将 JWT 解析结果写入 Dubbo Attachment</li>
 *   <li><b>服务适配</b>：在 RpcContextFilter 中从 Attachment 重建 AuthContext</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.07
 * @see com.njydsz.common.core.context.RequestContext
 */
public interface AuthContextProvider {

  /**
   * 获取当前认证上下文。
   *
   * @return 当前请求的认证上下文，无上下文时返回 {@link Optional#empty()}
   */
  Optional<AuthContext> getCurrentContext();

  /**
   * 获取当前登录用户 ID。
   *
   * @return 用户 ID，未登录时返回 {@link Optional#empty()}
   */
  default Optional<String> getCurrentUserId() {
    return getCurrentContext().map(AuthContext::userId);
  }

  /**
   * 认证上下文数据对象。
   *
   * <p>封装认证后的用户身份信息，用于跨服务传递。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param tenantId 租户 ID
   * @param userType 用户类型（PLATFORM / ISV / TENANT_ADMIN / REGULAR）
   * @param roles 角色编码集合
   * @param authType 认证类型（PASSWORD / WEBHOOK / API_KEY / SSO）
   */
  record AuthContext(
      String userId,
      String username,
      String tenantId,
      String userType,
      java.util.Set<String> roles,
      String authType) {
  }
}
