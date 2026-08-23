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
 * <p>P0-1 新增字段：{@code revocationEndpoint}、{@code introspectionEndpoint}、
 * {@code idTokenSigningAlgValuesSupported}、{@code claimsSupported}、{@code responseModesSupported}，
 * 对标 MaxKey 实现 OIDC 协议完整性。
 *
 * @param issuer                                  签发者标识（Issuer Identifier），必须以 https:// 开头
 * @param authorizationEndpoint                   授权端点 URL
 * @param tokenEndpoint                           令牌端点 URL
 * @param userinfoEndpoint                        用户信息端点 URL
 * @param jwksUri                                 JWKS 公钥端点 URL
 * @param revocationEndpoint                      Token 撤销端点 URL（RFC 7009）
 * @param introspectionEndpoint                    Token 自省端点 URL（RFC 7662）
 * @param scopesSupported                         支持的 scope 列表（必须包含 "openid"）
 * @param responseTypesSupported                  支持的 response_type 列表
 * @param responseModesSupported                  支持的 response_mode 列表
 * @param grantTypesSupported                     支持的 grant_type 列表
 * @param subjectTypesSupported                   支持的 subject_type 列表（如 "public"）
 * @param tokenEndpointAuthMethodsSupported       token 端点支持的客户端认证方式
 * @param idTokenSigningAlgValuesSupported        签名 ID Token 的算法列表
 * @param claimsSupported                         支持的声明（claim）列表
 * @author ydsz-team
 * @since 1.0.0
 */
public record OidcDiscoveryEndpoint(
    String issuer,
    String authorizationEndpoint,
    String tokenEndpoint,
    String userinfoEndpoint,
    String jwksUri,
    String revocationEndpoint,
    String introspectionEndpoint,
    List<String> scopesSupported,
    List<String> responseTypesSupported,
    List<String> responseModesSupported,
    List<String> grantTypesSupported,
    List<String> subjectTypesSupported,
    List<String> tokenEndpointAuthMethodsSupported,
    List<String> idTokenSigningAlgValuesSupported,
    List<String> claimsSupported) {

  /** OIDC 必须支持的 scope */
  public static final String SCOPE_OPENID = "openid";

  /** 默认支持的 response_type */
  public static final String RESPONSE_TYPE_CODE = "code";

  /** 默认支持的 response_mode */
  public static final String RESPONSE_MODE_QUERY = "query";

  /** 默认支持的 grant_type */
  public static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";

  /** 默认支持的 subject_type */
  public static final String SUBJECT_TYPE_PUBLIC = "public";

  /** 默认支持的 token 端点认证方式 */
  public static final String AUTH_METHOD_CLIENT_SECRET_BASIC = "client_secret_basic";

  /** 默认支持的 token 端点认证方式 */
  public static final String AUTH_METHOD_CLIENT_SECRET_POST = "client_secret_post";

  /** 默认支持的 ID Token 签名算法 */
  public static final String ALG_RS256 = "RS256";

  /** 默认支持的 ID Token 签名算法 */
  public static final String ALG_HS256 = "HS256";

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
