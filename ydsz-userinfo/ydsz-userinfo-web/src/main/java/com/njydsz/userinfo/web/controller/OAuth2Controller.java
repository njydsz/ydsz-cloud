package com.njydsz.userinfo.web.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.server.config.UserInfoProperties;

/**
 * OAuth2 授权码模式 Controller
 *
 * <p>实现标准 OAuth2 Authorization Code Grant 流程（参考 RFC 6749），并支持 PKCE（RFC 7636）增强公共客户端安全性。
 *
 * <p><b>接口路径：</b>{@code /api/v1/oauth2}
 *
 * <p><b>两步流程：</b>
 *
 * <ol>
 *   <li>{@code GET /authorize} — 资源拥有者（用户）授权阶段，需携带已登录的 access_token， 签发短期授权码（5 分钟有效）
 *   <li>{@code POST /token} — 客户端用授权码 + clientSecret（或 PKCE code_verifier）换取 access_token
 * </ol>
 *
 * <p><b>PKCE（RFC 7636）支持：</b>
 *
 * <ul>
 *   <li>适用于公共客户端（无法安全存储 clientSecret 的场景，如 SPA、移动端）
 *   <li>客户端在 /authorize 时发送 code_challenge（SHA-256 哈希）
 *   <li>客户端在 /token 时发送 code_verifier（原始随机串）
 *   <li>服务端验证 code_verifier 的 SHA-256 哈希是否与 code_challenge 匹配
 * </ul>
 *
 * <p><b>安全机制：</b>
 *
 * <ul>
 *   <li><b>clientId 校验</b>：客户端必须先在 {@link UserInfoProperties} 中注册
 *   <li><b>redirect_uri 校验</b>：必须命中注册时配置的白名单（防开放重定向）
 *   <li><b>clientSecret 校验</b>：confidential 客户端必须提供；public 客户端可使用 PKCE 替代
 *   <li><b>授权码一次性</b>：使用后立即从 Redis 删除（防重放）
 *   <li><b>TTL 5 分钟</b>：授权码短时有效（{@link #CODE_TTL_SECONDS}）
 *   <li><b>用户上下文透传</b>：userId / username / tenantId 一起打包到授权码
 * </ul>
 *
 * <p><b>与普通登录的区别：</b>OAuth2 流程下用户始终在 ydsz 系统登录，仅授权第三方获取<b>受限</b>的 token； 与 AuthController 相比，OAuth2
 * 的 token 携带了 redirectUri 等客户端上下文。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.userinfo.server.config.UserInfoProperties OAuth2 客户端配置
 * @see com.njydsz.userinfo.web.controller.AuthController 普通登录（账号密码模式）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/oauth2")
@RequiredArgsConstructor
@Tag(name = "OAuth2", description = "OAuth2 授权码模式")
public class OAuth2Controller {

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  private final RedisStringOps redisStringOps;
  private final TokenService tokenService;
  private final UserInfoProperties properties;

  /** 授权码有效期（秒）：5 分钟，符合 RFC 6749 §4.1.2 建议（推荐 ≤ 10 分钟） */
  private static final long CODE_TTL_SECONDS = 300;

  /** 授权码 Redis Key 前缀：{@code oauth2:code:{code}} */
  private static final String CODE_KEY_PREFIX = "oauth2:code:";

  /** PKCE code_challenge_method 常量：S256（SHA-256） */
  private static final String PKCE_METHOD_S256 = "S256";

  /** PKCE code_verifier 最小长度（RFC 7636 §4.1） */
  private static final int PKCE_VERIFIER_MIN_LENGTH = 43;

  /** PKCE code_verifier 最大长度（RFC 7636 §4.1） */
  private static final int PKCE_VERIFIER_MAX_LENGTH = 128;

  /**
   * 获取授权码
   *
   * <p>OAuth2 授权码模式第一步。
   *
   * <p><b>流程：</b>
   *
   * <ol>
   *   <li>校验 Authorization 头（必须是已登录的 access_token）
   *   <li>解析并验证 token 有效性
   *   <li>校验 clientId 已在系统中注册
   *   <li>校验 redirectUri 在 clientId 的白名单中（防开放重定向）
   *   <li}生成 UUID 授权码，将 userId/username/tenantId/clientId/redirectUri/codeChallenge 序列化后写入 Redis
   *   <li>返回授权码（业务方需将 code 拼到 redirectUri 的 query 上）
   * </ol>
   *
   * @param authorization Authorization 请求头（Bearer access_token）
   * @param clientId 客户端 ID（必须已注册）
   * @param redirectUri 回调地址（必须在 clientId 的白名单中）
   * @param state 客户端防 CSRF 随机串（透传，ydsz 不存储）
   * @param codeChallenge PKCE 码挑战值（可选，用于公共客户端）
   * @param codeChallengeMethod PKCE 码挑战方法（可选，仅支持 S256）
   * @return OAuth2 授权码
   * @throws BusinessException {@code TOKEN_INVALID} / {@code OAUTH2_CLIENT_INVALID} / {@code
   *     OAUTH2_REDIRECT_URI_MISMATCH}
   */
  @GetMapping("/authorize")
  @Operation(summary = "获取授权码", description = "需携带已登录的 access_token，生成 OAuth2 授权码，5 分钟有效")
  public BaseResponse<String> authorize(
      @RequestHeader(HeaderConstants.AUTHORIZATION) String authorization,
      @RequestParam String clientId,
      @RequestParam String redirectUri,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String codeChallenge,
      @RequestParam(required = false) String codeChallengeMethod) {

    // 1. 认证检查：必须携带有效的 access_token
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
    }
    String accessToken = authorization.substring(7);
    UserInfo userInfo = tokenService.parseAccessToken(accessToken);
    if (userInfo == null || !tokenService.validateAccessToken(accessToken)) {
      throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
    }

    // 2. 验证 clientId 是否已注册
    UserInfoProperties.OAuth2Client clientConfig = properties.getOauth2Clients().get(clientId);
    if (clientConfig == null) {
      throw new BusinessException(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID);
    }

    // 3. 校验 redirect_uri 在客户端注册白名单中（RFC 6749 §3.1.2.3）
    if (clientConfig.getRedirectUris() != null
        && !clientConfig.getRedirectUris().isEmpty()
        && !clientConfig.getRedirectUris().contains(redirectUri)) {
      throw new BusinessException(UserInfoExceptionCode.OAUTH2_REDIRECT_URI_MISMATCH);
    }

    // 4. 使用 YdszJson 序列化授权码上下文（含 tenantId + PKCE），Redis 存储 5 分钟
    String code = String.valueOf(snowflakeIdGenerator.nextId()).replace("-", "");
    Map<String, String> contextMap = new HashMap<>();
    contextMap.put("clientId", clientId);
    contextMap.put("userId", userInfo.getUserId());
    contextMap.put("username", userInfo.getUsername());
    contextMap.put("tenantId", userInfo.getTenantId() != null ? userInfo.getTenantId() : "1");
    contextMap.put("redirectUri", redirectUri);
    // PKCE 支持：存储 code_challenge 和 code_challenge_method
    if (codeChallenge != null && !codeChallenge.isBlank()) {
      contextMap.put("codeChallenge", codeChallenge);
      contextMap.put(
          "codeChallengeMethod",
          codeChallengeMethod != null ? codeChallengeMethod : PKCE_METHOD_S256);
    }
    String context = YdszJson.toJson(contextMap);
    redisStringOps.set(CODE_KEY_PREFIX + code, context, CODE_TTL_SECONDS);
    log.info(
        "OAuth2 authorize: clientId={}, userId={}, code={}, pkce={}",
        clientId,
        userInfo.getUserId(),
        code,
        codeChallenge != null);
    return BaseResponse.success(code);
  }

  /**
   * 用授权码换取 Token
   *
   * <p>OAuth2 授权码模式第二步。
   *
   * <p><b>流程：</b>
   *
   * <ol>
   *   <li>校验 clientId + clientSecret（confidential 客户端）或 PKCE code_verifier（public 客户端）
   *   <li>从 Redis 读取授权码上下文
   *   <li>校验传入的 clientId 与授权码内的 clientId 一致（防跨客户端重放）
   *   <li>校验 PKCE code_verifier（如果授权码包含 code_challenge）
   *   <li>删除授权码（一次性）
   *   <li>为 userId 签发新的 access_token / refresh_token
   *   <li>返回标准 OAuth2 token 响应（参考 RFC 6749 §5.1）
   * </ol>
   *
   * @param code 授权码（/authorize 返回）
   * @param clientId 客户端 ID
   * @param clientSecret 客户端密钥（confidential 客户端必填）
   * @param codeVerifier PKCE 码验证器（public 客户端必填，用于验证 code_challenge）
   * @return 标准 OAuth2 token 响应（含 access_token / refresh_token / token_type / expires_in / scope）
   * @throws BusinessException {@code OAUTH2_CLIENT_INVALID} / {@code OAUTH2_CODE_INVALID} / {@code
   *     OAUTH2_PKCE_VERIFIER_INVALID}
   */
  @PostMapping("/token")
  @Operation(
      summary = "用授权码换取 Token",
      description = "标准 OAuth2 token 端点，需校验 clientSecret 或 PKCE code_verifier")
  public BaseResponse<Map<String, Object>> token(
      @RequestParam String code,
      @RequestParam String clientId,
      @RequestParam(required = false) String clientSecret,
      @RequestParam(required = false) String codeVerifier) {

    // 1. 读取并解析授权码上下文
    String storedContext = redisStringOps.get(CODE_KEY_PREFIX + code, String.class);
    if (storedContext == null) {
      throw new BusinessException(UserInfoExceptionCode.OAUTH2_CODE_INVALID);
    }

    Map<String, Object> context;
    try {
      context = YdszJson.parseMap(storedContext);
    } catch (Exception e) {
      log.error("Failed to parse OAuth2 code context", e);
      throw new BusinessException(UserInfoExceptionCode.OAUTH2_CODE_INVALID);
    }

    String storedClientId = getString(context, "clientId");
    String userId = getString(context, "userId");
    String username = getString(context, "username");
    String tenantId = getString(context, "tenantId");
    String storedCodeChallenge = getString(context, "codeChallenge");
    String storedCodeChallengeMethod = getString(context, "codeChallengeMethod");

    // 2. 校验 clientId 一致性（防跨客户端重放）
    if (!clientId.equals(storedClientId)) {
      throw new BusinessException(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID);
    }

    // 3. 认证方式校验：clientSecret 或 PKCE 二选一
    if (storedCodeChallenge != null && !storedCodeChallenge.isBlank()) {
      // PKCE 流程：验证 code_verifier
      if (codeVerifier == null || codeVerifier.isBlank()) {
        throw new BusinessException(UserInfoExceptionCode.OAUTH2_PKCE_VERIFIER_INVALID);
      }
      if (!verifyPkceCodeVerifier(
          codeVerifier, storedCodeChallenge, storedCodeChallengeMethod)) {
        throw new BusinessException(UserInfoExceptionCode.OAUTH2_PKCE_VERIFIER_INVALID);
      }
    } else {
      // 传统流程：强制校验 clientSecret
      if (!properties.validateOAuth2Client(clientId, clientSecret)) {
        throw new BusinessException(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID);
      }
    }

    // 4. 授权码一次性使用：使用后立即删除
    redisStringOps.del(CODE_KEY_PREFIX + code);

    // 5. 重建 UserInfo 并签发新 token
    UserInfo userInfo = new UserInfo();
    userInfo.setUserId(userId);
    userInfo.setUsername(username);
    userInfo.setTenantId(tenantId != null ? tenantId : "1");

    String newAccessToken = tokenService.issueAccessToken(userInfo);
    String refreshToken = tokenService.issueRefreshToken(userInfo);

    log.info("OAuth2 token issued: clientId={}, userId={}", clientId, userId);

    // 6. 返回标准 OAuth2 响应（RFC 6749 §5.1）
    return BaseResponse.success(
        Map.of(
            "access_token",
            newAccessToken,
            "refresh_token",
            refreshToken,
            "token_type",
            "Bearer",
            "expires_in",
            properties.getTokenTtlSeconds(),
            "scope",
            "read write"));
  }

  /**
   * 安全地从 Map 中读取字符串字段
   *
   * <p>处理 JSON 反序列化时 {@code Object} → {@code String} 的隐式转换， 同时防御 null 字段，统一返回 null 表示字段缺失。
   *
   * @param map 上下文 Map
   * @param key 字段名
   * @return 字段字符串值；缺失或为 null 时返回 null
   */
  private String getString(Map<String, Object> map, String key) {
    Object value = map.get(key);
    return value != null ? value.toString() : null;
  }

  /**
   * 验证 PKCE code_verifier（RFC 7636 §4.1）。
   *
   * <p>使用 S256 方法：code_challenge = BASE64URL(SHA256(code_verifier))。
   *
   * @param codeVerifier 码验证器（原始随机串）
   * @param codeChallenge 码挑战值（存储的哈希值）
   * @param codeChallengeMethod 码挑战方法（仅支持 S256）
   * @return true 验证通过；false 验证失败
   */
  private boolean verifyPkceCodeVerifier(
      String codeVerifier, String codeChallenge, String codeChallengeMethod) {
    // 校验 code_verifier 长度（RFC 7636 §4.1: 43-128 字符）
    if (codeVerifier.length() < PKCE_VERIFIER_MIN_LENGTH
        || codeVerifier.length() > PKCE_VERIFIER_MAX_LENGTH) {
      log.warn("PKCE code_verifier length invalid: {}", codeVerifier.length());
      return false;
    }
    // 仅支持 S256 方法
    if (!PKCE_METHOD_S256.equalsIgnoreCase(codeChallengeMethod)) {
      log.warn("PKCE code_challenge_method not supported: {}", codeChallengeMethod);
      return false;
    }
    try {
      // 计算 SHA-256 哈希并 Base64URL 编码
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
      String computedChallenge =
          Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
      // 恒定时间比较（防时序攻击）
      return MessageDigest.isEqual(
          computedChallenge.getBytes(StandardCharsets.US_ASCII),
          codeChallenge.getBytes(StandardCharsets.US_ASCII));
    } catch (NoSuchAlgorithmException e) {
      log.error("SHA-256 algorithm not available", e);
      return false;
    }
  }
}
