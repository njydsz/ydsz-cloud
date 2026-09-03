package com.njydsz.common.auth.context;

import java.util.Map;

import com.njydsz.common.auth.model.ColumnPermissionInfo;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.security.LoginUser;
import com.njydsz.common.tenant.TenantContext;
import com.njydsz.common.tenant.TenantContextHolder;

/**
 * 认证上下文便捷访问工具类。
 *
 * <p>自 26.09.01 起，认证与权限上下文统一收口至 {@link RequestContext}。 本类提供静态便捷 API，内部全部委托 {@link RequestContext}
 * 实现：
 *
 * <ul>
 *   <li>登录用户：{@link #getCurrentOrNull()} / {@link #getUserId()} / {@link #getUsername()}
 *   <li>租户：{@link #getTenantIdOrDefault()} / {@link #getTenantId()}
 *   <li>列权限：{@link #getColumnPermission()} / {@link #setColumnPermission(ColumnPermissionInfo)}
 *   <li>请求级用户信息缓存：{@link #getCachedUserInfoMap()} / {@link #setCachedUserInfoMap(Map)}
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * String userId = AuthContextUtils.getUserId();
 * String tenantId = AuthContextUtils.getTenantIdOrDefault("1");
 * LoginUser user = AuthContextUtils.getCurrentOrNull();
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see RequestContext
 */
public final class AuthContextUtils {

  private AuthContextUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  // ==================== LoginUser 管理 ====================

  /**
   * 设置当前登录用户。
   *
   * <p>同步关键信息（userId/tenantId）到 {@link RequestContext}， 便于跨模块统一访问。
   *
   * @param user 登录用户
   */
  public static void setCurrent(LoginUser user) {
    if (user != null) {
      RequestContext.put(BizContextKeys.KEY_LOGIN_USER, user);
      RequestContext.setUserId(user.getUserId());
      if (user.getTenantId() != null) {
        RequestContext.setTenantId(user.getTenantId());
      }
    }
  }

  /**
   * 获取当前登录用户。
   *
   * @return 当前登录用户
   * @throws SysException 未登录时抛出
   */
  public static LoginUser getCurrent() {
    LoginUser user = getCurrentOrNull();
    if (user == null) {
      throw SysException.builder()
          .code(YdszResultCode.UNAUTHORIZED.getCode())
          .key("error.common.msg_1923bd82")
          .httpStatus(401)
          .build();
    }
    return user;
  }

  /**
   * 获取当前登录用户（允许为空）。
   *
   * @return 当前登录用户；未登录时返回 null
   */
  public static LoginUser getCurrentOrNull() {
    return LoginUser.class.cast(RequestContext.get(BizContextKeys.KEY_LOGIN_USER));
  }

  /**
   * 当前用户 ID。
   *
   * @return 当前用户 ID
   * @throws SysException 未登录时抛出
   */
  public static String getUserId() {
    return getCurrent().getUserId();
  }

  /**
   * 当前用户名。
   *
   * @return 当前用户名
   * @throws SysException 未登录时抛出
   */
  public static String getUsername() {
    return getCurrent().getUsername();
  }

  /**
   * 当前部门 ID。
   *
   * @return 当前部门 ID
   * @throws SysException 未登录时抛出
   */
  public static String getDeptId() {
    return getCurrent().getDeptId();
  }

  /**
   * 当前租户 ID（多租户上下文）。
   *
   * <p>从 {@link TenantContextHolder} 获取 tenantId；未设置时返回默认值 "1"。 适用于后台任务、单元测试等无 HTTP 请求上下文的场景。
   *
   * @return 当前租户 ID；未设置时返回 "1"
   */
  public static String getTenantIdOrDefault() {
    return getTenantIdOrDefault("1");
  }

  /**
   * 当前租户 ID（带自定义默认值）。
   *
   * @param defaultTenantId 默认租户 ID（未设置时使用）
   * @return 当前租户 ID；未设置时返回 defaultTenantId
   */
  public static String getTenantIdOrDefault(String defaultTenantId) {
    String tenantId = TenantContextHolder.getTenantId();
    if (tenantId == null || tenantId.isEmpty()) {
      return defaultTenantId == null || defaultTenantId.isEmpty() ? "1" : defaultTenantId;
    }
    return tenantId;
  }

  /**
   * 校验权限。
   *
   * @param perm 权限编码
   * @throws SysException 无权限时抛出
   */
  public static void requirePermission(String perm) {
    LoginUser user = getCurrent();
    if (!user.hasPermission(perm)) {
      throw SysException.builder()
          .code(YdszResultCode.FORBIDDEN.getCode())
          .key("error.common.msg_1e40057e")
          .params(new Object[] {perm})
          .httpStatus(403)
          .build();
    }
  }

  /**
   * 校验任一权限。
   *
   * @param perms 权限编码列表
   * @throws SysException 全部权限都不拥有时抛出
   */
  public static void requireAnyPermission(String... perms) {
    LoginUser user = getCurrent();
    for (String p : perms) {
      if (user.hasPermission(p)) {
        return;
      }
    }
    throw SysException.builder()
        .code(YdszResultCode.FORBIDDEN.getCode())
        .key("error.common.msg_ad4fff48")
        .httpStatus(403)
        .build();
  }

  // ==================== 列权限管理 ====================

  /**
   * 获取列权限信息。
   *
   * @return 列权限信息，未设置时返回 null
   */
  public static ColumnPermissionInfo getColumnPermission() {
    return (ColumnPermissionInfo) RequestContext.get(BizContextKeys.KEY_COLUMN_PERMISSION);
  }

  /**
   * 设置列权限信息。
   *
   * @param columnPermission 列权限信息
   */
  public static void setColumnPermission(ColumnPermissionInfo columnPermission) {
    RequestContext.put(BizContextKeys.KEY_COLUMN_PERMISSION, columnPermission);
  }

  /**
   * 判断是否有列权限。
   *
   * @return true 表示有列权限且权限信息非空
   */
  public static boolean hasColumnPermission() {
    ColumnPermissionInfo info = getColumnPermission();
    return info != null && !info.isEmpty();
  }

  // ==================== 请求级缓存 ====================

  /**
   * 获取请求级缓存的用户信息 Map。
   *
   * <p>由 RbacPermissionEvaluator 首次加载后写入，后续同一请求内直接读取， 避免反复 Redis 调用。
   *
   * @return 缓存的用户信息 Map，未设置时返回 null
   */
  public static Map<String, Object> getCachedUserInfoMap() {
    return RequestContext.getCachedUserInfoMap();
  }

  /**
   * 设置请求级缓存的用户信息 Map。
   *
   * @param userInfoMap 用户信息 Map
   */
  public static void setCachedUserInfoMap(Map<String, Object> userInfoMap) {
    RequestContext.put(BizContextKeys.KEY_CACHED_USER_INFO_MAP, userInfoMap);
  }

  // ==================== 租户 ====================

  /**
   * 获取租户 ID。
   *
   * <p>从 {@link TenantContextHolder} 获取当前租户 ID。
   *
   * @return 租户 ID，未设置时返回 null
   */
  public static String getTenantId() {
    return TenantContextHolder.getTenantId();
  }

  /**
   * 设置租户 ID。
   *
   * <p>通过 {@link TenantContextHolder} 设置当前租户 ID。
   *
   * @param tenantId 租户 ID
   */
  public static void setTenantId(String tenantId) {
    if (tenantId == null) {
      TenantContextHolder.clear();
      return;
    }
    TenantContextHolder.set(TenantContext.builder(tenantId).build());
  }

  // ==================== 清理 ====================

  /**
   * 清理当前线程的上下文数据。
   *
   * <p>必须在请求结束时调用（通常由 Filter 或 Interceptor 负责）， 防止 ThreadLocal 内存泄漏。
   */
  public static void clear() {
    RequestContext.clear();
  }
}
