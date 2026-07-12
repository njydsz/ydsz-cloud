package com.njydsz.pmis.common.auth.service.impl;

import com.njydsz.pmis.common.auth.model.UserInfo;
import com.njydsz.pmis.common.auth.service.RbacUserInfoService;
import com.njydsz.pmis.common.auth.util.AccessTokenUtils;
import com.njydsz.pmis.common.redis.service.ops.RedisHashOps;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.Map;

/**
 * 基于 Redis 的 RBAC 用户信息服务实现。
 *
 * <p>从 Redis token hash 中加载用户信息，实现 {@link RbacUserInfoService} 接口。
 *
 * <p><b>数据来源：</b>
 * <ul>
 *   <li>accessToken 作为 Redis Hash 的 key</li>
 *   <li>使用 hmget 获取所有字段</li>
 *   <li>userInfo 中必须包含 roleCode 字段（支持多角色 CSV 格式）</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see RbacUserInfoService
 */
@RequiredArgsConstructor
public class RedisRbacUserInfoService implements RbacUserInfoService {

    private final RedisHashOps redisHashOps;

    /**
     * 根据访问令牌加载用户信息。
     *
     * @param accessToken 访问令牌
     * @return 用户信息对象，令牌无效或用户信息不完整时返回 {@code null}
     */
    @Override
    public UserInfo loadUserInfo(String accessToken) {
        Map<String, Object> map = loadUserInfoMap(accessToken);
        if (map == null || map.isEmpty()) {
            return null;
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(getStringValue(map, "userId"));
        userInfo.setUsername(getStringValue(map, "username"));
        userInfo.setRoleCode(getStringValue(map, "roleCode"));
        userInfo.setRoleName(getStringValue(map, "roleName"));
        userInfo.setDeptId(getStringValue(map, "deptId"));
        userInfo.setTenantId(getStringValue(map, "tenantId"));
        return userInfo.isValid() ? userInfo : null;
    }

    /**
     * 根据访问令牌从 Redis Hash 中加载用户信息 Map。
     *
     * @param accessToken 访问令牌
     * @return 用户信息 Map，令牌为空或 Redis 中无数据时返回空 Map
     */
    @Override
    public Map<String, Object> loadUserInfoMap(String accessToken) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = redisHashOps.hGetAll(accessToken.trim(), Object.class);
        if (map == null) {
            return Collections.emptyMap();
        }
        return map;
    }

    /**
     * 从当前请求上下文中解析访问令牌。
     *
     * @return 访问令牌字符串
     */
    @Override
    public String loadCurrentToken() {
        return AccessTokenUtils.resolve();
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}