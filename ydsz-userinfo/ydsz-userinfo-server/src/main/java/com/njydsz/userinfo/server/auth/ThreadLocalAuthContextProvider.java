package com.njydsz.userinfo.server.auth;

import java.util.Optional;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.auth.model.AuthInfo;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.userinfo.domain.auth.AuthContextProvider;

/**
 * 基于 ThreadLocal 的认证上下文提供者实现（当前阶段）。
 *
 * <p>从 {@link RequestContext} 获取当前请求的 ThreadLocal 认证信息。
 * 当未来升级到 Dubbo3 时，可新增 {@code DubboAttachmentAuthContextProvider} 实现本接口，
 * 从 Dubbo ServiceContext Attachment 获取认证信息，业务层代码无需修改。
 *
 * @author ydsz-team
 * @since 26.09.07
 * @see AuthContextProvider
 */
@Slf4j
@Component
public class ThreadLocalAuthContextProvider implements AuthContextProvider {

  /** 认证类型：会话 Token */
  private static final String AUTH_TYPE_TOKEN = "TOKEN";

  /** 认证类型：API Key */
  private static final String AUTH_TYPE_API_KEY = "API_KEY";

  /** 认证类型：未知 */
  private static final String AUTH_TYPE_UNKNOWN = "UNKNOWN";

  @Override
  public Optional<AuthContext> getCurrentContext() {
    String userId = RequestContext.getUserId();
    if (userId == null || userId.isBlank()) {
      return Optional.empty();
    }

    // 从 RequestContext 提取认证信息（AuthInfo 在 BaseAuthFilter 中写入）
    Object authObj = RequestContext.get(RequestContext.KEY_AUTH_INFO);
    if (authObj instanceof AuthInfo authInfo) {
      // 当前认证体系未提供 username / userType / roles 字段，留待后续扩展
      return Optional.of(new AuthContext(
          authInfo.getUniqueId(),
          null,
          authInfo.getTenantId(),
          null,
          Set.of(),
          authInfo.getServiceTypeCode() != null ? AUTH_TYPE_TOKEN : AUTH_TYPE_UNKNOWN));
    }

    // Fallback：仅基于 userId 构建最小上下文
    return Optional.of(new AuthContext(userId, null, null, null, Set.of(), AUTH_TYPE_UNKNOWN));
  }
}
