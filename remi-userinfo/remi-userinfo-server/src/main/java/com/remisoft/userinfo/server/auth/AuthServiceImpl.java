package com.remisoft.userinfo.server.auth;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import com.remisoft.common.redis.service.RedisService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.remisoft.common.auth.model.UserInfo;
import com.remisoft.common.auth.service.TokenBlacklistService;
import com.remisoft.common.auth.token.TokenService;
import com.remisoft.common.core.code.BaseResultCode;
import com.remisoft.common.redis.service.ops.RedisHashOps;
import com.remisoft.userinfo.domain.converter.UserInfoConverter;
import com.remisoft.userinfo.domain.dto.LoginDTO;
import com.remisoft.userinfo.domain.entity.Role;
import com.remisoft.userinfo.domain.entity.UserAccount;
import com.remisoft.userinfo.domain.entity.UserRole;
import com.remisoft.userinfo.domain.enums.UserInfoResultCode;
import com.remisoft.common.exception.custom.BusinessException;
import com.remisoft.userinfo.domain.vo.LoginVO;
import com.remisoft.userinfo.infra.mapper.RoleMapper;
import com.remisoft.userinfo.infra.mapper.UserAccountMapper;
import com.remisoft.common.event.model.StandardEventTypes;
import com.remisoft.common.event.service.OutboxService;
import com.remisoft.common.json.RemiJson;
import com.remisoft.userinfo.infra.mapper.UserRoleMapper;
import com.remisoft.userinfo.server.config.UserInfoProperties;
import com.remisoft.userinfo.server.metrics.UserInfoMetrics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

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
    private final RedisService redisService;
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
    /** Outbox 事件服务（可选依赖，用于发布用户登录/登出领域事件） */
    private final ObjectProvider<OutboxService> outboxServiceProvider;

    /**
     * {@inheritDoc}
     * <p>认证流程：验证码校验 → 用户查询 → 状态/锁定检查 → 密码校验（本地优先，失败回退 LDAP）
     * → 角色加载 → JWT 签发 → Redis 会话存储 → 登录信息更新。
     * <p>失败计数达到阈值时自动锁定账号，所有步骤均埋点 Micrometer 指标。
     *
     * @param loginDTO 登录请求（用户名、密码、可选验证码）
     * @return 登录响应（含 accessToken、refreshToken、用户信息）
     * @throws BusinessException 当验证码缺失、用户不存在、账号禁用/锁定、密码错误时抛出
     */
    @Override
    public LoginVO login(LoginDTO loginDTO) {
        Timer.Sample sample = userInfoMetrics.startTimer();

        // 验证码校验（可配置开关）
        if (properties.isCaptchaEnabled()) {
            if (loginDTO.getCaptchaKey() == null || loginDTO.getCaptcha() == null) {
                userInfoMetrics.recordLoginFail();
                userInfoMetrics.stopTimer(sample);
                throw new BusinessException(UserInfoResultCode.CAPTCHA_REQUIRED);
            }
            captchaService.validate(loginDTO.getCaptchaKey(), loginDTO.getCaptcha());
        }

        String username = loginDTO.getUsername();
        String password = loginDTO.getPassword();

        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAccount::getUsername, username);
        UserAccount user = userAccountMapper.selectOne(wrapper);

        if (user == null) {
            userInfoMetrics.recordLoginFail();
            userInfoMetrics.stopTimer(sample);
            throw new BusinessException(UserInfoResultCode.USER_NOT_FOUND);
        }

        // 账号状态检查（status 为 String: "0"=禁用, "1"=启用）
        if ("0".equals(user.getStatus())) {
            userInfoMetrics.recordLoginFail();
            userInfoMetrics.stopTimer(sample);
            throw new BusinessException(UserInfoResultCode.USER_DISABLED);
        }

        // 账号锁定检查
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            userInfoMetrics.recordLoginFail();
            userInfoMetrics.stopTimer(sample);
            throw new BusinessException(UserInfoResultCode.ACCOUNT_LOCKED);
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
            userInfoMetrics.recordLoginFail();
            userInfoMetrics.stopTimer(sample);
            throw new BusinessException(UserInfoResultCode.PASSWORD_INCORRECT);
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
        redisService.expire(accessToken, Duration.ofSeconds(properties.getTokenTtlSeconds()));

        updateLoginSuccess(user);

        userInfoMetrics.recordLoginSuccess();
        userInfoMetrics.stopTimer(sample);

        // 发布用户登录领域事件到 Outbox
        publishEvent(StandardEventTypes.USER_LOGIN, user.getId(), user);

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
     * <p>将 Token 加入黑名单并删除 Redis 会话，实现主动失效。
     *
     * @param accessToken 访问令牌，为空时直接返回
     */
    @Override
    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        tokenBlacklistService.addToBlacklist(accessToken);
        redisService.delete(accessToken);
        userInfoMetrics.recordLogout();
        log.info("User logged out, token blacklisted");
    }

    /**
     * {@inheritDoc}
     * <p>通过 refreshToken 换取新的 accessToken，refreshToken 本身不变。
     *
     * @param refreshToken 刷新令牌
     * @return 新的登录响应（含新 accessToken）
     * @throws BusinessException 当 refreshToken 无效或过期时抛出
     */
    @Override
    public LoginVO refresh(String refreshToken) {
        String newAccessToken = tokenService.refreshAccessToken(refreshToken);
        if (newAccessToken == null) {
            throw new BusinessException(BaseResultCode.TOKEN_EXPIRED);
        }
        LoginVO result = new LoginVO();
        result.setAccessToken(newAccessToken);
        result.setRefreshToken(refreshToken);
        result.setTokenType("Bearer");
        result.setExpiresIn(properties.getTokenTtlSeconds());
        result.setScope("read write");
        return result;
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
     * 记录登录失败：递增失败计数，达到阈值自动锁定。
     */
    private void recordLoginFailure(UserAccount user) {
        int failCount = (user.getLoginFailCount() == null ? 0 : user.getLoginFailCount()) + 1;

        LambdaUpdateWrapper<UserAccount> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserAccount::getId, user.getId());
        updateWrapper.set(UserAccount::getLoginFailCount, failCount);

        if (failCount >= properties.getMaxLoginFailCount()) {
            updateWrapper.set(UserAccount::getLockedUntil,
                    LocalDateTime.now().plusMinutes(properties.getLockDurationMinutes()));
            log.warn("User {} locked after {} failed attempts", user.getUsername(), failCount);
        }
        userAccountMapper.update(null, updateWrapper);
    }

    /**
     * 更新登录成功信息：重置失败计数、记录最后登录时间。
     */
    private void updateLoginSuccess(UserAccount user) {
        LambdaUpdateWrapper<UserAccount> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserAccount::getId, user.getId());
        updateWrapper.set(UserAccount::getLoginFailCount, 0);
        updateWrapper.set(UserAccount::getLockedUntil, null);
        updateWrapper.set(UserAccount::getLastLoginAt, LocalDateTime.now());
        userAccountMapper.update(null, updateWrapper);
    }

    /**
     * 发布领域事件到 Outbox（可选依赖，不存在时安全降级）
     */
    private void publishEvent(String eventType, String aggregateId, Object payload) {
        OutboxService outboxService = outboxServiceProvider.getIfAvailable();
        if (outboxService == null) {
            log.debug("OutboxService not available, skipping event: type={}, id={}", eventType, aggregateId);
            return;
        }
        try {
            outboxService.appendToOutbox("UserAccount", aggregateId, eventType,
                    RemiJson.toJson(payload));
        } catch (Exception e) {
            log.warn("Failed to publish outbox event: type={}, id={}, error={}",
                    eventType, aggregateId, e.getMessage());
        }
    }
}
