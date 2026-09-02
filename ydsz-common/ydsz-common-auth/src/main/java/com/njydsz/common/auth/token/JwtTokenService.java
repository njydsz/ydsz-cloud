package com.njydsz.common.auth.token;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.service.TokenBlacklistService;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.util.security.DigestUtils;

/**
 * JWT Token 服务实现
 *
 * <p>基于 jjwt 库实现双令牌机制（access_token + refresh_token）：
 *
 * <ul>
 *   <li>Access Token：短有效期，用于 API 访问授权
 *   <li>Refresh Token：长有效期，用于刷新 Access Token
 * </ul>
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>使用 HMAC-SHA256 签名算法
 *   <li>Token 中包含用户基本信息（userId, username, tenantId）
 *   <li>支持 Token 黑名单（登出后失效）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Service
@ConditionalOnClass(name = "io.jsonwebtoken.Jwts")
@ConditionalOnProperty(
    prefix = "ydsz.auth.token",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class JwtTokenService implements TokenService {

  private static final Logger LOG = LoggerFactory.getLogger(JwtTokenService.class);

  private static final String CLAIM_USER_ID = "userId";
  private static final String CLAIM_USERNAME = "username";
  private static final String CLAIM_TENANT_ID = "tenantId";
  private static final String CLAIM_ROLE_CODE = "roleCode";
  private static final String CLAIM_TOKEN_TYPE = "tokenType";

  /**
   * P1: JWT ID（jti）— 每个 token 唯一标识
   *
   * <p>用途：
   *
   * <ul>
   *   <li>支持精确的 Token 黑名单（基于 jti 而非完整 token 字符串）
   *   <li>审计日志关联具体 token
   *   <li>未来扩展：refresh_token 轮换时关联父子 token
   * </ul>
   */
  private static final String CLAIM_JTI = "jti";

  private static final String TOKEN_TYPE_ACCESS = "access";
  private static final String TOKEN_TYPE_REFRESH = "refresh";
  private static final String TOKEN_TYPE_ID = "id_token";

  private final TokenProperties tokenProperties;
  private final SecretKey secretKey;
  private final TokenBlacklistService tokenBlacklistService;

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /**
   * JWT 解析结果本地缓存（按 token 的 SHA-256 哈希缓存签名验证后的 Claims）。
   *
   * <p>避免每次请求都执行 HMAC 签名验证：高 QPS 场景下将验签开销从「每请求一次」降为「每 TTL 窗口一次」。
   * 缓存上限 10 万条、过期 5 分钟，与 access_token 有效期（默认 2 小时）相比足够短， 不会造成令牌撤销延迟。
   */
  private final Cache<String, Claims> claimsCache;

  public JwtTokenService(
      TokenProperties tokenProperties,
      @Autowired(required = false) TokenBlacklistService tokenBlacklistService,
      SnowflakeIdGenerator snowflakeIdGenerator) {
    this.tokenProperties = tokenProperties;
    this.tokenBlacklistService = tokenBlacklistService;
    if (snowflakeIdGenerator == null) {
      throw new IllegalStateException(
          "SnowflakeIdGenerator Bean 缺失：JWT jti 依赖分布式 ID 生成器，请启用 ydsz.util.snowflake 或提供替代实现");
    }
    this.snowflakeIdGenerator = snowflakeIdGenerator;
    // 校验密钥非空，避免 NPE 或签名失败
    String secretKeyRaw = tokenProperties.getSecretKey();
    if (secretKeyRaw == null || secretKeyRaw.isBlank()) {
      throw new IllegalStateException("ydsz.auth.token.secret-key 不能为空，请在配置文件中设置 JWT 签名密钥");
    }
    this.secretKey = Keys.hmacShaKeyFor(secretKeyRaw.getBytes(StandardCharsets.UTF_8));
    this.claimsCache =
        YdszCache.<String, Claims>newBuilder()
            .name("auth:jwt-claims")
            .maximumSize(100_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();
  }

  @Override
  public String issueAccessToken(UserInfo userInfo) {
    return buildToken(userInfo, TOKEN_TYPE_ACCESS, tokenProperties.getAccessTokenExpireSeconds());
  }

  @Override
  public String issueRefreshToken(UserInfo userInfo) {
    return buildToken(userInfo, TOKEN_TYPE_REFRESH, tokenProperties.getRefreshTokenExpireSeconds());
  }

  @Override
  public boolean validateAccessToken(String token) {
    if (tokenBlacklistService != null && tokenBlacklistService.isBlacklisted(token)) {
      LOG.warn("Access token is blacklisted");
      return false;
    }
    return validateToken(token, TOKEN_TYPE_ACCESS);
  }

  @Override
  public boolean validateRefreshToken(String token) {
    if (tokenBlacklistService != null && tokenBlacklistService.isBlacklisted(token)) {
      LOG.warn("Refresh token is blacklisted");
      return false;
    }
    return validateToken(token, TOKEN_TYPE_REFRESH);
  }

  @Override
  public UserInfo parseAccessToken(String token) {
    return parseToken(token, TOKEN_TYPE_ACCESS);
  }

  @Override
  public UserInfo parseRefreshToken(String token) {
    return parseToken(token, TOKEN_TYPE_REFRESH);
  }

  @Override
  public String refreshAccessToken(String refreshToken) {
    if (tokenBlacklistService != null && tokenBlacklistService.isBlacklisted(refreshToken)) {
      LOG.warn("Refresh token is blacklisted");
      return null;
    }
    // 获取分布式锁，防止并发刷新导致重放攻击
    if (tokenBlacklistService != null
        && !tokenBlacklistService.tryAcquireRefreshLock(refreshToken)) {
      LOG.warn("Refresh token 正在被其他请求刷新，拒绝并发刷新");
      return null;
    }
    try {
      // 再次检查黑名单，防止在获取锁的间隙被其他请求加入黑名单
      if (tokenBlacklistService != null && tokenBlacklistService.isBlacklisted(refreshToken)) {
        LOG.warn("Refresh token was blacklisted during lock acquisition");
        return null;
      }
      UserInfo userInfo = parseRefreshToken(refreshToken);
      if (userInfo == null) {
        LOG.warn("Refresh token validation failed");
        return null;
      }
      String newAccessToken = issueAccessToken(userInfo);
      // 颁发新 token 后将旧 refresh_token 加入黑名单，防止 refresh_token 重放攻击
      if (tokenBlacklistService != null && newAccessToken != null) {
        tokenBlacklistService.addToBlacklist(refreshToken);
        LOG.warn("旧 refresh_token 已加入黑名单，防止重放");
      }
      return newAccessToken;
    } finally {
      // 释放分布式锁
      if (tokenBlacklistService != null) {
        tokenBlacklistService.releaseRefreshLock(refreshToken);
      }
    }
  }

  @Override
  public String issueIdToken(UserInfo userInfo, String nonce, String clientId) {
    if (userInfo == null || userInfo.getUserId() == null || userInfo.getUserId().isBlank()) {
      LOG.warn("签发 ID Token 失败: userInfo 或 userId 为空");
      return null;
    }
    if (clientId == null || clientId.isBlank()) {
      LOG.warn("签发 ID Token 失败: clientId 不能为空");
      return null;
    }
    try {
      return buildIdToken(userInfo, nonce, clientId);
    } catch (Exception e) {
      LOG.error("签发 ID Token 异常", e);
      return null;
    }
  }

  /**
   * 构建 JWT Token
   *
   * <p>P1: 在原有 iss/sub/iat/exp 基础上，新增 jti（JWT ID）和 aud（audience）：
   *
   * <ul>
   *   <li>jti：每个 token 唯一标识，用于精确黑名单和审计
   *   <li>aud：受众声明，防止跨服务令牌重用（如颁发给 gateway 的 token 不能用于其他服务）
   * </ul>
   */
  private String buildToken(UserInfo userInfo, String tokenType, long expireSeconds) {
    Instant now = Instant.now();
    Instant expiration = now.plusSeconds(expireSeconds);

    Map<String, Object> claims = new HashMap<>(16);
    claims.put(CLAIM_USER_ID, userInfo.getUserId());
    claims.put(CLAIM_USERNAME, userInfo.getUsername());
    claims.put(CLAIM_TENANT_ID, userInfo.getTenantId());
    claims.put(CLAIM_ROLE_CODE, userInfo.getRoleCode());
    claims.put(CLAIM_TOKEN_TYPE, tokenType);

    var builder =
        Jwts.builder()
            .claims(claims)
            // P1: jti — 每个 token 唯一 ID，便于精确黑名单和审计关联
            .id(String.valueOf(snowflakeIdGenerator.nextId()))
            .issuer(tokenProperties.getIssuer())
            .subject(tokenProperties.getSubject())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration));

    // P1: aud — 受众声明（可选），配置后强制校验，防止跨服务令牌重用
    String audience = tokenProperties.getAudience();
    if (audience != null && !audience.isBlank()) {
      // jjwt 0.12.x: audience() 返回 AudienceCollection<JwtBuilder>，
      // 调用 single(String) 设置单一 audience 并返回 JwtBuilder
      builder.audience().single(audience);
    }

    return builder.signWith(secretKey).compact();
  }

  /**
   * 构建 OIDC ID Token
   *
   * <p>ID Token 遵循 OpenID Connect Core 1.0 规范，包含以下标准声明：
   *
   * <ul>
   *   <li><b>iss</b>（Issuer）：令牌签发者，取自 tokenProperties.issuer
   *   <li><b>sub</b>（Subject）：用户标识，取自 userInfo.userId
   *   <li><b>aud</b>（Audience）：受众，取客户端 ID（clientId）
   *   <li><b>exp</b>（Expiration）：过期时间，默认 10 分钟
   *   <li><b>iat</b>（Issued At）：签发时间
   *   <li><b>nonce</b>（可选）：一次性随机值，用于防止重放攻击
   * </ul>
   *
   * <p>token_type 声明设为 "id_token"，便于后续解析时区分。
   *
   * @param userInfo 用户信息
   * @param nonce    一次性随机值（可为 null）
   * @param clientId 客户端 ID
   * @return ID Token JWT 字符串
   */
  private String buildIdToken(UserInfo userInfo, String nonce, String clientId) {
    Instant now = Instant.now();
    Instant expiration = now.plusSeconds(tokenProperties.getIdTokenExpireSeconds());

    var builder = Jwts.builder()
        .subject(userInfo.getUserId())
        .issuer(tokenProperties.getIssuer())
        .audience().single(clientId)
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiration))
        .id(String.valueOf(snowflakeIdGenerator.nextId()))
        .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ID)
        .claim(CLAIM_USERNAME, userInfo.getUsername());

    if (nonce != null && !nonce.isBlank()) {
      builder.claim("nonce", nonce);
    }

    return builder.signWith(secretKey).compact();
  }

  /** 验证 JWT Token */
  private boolean validateToken(String token, String expectedTokenType) {
    try {
      Claims claims = parseClaims(token);
      String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
      return expectedTokenType.equals(tokenType);
    } catch (JwtException e) {
      LOG.debug("Token validation failed: {}", e.getMessage());
      return false;
    } catch (Exception e) {
      LOG.error("Token validation error", e);
      return false;
    }
  }

  /** 解析 JWT Token 为用户信息 */
  private UserInfo parseToken(String token, String expectedTokenType) {
    try {
      Claims claims = parseClaims(token);
      String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
      if (!expectedTokenType.equals(tokenType)) {
        LOG.warn("Token type mismatch: expected={}, actual={}", expectedTokenType, tokenType);
        return null;
      }

      UserInfo userInfo = new UserInfo();
      userInfo.setUserId(claims.get(CLAIM_USER_ID, String.class));
      userInfo.setUsername(claims.get(CLAIM_USERNAME, String.class));
      userInfo.setTenantId(claims.get(CLAIM_TENANT_ID, String.class));
      userInfo.setRoleCode(claims.get(CLAIM_ROLE_CODE, String.class));
      return userInfo;
    } catch (JwtException e) {
      LOG.debug("Token parse failed: {}", e.getMessage());
      return null;
    } catch (Exception e) {
      LOG.error("Token parse error", e);
      return null;
    }
  }

  /**
   * 解析 JWT Claims
   *
   * <p>校验签名 + issuer + subject + audience，防止跨服务令牌混淆攻击
   *
   * <p>P1: 新增 audience 校验，配置 {@link TokenProperties#getAudience()} 后， token 必须包含匹配的 aud
   * 字段，防止颁发给其他服务的 token 被错误地用于本服务。
   */
  private Claims parseClaims(String token) {
    // 快速格式校验：JWT 格式必须包含恰好两个 '.' 分隔符（header.payload.signature）
    // 避免非 JWT 格式的垃圾输入进入昂贵的签名验证流程
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("Token is null or blank");
    }
    long dotCount = token.chars().filter(c -> c == '.').count();
    if (dotCount != 2) {
      throw new IllegalArgumentException("Invalid JWT format: expected 2 dots, found " + dotCount);
    }
    // 本地缓存命中直接返回，避免重复 HMAC 验签
    String cacheKey = DigestUtils.sha256Hex(token);
    Claims cached = claimsCache.getIfPresent(cacheKey);
    if (cached != null) {
      return cached;
    }
    JwtParserBuilder parserBuilder = Jwts.parser().verifyWith(secretKey);
    // 校验 issuer 防止跨服务令牌混淆
    String issuer = tokenProperties.getIssuer();
    if (issuer != null && !issuer.isBlank()) {
      parserBuilder.requireIssuer(issuer);
    }
    // 校验 subject 防止跨服务令牌混淆
    String subject = tokenProperties.getSubject();
    if (subject != null && !subject.isBlank()) {
      parserBuilder.requireSubject(subject);
    }
    // P1: 校验 audience 防止跨服务令牌重用
    // 若配置了 audience，则 token 必须包含匹配的 aud 字段
    String audience = tokenProperties.getAudience();
    if (audience != null && !audience.isBlank()) {
      // jjwt 0.12.x: require(claimName, expectedValue)
      // 注意：require("aud", audience) 要求 token 中 aud 字段精确匹配配置值
      parserBuilder.require("aud", audience);
    }
    Claims claims = parserBuilder.build().parseSignedClaims(token).getPayload();
    claimsCache.put(cacheKey, claims);
    return claims;
  }

  @Override
  public long getAccessTokenRemainingTtl(String token) {
    try {
      Claims claims = parseClaims(token);
      if (claims == null || claims.getExpiration() == null) {
        return 0;
      }
      long remainingMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
      return Math.max(0, remainingMillis / 1000);
    } catch (Exception e) {
      LOG.debug("获取 Token 剩余有效期失败: {}", e.getMessage());
      return 0;
    }
  }
}
