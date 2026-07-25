package com.njydsz.userinfo.server.auth;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.service.TokenBlacklistService;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.entity.RoleDO;
import com.njydsz.userinfo.domain.entity.UserAccountDO;
import com.njydsz.userinfo.domain.entity.UserRoleDO;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.userinfo.domain.vo.LoginVO;
import com.njydsz.userinfo.infra.mapper.RoleMapper;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;
import com.njydsz.userinfo.infra.mapper.UserRoleMapper;
import com.njydsz.userinfo.server.metrics.UserInfoMetrics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 认证服务实现。
 *
 * <p>核心能力：
 * <ul>
 *   <li>账号锁定（N 次密码错误后自动锁定）</li>
 *   <li>登录失败计数 + 最后登录信息更新</li>
 *   <li>JWT Token 签发/刷新/吊销</li>
 *   <li>用户角色按 user_role 关联表精确查询</li>
 *   <li>Micrometer 指标埋点</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final TokenService tokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final RedisHashOps redisHashOps;
    private final RedisStringOps redisStringOps;
    private final PasswordEncoder passwordEncoder;
    private final UserInfoMetrics userInfoMetrics;

    private static final long TOKEN_TTL_SECONDS = 7200;
    private static final int MAX_LOGIN_FAIL_COUNT = 5;
    private static final int LOCK_DURATION_MINUTES = 30;
    private static final String LOGIN_FAIL_COUNT_KEY = "auth:login:fail:";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(String username, String password) {
        var sample = userInfoMetrics.startTimer();

        LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAccountDO::getUsername, username);
        wrapper.eq(UserAccountDO::getDeleted, 0);
        UserAccountDO user = userAccountMapper.selectOne(wrapper);

        if (user == null) {
            userInfoMetrics.recordLoginFail();
            userInfoMetrics.stopTimer(sample);
            throw new BusinessException(UserInfoResultCode.USER_NOT_FOUND);
        }

        if ("DISABLED".equals(user.getStatus()) || (user.getStatus() != null && user.getStatus() == 0)) {
            userInfoMetrics.recordLoginFail();
            userInfoMetrics.stopTimer(sample);
            throw new BusinessException(UserInfoResultCode.USER_DISABLED);
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            userInfoMetrics.recordLoginFail();
            userInfoMetrics.stopTimer(sample);
            throw new BusinessException(UserInfoResultCode.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
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

        Map<String, Object> userInfoMap = new HashMap<>();
        userInfoMap.put("userId", user.getId());
        userInfoMap.put("username", user.getUsername());
        userInfoMap.put("roleCode", roleCodes);
        userInfoMap.put("roleName", roleNames);
        userInfoMap.put("tenantId", user.getTenantId());
        redisHashOps.hSetAll(accessToken, userInfoMap, TOKEN_TTL_SECONDS, TimeUnit.SECONDS);

        updateLoginSuccess(user);

        userInfoMetrics.recordLoginSuccess();
        userInfoMetrics.stopTimer(sample);

        LoginVO result = new LoginVO();
        result.setAccessToken(accessToken);
        result.setRefreshToken(refreshToken);
        result.setTokenType("Bearer");
        result.setExpiresIn(TOKEN_TTL_SECONDS);
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
        redisHashOps.delete(accessToken);
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
        result.setExpiresIn(TOKEN_TTL_SECONDS);
        result.setScope("read write");
        return result;
    }

    /**
     * 按 user_role 关联表查询用户角色。
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

        if (failCount >= MAX_LOGIN_FAIL_COUNT) {
            updateWrapper.set(UserAccountDO::getLockedUntil,
                    LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
            log.warn("User {} locked after {} failed attempts", user.getUsername(), failCount);
        }
        userAccountMapper.update(null, updateWrapper);
    }

    /**
     * 更新登录成功信息：重置失败计数、记录最后登录时间和 IP。
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
