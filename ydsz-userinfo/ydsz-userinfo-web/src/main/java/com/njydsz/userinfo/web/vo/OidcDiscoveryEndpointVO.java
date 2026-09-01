package com.njydsz.userinfo.web.vo;

import java.util.List;

/**
 * OIDC Discovery 文档端点响应体（本地实现，避免 common-auth 依赖编译问题）。
 *
 * <p>实现 OpenID Connect Discovery 1.0 规范中的 Provider Configuration Response。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @param issuer                                  签发者标识
 * @param authorizationEndpoint                   授权端点 URL
 * @param tokenEndpoint                           令牌端点 URL
 * @param userinfoEndpoint                        用户信息端点 URL
 * @param jwksUri                                 JWKS 公钥端点 URL
 * @param revocationEndpoint                      Token 撤销端点 URL
 * @param introspectionEndpoint                    Token 自省端点 URL
 * @param scopesSupported                        支持的 scope 列表
 * @param responseTypesSupported                  支持的 response_type 列表
 * @param responseModesSupported                  支持的 response_mode 列表
 * @param grantTypesSupported                     支持的 grant_type 列表
 * @param subjectTypesSupported                   支持的 subject_type 列表
 * @param tokenEndpointAuthMethodsSupported       token 端点支持的客户端认证方式
 * @param idTokenSigningAlgValuesSupported        签名 ID Token 的算法列表
 * @param claimsSupported                         支持的声明（claim）列表
 */
public record OidcDiscoveryEndpointVO(
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
}
