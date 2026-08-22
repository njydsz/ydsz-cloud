package com.njydsz.userinfo.server.oauth2;

import com.njydsz.common.json.annotation.JsonProperty;
import lombok.Builder;

/**
 * OAuth2 授权码上下文（P1-5：Map→Record 结构化重构）。
 *
 * <p>替代原 {@code Map<String, String>} 临时结构，提供类型安全的字段访问。
 * 通过 {@link #toJson()} / {@link #fromJson(String)} 与 Redis 存储层对接。
 *
 * <p><b>字段说明：</b>
 *
 * <ul>
 *   <li>{@link #clientId} — OAuth2 客户端 ID
 *   <li>{@link #userId} — 授权用户 ID
 *   <li>{@link #username} — 授权用户名
 *   <li>{@link #tenantId} — 租户 ID
 *   <li>{@link #redirectUri} — 回调地址（已在 authorize 端点校验白名单）
 *   <li>{@link #scope} — 授权 scope
 *   <li>{@link #nonce} — OIDC nonce（可为 null，用于防重放）
 *   <li>{@link #codeChallenge} — PKCE code_challenge（可为 null）
 *   <li>{@link #codeChallengeMethod} — PKCE code_challenge_method（可为 null）
 *   <li>{@link #state} — OAuth2 CSRF 防护 state 参数（可为 null）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Builder
public record OAuthCodeContext(
    @JsonProperty("clientId") String clientId,
    @JsonProperty("userId") String userId,
    @JsonProperty("username") String username,
    @JsonProperty("tenantId") String tenantId,
    @JsonProperty("redirectUri") String redirectUri,
    @JsonProperty("scope") String scope,
    @JsonProperty("nonce") String nonce,
    @JsonProperty("codeChallenge") String codeChallenge,
    @JsonProperty("codeChallengeMethod") String codeChallengeMethod,
    @JsonProperty("state") String state) {

  /** 序列化为 JSON 字符串（存储到 Redis）。 */
  public String toJson() {
    return com.njydsz.common.json.YdszJson.toJson(this);
  }

  /** 从 JSON 字符串反序列化。 */
  public static OAuthCodeContext fromJson(String json) {
    return com.njydsz.common.json.YdszJson.fromJson(json, OAuthCodeContext.class);
  }
}
