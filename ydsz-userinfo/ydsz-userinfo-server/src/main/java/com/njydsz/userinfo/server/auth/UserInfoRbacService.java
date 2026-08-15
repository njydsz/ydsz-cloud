package com.njydsz.userinfo.server.auth;

import java.util.Collections;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.service.RbacUserInfoService;
import com.njydsz.common.auth.util.AccessTokenUtils;
import com.njydsz.common.redis.service.ops.RedisHashOps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户信息 RBAC 服务实现。
 *
 * <p>从 Redis Token Hash 加载用户信息，实现 common-auth 的 RbacUserInfoService SPI。
 * 登录时由 AuthService 将用户信息写入 Redis Hash，本类负责读取。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserInfoRbacService implements RbacUserInfoService {

    private final RedisHashOps redisHashOps;

    @Override
    public UserInfo loadUserInfo(String accessToken) {
        Map<String, Object> map = loadUserInfoMap(accessToken);
        if (map == null || map.isEmpty()) {
            return null;
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(getString(map, "userId"));
        userInfo.setUsername(getString(map, "username"));
        userInfo.setRoleCode(getString(map, "roleCode"));
        userInfo.setRoleName(getString(map, "roleName"));
        userInfo.setDeptId(getString(map, "deptId"));
        userInfo.setTenantId(getString(map, "tenantId"));
        return userInfo.isValid() ? userInfo : null;
    }

    @Override
    public Map<String, Object> loadUserInfoMap(String accessToken) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = redisHashOps.hGetAll(accessToken.trim(), Object.class);
        return map == null ? Collections.emptyMap() : map;
    }

    @Override
    public String loadCurrentToken() {
        return AccessTokenUtils.resolve();
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
