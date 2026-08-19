package com.njydsz.common.auth.oidc;

import java.util.List;

import com.njydsz.common.json.YdszJson;

/**
 * OIDC Discovery 文档端点响应体
 *
 * <p>实现 OpenID Connect Discovery 1.0 规范中的 Provider Configuration Response。 参考 RFC：<a href="https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfigurationResponse">Section
 * 3</a>
 *
 * <p>该记录类承载标准 OIDC Discovery 元数据字段，可通过 {@link #toJson()} 序列化为 JSON 字符串， 供 {@code
 * /.well-known/openid-configuration} 端点返回。
 *
 * @param issuer                                  签发者标识（Issuer Identifier），必须以 https:// 开头
 * @param authorization_endpoint                  授权端点 URL
 * @param token_endpoint                          令牌端点 URL
 * @param userinfo_endpoint                       用户信息端点 URL
 * @param jwks_uri                                JWKS 公钥端点 URL
 * @param scopes_supported                        支持的 scope 列表（必须包含 "openid"）
 * @param response_types_supported                支持的 response_type 列表
 * @param grant_types_supported                   支持的 grant_type 列表
 * @param subject_types_supported                 支持的 subject_type 列表（如 "public"）
 * @param token_endpoint_auth_methods_supported   token 端点支持的客户端认证方式
 * @author ydsz-team
 * @since 1.6.0
 */
public record OidcDiscoveryEndpoint(
    String issuer,
    String authorizationEndpoint,
    String tokenEndpoint,
    String userinfoEndpoint,
    String jwksUri,
    List<String> scopesSupported,
    List<String> responseTypesSupported,
    List<String> grantTypesSupported,
    List<String> subjectTypesSupported,
    List<String> tokenEndpointAuthMethodsSupported) {

  /** OIDC 必须支持的 scope */
  public static final String SCOPE_OPENID = "openid";

  /** 默认支持的 response_type */
  public static final String RESPONSE_TYPE_CODE = "code";

  /** 默认支持的 grant_type */
  public static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";

  /** 默认支持的 subject_type */
  public static final String SUBJECT_TYPE_PUBLIC = "public";

  /** 默认支持的 token 端点认证方式 */
  public static final String AUTH_METHOD_CLIENT_SECRET_BASIC = "client_secret_basic";

  /** 默认支持的 token 端点认证方式 */
  public static final String AUTH_METHOD_CLIENT_SECRET_POST = "client_secret_post";

  /**
   * 将当前 OIDC Discovery 元数据序列化为 JSON 字符串
   *
   * <p>使用 {@link YdszJson#toJson(Object)} 进行序列化，确保与项目 JSON 规范一致。
   *
   * @return JSON 格式的 OIDC Discovery 文档
   */
  public String toJson() {
    return YdszJson.toJson(this);
  }
}
