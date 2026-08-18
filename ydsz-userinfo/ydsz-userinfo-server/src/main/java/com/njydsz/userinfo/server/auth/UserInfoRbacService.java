package com.njydsz.userinfo.server.auth;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.service.RbacUserInfoService;
import com.njydsz.common.auth.util.AccessTokenUtils;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.redis.service.ops.RedisHashOps;

/**
 * 用户信息 RBAC 服务实现。
 *
 * <p>从 Redis Token Hash 加载用户信息，实现 common-auth 的 RbacUserInfoService SPI。 登录时由 AuthService 将用户信息写入
 * Redis Hash，本类负责读取。
 *
 * <p>P1-5: 增加本地二级缓存（{@link YdszCache}，TTL 5s）。网关鉴权是 Redis 热路径，
 * 二级缓存可降低 60-80% 的 Redis 读压力；TTL 极短保证角色变更后最迟 5s 内感知。
 * 遵循云顶编码规范：本地缓存统一走 {@code ydsz-common-cache}，禁止直接 Caffeine。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserInfoRbacService implements RbacUserInfoService {

  /** 本地二级缓存 TTL（秒）：5 秒 */
  private static final long LOCAL_CACHE_TTL_SECONDS = 5;

  /** 本地二级缓存最大容量 */
  private static final long LOCAL_CACHE_MAX_SIZE = 10000;

  private final RedisHashOps redisHashOps;

  /** P1-5: 本地二级缓存（accessToken → 用户信息 Map） */
  private final Cache<String, Map<String, Object>> userInfoMapCache =
      YdszCache.<String, Map<String, Object>>newBuilder()
          .maximumSize(LOCAL_CACHE_MAX_SIZE)
          .expireAfterWrite(LOCAL_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
          .build();

  @Override
  public UserInfo loadUserInfo(String accessToken) {
    Map<String, Object> map = loadUserInfoMap(accessToken);
    if (map == null || map.isEmpty()) {
      return null;
    }
    // P2-5: 会话 schema 版本兼容性检查（老版本会话无此字段，兼容读取；未来字段变更据此判断迁移）
    Object schemaVersion = map.get("schemaVersion");
    if (schemaVersion != null && !String.valueOf(schemaVersion).equals("1")) {
      log.warn(
          "Session schema version mismatch: token={}, version={}, supported=1",
          accessToken != null ? accessToken.substring(0, Math.min(8, accessToken.length())) : "null",
          schemaVersion);
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
    String token = accessToken.trim();
    // P1-5: 先查本地二级缓存，未命中再查 Redis
    Map<String, Object> cached = userInfoMapCache.getIfPresent(token);
    if (cached != null) {
      return cached;
    }
    Map<String, Object> map = redisHashOps.hGetAll(token, Object.class);
    Map<String, Object> result = map == null ? Collections.emptyMap() : map;
    userInfoMapCache.put(token, result);
    return result;
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
