package com.njydsz.userinfo.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.userinfo.domain.vo.JwksEndpointVO;
import com.njydsz.userinfo.domain.vo.OidcDiscoveryEndpointVO;
import com.njydsz.userinfo.server.config.OidcProperties;
import com.njydsz.common.locales.util.I18n;

/**
 * OIDC（OpenID Connect）协议端点 Controller
 *
 * <p>实现 OpenID Connect Discovery 1.0 规范的标准端点，提供元数据自发现和公钥获取能力。
 *
 * <p><b>端点清单：</b>
 *
 * <ul>
 *   <li>{@code GET /.well-known/openid-configuration} — OIDC Discovery 元数据文档
 *   <li>{@code GET /.well-known/jwks.json} — JWKS 公钥集合
 * </ul>
 *
 * <p>参考规范：<a href="https://openid.net/specs/openid-connect-discovery-1_0.html">OpenID Connect Discovery 1.0</a>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@ApiVersion("26.10.01")
@RestController
@RequestMapping("/.well-known")
@Tag(name = "OIDC", description = "OpenID Connect 标准端点")
public class OidcController {

  /** OIDC 配置属性 */
  private final OidcProperties oidcProperties;

  /**
   * JWKS 公钥端点（可选 Bean）。
   *
   * <p>通过 ObjectProvider 延迟获取，未注册时降级为 503 响应，避免启动失败。
   */
  private final JwksEndpointVO jwksEndpoint;

  /** OIDC 基础 URL */
  private final String baseUrl;

  /**
   * 手写构造器（替代 @RequiredArgsConstructor），将 JwksEndpointVO 改为 ObjectProvider 注入 + null 保护，
   * 避免因缺少 JwksEndpointVO Bean 导致启动失败。
   *
   * @param oidcProperties OIDC 配置属性
   * @param jwksEndpointProvider JWKS 公钥端点（可选）
   * @param baseUrl OIDC 基础 URL
   */
  public OidcController(
      OidcProperties oidcProperties,
      ObjectProvider<JwksEndpointVO> jwksEndpointProvider,
      @Value("${ydsz.userinfo.base-url:}") String baseUrl) {
    this.oidcProperties = oidcProperties;
    this.jwksEndpoint = jwksEndpointProvider.getIfAvailable();
    this.baseUrl = baseUrl;
  }

  /**
   * OIDC Discovery 元数据端点
   *
   * <p>返回 OpenID Connect Provider 的标准配置元数据，客户端可通过此文档自动发现授权端点、令牌端点、
   * 用户信息端点、JWKS 端点位置以及支持的 scope、response_type、grant_type 等能力声明。
   *
   * <p>P0-1 新增：revocation_endpoint（RFC 7009）、introspection_endpoint（RFC 7662）、
   * id_token_signing_alg_values_supported、claims_supported、response_modes_supported，
   * 对标 MaxKey 实现 OIDC 协议完整性。
   *
   * <p>响应 Content-Type 为 {@code application/json}，符合 OIDC Discovery 1.0 §3 规范。
   *
   * @return OIDC Discovery 元数据 JSON
   */
  @GetMapping(
      value = "/openid-configuration",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "OIDC Discovery 元数据",
      description = "返回 OpenID Connect Provider 的标准配置元数据，供客户端自动发现端点与能力")
  public ResponseEntity<OidcDiscoveryEndpointVO> discovery() {
    OidcDiscoveryEndpointVO metadata = new OidcDiscoveryEndpointVO(
        oidcProperties.getIssuer(),
        baseUrl + "/api/oauth2/authorize",
        baseUrl + "/api/oauth2/token",
        baseUrl + "/api/oauth2/userinfo",
        baseUrl + "/.well-known/jwks.json",
        baseUrl + "/api/oauth2/revoke",
        baseUrl + "/api/oauth2/introspect",
        List.of(
            OidcDiscoveryEndpointVO.SCOPE_OPENID,
            "profile",
            "email",
            "tenant"),
        List.of(OidcDiscoveryEndpointVO.RESPONSE_TYPE_CODE),
        List.of(OidcDiscoveryEndpointVO.RESPONSE_MODE_QUERY),
        List.of(
            OidcDiscoveryEndpointVO.GRANT_TYPE_AUTHORIZATION_CODE,
            "refresh_token"),
        List.of(OidcDiscoveryEndpointVO.SUBJECT_TYPE_PUBLIC),
        List.of(
            OidcDiscoveryEndpointVO.AUTH_METHOD_CLIENT_SECRET_BASIC,
            OidcDiscoveryEndpointVO.AUTH_METHOD_CLIENT_SECRET_POST),
        List.of(
            OidcDiscoveryEndpointVO.ALG_RS256,
            OidcDiscoveryEndpointVO.ALG_HS256),
        List.of(
            "sub", "preferred_username", "tenant_id", "email", "name", "iss", "aud", "exp", "iat"));
    log.debug(I18n.message("userinfo.OIDC.Discovery.请求.issuer=", new Object[]{oidcProperties.getIssuer()}));
    return ResponseEntity.ok(metadata);
  }

  /**
   * JWKS（JSON Web Key Set）公钥端点
   *
   * <p>返回 OIDC Provider 用于签名 JWT（含 ID Token）的公钥集合。客户端可通过此端点获取公钥并验证
   * ID Token 签名，无需共享密钥。
   *
   * <p>响应 Content-Type 为 {@code application/json}，符合 RFC 7517 规范。
   *
   * <p>null 保护：当 JWksEndpointVO Bean 未注册时返回 503。
   *
   * @return JWKS 公钥 JSON，格式如 {"keys":[{...}]}
   */
  @GetMapping(value = "/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "JWKS 公钥端点",
      description = "返回 OIDC Provider 用于签名 JWT 的公钥集合，符合 RFC 7517 规范")
  public ResponseEntity<String> jwks() {
    // null 保护：JwksEndpointVO Bean 不存在时降级返回 503
    if (jwksEndpoint == null) {
      log.warn("[OidcController] JwksEndpointVO Bean 未注册，JWKS 端点不可用");
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body("{\"error\":\"JWKS endpoint not available\"}");
    }
    String jwks = jwksEndpoint.generateJwks();
    return ResponseEntity.ok(jwks);
  }

}
