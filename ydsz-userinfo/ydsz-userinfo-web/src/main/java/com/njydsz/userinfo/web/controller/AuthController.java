package com.njydsz.userinfo.web.controller;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.njydsz.common.exception.custom.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.web.version.ApiVersion;
import com.njydsz.userinfo.domain.dto.LoginDTO;
import com.njydsz.userinfo.domain.dto.SendVerifyCodeDTO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.vo.LoginVO;
import com.njydsz.userinfo.server.auth.AuthService;
import com.njydsz.userinfo.server.auth.MfaService;
import com.njydsz.userinfo.server.config.UserInfoProperties;
import com.njydsz.userinfo.web.dto.RefreshRequest;

/**
 * 认证 Controller
 *
 * <p>提供用户登录、登出、刷新 Token 等基础认证端点。 是整个用户中心服务的对外认证入口，被各业务系统通过 Feign 远程调用（{@code AuthServiceClient}）。
 *
 * <p><b>接口路径：</b>{@code /api/v1/auth}
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>登录接口：启用 {@link RateLimit} 限流 50 QPS + {@link Idempotent} 5s 防重放 + 滑块验证码（可选）
 *   <li>登出接口：将 access_token 加入 Redis 黑名单，使其立即失效
 *   <li>刷新接口：限流 + 5s 幂等 + refresh_token 分布式锁（防并发重放）
 *   <li>密码错误次数超限自动锁定账号（{@code ydsz.auth.login-fail-threshold}）
 * </ul>
 *
 * <p><b>Token 设计：</b>
 *
 * <ul>
 *   <li>access_token：短期（默认 2h），用于业务接口鉴权
 *   <li>refresh_token：长期（默认 7d），仅用于刷新 access_token
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.userinfo.server.auth.AuthService 认证业务逻辑
 * @see com.njydsz.userinfo.web.controller.OAuth2Controller OAuth2.0 授权端点
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "登录/登出/Token 刷新")
@ApiVersion("1")
public class AuthController {

  private final AuthService authService;

  /** 双因素认证服务（登录短信验证码发送） */
  private final MfaService mfaService;

  /** P2-6: 可信代理配置（决定是否信任转发头） */
  private final UserInfoProperties properties;

  /** Token 服务（签发 access/refresh token） */
  private final TokenService tokenService;

  /** Redis 操作（设备登录码存储） */
  private final RedisStringOps redisStringOps;

  /** 设备登录码有效期（秒）：5 分钟 */
  private static final long DEVICE_CODE_TTL_SECONDS = 300;

  /** 设备登录码 Redis Key 前缀：{@code sso:device:code:{code}} */
  private static final String DEVICE_CODE_KEY_PREFIX = "sso:device:code:";

  /** 设备登录码字符集（排除易混淆字符 0/O/1/I/L） */
  private static final String DEVICE_CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

  /** 设备登录码长度 */
  private static final int DEVICE_CODE_LENGTH = 8;

  /** 设备登录码随机数生成器 */
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /**
   * 发送登录 MFA 短信验证码（风险为 HIGH 且未绑定 TOTP 时调用）。
   *
   * <p>限流 5 QPS，同手机号 60 秒内仅可发送一次（由 {@link VerifyCodeService} 频率限制保证）。
   *
   * @param request 发送请求（含手机号）
   * @return 是否发送成功
   */
  @RateLimit(resource = "userinfo.auth.mfaSendCode", threshold = 5)
  @PostMapping("/mfa/send-code")
  @Operation(summary = "发送登录 MFA 短信验证码")
  public YdszResponse<Boolean> sendMfaCode(@Valid @RequestBody SendVerifyCodeDTO request) {
    mfaService.sendLoginSmsCode(request.getPhone());
    return YdszResponse.success(true);
  }

  /**
   * 发送登录 MFA 邮件验证码（风险为 HIGH 且未绑定 TOTP 且无手机号时调用）。
   *
   * <p>限流 5 QPS，同邮箱 60 秒内仅可发送一次（由 {@link VerifyCodeService} 频率限制保证）。
   * 作为短信验证码的降级方案，适用于未绑定手机但有邮箱的用户。
   *
   * @param request 发送请求（含邮箱地址）
   * @return 是否发送成功
   */
  @RateLimit(resource = "userinfo.auth.mfaSendEmailCode", threshold = 5)
  @PostMapping("/mfa/send-email-code")
  @Operation(summary = "发送登录 MFA 邮件验证码")
  public YdszResponse<Boolean> sendMfaEmailCode(@Valid @RequestBody SendVerifyCodeDTO request) {
    mfaService.sendLoginEmailCode(request.getEmail());
    return YdszResponse.success(true);
  }

  /**
   * 用户登录（账号密码模式）
   *
   * <p>认证流程：账号密码校验 → 滑块验证码校验（可选）→ 签发 access_token / refresh_token。
   *
   * <p>限流 50 QPS；5 秒幂等保护（防重放）；失败次数超限后账号会被自动锁定 {@code ydsz.auth.lock-duration-minutes} 分钟（默认 30 分钟）。
   *
   * <p>成功登录会重置失败计数；失败累加计数到 {@code ydsz.auth.login-fail-threshold}（默认 5 次）触发锁定。
   *
   * @param request 登录请求（含 username / password / captchaKey / captchaCode / tenantId）
   * @return 登录结果（accessToken / refreshToken / expiresIn / userInfo）
   */
  @Audit(
      module = "认证管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'用户登录: ' + #request.username")
  @RateLimit(resource = "userinfo.auth.login", threshold = 50)
  @Idempotent(key = "ydsz:userinfo:AuthController:login:lock", ttlSeconds = 5)
  @PostMapping("/login")
  @Operation(summary = "用户登录", description = "账号密码登录，返回 access_token 和 refresh_token")
  public YdszResponse<LoginVO> login(
      @Valid @RequestBody LoginDTO request,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    // P1-3: 提取客户端 IP 和 User-Agent 传入 LoginDTO
    request.setLoginIp(extractClientIp(servletRequest));
    request.setUserAgent(servletRequest.getHeader("User-Agent"));
    LoginVO result = authService.login(request, servletResponse);
    return YdszResponse.success(result);
  }

  /**
   * 用户登出
   *
   * <p>将 access_token 加入 Redis 黑名单（TTL 与 token 剩余有效期对齐）， 同时清理服务端会话状态（缓存的用户权限 / 角色）。
   *
   * <p>refresh_token 仍可使用一次以兼容客户端清理逻辑，业务方应在登出后主动丢弃 refresh_token。
   *
   * @param token Authorization 请求头（Bearer xxx 或裸 token）
   * @return 成功响应（无业务数据）
   */
  @Audit(
      module = "认证管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'用户登出'")
  @PostMapping("/logout")
  @Operation(summary = "用户登出", description = "将 access_token 加入黑名单")
  public YdszResponse<Void> logout(@RequestHeader(HeaderConstants.AUTHORIZATION) String token) {
    String accessToken = token != null && token.startsWith("Bearer ") ? token.substring(7) : token;
    authService.logout(accessToken);
    return YdszResponse.success();
  }

  /**
   * 刷新 access_token
   *
   * <p>使用 refresh_token 换发新的 access_token（同时返回<b>新的</b> refresh_token 实现 token 轮换）， 旧 refresh_token
   * 立即失效（一次性），防止 token 泄露后的长期滥用。
   *
   * <p>启用分布式锁（{@code ydsz:userinfo:AuthController:refresh:lock}）防止并发重放。
   *
   * <p>限流 50 QPS；5 秒幂等保护。
   *
   * @param request 刷新请求（含 refreshToken 字段）
   * @return 新的登录结果（accessToken / refreshToken / expiresIn / userInfo）
   */
  @Audit(
      module = "认证管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'刷新Token'")
  @RateLimit(resource = "userinfo.auth.refresh", threshold = 50)
  @Idempotent(key = "ydsz:userinfo:AuthController:refresh:lock", ttlSeconds = 5)
  @PostMapping("/refresh")
  @Operation(summary = "刷新 Token", description = "使用 refresh_token 获取新的 access_token")
  public YdszResponse<LoginVO> refresh(@RequestBody RefreshRequest request) {
    LoginVO result = authService.refresh(request.getRefreshToken());
    return YdszResponse.success(result);
  }

  /**
   * 生成设备登录码（Token 模式 SSO 第一步）。
   *
   * <p>已登录用户调用此端点生成一个短设备登录码，可在另一台设备（APP/小程序）上输入此码完成跨设备登录。
   *
   * <p><b>流程：</b>
   *
   * <ol>
   *   <li>用户在本设备已登录（携带有效 access_token）</li>
   *   <li>调用此端点生成 8 位设备登录码（5 分钟有效）</li>
   *   <li>在另一台设备上输入此码，调用 {@code /sso/device-exchange} 完成登录</li>
   * </ol>
   *
   * <p><b>安全约束：</b>
   *
   * <ul>
   *   <li>必须携带有效 access_token（已登录状态）</li>
   *   <li>设备登录码一次性使用（交换后立即失效）</li>
   *   <li>限流 10 QPS（防批量生成）</li>
   * </ul>
   *
   * @param authorization Authorization 请求头（Bearer access_token）
   * @return 设备登录码及过期时间
   */
  @Audit(
      module = "认证管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'生成设备登录码'")
  @RateLimit(resource = "userinfo.auth.ssoDeviceCode", threshold = 10)
  @PostMapping("/sso/device-code")
  @Operation(summary = "生成设备登录码", description = "已登录用户生成跨设备登录码，供 APP/小程序使用")
  public YdszResponse<Map<String, Object>> generateDeviceCode(
      @RequestHeader(HeaderConstants.AUTHORIZATION) String authorization) {
    // 1. 校验登录态
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
    }
    String accessToken = authorization.substring(7);
    UserInfo userInfo = tokenService.parseAccessToken(accessToken);
    if (userInfo == null || !tokenService.validateAccessToken(accessToken)) {
      throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
    }

    // 2. 生成设备登录码
    String code = generateDeviceCode();

    // 3. 存储用户信息到 Redis（5 分钟有效，一次性使用）
    Map<String, String> codeData = new HashMap<>();
    codeData.put("userId", userInfo.getUserId());
    codeData.put("username", userInfo.getUsername());
    codeData.put("tenantId", userInfo.getTenantId() != null ? userInfo.getTenantId() : "1");
    redisStringOps.set(
        DEVICE_CODE_KEY_PREFIX + code,
        com.njydsz.common.json.YdszJson.toJson(codeData),
        DEVICE_CODE_TTL_SECONDS);

    log.info("SSO device code generated: user={}", userInfo.getUsername());

    Map<String, Object> result = new HashMap<>();
    result.put("deviceCode", code);
    result.put("expiresIn", DEVICE_CODE_TTL_SECONDS);
    return YdszResponse.success(result);
  }

  /**
   * 交换设备登录码获取 Token（Token 模式 SSO 第二步）。
   *
   * <p>在另一台设备（APP/小程序）上输入设备登录码后，调用此端点换取 access_token + refresh_token，
   * 完成跨设备登录。
   *
   * <p><b>安全约束：</b>
   *
   * <ul>
   *   <li>设备登录码一次性使用（交换后立即从 Redis 删除，防重放）</li>
   *   <li>限流 20 QPS（防暴力破解）</li>
   * </ul>
   *
   * @param code 设备登录码（8 位字母数字）
   * @return 标准登录响应（access_token + refresh_token）
   */
  @Audit(
      module = "认证管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'设备登录码换Token'")
  @RateLimit(resource = "userinfo.auth.ssoDeviceExchange", threshold = 20)
  @PostMapping("/sso/device-exchange")
  @Operation(summary = "设备登录码换 Token", description = "用设备登录码换取 access_token，完成跨设备登录")
  public YdszResponse<LoginVO> exchangeDeviceCode(@RequestParam String code) {
    if (code == null || code.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.SSO_DEVICE_CODE_INVALID);
    }

    // 1. 读取并删除设备登录码（一次性使用，防重放）
    String codeKey = DEVICE_CODE_KEY_PREFIX + code.toUpperCase();
    String codeDataJson = redisStringOps.get(codeKey, String.class);
    if (codeDataJson == null) {
      throw new BusinessException(UserInfoExceptionCode.SSO_DEVICE_CODE_INVALID);
    }

    // 立即删除（一次性使用）
    redisStringOps.del(codeKey);

    // 2. 解析用户信息
    Map<String, String> codeData = com.njydsz.common.json.YdszJson.fromJson(codeDataJson, Map.class);
    if (codeData == null || codeData.get("userId") == null) {
      log.warn("SSO device code data corrupted: code={}", code);
      throw new BusinessException(UserInfoExceptionCode.SSO_DEVICE_CODE_INVALID);
    }

    // 3. 重建 UserInfo 并签发 Token
    UserInfo userInfo = new UserInfo();
    userInfo.setUserId(codeData.get("userId"));
    userInfo.setUsername(codeData.get("username"));
    userInfo.setTenantId(codeData.get("tenantId"));

    String accessToken = tokenService.issueAccessToken(userInfo);
    String refreshToken = tokenService.issueRefreshToken(userInfo);

    log.info("SSO device code exchanged: user={}", userInfo.getUsername());

    LoginVO loginVO = new LoginVO();
    loginVO.setAccessToken(accessToken);
    loginVO.setRefreshToken(refreshToken);
    loginVO.setTokenType("Bearer");
    return YdszResponse.success(loginVO);
  }

  /**
   * 生成设备登录码（8 位字母数字，排除易混淆字符）。
   *
   * @return 设备登录码
   */
  private String generateDeviceCode() {
    StringBuilder sb = new StringBuilder(DEVICE_CODE_LENGTH);
    for (int i = 0; i < DEVICE_CODE_LENGTH; i++) {
      sb.append(DEVICE_CODE_CHARS.charAt(SECURE_RANDOM.nextInt(DEVICE_CODE_CHARS.length())));
    }
    return sb.toString();
  }

  /**
   * 从 HttpServletRequest 中提取客户端真实 IP
   *
   * <p>P2-6: 仅当请求来自可信代理（{@code ydsz.userinfo.trusted-proxies}）时，才读取
   * X-Forwarded-For、X-Real-IP 等代理头；否则直接用 getRemoteAddr()，
   * 防止客户端直接伪造转发头绕过 IP 风控。
   *
   * @param request HTTP 请求
   * @return 客户端真实 IP；无 IP 时为 null
   */
  private String extractClientIp(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    if (!isTrustedProxy(request)) {
      return request.getRemoteAddr();
    }
    String ip = request.getHeader(HeaderConstants.X_FORWARDED_FOR);
    if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
      // 多级代理场景：取第一个非 unknown 的 IP
      int idx = ip.indexOf(',');
      return (idx > 0) ? ip.substring(0, idx).trim() : ip.trim();
    }
    ip = request.getHeader("X-Real-IP");
    if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
      return ip.trim();
    }
    ip = request.getHeader("Proxy-Client-IP");
    if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
      return ip.trim();
    }
    ip = request.getHeader("WL-Proxy-Client-IP");
    if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
      return ip.trim();
    }
    return request.getRemoteAddr();
  }

  /**
   * P2-6: 判断请求是否来自可信代理。
   *
   * <p>远程地址命中 {@code ydsz.userinfo.trusted-proxies} 列表才返回 true；
   * 列表为空时一律不信任代理头（默认安全策略）。
   *
   * @param request HTTP 请求
   * @return true 表示来自可信代理
   */
  private boolean isTrustedProxy(HttpServletRequest request) {
    java.util.List<String> trustedProxies = properties.getTrustedProxies();
    if (trustedProxies == null || trustedProxies.isEmpty()) {
      return false;
    }
    String remoteAddr = request.getRemoteAddr();
    return remoteAddr != null && trustedProxies.contains(remoteAddr);
  }

  /**
   * 查询当前用户的活跃会话列表。
   *
   * <p>返回当前登录用户的所有活跃 accessToken，用于会话管理界面展示。
   *
   * @return 活跃 accessToken 集合
   */
  @GetMapping("/sessions")
  @Operation(summary = "查询活跃会话列表")
  public YdszResponse<Set<String>> listActiveSessions() {
    String userId = RequestContext.getUserId();
    return YdszResponse.success(authService.listActiveSessions(userId));
  }

  /**
   * 下线指定会话（踢出指定设备）。
   *
   * <p>将指定 accessToken 加入黑名单，使其立即失效。 可用于"退出其他设备"场景。
   *
   * @param token 要下线的 accessToken
   * @return 是否成功
   */
  @Audit(
      module = "认证管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'下线指定会话'")
  @DeleteMapping("/sessions/{token}")
  @Operation(summary = "下线指定会话")
  public YdszResponse<Void> kickOutSession(@PathVariable String token) {
    authService.logout(token);
    return YdszResponse.success();
  }

  /**
   * 下线当前用户全部其他会话（强制其他设备重新登录）。
   *
   * <p>保留当前请求的 accessToken，将其他全部 accessToken 加入黑名单。 用于"保护账号，退出其他设备"场景。
   *
   * @param currentToken 当前请求的 Authorization token（不会被下线）
   * @return 是否成功
   */
  @Audit(
      module = "认证管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'下线全部其他会话'")
  @DeleteMapping("/sessions")
  @Operation(summary = "下线全部其他会话")
  public YdszResponse<Void> kickOutOtherSessions(
      @RequestHeader(HeaderConstants.AUTHORIZATION) String currentToken) {
    String userId = RequestContext.getUserId();
    if (userId == null || userId.isBlank()) {
      return YdszResponse.success();
    }
    String currentAccessToken =
        currentToken != null && currentToken.startsWith("Bearer ")
            ? currentToken.substring(7)
            : currentToken;

    // 获取全部活跃会话，除当前 token 外全部下线
    Set<String> activeSessions = authService.listActiveSessions(userId);
    for (String token : activeSessions) {
      if (!token.equals(currentAccessToken)) {
        authService.logout(token);
      }
    }
    return YdszResponse.success();
  }
}
