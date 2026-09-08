package com.njydsz.common.auth.util;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.security.LoginUser;

/**
 * 安全上下文工具类（便捷入口）。
 *
 * <p>为业务模块提供统一的当前用户信息查询入口，内部委托给 {@link AuthContextUtils} 实现。
 * 第三方框架集成、遗留代码迁移场景推荐使用本类，新代码可直接使用 {@link AuthContextUtils}。
 *
 * <ul>
 *   <li>{@link #getCurrentUserId()} — 获取当前登录用户 ID（未登录时抛异常）</li>
 *   <li>{@link #getCurrentUserIdOrNull()} — 获取当前登录用户 ID（未登录时返回 null）</li>
 *   <li>{@link #getCurrentUserName()} — 获取当前登录用户姓名</li>
 *   <li>{@link #getCurrentUser()} — 获取当前登录用户完整对象</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
public final class SecurityUtils {

  private SecurityUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 获取当前登录用户 ID。
   *
   * @return 当前用户 ID
   * @throws com.njydsz.common.exception.custom.SysException 未登录时抛出 401
   */
  public static String getCurrentUserId() {
    return AuthContextUtils.getUserId();
  }

  /**
   * 获取当前登录用户 ID（允许为空）。
   *
   * @return 当前用户 ID；未登录时返回 null
   */
  public static String getCurrentUserIdOrNull() {
    LoginUser user = AuthContextUtils.getCurrentOrNull();
    return user != null ? user.getUserId() : null;
  }

  /**
   * 获取当前登录用户姓名。
   *
   * @return 当前用户姓名
   */
  public static String getCurrentUserName() {
    return AuthContextUtils.getUsername();
  }

  /**
   * 获取当前登录用户完整信息。
   *
   * @return 当前登录用户对象；未登录时返回 null
   */
  public static LoginUser getCurrentUser() {
    return AuthContextUtils.getCurrentOrNull();
  }

  /**
   * 获取当前租户 ID。
   *
   * @return 当前租户 ID；未设置时返回 null
   */
  public static String getCurrentTenantId() {
    return AuthContextUtils.getTenantId();
  }
}
