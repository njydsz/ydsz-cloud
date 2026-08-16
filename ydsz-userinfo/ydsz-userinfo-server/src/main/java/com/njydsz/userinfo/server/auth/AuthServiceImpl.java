package com.njydsz.userinfo.server.auth;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.njydsz.common.redis.service.ops.RedisCollectionOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.service.TokenBlacklistService;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.userinfo.domain.converter.UserInfoConverter;
import com.njydsz.userinfo.domain.dto.LoginDTO;
import com.njydsz.userinfo.domain.entity.Role;
import com.njydsz.userinfo.domain.entity.UserAccount;
import com.njydsz.userinfo.domain.entity.UserRole;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.vo.LoginVO;
import com.njydsz.userinfo.infra.mapper.RoleMapper;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.userinfo.infra.mapper.UserRoleMapper;
import com.njydsz.userinfo.server.config.UserInfoProperties;
import com.njydsz.userinfo.server.metrics.UserInfoMetrics;
import com.njydsz.userinfo.server.service.LoginHistoryService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 认证服务实现。
 *
 * <p>核心能力：
 * <ul>
 *   <li>验证码校验（可配置开关）</li>
 *   <li>LDAP 域认证（可选依赖，自动探测）</li>
 *   <li>账号锁定（N 次密码错误后自动锁定 30 分钟）</li>
 *   <li>登录失败计数 + 最后登录信息更新</li>
 *   <li>JWT Token 签发/刷新/吊销（使用 common-auth TokenService）</li>
 *   <li>用户角色按 user_role 关联表精确查询</li>
 *   <li>Micrometer 指标埋点（登录成功/失败/耗时）</li>
 *   <li>会话管理：userId → Set&lt;accessToken&gt; 索引，支持强制下线</li>
 * </ul>
 *
 * <p><b>会话索引设计（P1-1）：</b>
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
    /** 统一领域事件发布门面 */
    private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;
    /** 登录历史服务（记录登录尝试，IP 封禁检查） */
    private final LoginHistoryService loginHistoryService;

    /**
     * {@inheritDoc}
     * <p>认证流程：验证码校验 → 用户查询 → 状态/锁定检查 → 密码校验（本地优先，失败回退 LDAP）
     * → 角色加载 → JWT 签发 → Redis 会话存储 + 会话索引维护 → 登录信息更新。
     * <p>失败计数达到阈值时自动锁定账号，所有步骤均埋点 Micrometer 指标。
     *
     * @param loginDTO 登录请求（用户名、密码、可选验证码）
     * @return 登录响应（含 accessToken、refreshToken、用户信息）
     * @throws BusinessException 当验证码缺失、用户不存在、账号禁用/锁定、密码错误时抛出
     */
    @Override
    public LoginVO login(LoginDTO loginDTO) {
        Timer.Sample sample = userInfoMetrics.startTimer();

        // P1-3: IP 封禁检查（在真实校验前拦截被禁 IP，避免被禁 IP 继续密码探测）
        String loginIp = loginDTO.getLoginIp();
        String userAgent = loginDTO.getUserAgent();
        if (loginIp != null && !loginIp.isBlank() && loginHistoryService.isIpBlocked(loginIp)) {
            log.warn("Login attempt from blocked IP: {}, username: {}", loginIp, loginDTO.getUsername());
            loginHistoryService.recordLoginAttempt(null, loginDTO.getUsername(), loginIp,
                    "FAILED", "IP_BLOCKED", userAgent);
            userInfoMetrics.recordLoginFail();
            userInfoMetrics.stopTimer(sample);
            throw new BusinessException(UserInfoExceptionCode.IP_BLOCKED);
        }

        // 验证码校验（可配置开关）
        if (properties.isCaptchaEnabled()) {
            if (loginDTO.getCaptchaKey() == null || loginDTO.getCaptcha() == null) {
                userInfoMetrics.recordLoginFail();
                userInfoMetrics.stopTimer(sample);
                throw new BusinessException(UserInfoExceptionCode.CAPTCHA_REQUIRED);
            }
            captchaService.validate(loginDTO.getCaptchaKey(), loginDTO.getCaptcha());
        }

        String username = loginDTO.getUsername();
        String password = loginDTO.getPassword();

        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAccount::getUsername, username);
        UserAccount user = userAccountMapper.selectOne(wrapper);

        if (user == null) {
            // P0-4: 统一返回「用户名或密码错误」，避免通过响应差异枚举有效用户名；
            // 真实原因（USER_NOT_FOUND）仅保留在审计日志中。
            loginHistoryService.recordLoginAttempt(null, username, loginIp, "FAILED", "USER_NOT_FOUND", userAgent);
            userInfoMetrics.recordLoginFail();
            userInfoMetrics.stopTimer(sample);
            throw new BusinessException(UserInfoExceptionCode.PASSWORD_INCORRECT);
        }

        // 账号状态检查（status 为 String: "0"=禁用, "1"=启用）
        if ("0".equals(user.getStatus())) {
            loginHistoryService.recordLoginAttempt(user.getId(), username, loginIp,
                    "FAILED", "USER_DISABLED", userAgent);
            userInfoMetrics.recordLoginFail();
            userInfoMetrics.stopTimer(sample);
            throw new BusinessException(UserInfoExceptionCode.USER_DISABLED);
        }

        // 账号锁定检查
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            loginHistoryService.recordLoginAttempt(user.getId(), username, loginIp,
                    "FAILED", "ACCOUNT_LOCKED", userAgent);
            userInfoMetrics.recordLoginFail();
            userInfoMetrics.stopTimer(sample);
            throw new BusinessException(UserInfoExceptionCode.ACCOUNT_LOCKED);
        }

        // 密码校验 — 先尝试本地密码，失败后尝试 LDAP
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
            loginHistoryService.recordLoginAttempt(user.getId(), username, loginIp,
                    "FAILED", "PASSWORD_INCORRECT", userAgent);
            userInfoMetrics.recordLoginFail();
            userInfoMetrics.stopTimer(sample);
            throw new BusinessException(UserInfoExceptionCode.PASSWORD_INCORRECT);
        }

        List<Role> roles = loadUserRoles(user.getId());
        String roleCodes = roles.stream().map(Role::getRoleCode)
                .collect(Collectors.joining(","));
        String roleNames = roles.stream().map(Role::getRoleName)
                .collect(Collectors.joining(","));

        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRoleCode(roleCodes);
        userInfo.setTenantId(user.getTenantId());

        String accessToken = tokenService.issueAccessToken(userInfo);
        String refreshToken = tokenService.issueRefreshToken(userInfo);

        // Redis 会话存储（用于登出时主动失效 + 在线会话管理）
        Map<String, Object> sessionInfo = new HashMap<>();
        sessionInfo.put("userId", user.getId());
        sessionInfo.put("username", user.getUsername());
        sessionInfo.put("roleCode", roleCodes);
        sessionInfo.put("roleName", roleNames);
        sessionInfo.put("tenantId", user.getTenantId());
        redisHashOps.hMSet(accessToken, sessionInfo);
        redisStringOps.expire(accessToken, Duration.ofSeconds(properties.getTokenTtlSeconds()));

        // P1-1: 维护 userId → Set&lt;accessToken&gt; 会话索引
        String sessionKey = buildSessionKey(user.getId());
        redisCollectionOps.sAdd(sessionKey, accessToken);
        redisStringOps.expire(sessionKey, Duration.ofSeconds(properties.getTokenTtlSeconds()));

        updateLoginSuccess(user, loginIp);

        // P1-3: 记录成功登录
        loginHistoryService.recordLoginAttempt(user.getId(), username, loginIp,
                "SUCCESS", null, userAgent);

        userInfoMetrics.recordLoginSuccess();
        userInfoMetrics.stopTimer(sample);

        // 发布用户登录领域事件到 Outbox
        publishEvent(DomainEventTypes.USER_LOGIN, user.getId(), user);

        LoginVO result = new LoginVO();
        result.setAccessToken(accessToken);
        result.setRefreshToken(refreshToken);
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
     * <p>从会话索引中移除该 Token → 加入黑名单 → 删除 Redis 会话，实现主动失效。
     *
     * <p>P1-1 改进：先读取会话 Hash 获取 userId，再从 userId 对应的 Set 中移除该 token，
     * 保持会话索引与实际活跃会话一致。
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
     * <p>通过 refreshToken 换取新的 accessToken 和 refreshToken（token 轮换），
     * 旧 refreshToken 立即加入黑名单（一次性使用），防止 token 泄露后的长期滥用。
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
     * <p>读取 userId 对应 Set 中全部 accessToken，逐个加入黑名单并清理 Hash，
     * <b>不</b>直接删除 Set Key（依赖 TTL 自然过期或后续 logout 调用）。
     * 实际生产建议通过广播通知各节点清理本地缓存。
     *
     * @param userId 用户 ID
     */
    @Override
    public void kickOutUser(String userId) {
        evictAllSessions(userId);
        log.info("User {} has been kicked out, all sessions invalidated", userId);
    }

    /**
     * 按 user_role 关联表查询用户角色（修复 P0-1 Bug）。
     */
    private List<Role> loadUserRoles(String userId) {
        LambdaQueryWrapper<UserRole> urWrapper = new LambdaQueryWrapper<>();
        urWrapper.eq(UserRole::getUserId, userId);
        List<UserRole> userRoles = userRoleMapper.selectList(urWrapper);

        if (userRoles.isEmpty()) {
            return List.of();
        }

        List<String> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toList());

        LambdaQueryWrapper<Role> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.in(Role::getId, roleIds);
        roleWrapper.eq(Role::getStatus, "ENABLED");
        return roleMapper.selectList(roleWrapper);
    }

    /**
     * 记录登录失败：原子自增失败计数，达到阈值时由 SQL 原子设置锁定时间。
     *
     * <p>P0-1: 改为数据库原子自增（{@code login_fail_count = login_fail_count + 1}），
     * 消除先读后写（read-modify-write）的并发竞态——并发失败时计数不再丢失，
     * 锁定阈值不可被并发击穿。
     */
    private void recordLoginFailure(UserAccount user) {
        userAccountMapper.increaseLoginFailCount(user.getId(),
                properties.getMaxLoginFailCount(), properties.getLockDurationMinutes());
        log.warn("User [{}] login failed, fail count incremented atomically", user.getUsername());
    }

    /**
     * 更新登录成功信息：原子重置失败计数、清除锁定时间、记录最后登录时间/IP。
     */
    private void updateLoginSuccess(UserAccount user, String loginIp) {
        userAccountMapper.resetLoginSuccess(user.getId(), loginIp);
    }

    /**
     * 发布领域事件到 Outbox（可选依赖，不存在时安全降级）
     */
    private void publishEvent(String eventType, String aggregateId, Object payload) {
        DomainEventPublisher publisher = eventPublisherProvider.getIfAvailable();
        if (publisher == null) {
            log.debug("DomainEventPublisher not available, skipping event: type={}, id={}", eventType, aggregateId);
            return;
        }
        publisher.publish(DomainEvent.builder()
                .aggregateType("UserAccount")
                .aggregateId(aggregateId)
                .eventType(eventType)
                .metadata("payload", payload)
                .build());
    }

    /**
     * 驱逐指定用户的全部活跃会话（改密/禁用/强制下线时调用）。
     *
     * <p>从 Redis Set 中读取所有 accessToken，逐个加入黑名单并删除 Hash，
     * 最后删除 Set 索引 Key。操作不抛出异常，单条 token 失败不影响后续清理。
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
}
