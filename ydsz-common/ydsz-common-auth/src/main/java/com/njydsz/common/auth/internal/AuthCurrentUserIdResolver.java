package com.njydsz.common.auth.internal;

import org.springframework.stereotype.Component;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.lock.spi.CurrentUserIdResolver;
import com.njydsz.common.security.LoginUser;

/**
 * 基于 AuthContext 的当前用户 ID 解析器实现
 *
 * <p>供 ydsz-common-lock 的 {@link CurrentUserIdResolver} SPI 使用， 将 lock 模块与 auth 模块解耦。当 auth
 * 模块存在于类路径时自动注册。
 *
 * <p>此实现遵循依赖倒置原则：lock 模块定义接口，auth 模块提供实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CurrentUserIdResolver
 */
@Component
public class AuthCurrentUserIdResolver implements CurrentUserIdResolver {

  /**
   * 从 AuthContext 获取当前登录用户 ID
   *
   * @return 用户 ID，未登录返回 null
   */
  @Override
  public String getCurrentUserId() {
    LoginUser loginUser = AuthContextUtils.getCurrentOrNull();
    return loginUser != null ? loginUser.getUserId() : null;
  }
}
