package com.njydsz.userinfo.web.controller;

import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.core.constant.HeaderConstants;

/**
 * OAuth2 授权端点请求参数值对象。
 *
 * <p>封装 {@code GET /authorize} 端点的全部请求参数，避免方法参数数量超限（云顶编码规范 5.4 节）。
 * 通过组件级绑定注解映射到 HTTP 请求。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @param authorization Authorization 请求头（Bearer access_token）
 * @param clientId 客户端 ID（必须已注册）
 * @param redirectUri 回调地址（必须在 clientId 的白名单中）
 * @param state 客户端防 CSRF 随机串（服务端存储并校验）
 * @param scope 授权范围（可选，OIDC 流程需包含 {@code openid}）
 * @param nonce OIDC nonce（可选，用于防重放攻击）
 * @param codeChallenge PKCE 码挑战值（可选，用于公共客户端）
 * @param codeChallengeMethod PKCE 码挑战方法（可选，仅支持 S256）
 */
public record OAuth2AuthorizeRequest(
    @RequestHeader(HeaderConstants.AUTHORIZATION) String authorization,
    @RequestParam String clientId,
    @RequestParam String redirectUri,
    @RequestParam(required = false) String state,
    @RequestParam(required = false) String scope,
    @RequestParam(required = false) String nonce,
    @RequestParam(required = false) String codeChallenge,
    @RequestParam(required = false) String codeChallengeMethod) {

  /** "Bearer " 前缀长度。 */
  private static final int BEARER_PREFIX_LENGTH = 7;

  /** 校验 Authorization 头是否为 Bearer 格式。 */
  public boolean hasBearerToken() {
    return authorization != null && authorization.startsWith("Bearer ");
  }

  /** 提取 Bearer access_token。 */
  public String extractAccessToken() {
    return authorization.substring(BEARER_PREFIX_LENGTH);
  }
}
