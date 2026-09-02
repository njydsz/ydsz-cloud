package com.njydsz.userinfo.web.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.service.TokenBlacklistService;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.util.security.DigestUtils;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.server.config.UserInfoProperties;
import com.njydsz.userinfo.server.oauth2.OAuthCodeContext;

/**
 * OAuth2 授权码模式 Controller
 *
 * <p>实现标准 OAuth2 Authorization Code Grant 流程（参考 RFC 6749），并支持 PKCE（RFC 7636）增强公共客户端安全性。
 *
 * <p><b>OIDC 支持：</b>当 scope 包含 {@code openid} 时，token 端点额外返回 id_token（遵循 OpenID Connect Core 1.0），
 * id_token 包含标准声明（iss, sub, aud, exp, iat, nonce），由 {@link TokenService#issueIdToken} 签发。
 *
 * <p><b>接口路径：</b>{@code /api/v1/oauth2}
 *
 * <p><b>端点清单（P1-4 补齐）：</b>
 *
 * <ol>
 *   <li>{@code GET /authorize} — 资源拥有者（用户）授权阶段，需携带已登录的 access_token， 签发短期授权码（5 分钟有效）
 *   <li>{@code Post /token} — 授权码换取 token（grant_type=authorization_code），或 refresh_token 轮换（grant_type=refresh_token）
 *   <li>{@code Post /revoke} — 撤销 token（RFC 7009）
 *   <li>{@code Post /introspect} — 校验 token 元数据（RFC 7662，资源服务器用）
 *   <li>{@code GET /userinfo} — 当前用户信息（OIDC userinfo 风格）
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
 * @since 26.09.01
 * @see com.njydsz.userinfo.server.config.UserInfoProperties OAuth2 客户端配置
 * @see com.njydsz.userinfo.web.controller.AuthController 普通登录（账号密码模式）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/oauth2")
@RequiredArgsConstructor
@Tag(name = "OAuth2", description = "OAuth2 授权码模式")
public class OAuth2Controller {
  /** "Bearer " 前缀长度 */
  private static final int BEARER_PREFIX_LENGTH = 7;

  private final RedisStringOps redisStringOps;
  private final TokenService tokenService;
  private final TokenBlacklistService tokenBlacklistService;
  private final UserInfoProperties properties;

  /** 授权码有效期（秒）：5 分钟，符合 RFC 6749 §4.1.2 建议（推荐 ≤ 10 分钟） */
  private static final long CODE_TTL_SECONDS = 300;

  /** 授权码 Redis Key 前缀：{@code oauth2:code:{code}} */
  private static final String CODE_KEY_PREFIX = "oauth2:code:";

  /** State 校验 Redis Key 前缀：{@code oauth2:state:{state}} */
  private static final String STATE_KEY_PREFIX = "oauth2:state:";

  /** PKCE code_challenge_method 常量：S256（SHA-256） */
  private static final String PKCE_METHOD_S256 = "S256";

  /** PKCE code_verifier 最小长度（RFC 7636 §4.1） */
  private static final int PKCE_VERIFIER_MIN_LENGTH = 43;

  /** PKCE code_verifier 最大长度（RFC 7636 §4.1） */
  private static final int PKCE_VERIFIER_MAX_LENGTH = 128;

  /** OAuth2 grant_type：授权码模式 */
  private static final String OAUTH2_GRANT_AUTHORIZATION_CODE = "authorization_code";

  /** OAuth2 grant_type：refresh_token 轮换 */
  private static final String OAUTH2_GRANT_REFRESH_TOKEN = "refresh_token";

  /** OIDC scope：openid（OpenID Connect Core 1.0 §3.1.2.1） */
  private static final String SCOPE_OPENID = "openid";

  /** scope 显示名称映射 */
  private static final Map<String, String> SCOPE_DISPLAY_NAMES = Map.of(
      "openid", "OpenID Connect 身份认证",
      "profile", "个人资料访问",
      "email", "邮箱地址访问",
      "tenant", "租户信息访问",
      "read", "读取权限",
      "write", "写入权限");

  /** P0-4: 授权码随机数生成器（SecureRandom，128 位熵替代可枚举的雪花 ID） */
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /** P0-4: 授权码随机字节长度（32 字节 = 256 位熵，Base64URL 编码后 43 字符） */
  private static final int CODE_RANDOM_BYTES = 32;

  /**
   * P0-3: 授权码原子取删 Lua 脚本（GETDEL 语义，防并发重放）。
   *
   * <p>授权码必须一次性使用：并发请求携带同一授权码时，仅第一个请求能读到值，
   * 后续请求 GET 返回 nil，无法二次签发 token。
   */
  private static final String GETDEL_CODE_LUA =
      "local v = redis.call('GET', KEYS[1]) "
          + "if v then redis.call('DEL', KEYS[1]) end "
          + "return v";

  /**
   * P0-3: 原子读取并删除授权码上下文（GETDEL）。
   *
   * @param code 授权码
   * @return 授权码上下文 JSON；不存在或已消费返回 null
   */
  private String getAndDeleteCode(String code) {
    try {
      return redisStringOps.executeScriptWithShaCache(
          GETDEL_CODE_LUA,
          String.class,
          Collections.singletonList(CODE_KEY_PREFIX + code));
    } catch (Exception e) {
      log.error("OAuth2 atomic get-and-delete code failed, code={}", code, e);
      return null;
    }
  }

  /**
   * P0-4: 生成高强度授权码（RFC 6749 §10.10：至少 128 位随机熵）。
   *
   * @return Base64URL 编码的 32 字节随机串（43 字符）
   */
  private String generateAuthorizationCode() {
    byte[] bytes = new byte[CODE_RANDOM_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

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
   *   <li>生成 UUID 授权码，将 userId/username/tenantId/clientId/redirectUri/codeChallenge/nonce 序列化后写入 Redis
   *   <li>返回授权码（业务方需将 code 拼到 redirectUri 的 query 上）
   * </ol>
   *
   * @param request 授权请求参数（含 Authorization 头、clientId、redirectUri、state、scope、nonce、PKCE 参数）
   * @return OAuth2 授权码
   * @throws BusinessException {@code TOKEN_INVALID} / {@code OAUTH2_CLIENT_INVALID} / {@code
   *     OAUTH2_REDIRECT_URI_MISMATCH}
   */
  @Audit(
      module = "OAuth2",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'OAuth2 授权码签发: clientId=' + #request.clientId() + ', redirectUri=' + #request.redirectUri()")
  @RateLimit(resource = "userinfo.oauth2.authorize", threshold = 20)
  @GetMapping("/authorize")
  @Operation(summary = "获取授权码", description = "需携带已登录的 access_token，生成 OAuth2 授权码，5 分钟有效")
  public YdszResponse<String> authorize(OAuth2AuthorizeRequest request) {
    // 1. 认证检查：必须携带有效的 access_token
    if (!request.hasBearerToken()) {
      throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
    }
    String accessToken = request.extractAccessToken();
    UserInfo userInfo = tokenService.parseAccessToken(accessToken);
    if (userInfo == null || !tokenService.validateAccessToken(accessToken)) {
      throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
    }

    // 2. 验证 clientId 是否已注册
    UserInfoProperties.OAuth2Client clientConfig =
        properties.getOauth2Clients().get(request.clientId());
    if (clientConfig == null) {
      throw new BusinessException(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID);
    }

    // 3. P0-2: 校验 redirect_uri 必须命中客户端注册白名单（RFC 6749 §3.1.2.3）
    validateRedirectUri(clientConfig, request.redirectUri(), request.clientId());

    // 4. P1-3: scope 细粒度授权校验（客户端配置了 allowedScopes 时强制）
    validateScopes(clientConfig, request.scope(), request.clientId());

    // 5. 构建类型安全的授权码上下文（P1-5：Map→Record 重构，消除字符串键硬编码）
    String code = generateAuthorizationCode();
    String effectiveTenantId = userInfo.getTenantId() != null ? userInfo.getTenantId() : "1";
    String effectiveScope = request.scope() != null ? request.scope() : "";
    String codeChallengeMethodResolved = resolveCodeChallengeMethod(request);

    OAuthCodeContext context = OAuthCodeContext.builder()
        .clientId(request.clientId())
        .userId(userInfo.getUserId())
        .username(userInfo.getUsername())
        .tenantId(effectiveTenantId)
        .redirectUri(request.redirectUri())
        .scope(effectiveScope)
        .nonce(request.nonce())
        .codeChallenge(request.codeChallenge())
        .codeChallengeMethod(codeChallengeMethodResolved)
        .state(request.state())
        .build();

    redisStringOps.set(CODE_KEY_PREFIX + code, context.toJson(), CODE_TTL_SECONDS);

    // 存储 state → code 映射（CSRF 防护，TTL 与授权码一致）
    if (request.state() != null && !request.state().isBlank()) {
      redisStringOps.set(STATE_KEY_PREFIX + request.state(), code, CODE_TTL_SECONDS);
      log.debug("OAuth2 state stored: state={}", request.state());
    }

    log.info(
        "OAuth2 authorize: clientId={}, userId={}, code={}, pkce={}, state={}",
        request.clientId(),
        userInfo.getUserId(),
        code,
        request.codeChallenge() != null,
        request.state() != null);
    return YdszResponse.success(code);
  }

  /**
   * 校验回调地址是否命中客户端注册白名单（防开放重定向）。
   *
   * @param clientConfig 客户端配置
   * @param redirectUri 回调地址
   * @param clientId 客户端 ID
   */
  private void validateRedirectUri(
      UserInfoProperties.OAuth2Client clientConfig, String redirectUri, String clientId) {
    List<String> redirectUris = clientConfig.getRedirectUris();
    if (redirectUris == null || redirectUris.isEmpty() || !redirectUris.contains(redirectUri)) {
      log.warn(
          "OAuth2 redirect_uri mismatch or empty whitelist rejected: clientId={}, redirectUri={}",
          clientId,
          redirectUri);
      throw new BusinessException(UserInfoExceptionCode.OAUTH2_REDIRECT_URI_MISMATCH);
    }
  }

  /**
   * 校验请求的 scope 是否在客户端允许范围内。
   *
   * @param clientConfig 客户端配置
   * @param scope 请求的 scope（可为 null）
   * @param clientId 客户端 ID
   */
  private void validateScopes(
      UserInfoProperties.OAuth2Client clientConfig, String scope, String clientId) {
    Set<String> requestedScopes = parseScopes(scope);
    Set<String> allowedScopes = clientConfig.getAllowedScopes();
    if (!requestedScopes.isEmpty()
        && allowedScopes != null
        && !allowedScopes.isEmpty()
        && !allowedScopes.containsAll(requestedScopes)) {
      log.warn(
          "OAuth2 scope not allowed: clientId={}, requested={}, allowed={}",
          clientId,
          requestedScopes,
          allowedScopes);
      throw new BusinessException(UserInfoExceptionCode.OAUTH2_SCOPE_INVALID);
    }
  }

  /**
   * 解析 PKCE code_challenge_method：未指定且携带挑战值时默认 S256。
   *
   * @param request 授权请求参数
   * @return 解析后的方法；无挑战值时返回 null
   */
  private String resolveCodeChallengeMethod(OAuth2AuthorizeRequest request) {
    if (request.codeChallenge() == null || request.codeChallenge().isBlank()) {
      return null;
    }
    return request.codeChallengeMethod() != null ? request.codeChallengeMethod() : PKCE_METHOD_S256;
  }

  /**
   * 获取 OAuth2 授权同意信息（用户授权页面展示用）。
   *
   * <p>返回当前用户、客户端信息、请求的 scope 列表，供前端渲染用户授权同意界面。
   *
   * @param authorization Authorization 请求头（Bearer access_token）
   * @param clientId 客户端 ID
   * @param redirectUri 回调地址
   * @param scope 请求的 scope（可选）
   * @return 授权同意上下文（用户、客户端、scope 详情）
   */
  @GetMapping("/consent")
  @Operation(summary = "获取授权同意信息", description = "返回客户端和 scope 信息，供用户授权页面展示")
  public YdszResponse<Map<String, Object>> getConsentInfo(
      @RequestHeader(HeaderConstants.AUTHORIZATION) String authorization,
      @RequestParam String clientId,
      @RequestParam String redirectUri,
      @RequestParam(required = false) String scope) {

    // 1. 认证 + 客户端校验
    UserInfo userInfo = authenticateRequest(authorization);
    UserInfoProperties.OAuth2Client clientConfig = validateClientAndRedirect(clientId, redirectUri);

    // 2. 解析 scope 并构建显示信息
    Set<String> requestedScopes = parseScopes(scope);
    List<Map<String, String>> scopeDetails = requestedScopes.stream()
        .sorted()
        .map(s -> Map.of("scope", s, "displayName", SCOPE_DISPLAY_NAMES.getOrDefault(s, s)))
        .toList();

    // 3. 构建响应
    Map<String, Object> consentInfo = new HashMap<>(16);
    consentInfo.put("user", Map.of(
        "userId", userInfo.getUserId(),
        "username", userInfo.getUsername()));
    consentInfo.put("client", Map.of(
        "clientId", clientId,
        "clientName", clientConfig.getClientName() != null ? clientConfig.getClientName() : clientId,
        "redirectUri", redirectUri));
    consentInfo.put("scopes", scopeDetails);
    consentInfo.put("scopeString", scope != null ? scope : "");

    return YdszResponse.success(consentInfo);
  }

  /**
   * 校验 Authorization 头并解析用户信息。
   *
   * @param authorization Authorization 请求头
   * @return 解析后的用户信息
   */
  private UserInfo authenticateRequest(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
    }
    String accessToken = authorization.substring(BEARER_PREFIX_LENGTH);
    UserInfo userInfo = tokenService.parseAccessToken(accessToken);
    if (userInfo == null || !tokenService.validateAccessToken(accessToken)) {
      throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
    }
    return userInfo;
  }

  /**
   * 校验客户端注册状态与回调地址白名单。
   *
   * @param clientId 客户端 ID
   * @param redirectUri 回调地址
   * @return 客户端配置
   */
  private UserInfoProperties.OAuth2Client validateClientAndRedirect(
      String clientId, String redirectUri) {
    UserInfoProperties.OAuth2Client clientConfig = properties.getOauth2Clients().get(clientId);
    if (clientConfig == null) {
      throw new BusinessException(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID);
    }
    validateRedirectUri(clientConfig, redirectUri, clientId);
    return clientConfig;
  }

  /**
   * 提交 OAuth2 授权同意（用户确认授权）。
   *
   * <p>用户确认授权后，生成授权码并返回（与 authorize() 逻辑一致，但作为独立的 consent 流程）。
   *
   * @param request 授权请求参数（含 Authorization 头、clientId、redirectUri、state、scope、nonce、PKCE 参数）
   * @return OAuth2 授权码
   */
  @Audit(
      module = "OAuth2",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'OAuth2 用户授权同意: clientId=' + #request.clientId()")
  @PostMapping("/consent")
  @Operation(summary = "提交授权同意", description = "用户确认授权后生成授权码")
  @RateLimit(resource = "userinfo.oauth2.consent", threshold = 10)
  public YdszResponse<String> submitConsent(OAuth2AuthorizeRequest request) {

    // 直接调用 authorize() 生成授权码
    return authorize(request);
  }

  /**
   * 用授权码或 refresh_token 换取 Token（OAuth2 token 端点）。
   *
   * <p><b>支持的 grant_type：</b>
   *
   * <ul>
   *   <li>{@code authorization_code} — 授权码模式：code + clientSecret（confidential）或 PKCE code_verifier（public）
   *   <li>{@code refresh_token} — token 轮换：refreshToken + clientSecret，旧 refresh_token 立即失效
   * </ul>
   *
   * @param request OAuth2 token 请求参数（grantType / code / refreshToken / clientId / clientSecret / codeVerifier / state）
   * @return 标准 OAuth2 token 响应
   */
  @Audit(
      module = "OAuth2",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'OAuth2 token 签发: grantType=' + #request.grantType() + ', clientId=' + #request.clientId()")
  @RateLimit(resource = "userinfo.oauth2.token", threshold = 20)
  @PostMapping("/token")
  @Operation(
      summary = "用授权码或 refresh_token 换取 Token",
      description = "支持 authorization_code（含 PKCE）与 refresh_token 两种授权类型")
  public YdszResponse<Map<String, Object>> token(OAuth2TokenRequest request) {

    if (OAUTH2_GRANT_REFRESH_TOKEN.equals(request.grantType())) {
      return refreshTokenGrant(request.clientId(), request.clientSecret(), request.refreshToken());
    }
    if (OAUTH2_GRANT_AUTHORIZATION_CODE.equals(request.grantType())) {
      return authorizationCodeGrant(
          request.code(), request.clientId(), request.clientSecret(),
          request.codeVerifier(), request.state());
    }
    throw new BusinessException(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID);
  }

  /**
   * 授权码模式换取 Token（RFC 6749 §4.1.3）。
   *
   * <p>当授权码上下文的 scope 包含 {@code openid} 时，额外签发 OIDC id_token（遵循 OpenID Connect Core 1.0）。
   *
   * @param code 授权码（/authorize 返回）
   * @param clientId 客户端 ID
   * @param clientSecret 客户端密钥（confidential 客户端必填）
   * @param codeVerifier PKCE 码验证器（public 客户端必填）
   * @param state OAuth2 CSRF 防护 state 参数（可选，若 authorize 时传了 state 则必须匹配）
   * @return 标准 OAuth2 token 响应（scope 含 openid 时包含 id_token）
   */
  private YdszResponse<Map<String, Object>> authorizationCodeGrant(
      String code, String clientId, String clientSecret, String codeVerifier, String state) {

    // 1. P0-3: 原子读取并删除授权码（GETDEL 语义，防并发重放）
    String storedContext = getAndDeleteCode(code);
    if (storedContext == null) {
      throw new BusinessException(UserInfoExceptionCode.OAUTH2_CODE_INVALID);
    }

    // P1-5：使用类型安全的 OAuthCodeContext 反序列化
    OAuthCodeContext context = parseCodeContext(storedContext);

    // 2. 校验 clientId 一致性（防跨客户端重放）
    if (!clientId.equals(context.clientId())) {
      throw new BusinessException(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID);
    }

    // 3. 认证方式校验：clientSecret 或 PKCE 二选一
    authenticateClient(context, codeVerifier, clientId, clientSecret);

    // 4. State 校验（CSRF 防护，可选——仅当客户端传入 state 时执行）
    validateState(state, code);

    // 5. 授权码已在上方由 GETDEL 原子删除（一次性使用），此处无需再次 DEL

    // 6. 重建 UserInfo 并签发新 token
    UserInfo userInfo = buildTokenUserInfo(context);
    String newAccessToken = tokenService.issueAccessToken(userInfo);
    String refreshToken = tokenService.issueRefreshToken(userInfo);

    log.info("OAuth2 token issued: clientId={}, userId={}", clientId, context.userId());

    // 7. P1-3: 返回实际授权的 scope
    String grantedScope = resolveGrantedScope(context, clientId);

    // 8. P1-2: OIDC — scope 含 openid 时签发 id_token
    Map<String, Object> tokenResponse = buildTokenResponse(
        newAccessToken, refreshToken, grantedScope, context, userInfo);

    // 9. 返回标准 OAuth2/OIDC 响应（RFC 6749 §5.1 + OIDC Core 1.0 §3.1.3.3）
    return YdszResponse.success(tokenResponse);
  }

  /**
   * 反序列化授权码上下文。
   *
   * @param storedContext Redis 中存储的 JSON 上下文
   * @return 类型安全的授权码上下文
   */
  private OAuthCodeContext parseCodeContext(String storedContext) {
    try {
      return OAuthCodeContext.fromJson(storedContext);
    } catch (Exception e) {
      log.error("Failed to parse OAuth2 code context", e);
      throw new BusinessException(UserInfoExceptionCode.OAUTH2_CODE_INVALID);
    }
  }

  /**
   * 客户端认证：PKCE 验证 code_verifier，传统流程校验 clientSecret。
   *
   * @param context 授权码上下文
   * @param codeVerifier PKCE 码验证器
   * @param clientId 客户端 ID
   * @param clientSecret 客户端密钥
   */
  private void authenticateClient(
      OAuthCodeContext context, String codeVerifier, String clientId, String clientSecret) {
    String storedCodeChallenge = context.codeChallenge();
    if (storedCodeChallenge != null && !storedCodeChallenge.isBlank()) {
      // PKCE 流程：验证 code_verifier
      if (codeVerifier == null || codeVerifier.isBlank()) {
        throw new BusinessException(UserInfoExceptionCode.OAUTH2_PKCE_VERIFIER_INVALID);
      }
      if (!verifyPkceCodeVerifier(
          codeVerifier, storedCodeChallenge, context.codeChallengeMethod())) {
        throw new BusinessException(UserInfoExceptionCode.OAUTH2_PKCE_VERIFIER_INVALID);
      }
    } else {
      // 传统流程：强制校验 clientSecret
      if (!properties.validateOAuth2Client(clientId, clientSecret)) {
        throw new BusinessException(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID);
      }
    }
  }

  /**
   * 校验并消费 state（一次性使用）。
   *
   * @param state CSRF state 参数（可为 null）
   * @param code 授权码
   */
  private void validateState(String state, String code) {
    if (state == null || state.isBlank()) {
      return;
    }
    String storedCode = redisStringOps.get(STATE_KEY_PREFIX + state, String.class);
    if (storedCode == null || !storedCode.equals(code)) {
      log.warn("OAuth2 state validation failed: state={}, code={}", state, code);
      throw new BusinessException(UserInfoExceptionCode.OAUTH2_STATE_INVALID);
    }
    // 校验通过后删除 state 标记（一次性使用）
    redisStringOps.del(STATE_KEY_PREFIX + state);
    log.debug("OAuth2 state validated and consumed: state={}", state);
  }

  /**
   * 从授权码上下文重建 UserInfo。
   *
   * @param context 授权码上下文
   * @return 用户信息
   */
  private UserInfo buildTokenUserInfo(OAuthCodeContext context) {
    UserInfo userInfo = new UserInfo();
    userInfo.setUserId(context.userId());
    userInfo.setUsername(context.username());
    userInfo.setTenantId(context.tenantId() != null ? context.tenantId() : "1");
    return userInfo;
  }

  /**
   * 解析实际授予的 scope：授权码上下文优先，未声明时回落客户端注册范围。
   *
   * @param context 授权码上下文
   * @param clientId 客户端 ID
   * @return 授予的 scope
   */
  private String resolveGrantedScope(OAuthCodeContext context, String clientId) {
    String grantedScope = context.scope();
    if (grantedScope == null || grantedScope.isBlank()) {
      grantedScope = resolveGrantedScope(clientId);
    }
    return grantedScope;
  }

  /**
   * 构建标准 OAuth2 token 响应（scope 含 openid 时附加 id_token）。
   *
   * @param accessToken 访问令牌
   * @param refreshToken 刷新令牌
   * @param grantedScope 授予的 scope
   * @param context 授权码上下文（含 nonce）
   * @param userInfo 用户信息
   * @param clientId 客户端 ID
   * @return token 响应 Map
   */
  private Map<String, Object> buildTokenResponse(
      String accessToken,
      String refreshToken,
      String grantedScope,
      OAuthCodeContext context,
      UserInfo userInfo) {
    Map<String, Object> tokenResponse = new HashMap<>(16);
    tokenResponse.put("access_token", accessToken);
    tokenResponse.put("refresh_token", refreshToken);
    tokenResponse.put("token_type", "Bearer");
    tokenResponse.put("expires_in", properties.getTokenTtlSeconds());
    tokenResponse.put("scope", grantedScope);

    if (grantedScope.contains(SCOPE_OPENID)) {
      String idToken = tokenService.issueIdToken(userInfo, context.nonce(), context.clientId());
      if (idToken != null) {
        tokenResponse.put("id_token", idToken);
        log.info("OIDC id_token issued: clientId={}, userId={}", context.clientId(), context.userId());
      } else {
        log.warn("OIDC id_token issuance failed: clientId={}, userId={}", context.clientId(), context.userId());
      }
    }
    return tokenResponse;
  }

  /**
   * refresh_token 轮换换取新 Token。
   *
   * <p>校验 clientId + clientSecret（confidential 客户端）后，验证 refresh_token 有效性并签发新双令牌，
   * 旧 refresh_token 立即加入黑名单（一次性使用，防重放）。
   *
   * @param clientId 客户端 ID
   * @param clientSecret 客户端密钥
   * @param refreshToken 刷新令牌
   * @return 标准 OAuth2 token 响应
   */
  private YdszResponse<Map<String, Object>> refreshTokenGrant(
      String clientId, String clientSecret, String refreshToken) {

    // 1. 客户端认证（RFC 6749 §6：refresh_token 流程强制 confidential 客户端认证）
    if (!properties.validateOAuth2Client(clientId, clientSecret)) {
      throw new BusinessException(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID);
    }

    // 2. 校验 refresh_token 有效性
    if (refreshToken == null || refreshToken.isBlank()
        || !tokenService.validateRefreshToken(refreshToken)) {
      log.warn("OAuth2 refresh token validation failed, possible token reuse attack");
      throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
    }

    // 3. 解析用户信息
    UserInfo userInfo = tokenService.parseRefreshToken(refreshToken);
    if (userInfo == null) {
      throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
    }

    // 4. 签发新双令牌（token 轮换）
    String newAccessToken = tokenService.issueAccessToken(userInfo);
    String newRefreshToken = tokenService.issueRefreshToken(userInfo);

    // 5. 旧 refresh_token 加入黑名单（一次性使用，防重放）
    tokenBlacklistService.addToBlacklist(refreshToken);

    log.info("OAuth2 refresh token rotated: clientId={}, userId={}", clientId, userInfo.getUserId());

    // P1-3: refresh 流程返回客户端注册范围内可授予的 scope
    return YdszResponse.success(
        Map.of(
            "access_token",
            newAccessToken,
            "refresh_token",
            newRefreshToken,
            "token_type",
            "Bearer",
            "expires_in",
            properties.getTokenTtlSeconds(),
            "scope",
            resolveGrantedScope(clientId)));
  }

  /**
   * P1-3: 解析客户端请求的 scope 集合（空格分隔）。
   *
   * @param scope scope 字符串，可为 null
   * @return scope 集合；scope 为空时返回空集合
   */
  private Set<String> parseScopes(String scope) {
    if (scope == null || scope.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(scope.trim().split("\\s+"))
        .filter(s -> !s.isBlank())
        .collect(Collectors.toSet());
  }

  /**
   * P1-3: 解析客户端可授予的默认 scope（P1-3）。
   *
   * <p>客户端配置了 {@code allowedScopes} 时返回其全部授权范围；未配置时兼容存量返回
   * {@code read write}。
   *
   * @param clientId 客户端 ID
   * @return 可授予的 scope 字符串（空格分隔）
   */
  private String resolveGrantedScope(String clientId) {
    UserInfoProperties.OAuth2Client client = properties.getOauth2Clients().get(clientId);
    if (client != null
        && client.getAllowedScopes() != null
        && !client.getAllowedScopes().isEmpty()) {
      return String.join(" ", client.getAllowedScopes());
    }
    return "read write";
  }

  /**
   * 撤销 Token（RFC 7009）。
   *
   * <p>将 access_token / refresh_token 加入黑名单，使其立即失效。 token_type_hint 可提示 token 类型，非必须。
   *
   * @param token 待撤销的令牌
   * @param tokenTypeHint 令牌类型提示（access_token / refresh_token，可选）
   * @return 成功响应（RFC 7009 规定撤销成功一律返回 200，不区分 token 是否有效）
   */
  @Audit(
      module = "OAuth2",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'OAuth2 token 撤销: typeHint=' + #tokenTypeHint")
  @RateLimit(resource = "userinfo.oauth2.revoke", threshold = 20)
  @PostMapping("/revoke")
  @Operation(summary = "撤销 Token", description = "RFC 7009：将 access_token / refresh_token 加入黑名单立即失效")
  public YdszResponse<Void> revoke(
      @RequestParam String token,
      @RequestParam(required = false) String tokenTypeHint) {
    if (token == null || token.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
    }
    tokenBlacklistService.addToBlacklist(token);
    log.info("OAuth2 token revoked: typeHint={}", tokenTypeHint);
    return YdszResponse.success();
  }

  /**
   * 校验 Token 元数据（RFC 7662 introspect）。
   *
   * <p>资源服务器在鉴权前调用，需以 confidential 客户端身份认证。 返回 {@code active} 及用户信息（active=true 时）。
   *
   * @param token 待校验的访问令牌
   * @param clientId 客户端 ID
   * @param clientSecret 客户端密钥
   * @return Token 元数据（active / sub / username / tenantId）
   */
  @RateLimit(resource = "userinfo.oauth2.introspect", threshold = 100)
  @PostMapping("/introspect")
  @Operation(summary = "校验 Token 元数据", description = "RFC 7662：资源服务器校验 access_token 有效性")
  public YdszResponse<Map<String, Object>> introspect(
      @RequestParam String token,
      @RequestParam String clientId,
      @RequestParam(required = false) String clientSecret) {

    if (!properties.validateOAuth2Client(clientId, clientSecret)) {
      throw new BusinessException(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID);
    }

    Map<String, Object> result = new HashMap<>(16);
    UserInfo userInfo = tokenService.parseAccessToken(token);
    boolean active = userInfo != null && tokenService.validateAccessToken(token);
    result.put("active", active);
    if (active) {
      result.put("sub", userInfo.getUserId());
      result.put("username", userInfo.getUsername());
      result.put("tenantId", userInfo.getTenantId());
    }
    return YdszResponse.success(result);
  }

  /**
   * 当前用户信息（OIDC userinfo 风格）。
   *
   * <p>客户端携带 access_token（Authorization: Bearer xxx）调用，返回用户身份信息。
   *
   * @param authorization Authorization 请求头
   * @return 用户信息（sub / preferred_username / tenant_id）
   */
  @RateLimit(resource = "userinfo.oauth2.userinfo", threshold = 100)
  @GetMapping("/userinfo")
  @Operation(summary = "当前用户信息", description = "OIDC userinfo：携带 access_token 获取用户身份")
  public YdszResponse<Map<String, Object>> userinfo(
      @RequestHeader(HeaderConstants.AUTHORIZATION) String authorization) {

    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
    }
    String accessToken = authorization.substring(BEARER_PREFIX_LENGTH);
    UserInfo userInfo = tokenService.parseAccessToken(accessToken);
    if (userInfo == null || !tokenService.validateAccessToken(accessToken)) {
      throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
    }
    return YdszResponse.success(
        Map.of(
            "sub", userInfo.getUserId(),
            "preferred_username", userInfo.getUsername(),
            "tenant_id", userInfo.getTenantId()));
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
    // 计算 SHA-256 哈希并 Base64URL 编码（复用 common-util 统一摘要能力，禁止自建 MessageDigest）
    byte[] hash = DigestUtils.sha256(codeVerifier.getBytes(StandardCharsets.US_ASCII));
    String computedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    // 恒定时间比较（防时序攻击，JDK 标准 API MessageDigest.isEqual，非自建哈希）
    return MessageDigest.isEqual(
        computedChallenge.getBytes(StandardCharsets.US_ASCII),
        codeChallenge.getBytes(StandardCharsets.US_ASCII));
  }
}
