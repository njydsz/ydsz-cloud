package com.njydsz.userinfo.server.auth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.njydsz.common.auth.token.JwtTokenService;
import com.njydsz.common.auth.token.TokenBlacklistService;
import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.userinfo.domain.entity.RoleDO;
import com.njydsz.userinfo.domain.entity.UserAccountDO;
import com.njydsz.userinfo.infra.mapper.RoleMapper;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 认证服务实现。使用 common-auth JwtTokenService 签发/验证/吊销 Token。
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
    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final RedisHashOps redisHashOps;
    private final PasswordEncoder passwordEncoder;

    private static final long TOKEN_TTL_SECONDS = 7200;

    @Override
    public LoginResult login(String username, String password) {
        LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAccountDO::getUsername, username);
        wrapper.eq(UserAccountDO::getDeleted, 0);
        UserAccountDO user = userAccountMapper.selectOne(wrapper);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        List<RoleDO> roles = loadUserRoles(user.getId());
        String roleCodes = roles.stream().map(RoleDO::getRoleCode)
                .collect(Collectors.joining(","));
        String roleNames = roles.stream().map(RoleDO::getRoleName)
                .collect(Collectors.joining(","));

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("roleCode", roleCodes);
        claims.put("tenantId", user.getTenantId());

        String accessToken = jwtTokenService.generateAccessToken(claims);
        String refreshToken = jwtTokenService.generateRefreshToken(claims);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("roleCode", roleCodes);
        userInfo.put("roleName", roleNames);
        userInfo.put("tenantId", user.getTenantId());
        redisHashOps.hSetAll(accessToken, userInfo, TOKEN_TTL_SECONDS, TimeUnit.SECONDS);

        LoginResult result = new LoginResult();
        result.setAccessToken(accessToken);
        result.setRefreshToken(refreshToken);
        result.setExpiresIn(TOKEN_TTL_SECONDS);
        result.setUserId(user.getId());
        result.setUsername(user.getUsername());
        result.setRealName(user.getRealName());
        return result;
    }

    @Override
    public void logout(String accessToken) {
        tokenBlacklistService.blacklist(accessToken);
        redisHashOps.delete(accessToken);
    }

    @Override
    public LoginResult refresh(String refreshToken) {
        String accessToken = jwtTokenService.refreshAccessToken(refreshToken);
        LoginResult result = new LoginResult();
        result.setAccessToken(accessToken);
        result.setRefreshToken(refreshToken);
        result.setExpiresIn(TOKEN_TTL_SECONDS);
        return result;
    }

    private List<RoleDO> loadUserRoles(String userId) {
        LambdaQueryWrapper<RoleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoleDO::getDeleted, 0);
        wrapper.eq(RoleDO::getStatus, "ENABLED");
        return roleMapper.selectList(wrapper);
    }
}
