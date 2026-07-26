package com.njydsz.userinfo.server.auth;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import com.njydsz.common.redis.service.RedisService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.service.TokenBlacklistService;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.userinfo.domain.dto.LoginDTO;
import com.njydsz.userinfo.domain.entity.RoleDO;
import com.njydsz.userinfo.domain.entity.UserAccountDO;
import com.njydsz.userinfo.domain.entity.UserRoleDO;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.vo.LoginVO;
import com.njydsz.userinfo.infra.mapper.RoleMapper;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;
import com.njydsz.userinfo.infra.mapper.UserRoleMapper;
import com.njydsz.userinfo.server.config.UserInfoProperties;
import com.njydsz.userinfo.server.metrics.UserInfoMetrics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.micrometer.core.instrument.Timer;
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
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final TokenService tokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final RedisHashOps redisHashOps;
    private final RedisService redisService;
    private final PasswordEncoder passwordEncoder;
    private final UserInfoMetrics userInfoMetrics;
    private final UserInfoProperties properties;
    private final CaptchaService captchaService;
    private final ObjectProvider<LdapAuthenticationProvider> ldapProviderProvider;

    public AuthServiceImpl(UserAccountMapper userAccountMapper,
                           RoleMapper roleMapper,
                           UserRoleMapper userRoleMapper,
                           TokenService tokenService,
                           TokenBlacklistService tokenBlacklistService,
                           RedisHashOps redisHashOps,
                           RedisTemplate<String, Object> redisTemplate,
                           PasswordEncoder passwordEncoder,
                           UserInfoMetrics userInfoMetrics,
                           UserInfoProperties properties,
                           CaptchaService captchaService,
                           ObjectProvider<LdapAuthenticationProvider> ldapProviderProvider) {
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.tokenService = tokenService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.redisHashOps = redisHashOps;
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.userInfoMetrics = userInfoMetrics;
        this.properties = properties;
        this.captchaService = captchaService;
        this.ldapProviderProvider = ldapProviderProvider;
    }

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

        LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAccountDO::getUsername, username);
        wrapper.eq(UserAccountDO::getDeleted, 0);
        UserAccountDO user = userAccountMapper.selectOne(wrapper);

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

        List<RoleDO> roles = loadUserRoles(user.getId());
        String roleCodes = roles.stream().map(RoleDO::getRoleCode)
                .collect(Collectors.joining(","));
        String roleNames = roles.stream().map(RoleDO::getRoleName)
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

        LoginVO result = new LoginVO();
        result.setAccessToken(accessToken);
        result.setRefreshToken(refreshToken);
        result.setTokenType("Bearer");
        result.setExpiresIn(properties.getTokenTtlSeconds());
        result.setScope("read write");

        LoginVO.UserInfoVO userInfoVO = new LoginVO.UserInfoVO();
        userInfoVO.setUserId(user.getId());
        userInfoVO.setUsername(user.getUsername());
        userInfoVO.setRealName(user.getRealName());
        userInfoVO.setRoleCode(roleCodes);
        userInfoVO.setRoleName(roleNames);
        userInfoVO.setTenantId(user.getTenantId());
        userInfoVO.setAvatar(user.getAvatar());
        result.setUserInfo(userInfoVO);

        return result;
    }

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

    @Override
    public LoginVO refresh(String refreshToken) {
        String newAccessToken = tokenService.refreshAccessToken(refreshToken);
        if (newAccessToken == null) {
            throw new BusinessException(BaseResultCode.TOKEN_INVALID);
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
    private List<RoleDO> loadUserRoles(String userId) {
        LambdaQueryWrapper<UserRoleDO> urWrapper = new LambdaQueryWrapper<>();
        urWrapper.eq(UserRoleDO::getUserId, userId);
        urWrapper.eq(UserRoleDO::getDeleted, 0);
        List<UserRoleDO> userRoles = userRoleMapper.selectList(urWrapper);

        if (userRoles.isEmpty()) {
            return List.of();
        }

        List<String> roleIds = userRoles.stream()
                .map(UserRoleDO::getRoleId)
                .collect(Collectors.toList());

        LambdaQueryWrapper<RoleDO> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.in(RoleDO::getId, roleIds);
        roleWrapper.eq(RoleDO::getDeleted, 0);
        roleWrapper.eq(RoleDO::getStatus, "ENABLED");
        return roleMapper.selectList(roleWrapper);
    }

    /**
     * 记录登录失败：递增失败计数，达到阈值自动锁定。
     */
    private void recordLoginFailure(UserAccountDO user) {
        int failCount = (user.getLoginFailCount() == null ? 0 : user.getLoginFailCount()) + 1;

        LambdaUpdateWrapper<UserAccountDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserAccountDO::getId, user.getId());
        updateWrapper.set(UserAccountDO::getLoginFailCount, failCount);

        if (failCount >= properties.getMaxLoginFailCount()) {
            updateWrapper.set(UserAccountDO::getLockedUntil,
                    LocalDateTime.now().plusMinutes(properties.getLockDurationMinutes()));
            log.warn("User {} locked after {} failed attempts", user.getUsername(), failCount);
        }
        userAccountMapper.update(null, updateWrapper);
    }

    /**
     * 更新登录成功信息：重置失败计数、记录最后登录时间。
     */
    private void updateLoginSuccess(UserAccountDO user) {
        LambdaUpdateWrapper<UserAccountDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserAccountDO::getId, user.getId());
        updateWrapper.set(UserAccountDO::getLoginFailCount, 0);
        updateWrapper.set(UserAccountDO::getLockedUntil, null);
        updateWrapper.set(UserAccountDO::getLastLoginAt, LocalDateTime.now());
        userAccountMapper.update(null, updateWrapper);
    }
}
