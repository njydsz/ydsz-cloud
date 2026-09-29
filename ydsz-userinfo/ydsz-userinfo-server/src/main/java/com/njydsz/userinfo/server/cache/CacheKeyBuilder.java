package com.njydsz.userinfo.server.cache;

import org.springframework.stereotype.Component;

import com.njydsz.common.cache.support.AbstractModuleCacheKeyBuilder;

/**
 * Userinfo 模块缓存键构造器（P1-1 整改：继承公共基类 {@link AbstractModuleCacheKeyBuilder}）。
 *
 * <p>为 Userinfo 模块的角色权限、敏感校验、设备信任、LDAP 同步令牌等缓存键提供租户感知的统一生成能力，
 * 替代原来自建的字符串拼接（{@code "userinfo:roles:"}、{@code "userinfo:device:trusted:"} 等）。
 *
 * <p><b>统一格式：</b>{@code ydsz:{tenantId}:userinfo:{entity}:{id}}
 *
 * @author ydsz-team
 * @since 26.09.29
 */
@Component("userinfoCacheKeyBuilder")
public class CacheKeyBuilder extends AbstractModuleCacheKeyBuilder {

  /** 模块标识：统一身份认证与权限引擎 */
  private static final String MODULE = "userinfo";

  /** 构造 Userinfo 模块缓存键构造器。 */
  public CacheKeyBuilder() {
    super(MODULE);
  }

  // ============================== 角色权限 key ==============================

  /**
   * 生成「用户角色列表」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:userinfo:roles:{userId}}
   *
   * @param userId 用户 ID
   * @return 租户隔离的缓存键
   */
  public String userRoles(String userId) {
    return buildKey("roles", userId);
  }

  // ============================== 敏感操作校验 key ==============================

  /**
   * 生成「敏感操作已校验」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:userinfo:sensitive:verified:{userId}}
   *
   * @param userId 用户 ID
   * @return 租户隔离的缓存键
   */
  public String sensitiveVerified(String userId) {
    return buildKeyPattern("sensitive", "verified", userId);
  }

  // ============================== 设备信任 key ==============================

  /**
   * 生成「用户信任设备」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:userinfo:device:trusted:{userId}:{deviceFingerprint}}
   *
   * @param userId 用户 ID
   * @param deviceFingerprint 设备指纹
   * @return 租户隔离的缓存键
   */
  public String trustedDevice(String userId, String deviceFingerprint) {
    return buildKeyPattern("device", "trusted", userId, deviceFingerprint);
  }

  // ============================== 用户名缓存 key ==============================

  /**
   * 生成「用户姓名拼装」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:userinfo:name:{userId}}
   *
   * @param userId 用户 ID
   * @return 租户隔离的缓存键
   */
  public String userName(String userId) {
    return buildKey("name", userId);
  }

  // ============================== LDAP 同步 key ==============================

  /**
   * 生成「LDAP 同步游标令牌」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:userinfo:provision:sync-token:{domain}}
   *
   * @param domain LDAP 域标识
   * @return 租户隔离的缓存键
   */
  public String provisionSyncToken(String domain) {
    return buildKeyPattern("provision", "sync-token", domain);
  }

  // ============================== OAuth2 设备授权 key ==============================

  /**
   * 生成「OAuth2 设备码」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:userinfo:oauth2:device:code:{deviceCode}}
   *
   * @param deviceCode 设备码
   * @return 租户隔离的缓存键
   */
  public String oauth2DeviceCode(String deviceCode) {
    return buildKeyPattern("oauth2", "device", "code", deviceCode);
  }

  /**
   * 生成「OAuth2 用户码」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:userinfo:oauth2:device:usercode:{userCode}}
   *
   * @param userCode 用户码
   * @return 租户隔离的缓存键
   */
  public String oauth2UserCode(String userCode) {
    return buildKeyPattern("oauth2", "device", "usercode", userCode);
  }

  // ============================== RBAC 权限 key ==============================

  /**
   * 生成「用户权限摘要」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:userinfo:rbac:{userId}}
   *
   * @param userId 用户 ID
   * @return 租户隔离的缓存键
   */
  public String userRbac(String userId) {
    return buildKey("rbac", userId);
  }

  /**
   * 生成「部门角色映射」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:userinfo:dept:role:{deptId}}
   *
   * @param deptId 部门 ID
   * @return 租户隔离的缓存键
   */
  public String departmentRoles(String deptId) {
    return buildKeyPattern("dept", "role", deptId);
  }
}
