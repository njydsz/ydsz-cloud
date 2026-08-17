package com.njydsz.userinfo.server.auth;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.service.TokenBlacklistService;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.converter.UserInfoConverter;
import com.njydsz.userinfo.domain.dto.LoginDTO;
import com.njydsz.userinfo.infra.entity.RoleDO;
import com.njydsz.userinfo.infra.entity.UserAccountDO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.vo.LoginVO;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.userinfo.server.config.UserInfoProperties;
import com.njydsz.userinfo.server.event.UserDomainEventPublisher;
import com.njydsz.userinfo.server.metrics.UserInfoMetrics;
import com.njydsz.userinfo.server.service.LoginAttemptContext;
import com.njydsz.userinfo.server.service.LoginHistoryService;

/**
 * 认证服务编排实现。
 *
 * <p><b>P0-5 拆分后职责收敛：</b>本类仅保留登录/登出/刷新/会话编排与横切关注点（验证码、风险评分、MFA、
 * 指标、事件），具体的单点职责委托给协作组件：
 *
 * <ul>
 *   <li>{@link AccountStatusGuard} — 用户查询与账号状态校验
 *   <li>{@link CredentialVerifier} — 密码校验（BCrypt + LDAP 回退）+ 失败计数/锁定
 *   <li>{@link SessionManager} — 会话 Hash/索引存储、登出吊销、全量驱逐
 *   <li>{@link RoleCacheService} — 用户角色加载与缓存
 * </ul>
 *
 * <p><b>核心流程（login）：</b>IP 封禁检查 → 用户状态校验 → 风险评分 → 动态认证策略（MEDIUM+ 验证码，
 * HIGH 追加 MFA）→ 密码校验 → 角色加载 → Token 签发 → 会话存储 → 登录信息更新 → 指标/事件。
 *
 * <p><b>会话索引设计：</b>
 *
 * <pre>
 *   userinfo:session:user:{userId}  →  Set&lt;accessToken&gt;   该用户所有活跃会话
 *   {accessToken}                   →  Hash&lt;String,Object&gt;  单会话详情（含 refreshToken，供登出吊销）
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see AccountStatusGuard 账号状态守卫
 * @see CredentialVerifier 凭据校验器
 * @see SessionManager 会话管理器
 * @see RoleCacheService 角色缓存服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private final UserAccountRepository userAccountRepository;
  private final TokenService tokenService;
  private final TokenBlacklistService tokenBlacklistService;
  private final CaptchaService captchaService;
  private final RiskScoringService riskScoringService;
  private final MfaService mfaService;
  private final LoginAttemptCounterService loginAttemptCounterService;
  private final UserDomainEventPublisher userDomainEventPublisher;
  private final LoginHistoryService loginHistoryService;
  private final UserInfoMetrics userInfoMetrics;
  private final UserInfoProperties properties;
  private final AccountStatusGuard accountStatusGuard;
  private final CredentialVerifier credentialVerifier;
  private final SessionManager sessionManager;
  private final RoleCacheService roleCacheService;

  /**
   * {@inheritDoc}
   *
   * <p>认证流程：IP 封禁检查 → 用户状态/锁定校验 → 风险评分 → 动态认证策略（MEDIUM+ 图形验证码，HIGH
   * 追加 MFA 动态码）→ 密码校验（本地优先，失败回退 LDAP）→ 角色加载 → JWT 签发 → Redis 会话存储 + 会话索引 →
   * 登录信息更新。
   *
   * @param loginDTO 登录请求（用户名、密码、可选验证码、可选 MFA 动态码）
   * @return 登录响应（含 accessToken、refreshToken、用户信息）
   * @throws BusinessException 当 IP 被封禁、验证码缺失、用户不存在、账号禁用/锁定、MFA 未通过、密码错误时抛出
   */
  @Override
  public LoginVO login(LoginDTO loginDTO) {
    Timer.Sample sample = userInfoMetrics.startTimer();
    String loginIp = loginDTO.getLoginIp();
    String userAgent = loginDTO.getUserAgent();

    // IP 封禁检查
    checkIpNotBlocked(loginIp, loginDTO.getUsername(), userAgent);

    // 查询用户 + 状态/锁定校验
    UserAccountDO user = accountStatusGuard.findValidUser(loginDTO.getUsername(), loginIp, userAgent);

    // 登录风险评估（基于 IP、时间、设备、频率等多维度）
    RiskScoringService.RiskScore risk = evaluateLoginRisk(user, loginIp, userAgent);

    // 动态认证策略 —— MEDIUM+ 强制图形验证码，HIGH 追加 MFA 动态码
    validateCaptchaIfEnabled(loginDTO, risk);
    validateMfaIfRequired(user, loginDTO, risk);

    // 密码校验（本地 BCrypt + LDAP 回退，失败原子锁定）
    credentialVerifier.verify(user, loginDTO.getPassword(), loginIp, userAgent);

    // 加载角色 + 签发 Token + 存储会话
    List<RoleDO> roles = roleCacheService.loadUserRoles(user.getId());
    TokenResult tokenResult = issueTokensAndCreateSession(user, roles);

    // 更新登录状态 + 审计
    updateLoginSuccess(user, loginIp, userAgent);
    loginHistoryService.recordLoginAttempt(
        new LoginAttemptContext(user.getId(), user.getUsername(), loginIp),
        "SUCCESS",
        null,
        userAgent);
    userInfoMetrics.recordLoginSuccess();
    userInfoMetrics.stopTimer(sample);
    userDomainEventPublisher.publishUserLogin(user.getId());

    return buildLoginResult(user, roles, tokenResult);
  }

  /**
   * 检查 IP 是否被封禁。
   *
   * @param loginIp 登录来源 IP
   * @param username 登录用户名（用于审计）
   * @param userAgent 用户代理
   * @throws BusinessException IP 被封禁时抛出
   */
  private void checkIpNotBlocked(String loginIp, String username, String userAgent) {
    if (loginIp != null && !loginIp.isBlank() && loginHistoryService.isIpBlocked(loginIp)) {
      log.warn("Login attempt from blocked IP: {}, username: {}", loginIp, username);
      loginHistoryService.recordLoginAttempt(
          new LoginAttemptContext(null, username, loginIp), "FAILED", "IP_BLOCKED", userAgent);
      userInfoMetrics.recordLoginFail();
      throw new BusinessException(UserInfoExceptionCode.IP_BLOCKED);
    }
  }

  /**
   * 校验图形验证码（全局开关开启或登录风险为 MEDIUM+ 时强制）。
   *
   * <p>风险评分引擎输出 MEDIUM/HIGH 时，即使全局验证码开关关闭，也强制要求图形验证码， 实现「正常用户无感、可疑请求加强验证」的动态认证策略。
   *
   * @param loginDTO 登录请求 DTO
   * @param risk 登录风险评估结果（可为 null，此时仅按全局开关判断）
   * @throws BusinessException 验证码为空或校验失败时抛出
   */
  private void validateCaptchaIfEnabled(LoginDTO loginDTO, RiskScoringService.RiskScore risk) {
    boolean forceCaptcha =
        risk != null && risk.requiresAdditionalVerification() && !risk.shouldReject();
    if (properties.isCaptchaEnabled() || forceCaptcha) {
      if (loginDTO.getCaptchaKey() == null || loginDTO.getCaptcha() == null) {
        userInfoMetrics.recordLoginFail();
        throw new BusinessException(UserInfoExceptionCode.CAPTCHA_REQUIRED);
      }
      captchaService.validate(loginDTO.getCaptchaKey(), loginDTO.getCaptcha());
    }
  }

  /**
   * 登录风险为 HIGH 时强制校验 MFA 动态码（TOTP 或短信验证码）。
   *
   * <p>消费风险评分引擎的 HIGH 分支（原实现仅处理 CRITICAL 拒绝，MEDIUM/HIGH 分支形同虚设）。
   *
   * @param user 登录用户
   * @param loginDTO 登录请求 DTO（含 mfaCode）
   * @param risk 登录风险评估结果
   * @throws BusinessException 风险为 HIGH 且 MFA 校验未通过时抛出
   */
  private void validateMfaIfRequired(
      UserAccountDO user, LoginDTO loginDTO, RiskScoringService.RiskScore risk) {
    if (risk == null || risk.level() != RiskScoringService.RiskLevel.HIGH || risk.shouldReject()) {
      return;
    }
    mfaService.validateLoginMfa(user, loginDTO.getMfaCode());
  }

  /**
   * 评估登录风险等级。
   *
   * @param user 用户账号
   * @param loginIp 登录来源 IP
   * @param userAgent 用户代理
   * @return 风险评估结果（含等级与因子），CRITICAL 时直接抛出异常
   * @throws BusinessException 风险等级为 CRITICAL 时拒绝登录
   */
  private RiskScoringService.RiskScore evaluateLoginRisk(
      UserAccountDO user, String loginIp, String userAgent) {
    int recentFailCount = getRecentFailCount(loginIp);
    boolean isNewDevice = checkIfNewDevice(user.getId(), userAgent);
    RiskScoringService.RiskScore riskScore =
        riskScoringService.evaluateRisk(
            user.getUsername(), loginIp, userAgent, recentFailCount, isNewDevice);
    log.info(
        "Login risk evaluated: username={}, ip={}, score={}, level={}, factors={}",
        user.getUsername(),
        loginIp,
        riskScore.score(),
        riskScore.level(),
        riskScore.factors());
    if (riskScore.shouldReject()) {
      log.warn(
          "Login rejected due to critical risk: username={}, ip={}, score={}",
          user.getUsername(),
          loginIp,
          riskScore.score());
      loginHistoryService.recordLoginAttempt(
          new LoginAttemptContext(user.getId(), user.getUsername(), loginIp),
          "FAILED",
          "RISK_CRITICAL",
          userAgent);
      throw new BusinessException(UserInfoExceptionCode.IP_BLOCKED);
    }
    return riskScore;
  }

  /**
   * 获取 IP 最近失败次数（窗口内，Redis 计数器）。
   *
   * <p>P1-2/P1-5: 由 Redis 计数器提供（{@link LoginAttemptCounterService#getIpFailCount}），
   * 替代原 DB count 查询，消除登录主路径 DB 往返；与 IP 封禁检查共用同一数据源。
   *
   * @param loginIp IP 地址
   * @return 窗口内失败次数
   */
  private int getRecentFailCount(String loginIp) {
    return loginAttemptCounterService.getIpFailCount(loginIp);
  }

  /**
   * 检查是否为新设备（基于 User-Agent 的 Redis 设备标记）。
   *
   * <p>P1-2: 由 Redis 设备标记提供（{@link LoginAttemptCounterService#isNewDevice}），
   * 替代原 DB count 查询；登录成功时写入设备标记（见 {@link #updateLoginSuccess}）。
   *
   * @param userId 用户 ID
   * @param userAgent 用户代理
   * @return true 如果是新设备
   */
  private boolean checkIfNewDevice(String userId, String userAgent) {
    return loginAttemptCounterService.isNewDevice(userId, userAgent);
  }

  /**
   * 签发 Token 并写入 Redis 会话。
   *
   * @param user 登录用户
   * @param roles 用户角色列表
   * @return 包含 accessToken 和 refreshToken 的结果对象
   */
  private TokenResult issueTokensAndCreateSession(UserAccountDO user, List<RoleDO> roles) {
    String roleCodes = roles.stream().map(RoleDO::getRoleCode).collect(Collectors.joining(","));
    String roleNames = roles.stream().map(RoleDO::getRoleName).collect(Collectors.joining(","));

    UserInfo userInfo = new UserInfo();
    userInfo.setUserId(user.getId());
    userInfo.setUsername(user.getUsername());
    userInfo.setRoleCode(roleCodes);
    userInfo.setTenantId(user.getTenantId());

    String accessToken = tokenService.issueAccessToken(userInfo);
    String refreshToken = tokenService.issueRefreshToken(userInfo);
    sessionManager.createSession(accessToken, refreshToken, user, roleCodes, roleNames);
    return new TokenResult(accessToken, refreshToken);
  }

  /**
   * 构建登录结果 VO。
   *
   * @param user 登录用户
   * @param roles 用户角色列表
   * @param tokenResult 包含 accessToken 和 refreshToken 的结果对象
   * @return 登录结果 VO
   */
  private LoginVO buildLoginResult(UserAccountDO user, List<RoleDO> roles, TokenResult tokenResult) {
    String roleCodes = roles.stream().map(RoleDO::getRoleCode).collect(Collectors.joining(","));
    String roleNames = roles.stream().map(RoleDO::getRoleName).collect(Collectors.joining(","));

    LoginVO result = new LoginVO();
    result.setAccessToken(tokenResult.accessToken());
    result.setRefreshToken(tokenResult.refreshToken());
    result.setTokenType("Bearer");
    result.setExpiresIn(properties.getTokenTtlSeconds());
    result.setScope("read write");

    LoginVO.UserInfoVO userInfoVO = UserInfoConverter.INSTANT.entityToUserInfoVO(user);
    userInfoVO.setRoleCode(roleCodes);
    userInfoVO.setRoleName(roleNames);
    result.setUserInfo(userInfoVO);
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * <p>委托 {@link SessionManager#revokeSession(String)} 完成会话索引移除与 access/refresh token 吊销。
   *
   * @param accessToken 访问令牌，为空时直接返回
   */
  @Override
  public void logout(String accessToken) {
    if (accessToken == null || accessToken.isBlank()) {
      return;
    }
    sessionManager.revokeSession(accessToken);
    userInfoMetrics.recordLogout();
    log.info("User logged out, token blacklisted");
  }

  /**
   * {@inheritDoc}
   *
   * <p>通过 refreshToken 换取新的 accessToken 和 refreshToken（token 轮换）， 旧 refreshToken 立即加入黑名单（一次性使用），防止
   * token 泄露后的长期滥用。 新会话写入 Redis 并维护会话索引。
   *
   * @param refreshToken 刷新令牌
   * @return 新的登录响应（含新 accessToken 和新 refreshToken）
   * @throws BusinessException 当 refreshToken 无效或过期时抛出
   */
  @Override
  public LoginVO refresh(String refreshToken) {
    if (!tokenService.validateRefreshToken(refreshToken)) {
      log.warn("Refresh token validation failed, possible token reuse attack");
      throw BusinessException.builder().resultCode(BaseResultCode.UNAUTHORIZED).build();
    }

    UserInfo userInfo = tokenService.parseRefreshToken(refreshToken);
    if (userInfo == null) {
      log.warn("Failed to parse user info from refresh token");
      throw BusinessException.builder().resultCode(BaseResultCode.UNAUTHORIZED).build();
    }

    // 签发新的 access_token 和 refresh_token（token 轮换）
    String newAccessToken = tokenService.issueAccessToken(userInfo);
    String newRefreshToken = tokenService.issueRefreshToken(userInfo);

    // 将旧 refresh_token 加入黑名单（一次性使用，防止重放攻击）
    tokenBlacklistService.addToBlacklist(refreshToken);

    // 更新 Redis 会话（新 access_token + 新 refresh_token）
    sessionManager.refreshSession(newAccessToken, newRefreshToken, userInfo);

    log.info("Token refreshed successfully for user: {}", userInfo.getUsername());

    LoginVO result = new LoginVO();
    result.setAccessToken(newAccessToken);
    result.setRefreshToken(newRefreshToken);
    result.setTokenType("Bearer");
    result.setExpiresIn(properties.getTokenTtlSeconds());
    result.setScope("read write");
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * @param userId 用户 ID
   */
  @Override
  public void kickOutUser(String userId) {
    sessionManager.evictAllSessions(userId);
    log.info("User {} has been kicked out, all sessions invalidated", userId);
  }

  /**
   * {@inheritDoc}
   *
   * <p>委托 {@link SessionManager#evictAllSessions(String)} 完成会话驱逐。
   *
   * @param userId 用户 ID，不可为 null 或空
   */
  @Override
  public void evictAllSessions(String userId) {
    sessionManager.evictAllSessions(userId);
  }

  /**
   * {@inheritDoc}
   *
   * <p>委托 {@link RoleCacheService#evictUserRolesCache(String)} 完成角色缓存失效。
   *
   * @param userId 用户 ID，不可为 null 或空
   */
  @Override
  public void evictUserRolesCache(String userId) {
    roleCacheService.evictUserRolesCache(userId);
  }

  /**
   * {@inheritDoc}
   *
   * <p>委托 {@link SessionManager#listActiveSessions(String)} 查询活跃会话。
   *
   * @param userId 用户 ID
   * @return 活跃 accessToken 集合
   */
  @Override
  public Set<String> listActiveSessions(String userId) {
    return sessionManager.listActiveSessions(userId);
  }

  /**
   * 更新登录成功信息：原子重置失败计数、清除锁定时间、记录最后登录时间/IP， 并标记设备已见（供风险评分识别新设备）。
   *
   * @param user 登录成功的用户账号
   * @param loginIp 登录来源 IP
   * @param userAgent 登录来源 User-Agent（用于设备标记）
   */
  private void updateLoginSuccess(UserAccountDO user, String loginIp, String userAgent) {
    user.recordLoginSuccess(loginIp);
    userAccountRepository.resetLoginSuccess(user.getId(), loginIp);
    loginAttemptCounterService.markDeviceSeen(
        user.getId(), userAgent, properties.getRiskWindowSeconds());
  }

  /**
   * Token 签发结果（内部传输对象）。
   *
   * @param accessToken 访问令牌（短期有效）
   * @param refreshToken 刷新令牌（长期有效，一次性使用）
   */
  private record TokenResult(String accessToken, String refreshToken) {}
}
