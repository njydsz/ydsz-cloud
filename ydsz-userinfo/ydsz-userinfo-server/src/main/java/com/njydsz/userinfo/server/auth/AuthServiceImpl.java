package com.njydsz.userinfo.server.auth;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.service.TokenBlacklistService;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.userinfo.server.event.UserDomainEventPublisher;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisCollectionOps;
import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.safe.alert.SecurityEvent;
import com.njydsz.common.safe.alert.SecurityEventPublisher;
import com.njydsz.common.safe.alert.SecurityEventType;
import com.njydsz.userinfo.domain.converter.UserInfoConverter;
import com.njydsz.userinfo.domain.dto.LoginDTO;
import com.njydsz.userinfo.domain.entity.Role;
import com.njydsz.userinfo.domain.entity.UserAccount;
import com.njydsz.userinfo.domain.entity.UserRole;
import com.njydsz.userinfo.domain.enums.EnableStatusEnum;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.vo.LoginVO;
import com.njydsz.userinfo.infra.mapper.UserLoginHistoryMapper;
import com.njydsz.userinfo.infra.mapper.RoleMapper;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;
import com.njydsz.userinfo.infra.mapper.UserRoleMapper;
import com.njydsz.userinfo.server.config.UserInfoProperties;
import com.njydsz.userinfo.server.metrics.UserInfoMetrics;
import com.njydsz.userinfo.server.service.LoginAttemptContext;
import com.njydsz.userinfo.server.service.LoginHistoryService;

/**
 * 认证服务实现。
 *
 * <p>核心能力：
 *
 * <ul>
 *   <li>验证码校验（可配置开关）
 *   <li>LDAP 域认证（可选依赖，自动探测）
 *   <li>账号锁定（N 次密码错误后自动锁定 30 分钟）
 *   <li>登录失败计数 + 最后登录信息更新
 *   <li>JWT Token 签发/刷新/吊销（使用 common-auth TokenService）
 *   <li>用户角色按 user_role 关联表精确查询
 *   <li>Micrometer 指标埋点（登录成功/失败/耗时）
 *   <li>会话管理：userId → Set&lt;accessToken&gt; 索引，支持强制下线
 * </ul>
 *
 * <p><b>会话索引设计（P1-1）：</b>
 *
 * <pre>
 *   userinfo:session:user:{userId}  →  Set&lt;accessToken&gt;   该用户所有活跃会话
 *   {accessToken}                   →  Hash&lt;String, Object&gt;  单会话详情（用户信息）
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  /** 用户会话索引 Redis Key 前缀：userinfo:session:user:{userId} */
  private static final String SESSION_KEY_PREFIX = "userinfo:session:user:";

  /** 用户角色缓存 Redis Key 前缀：userinfo:roles:{userId} */
  private static final String USER_ROLES_KEY_PREFIX = "userinfo:roles:";

  /** 用户角色缓存 TTL（秒）：10 分钟 */
  private static final long USER_ROLES_CACHE_TTL = 600L;

  /** 用户账号 Mapper */
  private final UserAccountMapper userAccountMapper;

  /** 角色 Mapper */
  private final RoleMapper roleMapper;

  /** 用户-角色关联 Mapper */
  private final UserRoleMapper userRoleMapper;

  /** JWT Token 服务（签发/刷新/解析） */
  private final TokenService tokenService;

  /** Token 黑名单服务（登出时主动失效） */
  private final TokenBlacklistService tokenBlacklistService;

  /** Redis Hash 操作（用于会话信息存储） */
  private final RedisHashOps redisHashOps;

  /** Redis 基础服务（用于会话 key 过期等操作） */
  private final RedisStringOps redisStringOps;

  /** Redis 集合操作（用于 userId → Set&lt;accessToken&gt; 会话索引） */
  private final RedisCollectionOps redisCollectionOps;

  /** 密码编码器（BCrypt） */
  private final PasswordEncoder passwordEncoder;

  /** 用户中心监控指标采集器 */
  private final UserInfoMetrics userInfoMetrics;

  /** 用户中心配置属性 */
  private final UserInfoProperties properties;

  /** 验证码服务 */
  private final CaptchaService captchaService;

  /** LDAP 认证提供者（可选依赖，未配置时为 null） */
  private final ObjectProvider<LdapAuthenticationProvider> ldapProviderProvider;

  /** 用户模块领域事件发布器 */
  private final UserDomainEventPublisher userDomainEventPublisher;

  /** 登录历史服务（记录登录尝试，IP 封禁检查） */
  private final LoginHistoryService loginHistoryService;

  /** 登录历史 Mapper（用于风险评分查询） */
  private final UserLoginHistoryMapper loginHistoryMapper;

  /** P2-2: 安全事件发布器（登录失败时发布 BRUTE_FORCE 事件，驱动 SecurityEventAggregator 自动封禁） */
  private final ObjectProvider<SecurityEventPublisher> securityEventPublisherProvider;

  /** P3: 风险评分服务（评估登录风险等级，动态调整认证策略） */
  private final RiskScoringService riskScoringService;

  /**
   * {@inheritDoc}
   *
   * <p>认证流程：验证码校验 → 用户查询 → 状态/锁定检查 → 密码校验（本地优先，失败回退 LDAP） → 角色加载 → JWT 签发 → Redis 会话存储 + 会话索引维护 →
   * 登录信息更新。
   *
   * <p>失败计数达到阈值时自动锁定账号，所有步骤均埋点 Micrometer 指标。
   *
   * @param loginDTO 登录请求（用户名、密码、可选验证码）
   * @return 登录响应（含 accessToken、refreshToken、用户信息）
   * @throws BusinessException 当验证码缺失、用户不存在、账号禁用/锁定、密码错误时抛出
   */
  @Override
  public LoginVO login(LoginDTO loginDTO) {
    Timer.Sample sample = userInfoMetrics.startTimer();
    String loginIp = loginDTO.getLoginIp();
    String userAgent = loginDTO.getUserAgent();

    // IP 封禁检查 + 验证码校验
    checkIpNotBlocked(loginIp, loginDTO.getUsername(), userAgent);
    validateCaptchaIfEnabled(loginDTO);

    // 查询用户 + 状态/锁定校验
    UserAccount user = findAndValidateUser(loginDTO.getUsername(), loginIp, userAgent);

    // P3: 登录风险评估（基于 IP、时间、设备、频率等多维度）
    evaluateLoginRisk(user, loginIp, userAgent);

    // 密码校验（本地 + LDAP 回退）
    authenticatePassword(user, loginDTO.getPassword(), loginIp, userAgent);

    // 加载角色 + 签发 Token + 存储会话
    List<Role> roles = loadUserRoles(user.getId());
    TokenResult tokenResult = issueTokensAndCreateSession(user, roles);

    // 更新登录状态 + 审计
    updateLoginSuccess(user, loginIp);
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
   * 校验图形验证码（启用时）。
   *
   * @param loginDTO 登录请求 DTO
   * @throws BusinessException 验证码为空或校验失败时抛出
   */
  private void validateCaptchaIfEnabled(LoginDTO loginDTO) {
    if (properties.isCaptchaEnabled()) {
      if (loginDTO.getCaptchaKey() == null || loginDTO.getCaptcha() == null) {
        userInfoMetrics.recordLoginFail();
        throw new BusinessException(UserInfoExceptionCode.CAPTCHA_REQUIRED);
      }
      captchaService.validate(loginDTO.getCaptchaKey(), loginDTO.getCaptcha());
    }
  }

  /**
   * 查询用户并校验账号状态与锁定状态。
   *
   * @param username 登录用户名
   * @param loginIp 登录来源 IP
   * @param userAgent 用户代理
   * @return 有效的用户账号实体
   * @throws BusinessException 用户不存在、已禁用或已锁定时抛出
   */
  private UserAccount findAndValidateUser(String username, String loginIp, String userAgent) {
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccount::getUsername, username);
    UserAccount user = userAccountMapper.selectOne(wrapper);

    if (user == null) {
      loginHistoryService.recordLoginAttempt(
          new LoginAttemptContext(null, username, loginIp), "FAILED", "USER_NOT_FOUND", userAgent);
      userInfoMetrics.recordLoginFail();
      throw new BusinessException(UserInfoExceptionCode.PASSWORD_INCORRECT);
    }

    if (user.getStatusEnum() == EnableStatusEnum.DISABLED) {
      loginHistoryService.recordLoginAttempt(
          new LoginAttemptContext(user.getId(), username, loginIp),
          "FAILED",
          "USER_DISABLED",
          userAgent);
      userInfoMetrics.recordLoginFail();
      throw new BusinessException(UserInfoExceptionCode.USER_DISABLED);
    }

    if (user.isLocked()) {
      loginHistoryService.recordLoginAttempt(
          new LoginAttemptContext(user.getId(), username, loginIp),
          "FAILED",
          "ACCOUNT_LOCKED",
          userAgent);
      userInfoMetrics.recordLoginFail();
      throw new BusinessException(UserInfoExceptionCode.ACCOUNT_LOCKED);
    }

    return user;
  }

  /**
   * 评估登录风险等级。
   *
   * <p>基于多维度因素评估登录请求风险，高风险时触发额外验证或拒绝登录。
   *
   * @param user 用户账号
   * @param loginIp 登录来源 IP
   * @param userAgent 用户代理
   * @throws BusinessException 风险等级为 CRITICAL 时拒绝登录
   */
  private void evaluateLoginRisk(UserAccount user, String loginIp, String userAgent) {
    // 获取最近失败次数
    int recentFailCount = getRecentFailCount(loginIp);
    // 判断是否新设备
    boolean isNewDevice = checkIfNewDevice(user.getId(), userAgent);
    // 评估风险
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
    // 极高风险：拒绝登录
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
  }

  /**
   * 获取 IP 最近失败次数。
   *
   * @param loginIp IP 地址
   * @return 最近 5 分钟内失败次数
   */
  private int getRecentFailCount(String loginIp) {
    if (loginIp == null || loginIp.isBlank()) {
      return 0;
    }
    try {
      LocalDateTime since = LocalDateTime.now().minusMinutes(5);
      LambdaQueryWrapper<com.njydsz.userinfo.domain.entity.UserLoginHistory> wrapper =
          new LambdaQueryWrapper<>();
      wrapper
          .eq(com.njydsz.userinfo.domain.entity.UserLoginHistory::getLoginIp, loginIp)
          .eq(com.njydsz.userinfo.domain.entity.UserLoginHistory::getLoginResult, "FAILED")
          .ge(com.njydsz.userinfo.domain.entity.UserLoginHistory::getCreatedAt, since);
      return Math.toIntExact(
          loginHistoryMapper != null ? loginHistoryMapper.selectCount(wrapper) : 0);
    } catch (Exception e) {
      log.warn("Failed to get recent fail count: ip={}", loginIp);
      return 0;
    }
  }

  /**
   * 检查是否为新设备。
   *
   * @param userId 用户 ID
   * @param userAgent 用户代理
   * @return true 如果是新设备
   */
  private boolean checkIfNewDevice(String userId, String userAgent) {
    if (userAgent == null || userAgent.isBlank()) {
      return false;
    }
    try {
      LambdaQueryWrapper<com.njydsz.userinfo.domain.entity.UserLoginHistory> wrapper =
          new LambdaQueryWrapper<>();
      wrapper
          .eq(com.njydsz.userinfo.domain.entity.UserLoginHistory::getUserId, userId)
          .eq(com.njydsz.userinfo.domain.entity.UserLoginHistory::getLoginResult, "SUCCESS")
          .eq(com.njydsz.userinfo.domain.entity.UserLoginHistory::getUserAgent, userAgent);
      return loginHistoryMapper == null || loginHistoryMapper.selectCount(wrapper) == 0;
    } catch (Exception e) {
      log.warn("Failed to check new device: userId={}", userId);
      return false;
    }
  }

  /**
   * 校验密码（本地 BCrypt 优先，失败后回退 LDAP）。
   *
   * @param user 用户账号
   * @param password 明文密码
   * @param loginIp 登录来源 IP
   * @param userAgent 用户代理
   * @throws BusinessException 密码校验失败时抛出
   */
  private void authenticatePassword(
      UserAccount user, String password, String loginIp, String userAgent) {
    String username = user.getUsername();
    boolean passwordMatched = passwordEncoder.matches(password, user.getPassword());
    if (!passwordMatched) {
      LdapAuthenticationProvider ldapProvider = ldapProviderProvider.getIfAvailable();
      if (ldapProvider != null && ldapProvider.isEnabled()) {
        passwordMatched = ldapProvider.authenticateLdap(username, password);
        if (passwordMatched) {
          log.info("LDAP authentication succeeded for user: {}", username);
        }
      }
    }

    if (!passwordMatched) {
      recordLoginFailure(user);
      loginHistoryService.recordLoginAttempt(
          new LoginAttemptContext(user.getId(), username, loginIp),
          "FAILED",
          "PASSWORD_INCORRECT",
          userAgent);
      publishBruteForceEvent(loginIp, userAgent, username);
      userInfoMetrics.recordLoginFail();
      throw new BusinessException(UserInfoExceptionCode.PASSWORD_INCORRECT);
    }
  }

  /**
   * 签发 Token 并写入 Redis 会话。
   *
   * @param user 登录用户
   * @param roles 用户角色列表
   * @return 包含 accessToken 和 refreshToken 的结果对象
   */
  private TokenResult issueTokensAndCreateSession(UserAccount user, List<Role> roles) {
    String roleCodes = roles.stream().map(Role::getRoleCode).collect(Collectors.joining(","));
    String roleNames = roles.stream().map(Role::getRoleName).collect(Collectors.joining(","));

    UserInfo userInfo = new UserInfo();
    userInfo.setUserId(user.getId());
    userInfo.setUsername(user.getUsername());
    userInfo.setRoleCode(roleCodes);
    userInfo.setTenantId(user.getTenantId());

    String accessToken = tokenService.issueAccessToken(userInfo);
    String refreshToken = tokenService.issueRefreshToken(userInfo);
    storeRedisSession(accessToken, user, roleCodes, roleNames);
    return new TokenResult(accessToken, refreshToken);
  }

  /**
   * 写入 Redis 会话（会话 Hash + 会话索引）。
   *
   * @param accessToken 访问令牌
   * @param user 用户账号
   * @param roleCodes 角色编码（逗号分隔）
   * @param roleNames 角色名称（逗号分隔）
   */
  private void storeRedisSession(
      String accessToken, UserAccount user, String roleCodes, String roleNames) {
    Map<String, Object> sessionInfo = new HashMap<>();
    sessionInfo.put("userId", user.getId());
    sessionInfo.put("username", user.getUsername());
    sessionInfo.put("roleCode", roleCodes);
    sessionInfo.put("roleName", roleNames);
    sessionInfo.put("tenantId", user.getTenantId());
    redisHashOps.hMSet(accessToken, sessionInfo);
    redisStringOps.expire(accessToken, Duration.ofSeconds(properties.getTokenTtlSeconds()));

    String sessionKey = buildSessionKey(user.getId());
    redisCollectionOps.sAdd(sessionKey, accessToken);
    redisStringOps.expire(sessionKey, Duration.ofSeconds(properties.getTokenTtlSeconds()));
  }

  /**
   * 构建登录结果 VO。
   *
   * @param user 登录用户
   * @param roles 用户角色列表
   * @param tokenResult 包含 accessToken 和 refreshToken 的结果对象
   * @return 登录结果 VO
   */
  private LoginVO buildLoginResult(UserAccount user, List<Role> roles, TokenResult tokenResult) {
    String roleCodes = roles.stream().map(Role::getRoleCode).collect(Collectors.joining(","));
    String roleNames = roles.stream().map(Role::getRoleName).collect(Collectors.joining(","));

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
   * <p>从会话索引中移除该 Token → 加入黑名单 → 删除 Redis 会话，实现主动失效。
   *
   * <p>P1-1 改进：先读取会话 Hash 获取 userId，再从 userId 对应的 Set 中移除该 token， 保持会话索引与实际活跃会话一致。
   *
   * @param accessToken 访问令牌，为空时直接返回
   */
  @Override
  public void logout(String accessToken) {
    if (accessToken == null || accessToken.isBlank()) {
      return;
    }

    // P1-1: 从会话 Hash 中获取 userId，以便清理会话索引
    String userId = redisHashOps.hGet(accessToken, "userId", String.class);

    // P1-1: 从 userId → Set 索引中移除该 token
    if (userId != null) {
      String sessionKey = buildSessionKey(userId);
      redisCollectionOps.sRem(sessionKey, accessToken);
      log.info("Removed token from session index for user: {}", userId);
    }

    tokenBlacklistService.addToBlacklist(accessToken);
    redisStringOps.del(accessToken);
    userInfoMetrics.recordLogout();
    log.info("User logged out, token blacklisted");
  }

  /**
   * {@inheritDoc}
   *
   * <p>通过 refreshToken 换取新的 accessToken 和 refreshToken（token 轮换）， 旧 refreshToken 立即加入黑名单（一次性使用），防止
   * token 泄露后的长期滥用。
   *
   * <p>P1-1: 维护会话索引 —— 将旧 accessToken 从 Set 中移除，加入新 accessToken。
   *
   * @param refreshToken 刷新令牌
   * @return 新的登录响应（含新 accessToken 和新 refreshToken）
   * @throws BusinessException 当 refreshToken 无效或过期时抛出
   */
  @Override
  public LoginVO refresh(String refreshToken) {
    // 1. 校验 refresh_token 有效性
    if (!tokenService.validateRefreshToken(refreshToken)) {
      log.warn("Refresh token validation failed, possible token reuse attack");
      throw BusinessException.builder().resultCode(BaseResultCode.UNAUTHORIZED).build();
    }

    // 2. 解析用户信息
    UserInfo userInfo = tokenService.parseRefreshToken(refreshToken);
    if (userInfo == null) {
      log.warn("Failed to parse user info from refresh token");
      throw BusinessException.builder().resultCode(BaseResultCode.UNAUTHORIZED).build();
    }

    // 3. 签发新的 access_token 和 refresh_token（token 轮换）
    String newAccessToken = tokenService.issueAccessToken(userInfo);
    String newRefreshToken = tokenService.issueRefreshToken(userInfo);

    // 4. 将旧 refresh_token 加入黑名单（一次性使用，防止重放攻击）
    tokenBlacklistService.addToBlacklist(refreshToken);

    // 5. 更新 Redis 会话（使用新 access_token）
    Map<String, Object> sessionInfo = new HashMap<>();
    sessionInfo.put("userId", userInfo.getUserId());
    sessionInfo.put("username", userInfo.getUsername());
    sessionInfo.put("roleCode", userInfo.getRoleCode());
    sessionInfo.put("roleName", userInfo.getRoleName());
    sessionInfo.put("tenantId", userInfo.getTenantId());
    redisHashOps.hMSet(newAccessToken, sessionInfo);
    redisStringOps.expire(newAccessToken, Duration.ofSeconds(properties.getTokenTtlSeconds()));

    // P1-1: 维护会话索引 —— 将新 token 加入 Set
    String sessionKey = buildSessionKey(userInfo.getUserId());
    redisCollectionOps.sAdd(sessionKey, newAccessToken);
    redisStringOps.expire(sessionKey, Duration.ofSeconds(properties.getTokenTtlSeconds()));

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
   * <p>读取 userId 对应 Set 中全部 accessToken，逐个加入黑名单并清理 Hash， <b>不</b>直接删除 Set Key（依赖 TTL 自然过期或后续 logout
   * 调用）。 实际生产建议通过广播通知各节点清理本地缓存。
   *
   * @param userId 用户 ID
   */
  @Override
  public void kickOutUser(String userId) {
    evictAllSessions(userId);
    log.info("User {} has been kicked out, all sessions invalidated", userId);
  }

  /**
   * 按 user_role 关联表查询用户角色（带 Redis 缓存）。
   *
   * <p>P1-1: 使用 Redis 缓存用户角色列表，减少登录时数据库查询次数。 缓存 key 为 {@code userinfo:roles:{userId}}，TTL 10 分钟。
   * 角色变更时通过 {@link #evictUserRolesCache(String)} 主动失效。
   *
   * @param userId 用户 ID
   * @return 用户持有的有效角色列表，无角色时返回空列表
   */
  private List<Role> loadUserRoles(String userId) {
    // 1. 尝试从 Redis 缓存读取
    String cacheKey = USER_ROLES_KEY_PREFIX + userId;
    try {
      List<Role> cachedRoles = redisHashOps.hGet(cacheKey, "roles", List.class);
      if (cachedRoles != null && !cachedRoles.isEmpty()) {
        log.debug("User roles cache hit: userId={}", userId);
        userInfoMetrics.recordCacheResult("roles_cache_total", "hit");
        return cachedRoles;
      }
    } catch (Exception e) {
      log.warn("Failed to read user roles cache: userId={}, error={}", userId, e.getMessage());
    }

    // 2. 缓存未命中，查询数据库
    userInfoMetrics.recordCacheResult("roles_cache_total", "miss");
    List<Role> roles = loadUserRolesFromDb(userId);

    // 3. 写入 Redis 缓存
    if (!roles.isEmpty()) {
      try {
        redisHashOps.hSet(cacheKey, "roles", roles);
        redisStringOps.expire(cacheKey, Duration.ofSeconds(USER_ROLES_CACHE_TTL));
        log.debug("User roles cached: userId={}, count={}", userId, roles.size());
      } catch (Exception e) {
        log.warn("Failed to cache user roles: userId={}, error={}", userId, e.getMessage());
      }
    }

    return roles;
  }

  /**
   * 从数据库查询用户角色（原始方法）。
   *
   * @param userId 用户 ID
   * @return 用户持有的有效角色列表
   */
  private List<Role> loadUserRolesFromDb(String userId) {
    LambdaQueryWrapper<UserRole> urWrapper = new LambdaQueryWrapper<>();
    urWrapper.eq(UserRole::getUserId, userId);
    List<UserRole> userRoles = userRoleMapper.selectList(urWrapper);

    if (userRoles.isEmpty()) {
      return List.of();
    }

    List<String> roleIds = userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toList());

    LambdaQueryWrapper<Role> roleWrapper = new LambdaQueryWrapper<>();
    roleWrapper.in(Role::getId, roleIds);
    roleWrapper.eq(Role::getStatus, "ENABLED");
    return roleMapper.selectList(roleWrapper);
  }

  /**
   * P1-1: 失效指定用户的角色缓存。
   *
   * <p>在角色分配变更时调用，保证缓存一致性。
   *
   * @param userId 用户 ID
   */
  public void evictUserRolesCache(String userId) {
    if (userId == null || userId.isBlank()) {
      return;
    }
    try {
      String cacheKey = USER_ROLES_KEY_PREFIX + userId;
      redisStringOps.del(cacheKey);
      log.info("User roles cache evicted: userId={}", userId);
    } catch (Exception e) {
      log.warn("Failed to evict user roles cache: userId={}, error={}", userId, e.getMessage());
    }
  }

  /**
   * 记录登录失败：原子自增失败计数，达到阈值时由 SQL 原子设置锁定时间。
   *
   * <p>P0-1: 改为数据库原子自增（{@code login_fail_count = login_fail_count + 1}），
   * 消除先读后写（read-modify-write）的并发竞态——并发失败时计数不再丢失，锁定阈值不可被并发击穿。
   *
   * <p>原子 SQL 执行后，同步更新内存实体状态（通过领域方法），保持聚合根一致性。
   *
   * @param user 登录失败的用户账号
   */
  private void recordLoginFailure(UserAccount user) {
    userAccountMapper.increaseLoginFailCount(
        user.getId(), properties.getMaxLoginFailCount(), properties.getLockDurationMinutes());
    // 同步内存状态（原子 SQL 不返回更新后的实体，通过领域方法模拟）
    user.recordLoginFailure(properties.getMaxLoginFailCount(), properties.getLockDurationMinutes());
    log.warn("User [{}] login failed, fail count incremented atomically", user.getUsername());
  }

  /**
   * 更新登录成功信息：原子重置失败计数、清除锁定时间、记录最后登录时间/IP。
   *
   * <p>先通过领域方法更新内存状态，再调用 Mapper 原子更新数据库，保持聚合根与持久化层一致。
   *
   * @param user 登录成功的用户账号
   * @param loginIp 登录来源 IP
   */
  private void updateLoginSuccess(UserAccount user, String loginIp) {
    user.recordLoginSuccess(loginIp);
    userAccountMapper.resetLoginSuccess(user.getId(), loginIp);
  }

  /**
   * P2-2: 发布 BRUTE_FORCE 安全事件
   *
   * <p>当密码校验失败时调用，将事件发布给 common-safe 的 {@link SecurityEventAggregator}。 聚合器基于滑动窗口统计同一 IP
   * 的登录失败频率，超过阈值时自动封禁 IP。
   *
   * @param sourceIp 请求来源 IP
   * @param userAgent 用户代理
   * @param username 登录尝试的用户名
   */
  private void publishBruteForceEvent(String sourceIp, String userAgent, String username) {
    SecurityEventPublisher publisher = securityEventPublisherProvider.getIfAvailable();
    if (publisher == null) {
      return;
    }
    if (sourceIp == null || sourceIp.isBlank()) {
      return;
    }
    publisher.publish(
        new SecurityEvent(
            SecurityEventType.BRUTE_FORCE,
            "/auth/login",
            sourceIp,
            userAgent,
            "Failed login for user: " + username,
            SecurityEvent.Severity.MEDIUM));
  }

  /**
   * 驱逐指定用户的全部活跃会话（改密/禁用/强制下线时调用）。
   *
   * <p>从 Redis Set 中读取所有 accessToken，逐个加入黑名单并删除 Hash， 最后删除 Set 索引 Key。操作不抛出异常，单条 token 失败不影响后续清理。
   *
   * @param userId 用户 ID，不可为 null 或空
   */
  public void evictAllSessions(String userId) {
    if (userId == null || userId.isBlank()) {
      return;
    }
    String sessionKey = buildSessionKey(userId);
    try {
      Set<String> tokens = redisCollectionOps.sMembers(sessionKey, String.class);
      if (tokens.isEmpty()) {
        log.debug("No active sessions found for user: {}", userId);
        redisStringOps.del(sessionKey);
        return;
      }
      for (String token : tokens) {
        try {
          tokenBlacklistService.addToBlacklist(token);
          redisStringOps.del(token);
        } catch (Exception e) {
          log.warn("Failed to evict session token for user: {}, error={}", userId, e.getMessage());
        }
      }
      redisStringOps.del(sessionKey);
      // P1-1: 按实际驱逐的会话数递减计数器
      for (int i = 0; i < tokens.size(); i++) {
        userInfoMetrics.recordLogout();
      }
      log.info("Evicted {} sessions for user: {}", tokens.size(), userId);
    } catch (Exception e) {
      log.warn("Failed to evict sessions for user: {}, error={}", userId, e.getMessage());
    }
  }

  /**
   * 构建用户会话索引 Redis Key。
   *
   * @param userId 用户 ID
   * @return 格式为 {@code userinfo:session:user:{userId}}
   */
  private String buildSessionKey(String userId) {
    return SESSION_KEY_PREFIX + userId;
  }

  /**
   * {@inheritDoc}
   *
   * <p>从 Redis Set 中读取 userId 对应的所有活跃 accessToken。
   *
   * @param userId 用户 ID
   * @return 活跃 accessToken 集合
   */
  @Override
  public Set<String> listActiveSessions(String userId) {
    if (userId == null || userId.isBlank()) {
      return java.util.Set.of();
    }
    String sessionKey = buildSessionKey(userId);
    try {
      return redisCollectionOps.sMembers(sessionKey, String.class);
    } catch (Exception e) {
      log.warn("Failed to list active sessions for user: {}, error={}", userId, e.getMessage());
      return java.util.Set.of();
    }
  }

  /**
   * Token 签发结果（内部传输对象）。
   *
   * <p>封装 accessToken 与 refreshToken，避免方法返回多个值或丢失 refreshToken。
   *
   * @param accessToken 访问令牌（短期有效）
   * @param refreshToken 刷新令牌（长期有效，一次性使用）
   */
  private record TokenResult(String accessToken, String refreshToken) {}
}
