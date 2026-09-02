package com.njydsz.userinfo.web.controller;

import org.springframework.web.bind.annotation.RequestParam;

/**
 * OAuth2 token 端点请求参数值对象。
 *
 * <p>封装 {@code POST /token} 端点的全部表单参数，避免方法参数数量超限（云顶编码规范 5.4 节）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @param grantType 授权类型（authorization_code / refresh_token）
 * @param code 授权码（authorization_code 必填）
 * @param refreshToken 刷新令牌（refresh_token 必填）
 * @param clientId 客户端 ID
 * @param clientSecret 客户端密钥（confidential 客户端必填）
 * @param codeVerifier PKCE 码验证器（public 客户端 authorization_code 必填）
 * @param state OAuth2 CSRF 防护 state 参数（可选）
 */
public record OAuth2TokenRequest(
    @RequestParam String grantType,
    @RequestParam(required = false) String code,
    @RequestParam(required = false) String refreshToken,
    @RequestParam String clientId,
    @RequestParam(required = false) String clientSecret,
    @RequestParam(required = false) String codeVerifier,
    @RequestParam(required = false) String state) {
}
