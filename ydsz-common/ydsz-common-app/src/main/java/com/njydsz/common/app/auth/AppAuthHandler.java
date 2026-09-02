package com.njydsz.common.app.auth;

import com.njydsz.common.auth.handler.AbstractAuthHandler;
import com.njydsz.common.auth.model.YdszAuthInfo;

/**
 * App 端认证信息处理器
 *
 * <p>通过模板方法模式，仅提供 {@link AppAuthInfo} 实例创建， 解析逻辑由基类 {@link AbstractAuthHandler#getAuthInfo} 统一处理。
 *
 * <p><b>APP 与 WEB 差异：</b>App 端不依赖浏览器 Cookie， 通常基于 {@code X-App-Token} 等自定义请求头进行认证， 业务方可注入自定义 {@code
 * AbstractAuthHandler} 子类以适配不同客户端协议。
 *
 * <p><b>注册方式：</b>由 {@code AppMvcConfiguration} 通过 {@code @Bean} + {@code @ConditionalOnMissingBean}
 * 注册，业务方可覆盖。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see AbstractAuthHandler
 * @see AppAuthInfo
 */
public class AppAuthHandler extends AbstractAuthHandler {

  /**
   * 创建 App 端认证信息实例
   *
   * @return 新的 {@link AppAuthInfo} 实例
   */
  @Override
  protected YdszAuthInfo createAuthInfo() {
    return new AppAuthInfo();
  }
}
